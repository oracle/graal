/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted;

import static com.oracle.svm.hosted.SubstrateBytecodeHandlerStub.unwrap;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.common.meta.MethodVariant;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.bytecode.Bytecodes;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.extended.PendingExceptionStateValueNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig;
import jdk.graal.compiler.phases.util.BytecodeHandlerConfig.ArgumentInfo;
import jdk.graal.compiler.phases.util.BytecodeHandlerStubHelper;
import jdk.graal.compiler.phases.util.BytecodeInterpreterAnnotations;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A {@link NodePlugin} implementation that collects invocations of bytecode handlers and
 * generates stubs for them.
 * <p>
 * This plugin is responsible for detecting invocations of methods annotated with
 * {@code @BytecodeInterpreterHandler} within methods that declare bytecode-handler metadata. It
 * generates a stub for the handler method, registers that stub as a root method in the analysis
 * universe, and records parser-time information needed to repair {@code copyFromReturn} locals on
 * generated-stub exception edges.
 */
public final class BytecodeHandlerInvokePlugin implements NodePlugin {

    private final EconomicMap<BytecodeHandlerStubKey, ResolvedJavaMethod> registeredBytecodeHandlers;
    /*
     * Keep callsite metadata scoped by graph: method and bci identify the bytecode, but the
     * copyFromReturn value is graph-local. The graph key and stored value reference are weak so
     * parser metadata cannot keep temporary graphs or values alive when no matching exception edge
     * is emitted.
     */
    private final Map<StructuredGraph, EconomicMap<BytecodeHandlerInvokeKey, BytecodeHandlerInvoke>> registeredHandlerInvokes = new WeakHashMap<>();
    private final SubstrateBytecodeHandlerStubHelper stubHolder;
    private final boolean threadingEnabled;
    private final IntConsumer handlerArityConsumer;

    private final EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod> nextOpcodeCache = EconomicMap.create();
    public BytecodeHandlerInvokePlugin(EconomicMap<BytecodeHandlerStubKey, ResolvedJavaMethod> registeredBytecodeHandlers,
                    SubstrateBytecodeHandlerStubHelper stubHolder, boolean threadingEnabled, IntConsumer handlerArityConsumer) {
        this.registeredBytecodeHandlers = registeredBytecodeHandlers;
        this.stubHolder = stubHolder;
        this.threadingEnabled = threadingEnabled;
        this.handlerArityConsumer = handlerArityConsumer;
    }

