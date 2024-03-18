/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateContext;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointBuiltins;
import com.oracle.svm.core.c.function.CEntryPointBuiltins.CEntryPointBuiltinImplementation;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.code.IsolateEnterStub;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.CInterfaceWrapper;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.c.info.EnumLookupInfo;
import com.oracle.svm.hosted.phases.CInterfaceEnumTool;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class CEntryPointCallStubMethod extends EntryPointCallStubMethod {
    static CEntryPointCallStubMethod create(AnalysisMethod targetMethod, CEntryPointData entryPointData, AnalysisMetaAccess aMetaAccess) {
        MetaAccessProvider originalMetaAccess = GraalAccess.getOriginalProviders().getMetaAccess();
        ResolvedJavaType declaringClass = originalMetaAccess.lookupJavaType(IsolateEnterStub.class);
        ConstantPool constantPool = IsolateEnterStub.getConstantPool(originalMetaAccess);
        return new CEntryPointCallStubMethod(entryPointData, targetMethod, declaringClass, constantPool, aMetaAccess.getUniverse().getWordKind(), originalMetaAccess);
    }

    private static final JavaKind cEnumParameterKind = JavaKind.Int;

    private final CEntryPointData entryPointData;
    private final ResolvedJavaMethod targetMethod;
    private final ResolvedSignature<AnalysisType> targetSignature;

    private CEntryPointCallStubMethod(CEntryPointData entryPointData, AnalysisMethod targetMethod, ResolvedJavaType holderClass, ConstantPool holderConstantPool, JavaKind wordKind,
                    MetaAccessProvider metaAccess) {
        super(SubstrateUtil.uniqueStubName(targetMethod.getWrapped()), holderClass, createSignature(targetMethod, wordKind, metaAccess), holderConstantPool);
        this.entryPointData = entryPointData;
        this.targetMethod = targetMethod.getWrapped();
        this.targetSignature = targetMethod.getSignature();
    }

    /**
     * This method creates a new signature for the stub in which all @CEnum values are converted
     * into their corresponding primitive type. In correspondence to how the @CEnum values are
     * actually handled, parameters are transformed to the type specified by cEnumParameterKind and
     * return type is transformed into the word type.
     *
     * @see CEnum
     * @see CEntryPointCallStubMethod#adaptParameterTypes
     * @see CEntryPointCallStubMethod#adaptReturnValue(HostedGraphKit, ValueNode)
     */
    private static ResolvedSignature<ResolvedJavaType> createSignature(AnalysisMethod targetMethod, JavaKind wordKind, MetaAccessProvider metaAccess) {
        ResolvedJavaType[] paramTypes = targetMethod.toParameterList().stream()
                        .map(type -> type.getAnnotation(CEnum.class) != null ? metaAccess.lookupJavaType(cEnumParameterKind.toJavaClass()) : type.getWrapped())
                        .toArray(ResolvedJavaType[]::new);
        ResolvedJavaType returnType = targetMethod.getSignature().getReturnType().getWrapped();
        if (returnType.getAnnotation(CEnum.class) != null) {
            returnType = metaAccess.lookupJavaType(wordKind.toJavaClass());
        }
        return ResolvedSignature.fromArray(paramTypes, returnType);
    }

    @Override
    public Parameter[] getParameters() {
        return targetMethod.getParameters();
    }

    AnalysisMethod lookupTargetMethod(AnalysisMetaAccess metaAccess) {
        return metaAccess.getUniverse().lookup(targetMethod);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        if (entryPointData.getBuiltin() != CEntryPointData.DEFAULT_BUILTIN) {
            return buildBuiltinGraph(debug, method, providers);
        }

        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        NativeLibraries nativeLibraries = CEntryPointCallStubSupport.singleton().getNativeLibraries();

        List<AnalysisType> parameterTypes = new ArrayList<>(targetSignature.toParameterList(null));
        List<AnalysisType> parameterLoadTypes = new ArrayList<>(parameterTypes);
        EnumInfo[] parameterEnumInfos;

        parameterEnumInfos = adaptParameterTypes(nativeLibraries, kit, parameterTypes, parameterLoadTypes);

        ValueNode[] args = kit.getInitialArguments().toArray(ValueNode.EMPTY_ARRAY);

        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ImageSingletons.lookup(CInterfaceWrapper.class).tagCEntryPointPrologue(kit, method);
        }

        InvokeWithExceptionNode invokePrologue = generatePrologue(kit, parameterLoadTypes, targetMethod.getParameterAnnotations(), args);
        if (invokePrologue != null) {
            ResolvedJavaMethod prologueMethod = invokePrologue.callTarget().targetMethod();
            JavaKind prologueReturnKind = prologueMethod.getSignature().getReturnKind();
            if (prologueReturnKind == JavaKind.Int) {
                kit.startIf(kit.unique(new IntegerEqualsNode(invokePrologue, ConstantNode.forInt(0, kit.getGraph()))), BranchProbabilityNode.VERY_FAST_PATH_PROFILE);
                kit.thenPart();
                kit.elsePart();

                Class<?> bailoutCustomizer = entryPointData.getPrologueBailout();
                JavaKind targetMethodReturnKind = targetMethod.getSignature().getReturnKind();
                boolean createdReturnNode = false;
                if (bailoutCustomizer == CEntryPointOptions.AutomaticPrologueBailout.class) {
                    if (targetMethodReturnKind == JavaKind.Int) {
                        kit.createReturn(invokePrologue, JavaKind.Int);
                        createdReturnNode = true;
                    } else if (targetMethodReturnKind == JavaKind.Void) {
                        kit.createReturn(null, JavaKind.Void);
                        createdReturnNode = true;
                    } else {
                        VMError.shouldNotReachHere("@CEntryPointOptions on " + targetMethod + " must specify a custom prologue bailout as the method's return type is neither int nor void.");
                    }
                }

                if (!createdReturnNode) {
                    AnalysisMethod[] bailoutMethods = kit.getMetaAccess().lookupJavaType(bailoutCustomizer).getDeclaredMethods(false);
                    UserError.guarantee(bailoutMethods.length == 1 && bailoutMethods[0].isStatic(), "Prologue bailout customization class must declare exactly one static method: %s -> %s",
                                    targetMethod, bailoutCustomizer);

                    InvokeWithExceptionNode invokeBailoutCustomizer = generatePrologueOrEpilogueInvoke(kit, bailoutMethods[0], invokePrologue);
                    VMError.guarantee(bailoutMethods[0].getSignature().getReturnKind() == method.getSignature().getReturnKind(),
                                    "Return type mismatch: %s is incompatible with %s", bailoutMethods[0], targetMethod);
                    kit.createReturn(invokeBailoutCustomizer, targetMethod.getSignature().getReturnKind());
                }

                kit.endIf();
            } else {
                VMError.guarantee(prologueReturnKind == JavaKind.Void, "%s is a prologue method and must therefore either return int or void.", prologueMethod);
            }
        }

        adaptArgumentValues(kit, parameterTypes, parameterEnumInfos, args);

        AnalysisMethod aTargetMethod = kit.getMetaAccess().getUniverse().lookup(targetMethod);
        kit.emitEnsureInitializedCall(aTargetMethod.getDeclaringClass());

        int invokeBci = kit.bci();
        // Also support non-static test methods (they are not allowed to use the receiver)
        InvokeKind invokeKind = aTargetMethod.isStatic() ? InvokeKind.Static : InvokeKind.Special;
        ValueNode[] invokeArgs = args;
        if (invokeKind != InvokeKind.Static) {
            invokeArgs = new ValueNode[args.length + 1];
            invokeArgs[0] = kit.createObject(null);
            System.arraycopy(args, 0, invokeArgs, 1, args.length);
        }
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(aTargetMethod, invokeKind, kit.getFrameState(), invokeBci, invokeArgs);
        patchNodeSourcePosition(invoke);
        kit.exceptionPart();
        ExceptionObjectNode exception = kit.exceptionObject();
        generateExceptionHandler(method, kit, exception, invoke.getStackKind());
        kit.endInvokeWithException();

        generateEpilogueAndReturn(method, kit, invoke);
        return kit.finalizeGraph();
    }

    private void generateEpilogueAndReturn(ResolvedJavaMethod method, HostedGraphKit kit, ValueNode value) {
        ValueNode returnValue = adaptReturnValue(kit, value);
        generateEpilogue(kit);

        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ImageSingletons.lookup(CInterfaceWrapper.class).tagCEntryPointEpilogue(kit, method);
        }

        kit.createReturn(returnValue, returnValue.getStackKind());
    }

    private static void patchNodeSourcePosition(InvokeWithExceptionNode invoke) {
        NodeSourcePosition position = invoke.getNodeSourcePosition();
        if (position != null && position.getBCI() == BytecodeFrame.INVALID_FRAMESTATE_BCI) {
            invoke.setNodeSourcePosition(new NodeSourcePosition(position.getCaller(), position.getMethod(), invoke.bci()));
        }
    }

    private StructuredGraph buildBuiltinGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        AnalysisMethod aTargetMethod = kit.getMetaAccess().getUniverse().lookup(targetMethod);

        UserError.guarantee(entryPointData.getPrologue() == CEntryPointData.DEFAULT_PROLOGUE,
                        "@%s method declared as built-in must not have a custom prologue: %s", CEntryPoint.class.getSimpleName(), aTargetMethod);
        UserError.guarantee(entryPointData.getEpilogue() == CEntryPointData.DEFAULT_EPILOGUE,
                        "@%s method declared as built-in must not have a custom epilogue: %s", CEntryPoint.class.getSimpleName(), aTargetMethod);
        UserError.guarantee(entryPointData.getExceptionHandler() == CEntryPointData.DEFAULT_EXCEPTION_HANDLER,
                        "@%s method declared as built-in must not have a custom exception handler: %s", CEntryPoint.class.getSimpleName(), aTargetMethod);

        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ImageSingletons.lookup(CInterfaceWrapper.class).tagCEntryPointPrologue(kit, method);
        }

        ExecutionContextParameters executionContext = findExecutionContextParameters(kit, aTargetMethod.toParameterList(), aTargetMethod.getParameterAnnotations());

        final CEntryPoint.Builtin builtin = entryPointData.getBuiltin();
        AnalysisMethod builtinCallee = null;
        for (AnalysisMethod candidate : kit.getMetaAccess().lookupJavaType(CEntryPointBuiltins.class).getDeclaredMethods(false)) {
            CEntryPointBuiltinImplementation annotation = candidate.getAnnotation(CEntryPointBuiltinImplementation.class);
            if (annotation != null && annotation.builtin().equals(builtin)) {
                VMError.guarantee(builtinCallee == null, "More than one candidate for @%s built-in %s", CEntryPoint.class.getSimpleName(), builtin);
                builtinCallee = candidate;
            }
        }
        VMError.guarantee(builtinCallee != null, "No candidate for @%s built-in %s", CEntryPoint.class.getSimpleName(), builtin);

        AnalysisType isolateType = kit.getMetaAccess().lookupJavaType(Isolate.class);
        AnalysisType threadType = kit.getMetaAccess().lookupJavaType(IsolateThread.class);
        int builtinIsolateIndex = -1;
        int builtinThreadIndex = -1;
        List<AnalysisType> builtinParamTypes = builtinCallee.toParameterList();
        for (int i = 0; i < builtinParamTypes.size(); i++) {
            AnalysisType type = builtinParamTypes.get(i);
            if (isolateType.isAssignableFrom(type)) {
                VMError.guarantee(builtinIsolateIndex == -1, "@%s built-in with more than one %s parameter: %s",
                                CEntryPoint.class.getSimpleName(), Isolate.class.getSimpleName(), builtinCallee);
                builtinIsolateIndex = i;
            } else if (threadType.isAssignableFrom(type)) {
                VMError.guarantee(builtinThreadIndex == -1, "@%s built-in with more than one %s parameter: %s",
                                CEntryPoint.class.getSimpleName(), IsolateThread.class.getSimpleName(), builtinCallee);
                builtinThreadIndex = i;
            } else {
                VMError.shouldNotReachHere("@%s built-in currently may have only %s or %s parameters: %s",
                                CEntryPoint.class.getSimpleName(), Isolate.class.getSimpleName(), IsolateThread.class.getSimpleName(), builtinCallee);
            }
        }

        ValueNode[] args = kit.getInitialArguments().toArray(ValueNode.EMPTY_ARRAY);

        ValueNode[] builtinArgs = new ValueNode[builtinParamTypes.size()];
        if (builtinIsolateIndex != -1) {
            VMError.guarantee(executionContext.designatedIsolateIndex != -1 || executionContext.isolateCount == 1,
                            "@%s built-in %s needs exactly one %s parameter: %s", CEntryPoint.class.getSimpleName(), entryPointData.getBuiltin(), Isolate.class.getSimpleName(), builtinCallee);
            int index = (executionContext.designatedIsolateIndex != -1) ? executionContext.designatedIsolateIndex : executionContext.lastIsolateIndex;
            builtinArgs[builtinIsolateIndex] = args[index];
        }
        if (builtinThreadIndex != -1) {
            VMError.guarantee(executionContext.designatedThreadIndex != -1 || executionContext.threadCount == 1,
                            "@%s built-in %s needs exactly one %s parameter: %s", CEntryPoint.class.getSimpleName(), entryPointData.getBuiltin(), IsolateThread.class.getSimpleName(), builtinCallee);
            int index = (executionContext.designatedThreadIndex != -1) ? executionContext.designatedThreadIndex : executionContext.lastThreadIndex;
            builtinArgs[builtinThreadIndex] = args[index];
        }

        int invokeBci = kit.bci();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(builtinCallee, InvokeKind.Static, kit.getFrameState(), invokeBci, builtinArgs);
        kit.exceptionPart();
        ExceptionObjectNode exception = kit.exceptionObject();

        generateExceptionHandler(method, kit, exception, invoke.getStackKind());
        kit.endInvokeWithException();

        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ImageSingletons.lookup(CInterfaceWrapper.class).tagCEntryPointEpilogue(kit, method);
        }

        kit.createReturn(invoke, aTargetMethod.getSignature().getReturnKind());

        return kit.finalizeGraph();
    }

    private EnumInfo[] adaptParameterTypes(NativeLibraries nativeLibraries, HostedGraphKit kit, List<AnalysisType> parameterTypes, List<AnalysisType> parameterLoadTypes) {
        EnumInfo[] parameterEnumInfos = null;
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (!parameterTypes.get(i).getJavaKind().isPrimitive() && !kit.getWordTypes().isWord(parameterTypes.get(i))) {
                ElementInfo typeInfo = nativeLibraries.findElementInfo(parameterTypes.get(i));
                if (typeInfo instanceof EnumInfo) {
                    UserError.guarantee(typeInfo.getChildren().stream().anyMatch(EnumLookupInfo.class::isInstance),
                                    "Enum class %s needs a method that is annotated with @%s because it is used as a parameter of an entry point method: %s",
                                    parameterTypes.get(i),
                                    CEnumLookup.class.getSimpleName(),
                                    targetMethod);

                    if (parameterEnumInfos == null) {
                        parameterEnumInfos = new EnumInfo[parameterTypes.size()];
                    }
                    parameterEnumInfos[i] = (EnumInfo) typeInfo;

                    parameterLoadTypes.set(i, kit.getMetaAccess().lookupJavaType(cEnumParameterKind.toJavaClass()));

                    final int parameterIndex = i;
                    FrameState initialState = kit.getGraph().start().stateAfter();
                    Iterator<ValueNode> matchingNodes = initialState.values().filter(node -> ((ParameterNode) node).index() == parameterIndex).iterator();
                    ValueNode parameterNode = matchingNodes.next();
                    assert !matchingNodes.hasNext() && parameterNode.usages().filter(n -> n != initialState).isEmpty();
                    parameterNode.setStamp(StampFactory.forKind(cEnumParameterKind));
                } else {
                    throw UserError.abort("Entry point method parameter types are restricted to primitive types, word types and enumerations (@%s): %s, given type was %s",
                                    CEnum.class.getSimpleName(), targetMethod, parameterTypes.get(i));
                }
            }
        }
        return parameterEnumInfos;
    }

    private static void adaptArgumentValues(HostedGraphKit kit, List<AnalysisType> parameterTypes, EnumInfo[] parameterEnumInfos, ValueNode[] args) {
        if (parameterEnumInfos != null) {
            // These methods must be called after the prologue established a safe context
            for (int i = 0; i < parameterEnumInfos.length; i++) {
                if (parameterEnumInfos[i] != null) {
                    CInterfaceEnumTool tool = new CInterfaceEnumTool(kit.getMetaAccess());
                    args[i] = tool.createEnumLookupInvoke(kit, parameterTypes.get(i), parameterEnumInfos[i], cEnumParameterKind, args[i]);
                }
            }
        }
    }

    private InvokeWithExceptionNode generatePrologue(HostedGraphKit kit, List<AnalysisType> parameterTypes, Annotation[][] parameterAnnotations, ValueNode[] args) {
        Class<?> prologueClass = entryPointData.getPrologue();
        if (prologueClass == NoPrologue.class) {
            UserError.guarantee(Uninterruptible.Utils.isUninterruptible(targetMethod),
                            "%s.%s is allowed only for methods annotated with @%s: %s",
                            CEntryPointOptions.class.getSimpleName(),
                            NoPrologue.class.getSimpleName(),
                            Uninterruptible.class.getSimpleName(),
                            targetMethod);
            return null;
        }
        if (prologueClass != CEntryPointOptions.AutomaticPrologue.class) {
            AnalysisType prologue = kit.getMetaAccess().lookupJavaType(prologueClass);
            AnalysisMethod[] prologueMethods = prologue.getDeclaredMethods(false);
            UserError.guarantee(prologueMethods.length == 1 && prologueMethods[0].isStatic(),
                            "Prologue class must declare exactly one static method: %s -> %s",
                            targetMethod,
                            prologue);
            UserError.guarantee(Uninterruptible.Utils.isUninterruptible(prologueMethods[0]),
                            "Prologue method must be annotated with @%s: %s", Uninterruptible.class.getSimpleName(), prologueMethods[0]);
            ValueNode[] prologueArgs = matchPrologueParameters(kit, parameterTypes, args, prologueMethods[0]);
            return generatePrologueOrEpilogueInvoke(kit, prologueMethods[0], prologueArgs);
        }

        // Automatically choose prologue from signature and annotations and call
        ExecutionContextParameters executionContext = findExecutionContextParameters(kit, parameterTypes, parameterAnnotations);
        int contextIndex = -1;
        if (executionContext.designatedThreadIndex != -1) {
            contextIndex = executionContext.designatedThreadIndex;
        } else if (executionContext.threadCount == 1) {
            contextIndex = executionContext.lastThreadIndex;
        } else {
            UserError.abort("@%s requires exactly one execution context parameter of type %s: %s", CEntryPoint.class.getSimpleName(),
                            IsolateThread.class.getSimpleName(), targetMethod);
        }
        ValueNode contextValue = args[contextIndex];
        prologueClass = CEntryPointSetup.EnterPrologue.class;
        AnalysisMethod[] prologueMethods = kit.getMetaAccess().lookupJavaType(prologueClass).getDeclaredMethods(false);
        assert prologueMethods.length == 1 && prologueMethods[0].isStatic() : "Prologue class must declare exactly one static method";
        return generatePrologueOrEpilogueInvoke(kit, prologueMethods[0], contextValue);
    }

    private static InvokeWithExceptionNode generatePrologueOrEpilogueInvoke(SubstrateGraphKit kit, AnalysisMethod method, ValueNode... args) {
        VMError.guarantee(Uninterruptible.Utils.isUninterruptible(method), "The method %s must be uninterruptible as it is used for a prologue or epilogue.", method);
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(method, InvokeKind.Static, kit.getFrameState(), kit.bci(), args);
        kit.exceptionPart();
        kit.append(new DeadEndNode());
        kit.endInvokeWithException();
        return invoke;
    }

    private static class ExecutionContextParameters {
        int isolateCount = 0;
        int lastIsolateIndex = -1;
        int designatedIsolateIndex = -1;

        int threadCount = 0;
        int lastThreadIndex = -1;
        int designatedThreadIndex = -1;
    }

    private ExecutionContextParameters findExecutionContextParameters(HostedGraphKit kit, List<AnalysisType> parameterTypes, Annotation[][] parameterAnnotations) {
        AnalysisType isolateType = kit.getMetaAccess().lookupJavaType(Isolate.class);
        AnalysisType threadType = kit.getMetaAccess().lookupJavaType(IsolateThread.class);

        ExecutionContextParameters result = new ExecutionContextParameters();
        for (int i = 0; i < parameterTypes.size(); i++) {
            AnalysisType declaredType = parameterTypes.get(i);
            boolean isIsolate = isolateType.isAssignableFrom(declaredType);
            boolean isThread = threadType.isAssignableFrom(declaredType);
            boolean isLong = declaredType.getJavaKind() == JavaKind.Long;
            boolean designated = false;
            for (Annotation ann : parameterAnnotations[i]) {
                if (ann.annotationType() == IsolateContext.class) {
                    UserError.guarantee(isIsolate || isLong, "@%s parameter %d is annotated with @%s, but does not have type %s: %s",
                                    CEntryPoint.class.getSimpleName(), i,
                                    CEntryPoint.IsolateContext.class.getSimpleName(),
                                    Isolate.class.getSimpleName(),
                                    targetMethod);
                    designated = true;
                    isIsolate = true;
                } else if (ann.annotationType() == IsolateThreadContext.class) {
                    UserError.guarantee(isThread || isLong, "@%s parameter %d is annotated with @%s, but does not have type %s: %s",
                                    CEntryPoint.class.getSimpleName(), i,
                                    CEntryPoint.IsolateThreadContext.class.getSimpleName(),
                                    IsolateThread.class.getSimpleName(),
                                    targetMethod);
                    designated = true;
                    isThread = true;
                }
            }
            UserError.guarantee(!(isIsolate && isThread), "@%s parameter %d has a type as both an %s and a %s: %s",
                            CEntryPoint.class.getSimpleName(), i,
                            Isolate.class.getSimpleName(),
                            IsolateThread.class.getSimpleName(),
                            targetMethod);
            if (isIsolate) {
                result.lastIsolateIndex = i;
                result.isolateCount++;
                if (designated) {
                    UserError.guarantee(result.designatedIsolateIndex == -1, "@%s has more than one designated %s parameter: %s",
                                    CEntryPoint.class.getSimpleName(),
                                    Isolate.class.getSimpleName(),
                                    targetMethod);
                    result.designatedIsolateIndex = i;
                }
            } else if (isThread) {
                result.lastThreadIndex = i;
                result.threadCount++;
                if (designated) {
                    UserError.guarantee(result.designatedThreadIndex == -1, "@%s has more than one designated %s parameter: %s",
                                    CEntryPoint.class.getSimpleName(),
                                    IsolateThread.class.getSimpleName(),
                                    targetMethod);
                    result.designatedThreadIndex = i;
                }
            }
        }
        return result;
    }

    private ValueNode[] matchPrologueParameters(HostedGraphKit kit, List<AnalysisType> types, ValueNode[] values, AnalysisMethod prologueMethod) {
        ValueNode[] prologueValues = new ValueNode[prologueMethod.getSignature().getParameterCount(false)];
        int i = 0;
        for (int p = 0; p < prologueValues.length; p++) {
            AnalysisType prologueType = prologueMethod.getSignature().getParameterType(p);
            UserError.guarantee(prologueType.isPrimitive() || kit.getWordTypes().isWord(prologueType),
                            "Prologue method parameter types are restricted to primitive types and word types: %s -> %s",
                            targetMethod,
                            prologueMethod);
            while (i < types.size() && !prologueType.isAssignableFrom(types.get(i))) {
                i++;
            }
            if (i >= types.size()) {
                throw UserError.abort("Unable to match signature of entry point method to that of prologue method: %s -> %s",
                                targetMethod,
                                prologueMethod);
            }
            prologueValues[p] = values[i];
            i++;
        }
        return prologueValues;
    }

    private void generateExceptionHandler(ResolvedJavaMethod method, HostedGraphKit kit, ExceptionObjectNode exception, JavaKind returnKind) {
        if (entryPointData.getExceptionHandler() == CEntryPoint.FatalExceptionHandler.class) {
            CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, exception);
            kit.append(leave);
            kit.append(new LoweredDeadEndNode());
        } else {
            AnalysisType throwable = kit.getMetaAccess().lookupJavaType(Throwable.class);
            AnalysisType handler = kit.getMetaAccess().lookupJavaType(entryPointData.getExceptionHandler());
            AnalysisMethod[] handlerMethods = handler.getDeclaredMethods(false);
            UserError.guarantee(handlerMethods.length == 1 && handlerMethods[0].isStatic(),
                            "Exception handler class must declare exactly one static method: %s -> %s", targetMethod, handler);
            UserError.guarantee(Uninterruptible.Utils.isUninterruptible(handlerMethods[0]),
                            "Exception handler method must be annotated with @%s: %s", Uninterruptible.class.getSimpleName(), handlerMethods[0]);
            List<AnalysisType> handlerParameterTypes = handlerMethods[0].toParameterList();
            UserError.guarantee(handlerParameterTypes.size() == 1 &&
                            handlerParameterTypes.get(0).isAssignableFrom(throwable),
                            "Exception handler method must have exactly one parameter of type Throwable: %s -> %s", targetMethod, handlerMethods[0]);
            InvokeWithExceptionNode handlerInvoke = kit.startInvokeWithException(handlerMethods[0], InvokeKind.Static, kit.getFrameState(), kit.bci(), exception);
            kit.noExceptionPart();
            ValueNode returnValue = handlerInvoke;
            if (handlerInvoke.getStackKind() != returnKind) {
                JavaKind fromKind = handlerInvoke.getStackKind();
                if (fromKind == JavaKind.Float && returnKind == JavaKind.Double) {
                    returnValue = kit.unique(new FloatConvertNode(FloatConvert.F2D, returnValue));
                } else if (fromKind.isUnsigned() && returnKind.isNumericInteger() && returnKind.getBitCount() > fromKind.getBitCount()) {
                    returnValue = kit.unique(new ZeroExtendNode(returnValue, returnKind.getBitCount()));
                } else if (fromKind.isNumericInteger() && returnKind.isNumericInteger() && returnKind.getBitCount() > fromKind.getBitCount()) {
                    returnValue = kit.unique(new SignExtendNode(returnValue, returnKind.getBitCount()));
                } else {
                    throw UserError.abort("Exception handler method return type must be assignable to entry point method return type: %s -> %s",
                                    targetMethod, handlerMethods[0]);
                }
            }

            /* The exception is handled, we can continue with the normal epilogue. */
            generateEpilogueAndReturn(method, kit, returnValue);

            kit.exceptionPart(); // fail-safe for exceptions in exception handler
            kit.append(new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, kit.exceptionObject()));
            kit.append(new LoweredDeadEndNode());
            kit.endInvokeWithException();
        }
    }

    private ValueNode adaptReturnValue(HostedGraphKit kit, ValueNode value) {
        ValueNode returnValue = value;
        if (returnValue.getStackKind().isPrimitive()) {
            return returnValue;
        }
        AnalysisType returnType = targetSignature.getReturnType();
        NativeLibraries nativeLibraries = CEntryPointCallStubSupport.singleton().getNativeLibraries();
        ElementInfo typeInfo = nativeLibraries.findElementInfo(returnType);
        if (typeInfo instanceof EnumInfo) {
            // Always return enum values as a signed word because it should never be a problem if
            // the caller expects a narrower integer type and the various checks already handle
            // replacements with word types.
            CInterfaceEnumTool tool = new CInterfaceEnumTool(kit.getMetaAccess());
            JavaKind cEnumReturnType = kit.getWordTypes().getWordKind();
            assert !cEnumReturnType.isUnsigned() : "requires correct representation of signed values";
            returnValue = tool.startEnumValueInvokeWithException(kit, (EnumInfo) typeInfo, cEnumReturnType, returnValue);
            kit.exceptionPart();
            kit.append(new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, kit.exceptionObject()));
            kit.append(new LoweredDeadEndNode());
            kit.endInvokeWithException();

        } else {
            throw UserError.abort("Entry point method return types are restricted to primitive types, word types and enumerations (@%s): %s",
                            CEnum.class.getSimpleName(), targetMethod);
        }
        return returnValue;
    }

    private void generateEpilogue(HostedGraphKit kit) {
        Class<?> epilogueClass = entryPointData.getEpilogue();
        if (epilogueClass == NoEpilogue.class) {
            UserError.guarantee(Uninterruptible.Utils.isUninterruptible(targetMethod),
                            "%s.%s is allowed only for methods annotated with @%s: %s",
                            CEntryPointOptions.class.getSimpleName(),
                            NoEpilogue.class.getSimpleName(),
                            Uninterruptible.class.getSimpleName(),
                            targetMethod);
            return;
        }
        AnalysisType epilogue = kit.getMetaAccess().lookupJavaType(epilogueClass);
        AnalysisMethod[] epilogueMethods = epilogue.getDeclaredMethods(false);
        UserError.guarantee(epilogueMethods.length == 1 && epilogueMethods[0].isStatic() && epilogueMethods[0].getSignature().getParameterCount(false) == 0,
                        "Epilogue class must declare exactly one static method without parameters: %s -> %s", targetMethod, epilogue);
        UserError.guarantee(Uninterruptible.Utils.isUninterruptible(epilogueMethods[0]),
                        "Epilogue method must be annotated with @%s: %s", Uninterruptible.class.getSimpleName(), epilogueMethods[0]);
        generatePrologueOrEpilogueInvoke(kit, epilogueMethods[0]);
    }
}