    private static ResolvedJavaMethod nextOpcodeMethod(ResolvedJavaType holder) {
        ResolvedJavaMethod nextOpcode = BytecodeInterpreterAnnotations.getUniqueFetchOpcodeMethod(holder);
        GraalError.guarantee(nextOpcode.getSignature().getReturnType(nextOpcode.getDeclaringClass()).getJavaKind() != JavaKind.Void,
                        "Method annotated with BytecodeInterpreterFetchOpcode must not return void: %s", nextOpcode);
        return nextOpcode;
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod target, ValueNode[] oldArguments) {
        ResolvedJavaMethod enclosingMethod = b.getMethod();
        boolean originalMethod = !(enclosingMethod instanceof MethodVariant sm) || sm.isOriginalMethod();

        if (originalMethod && tryRegisterSwitchExtensionInvoke(b, enclosingMethod, target, oldArguments)) {
            return false;
        }

        if (!BytecodeInterpreterAnnotations.hasBytecodeInterpreterHandlerConfig(enclosingMethod)) {
            return false;
        }

        AnnotationValue handlerAnnotationValue = BytecodeInterpreterAnnotations.getBytecodeInterpreterHandler(target);
        if (handlerAnnotationValue == null) {
            return false;
        }

        BytecodeHandlerConfig handlerConfig = BytecodeHandlerConfig.getHandlerConfig(enclosingMethod, target, true);

        boolean threading = threadingEnabled && handlerAnnotationValue.getBoolean("threading");
        boolean safepoint = handlerAnnotationValue.getBoolean("safepoint");
        int templatesLength = handlerConfig.getTemplatesLength();

        if (handlerArityConsumer != null) {
            handlerArityConsumer.accept(handlerConfig.getStubAbiArgumentInfos().size());
        }
        if (!originalMethod) {
            /*
             * Non-original variants are not processed by SubstrateOutlineBytecodeHandlerPhase, so
             * these handler invokes stay direct Java calls. Do not register pending-state metadata:
             * there will be no outlined callee stub running writeOnCallee() for the exception edge
             * to consume.
             */
            return false;
        }
        ResolvedJavaType interpreterHolder = unwrap(enclosingMethod.getDeclaringClass());
        String stubName = BytecodeHandlerStubHelper.getStubName(target);

        ResolvedJavaMethod nextOpcode = null;
        if (threading) {
            if (!nextOpcodeCache.containsKey(enclosingMethod)) {
                ResolvedJavaMethod temp = nextOpcodeMethod(target.getDeclaringClass());
                synchronized (nextOpcodeCache) {
                    nextOpcodeCache.putIfAbsent(enclosingMethod, temp);
                }
            }
            nextOpcode = nextOpcodeCache.get(enclosingMethod);
        }
        AnalysisUniverse universe = ((AnalysisMetaAccess) b.getMetaAccess()).getUniverse();
        synchronized (registeredBytecodeHandlers) {
            if (threading) {
                for (int templateIndex = 0; templateIndex < templatesLength; templateIndex++) {
                    BytecodeHandlerStubKey defaultHandlerKey = BytecodeHandlerStubKey.createDefaultHandlerKey(interpreterHolder, handlerConfig, templateIndex);
                    if (registeredBytecodeHandlers.containsKey(defaultHandlerKey)) {
                        continue;
                    }
                    SubstrateBytecodeHandlerStub defaultHandlerStub = new SubstrateBytecodeHandlerStub(stubHolder, unwrap(target.getDeclaringClass()),
                                    stubNameForTemplate("__stub_defaultHandler", templateIndex, templatesLength), interpreterHolder, handlerConfig, false, null, false, true, null,
                                    templateIndex);
                    AnalysisMethod defaultStubWrapper = universe.lookup(defaultHandlerStub);
                    universe.getBigbang().addRootMethod(defaultStubWrapper, true, "Default bytecode handler stub");

                    registeredBytecodeHandlers.put(defaultHandlerKey, defaultStubWrapper);
                }
            }

            for (int templateIndex = 0; templateIndex < templatesLength; templateIndex++) {
                SubstrateBytecodeHandlerStub stub = new SubstrateBytecodeHandlerStub(stubHolder, unwrap(target.getDeclaringClass()),
                                stubNameForTemplate(stubName, templateIndex, templatesLength), interpreterHolder, handlerConfig, threading, nextOpcode, safepoint, false, target,
                                templateIndex);
                AnalysisMethod handlerStubWrapper = universe.lookup(stub);
                universe.getBigbang().addRootMethod(handlerStubWrapper, true, "Bytecode handler stub " + stubName);
                registeredBytecodeHandlers.put(BytecodeHandlerStubKey.create(unwrap(target), interpreterHolder, handlerConfig, templateIndex), handlerStubWrapper);
            }
        }
        /*
         * The parser's exception-dispatch hook receives only the current bci and dispatch state.
         * Record the graph-local invoke argument here so the later hook can insert an
         * exception-path proxy for copyFromReturn values if the bytecode stores the invoke result
         * back to a local.
         */
        registerHandlerInvoke(b, enclosingMethod, handlerConfig, oldArguments);

        return false;
    }

    private static String stubNameForTemplate(String stubName, int templateIndex, int templatesLength) {
        return templatesLength == 1 ? stubName : stubName + templateIndex;
    }

    private boolean tryRegisterSwitchExtensionInvoke(GraphBuilderContext b, ResolvedJavaMethod enclosingMethod, ResolvedJavaMethod target, ValueNode[] arguments) {
        if (!threadingEnabled) {
            return false;
        }
        AnnotationValue handlerConfigAnnotation = BytecodeInterpreterAnnotations.getBytecodeInterpreterHandlerConfig(target);
        AnnotationValue enclosingHandlerConfigAnnotation = BytecodeInterpreterAnnotations.getBytecodeInterpreterHandlerConfig(enclosingMethod);
        if (handlerConfigAnnotation == null ||
                        !sameInterpreterByAnnotation(handlerConfigAnnotation, enclosingHandlerConfigAnnotation)) {
            return false;
        }
        if (!hasTrailingArgumentsAfterAnnotatedPrefix(handlerConfigAnnotation, target)) {
            return false;
        }

        /*
         * Switch partition helpers are generated as extensions of the enclosing switch, not as
         * handler targets to outline again. They carry the same handler-config prefix followed by
         * private selector state, so detect this shape before the regular handler-stub path. For
         * exception dispatch we only need copyFromReturn metadata, not a complete generated-stub
         * ABI model. If the helper is later inlined, the SVM outline phase decides per cloned
         * exception path whether a generated stub actually published pending state or whether the
         * original input value must be kept.
         */
        CopyFromReturnInfo copyFromReturnInfo = findCopyFromReturnInfo(handlerConfigAnnotation, target);
        if (copyFromReturnInfo != null) {
            registerHandlerInvoke(b, enclosingMethod, copyFromReturnInfo, arguments, PendingExceptionStateValueNode.Source.INFER);
        }
        return true;
    }

    /**
     * Compares all {@code BytecodeInterpreterHandlerConfig} annotation fields except
     * {@code secondarySwitch}, which does not affect whether two methods belong to the same
     * interpreter.
     */
    private static boolean sameInterpreterByAnnotation(AnnotationValue first, AnnotationValue second) {
        return second != null && first.getInt("maximumOperationCode") == second.getInt("maximumOperationCode") &&
                        first.getList("arguments", AnnotationValue.class).equals(second.getList("arguments", AnnotationValue.class));
    }

    private static boolean hasTrailingArgumentsAfterAnnotatedPrefix(AnnotationValue handlerConfigAnnotation, ResolvedJavaMethod target) {
        int annotatedArgumentCount = handlerConfigAnnotation.getList("arguments", AnnotationValue.class).size();
        int actualArgumentCount = target.getSignature().getParameterCount(false) + (target.isStatic() ? 0 : 1);
        return actualArgumentCount > annotatedArgumentCount;
    }

    @Override
    public FixedWithNextNode instrumentExceptionDispatch(StructuredGraph graph, int bci, FixedWithNextNode afterExceptionLoaded, FrameStateBuilder dispatchState,
                    Supplier<FrameState> frameStateFunction) {
        /*
         * Invoke exception edges are built immediately after parsing the invoke bytecode. Consume
         * the recorded metadata for this graph/method/bci so duplicated bytecodes or later parses
         * cannot accidentally reuse it.
         */
        BytecodeHandlerInvoke handlerInvoke = removeHandlerInvoke(graph, dispatchState.getMethod(), bci);
        if (handlerInvoke == null) {
            return afterExceptionLoaded;
        }
        FixedWithNextNode insertAfter = afterExceptionLoaded;

        ValueNode copyFromReturnArgument = handlerInvoke.copyFromReturnArgument();
        if (copyFromReturnArgument != null) {
            int returnLocalIndex = findCopyFromReturnLocalIndex(handlerInvoke.bytecode(), bci, handlerInvoke.returnLocalKind());
            if (returnLocalIndex >= 0) {
                PendingExceptionStateValueNode pendingExceptionValue = graph.add(new PendingExceptionStateValueNode(copyFromReturnArgument, handlerInvoke.copyFromReturnSlotIndex(),
                                handlerInvoke.copyFromReturnKind(), handlerInvoke.source()));
                insertAfter.setNext(pendingExceptionValue);
                insertAfter = pendingExceptionValue;
                dispatchState.storeLocal(returnLocalIndex, handlerInvoke.returnLocalKind(), pendingExceptionValue);
            }
        }
        return insertAfter;
    }

    private void registerHandlerInvoke(GraphBuilderContext b, ResolvedJavaMethod enclosingMethod, BytecodeHandlerConfig handlerConfig, ValueNode[] arguments) {
        registerHandlerInvoke(b, enclosingMethod, handlerConfig, arguments, PendingExceptionStateValueNode.Source.STUB);
    }

    private void registerHandlerInvoke(GraphBuilderContext b, ResolvedJavaMethod enclosingMethod, BytecodeHandlerConfig handlerConfig,
                    ValueNode[] arguments, PendingExceptionStateValueNode.Source source) {
        ArgumentInfo copyFromReturn = handlerConfig.getCopyFromReturnArgument();
        if (copyFromReturn == null) {
            return;
        }
        registerHandlerInvoke(b, enclosingMethod,
                        new CopyFromReturnInfo(copyFromReturn.originalIndex(), copyFromReturn.index(), copyFromReturn.type().getJavaKind()),
                        arguments, source);
    }

    private void registerHandlerInvoke(GraphBuilderContext b, ResolvedJavaMethod enclosingMethod, CopyFromReturnInfo copyFromReturnInfo,
                    ValueNode[] arguments, PendingExceptionStateValueNode.Source source) {
        /*
         * Record only copyFromReturn. Mutable virtual-expanded fields are restored at
         * generated-stub invoke exception edges, not through parser-local pending-state proxies.
         */
        GraalError.guarantee(copyFromReturnInfo.originalParameterIndex() < arguments.length, "copyFromReturn argument %s is missing from %s",
                        copyFromReturnInfo, b.getMethod());
        BytecodeHandlerInvokeKey key = new BytecodeHandlerInvokeKey(enclosingMethod, b.bci());
        BytecodeHandlerInvoke handlerInvoke = new BytecodeHandlerInvoke(b.getCode(),
                        copyFromReturnInfo.kind().getStackKind(),
                        copyFromReturnInfo.abiSlotIndex(),
                        copyFromReturnInfo.kind(),
                        new WeakReference<>(arguments[copyFromReturnInfo.originalParameterIndex()]),
                        source);
        synchronized (registeredHandlerInvokes) {
            EconomicMap<BytecodeHandlerInvokeKey, BytecodeHandlerInvoke> graphInvokes = registeredHandlerInvokes.computeIfAbsent(b.getGraph(), _ -> EconomicMap.create());
            graphInvokes.put(key, handlerInvoke);
        }
    }

    private static CopyFromReturnInfo findCopyFromReturnInfo(AnnotationValue handlerConfigAnnotation, ResolvedJavaMethod target) {
        List<AnnotationValue> argumentAnnotations = handlerConfigAnnotation.getList("arguments", AnnotationValue.class);
        ResolvedJavaType declaringClass = target.getDeclaringClass();
        Signature signature = target.getSignature();
        JavaKind returnKind = signature.getReturnType(declaringClass).getJavaKind();

        int originalParameterIndex = 0;
        int abiSlotIndex = 0;
        if (!target.isStatic()) {
            GraalError.guarantee(!argumentAnnotations.isEmpty(), "Missing receiver argument config for %s", target);
            abiSlotIndex = nextAbiSlotIndex(argumentAnnotations.get(originalParameterIndex), declaringClass, abiSlotIndex, true);
            originalParameterIndex++;
        }

        int parameterCount = signature.getParameterCount(false);
        CopyFromReturnInfo copyFromReturnInfo = null;
        for (int parameterIndex = 0; parameterIndex < parameterCount && originalParameterIndex < argumentAnnotations.size(); parameterIndex++, originalParameterIndex++) {
            AnnotationValue parameterConfig = argumentAnnotations.get(originalParameterIndex);
            ResolvedJavaType parameterType = signature.getParameterType(parameterIndex, declaringClass).resolve(declaringClass);
            String expansionKind = expansionKind(parameterConfig);
            if (!"VIRTUAL".equals(expansionKind) && parameterConfig.getBoolean("returnValue")) {
                GraalError.guarantee(copyFromReturnInfo == null, "Multiple arguments with returnValue set to true in %s", target);
                GraalError.guarantee(returnKind != JavaKind.Void, "returnValue argument in %s cannot copy from void return", target);
                GraalError.guarantee(parameterType.getJavaKind().getStackKind() == returnKind.getStackKind(),
                                "returnValue argument type %s does not match return type %s", parameterType.getJavaKind(), returnKind);
                copyFromReturnInfo = new CopyFromReturnInfo(originalParameterIndex, abiSlotIndex, returnKind);
            }
            abiSlotIndex = nextAbiSlotIndex(parameterConfig, parameterType, abiSlotIndex, false);
        }
        GraalError.guarantee(originalParameterIndex == argumentAnnotations.size(), "Unused argument config for %s", target);
        return copyFromReturnInfo;
    }

    private static int nextAbiSlotIndex(AnnotationValue argumentConfig, ResolvedJavaType argumentType, int currentAbiSlotIndex, boolean receiver) {
        return switch (expansionKind(argumentConfig)) {
            case "NONE" -> currentAbiSlotIndex + 1;
            case "MATERIALIZED" -> currentAbiSlotIndex + 1 + countMaterializedFields(argumentConfig, argumentType);
            case "VIRTUAL" -> {
                GraalError.guarantee(!receiver, "Receiver cannot be VIRTUAL");
                yield currentAbiSlotIndex + countVirtualAbiFields(argumentConfig, argumentType);
            }
            default -> throw GraalError.shouldNotReachHere("Unknown expansion kind " + expansionKind(argumentConfig));
        };
    }

    private static int countVirtualAbiFields(AnnotationValue virtualConfig, ResolvedJavaType expandedType) {
        int count = 0;
        List<AnnotationValue> fields = virtualConfig.getList("fields", AnnotationValue.class);
        for (ResolvedJavaField javaField : expandedType.getInstanceFields(true)) {
            AnnotationValue fieldConfig = findFieldConfig(fields, javaField.getName());
            if (fieldConfig == null || fieldConfig.getInt("templateVariable") == 0) {
                count++;
            }
        }
        return count;
    }

    private static int countMaterializedFields(AnnotationValue materializedConfig, ResolvedJavaType expandedType) {
        int count = 0;
        List<AnnotationValue> fields = materializedConfig.getList("fields", AnnotationValue.class);
        for (ResolvedJavaField javaField : expandedType.getInstanceFields(true)) {
            if (findFieldConfig(fields, javaField.getName()) != null) {
                count++;
            }
        }
        return count;
    }

    private static AnnotationValue findFieldConfig(List<AnnotationValue> fields, String name) {
        for (AnnotationValue fieldConfig : fields) {
            if (fieldConfig.getString("name").equals(name)) {
                return fieldConfig;
            }
        }
        return null;
    }

    private static String expansionKind(AnnotationValue argumentConfig) {
        return argumentConfig.getEnum("expand").name;
    }

    private BytecodeHandlerInvoke removeHandlerInvoke(StructuredGraph graph, ResolvedJavaMethod enclosingMethod, int bci) {
        synchronized (registeredHandlerInvokes) {
            EconomicMap<BytecodeHandlerInvokeKey, BytecodeHandlerInvoke> graphInvokes = registeredHandlerInvokes.get(graph);
            if (graphInvokes == null) {
                return null;
            }
            return graphInvokes.removeKey(new BytecodeHandlerInvokeKey(enclosingMethod, bci));
        }
    }

    private static int findCopyFromReturnLocalIndex(Bytecode bytecode, int invokeBci, JavaKind returnLocalKind) {
        /*
         * The generated shape should be bci = handler(bci, ...), where the bytecode local store
         * gives us the exact local to update on the exception path. If the invoke has no matching
         * local store, do not inject a copyFromReturn proxy.
         */
        return findNextLocalStoreIndex(bytecode, invokeBci, returnLocalKind);
    }

    private static int findNextLocalStoreIndex(Bytecode bytecode, int invokeBci, JavaKind storeKind) {
        BytecodeStream stream = new BytecodeStream(bytecode.getCode());
        stream.setBCI(invokeBci);
        int storeBci = stream.nextBCI();
        if (storeBci >= stream.endBCI()) {
            return -1;
        }
        stream.setBCI(storeBci);
        return localStoreIndex(stream, storeKind);
    }

    private static int localStoreIndex(BytecodeStream stream, JavaKind storeKind) {
        int opcode = stream.currentBC();
        return switch (storeKind) {
            case Int -> localStoreIndex(stream, opcode, Bytecodes.ISTORE, Bytecodes.ISTORE_0);
            case Long -> localStoreIndex(stream, opcode, Bytecodes.LSTORE, Bytecodes.LSTORE_0);
            case Float -> localStoreIndex(stream, opcode, Bytecodes.FSTORE, Bytecodes.FSTORE_0);
            case Double -> localStoreIndex(stream, opcode, Bytecodes.DSTORE, Bytecodes.DSTORE_0);
            case Object -> localStoreIndex(stream, opcode, Bytecodes.ASTORE, Bytecodes.ASTORE_0);
            default -> -1;
        };
    }

    private static int localStoreIndex(BytecodeStream stream, int opcode, int indexedStoreOpcode, int compactStoreOpcode0) {
        if (opcode == indexedStoreOpcode) {
            return stream.readLocalIndex();
        }
        if (opcode >= compactStoreOpcode0 && opcode <= compactStoreOpcode0 + 3) {
            return opcode - compactStoreOpcode0;
        }
        return -1;
    }

    /**
     * Minimal copyFromReturn metadata needed to insert a {@link PendingExceptionStateValueNode}.
     */
    private record CopyFromReturnInfo(int originalParameterIndex, int abiSlotIndex, JavaKind kind) {
    }

    /**
     * Identifies the bytecode instruction for a recorded handler invoke within one graph-local
     * metadata map.
     */
    private record BytecodeHandlerInvokeKey(ResolvedJavaMethod method, int bci) {
    }

    /**
     * Graph-local data needed later when the parser visits the corresponding exception dispatch
     * edge.
     */
    private record BytecodeHandlerInvoke(Bytecode bytecode, JavaKind returnLocalKind, int copyFromReturnSlotIndex, JavaKind copyFromReturnKind,
                    WeakReference<ValueNode> copyFromReturnArgumentReference, PendingExceptionStateValueNode.Source source) {
        ValueNode copyFromReturnArgument() {
            return copyFromReturnArgumentReference == null ? null : copyFromReturnArgumentReference.get();
        }
    }

}
