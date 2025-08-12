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

import static com.oracle.svm.core.Uninterruptible.Utils.isUninterruptible;

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

import com.oracle.graal.pointsto.BigBang;
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

/**
 * Builds a graph for each {@link CEntryPoint} method. Each graph looks roughly like the pseudocode
 * below. Note that only the prologue may be inlined, while all other calls must never be inlined.
 * This restriction is necessary because the prologue typically initializes well-known registers
 * such as the heap base, so we need to make sure that nothing (not even Java heap constants) can
 * float above the (potentially inlined) prologue logic.
 * <p>
 * Besides that, we also need to prevent inlining to ensure that exception handling works correctly
 * (see GR-24649).
 *
 * {@snippet :
 * CEntryPointCallStub(args) {
 *     prologue();
 *     if (errorInPrologue) {
 *         return prologueBailout();
 *     }
 *
 *     adaptedArgs = adaptArgumentValues(args);
 *     ensureClassInitialization();
 *     try {
 *         result = targetMethod(adaptedArgs);
 *     } catch (Throwable e) {
 *         result = exceptionHandler(e);
 *         result = adaptReturnValue(result);
 *         epilogue();
 *         return result;
 *     }
 *
 *     result = adaptReturnValue(result);
 *     epilogue();
 *     return result;
 * }
 * }
 */
public final class CEntryPointCallStubMethod extends EntryPointCallStubMethod {
    static CEntryPointCallStubMethod create(BigBang bb, AnalysisMethod targetMethod, CEntryPointData entryPointData) {
        MetaAccessProvider originalMetaAccess = GraalAccess.getOriginalProviders().getMetaAccess();
        ResolvedJavaType declaringClass = originalMetaAccess.lookupJavaType(IsolateEnterStub.class);
        ConstantPool constantPool = IsolateEnterStub.getConstantPool(originalMetaAccess);
        return new CEntryPointCallStubMethod(entryPointData, targetMethod, declaringClass, constantPool, bb.getMetaAccess());
    }

    private final CEntryPointData entryPointData;
    private final ResolvedJavaMethod targetMethod;
    private final ResolvedSignature<AnalysisType> targetSignature;

    private CEntryPointCallStubMethod(CEntryPointData entryPointData, AnalysisMethod targetMethod, ResolvedJavaType holderClass, ConstantPool holderConstantPool, AnalysisMetaAccess metaAccess) {
        super(SubstrateUtil.uniqueStubName(targetMethod.getWrapped()), holderClass, createSignature(targetMethod, metaAccess), holderConstantPool);
        this.entryPointData = entryPointData;
        this.targetMethod = targetMethod.getWrapped();
        this.targetSignature = targetMethod.getSignature();
    }

    /**
     * This method creates a new signature for the stub in which all {@link CEnum} values are
     * converted to suitable primitive types.
     */
    private static ResolvedSignature<ResolvedJavaType> createSignature(AnalysisMethod method, AnalysisMetaAccess metaAccess) {
        NativeLibraries nativeLibraries = NativeLibraries.singleton();
        AnalysisType[] args = method.toParameterList().toArray(AnalysisType[]::new);

        ResolvedJavaType[] patchedArgs = new ResolvedJavaType[args.length];
        for (int i = 0; i < args.length; i++) {
            if (CInterfaceEnumTool.isPrimitiveOrWord(args[i])) {
                patchedArgs[i] = args[i].getWrapped();
            } else {
                /* Replace the CEnum with the corresponding primitive type. */
                EnumInfo enumInfo = getEnumInfo(nativeLibraries, method, args[i], false);
                patchedArgs[i] = CInterfaceEnumTool.getCEnumValueType(enumInfo, metaAccess).getWrapped();
            }
        }

        AnalysisType patchedReturnType = method.getSignature().getReturnType();
        if (!CInterfaceEnumTool.isPrimitiveOrWord(patchedReturnType)) {
            /*
             * The return type is a @CEnum. Change the return type to Word because the C entry point
             * will return some primitive value.
             */
            assert getEnumInfo(nativeLibraries, method, patchedReturnType, true) != null;
            patchedReturnType = (AnalysisType) nativeLibraries.getWordTypes().getWordImplType();
        }
        return ResolvedSignature.fromArray(patchedArgs, patchedReturnType.getWrapped());
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
        NativeLibraries nativeLibraries = NativeLibraries.singleton();

        List<AnalysisType> parameterTypes = new ArrayList<>(targetSignature.toParameterList(null));
        List<AnalysisType> parameterLoadTypes = new ArrayList<>(parameterTypes);

        EnumInfo[] parameterEnumInfos = adaptParameterTypes(nativeLibraries, kit, parameterTypes, parameterLoadTypes);
        ValueNode[] args = kit.getInitialArguments().toArray(ValueNode.EMPTY_ARRAY);

        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ImageSingletons.lookup(CInterfaceWrapper.class).tagCEntryPointPrologue(kit, method);
        }

        /* Invoke the prologue. This call is the only one that may be inlined. */
        InvokeWithExceptionNode invokePrologue = generatePrologue(kit, parameterLoadTypes, targetMethod.getParameterAnnotations(), args);
        if (invokePrologue != null) {
            ResolvedJavaMethod prologueMethod = invokePrologue.callTarget().targetMethod();
            JavaKind prologueReturnKind = prologueMethod.getSignature().getReturnKind();
            if (prologueReturnKind == JavaKind.Int) {
                kit.startIf(kit.unique(new IntegerEqualsNode(invokePrologue, ConstantNode.forInt(0, kit.getGraph()))), BranchProbabilityNode.VERY_FAST_PATH_PROFILE);

                /* Prologue returned zero, so continue execution normally. */
                kit.thenPart();

                /* Prologue returned non-zero, start the error handling. */
                kit.elsePart();

                Class<?> bailoutCustomizer = entryPointData.getPrologueBailout();
                JavaKind targetMethodReturnKind = targetMethod.getSignature().getReturnKind();

                /* Generate the default bailout logic if no custom bailout logic was registered. */
                boolean createdReturnNode = false;
                if (bailoutCustomizer == CEntryPointOptions.AutomaticPrologueBailout.class) {
                    if (targetMethodReturnKind == JavaKind.Int) {
                        /* Directly return the error code that was returned from the prologue. */
                        kit.createReturn(invokePrologue, JavaKind.Int);
                        createdReturnNode = true;
                    } else if (targetMethodReturnKind == JavaKind.Void) {
                        /* Swallow the error code from the prologue. */
                        kit.createReturn(null, JavaKind.Void);
                        createdReturnNode = true;
                    } else {
                        throw VMError.shouldNotReachHere("@CEntryPointOptions on " + targetMethod + " must specify a custom prologue bailout as the method's return type is neither int nor void.");
                    }
                }

                /* Invoke the custom bailout logic if necessary. */
                if (!createdReturnNode) {
                    AnalysisMethod[] bailoutMethods = kit.getMetaAccess().lookupJavaType(bailoutCustomizer).getDeclaredMethods(false);
                    UserError.guarantee(bailoutMethods.length == 1 && bailoutMethods[0].isStatic(), "Prologue bailout customization class must declare exactly one static method: %s -> %s",
                                    targetMethod, bailoutCustomizer);

                    InvokeWithExceptionNode invokeBailoutCustomizer = createInvokeStaticWithFatalExceptionHandler(kit, bailoutMethods[0], invokePrologue);
                    invokeBailoutCustomizer.setUseForInlining(false);
                    VMError.guarantee(bailoutMethods[0].getSignature().getReturnKind() == method.getSignature().getReturnKind(),
                                    "Return type mismatch: %s is incompatible with %s", bailoutMethods[0], targetMethod);
                    kit.createReturn(invokeBailoutCustomizer, targetMethod.getSignature().getReturnKind());
                }

                kit.endIf();
            } else {
                VMError.guarantee(prologueReturnKind == JavaKind.Void, "%s is a prologue method and must therefore either return int or void.", prologueMethod);
            }
        }

        /* Convert primitive argument values to Java enums if needed. */
        adaptArgumentValues(kit, parameterTypes, parameterEnumInfos, args);

        /* Ensure that the target class is initialized. */
        AnalysisMethod aTargetMethod = kit.getMetaAccess().getUniverse().lookup(targetMethod);
        kit.emitEnsureInitializedCall(aTargetMethod.getDeclaringClass());

        /*
         * Invoke the target method that is annotated with @CEntryPoint. Also support non-static
         * test methods by passing null as the receiver (they are not allowed to use the receiver).
         */
        int invokeBci = kit.bci();
        InvokeKind invokeKind = aTargetMethod.isStatic() ? InvokeKind.Static : InvokeKind.Special;
        ValueNode[] invokeArgs = args;
        if (invokeKind != InvokeKind.Static) {
            invokeArgs = new ValueNode[args.length + 1];
            invokeArgs[0] = kit.createObject(null);
            System.arraycopy(args, 0, invokeArgs, 1, args.length);
        }

        InvokeWithExceptionNode invoke = kit.startInvokeWithException(aTargetMethod, invokeKind, kit.getFrameState(), invokeBci, invokeArgs);
        invoke.setUseForInlining(false);
        patchNodeSourcePosition(invoke);

        /* Invoke the exception handler if an exception occurred in the target method. */
        kit.exceptionPart();
        generateExceptionHandler(method, kit, kit.exceptionObject(), invoke.getStackKind());
        kit.endInvokeWithException();

        /* Invoke the epilogue and return the value that was returned from the target method. */
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

        /* Find the target method for the built-in. */
        CEntryPoint.Builtin builtin = entryPointData.getBuiltin();
        AnalysisMethod builtinCallee = null;
        for (AnalysisMethod candidate : kit.getMetaAccess().lookupJavaType(CEntryPointBuiltins.class).getDeclaredMethods(false)) {
            CEntryPointBuiltinImplementation annotation = candidate.getAnnotation(CEntryPointBuiltinImplementation.class);
            if (annotation != null && annotation.builtin().equals(builtin)) {
                VMError.guarantee(builtinCallee == null, "More than one candidate for @%s built-in %s", CEntryPoint.class.getSimpleName(), builtin);
                builtinCallee = candidate;
            }
        }
        VMError.guarantee(builtinCallee != null, "No candidate for @%s built-in %s", CEntryPoint.class.getSimpleName(), builtin);

        /* Determine which Isolate/IsolateThread should be used as the context. */
        AnalysisType isolateType = kit.getMetaAccess().lookupJavaType(Isolate.class);
        AnalysisType threadType = kit.getMetaAccess().lookupJavaType(IsolateThread.class);
        int isolateIndex = -1;
        int threadIndex = -1;
        List<AnalysisType> builtinParamTypes = builtinCallee.toParameterList();
        for (int i = 0; i < builtinParamTypes.size(); i++) {
            AnalysisType type = builtinParamTypes.get(i);
            if (isolateType.isAssignableFrom(type)) {
                VMError.guarantee(isolateIndex == -1, "@%s built-in with more than one %s parameter: %s",
                                CEntryPoint.class.getSimpleName(), Isolate.class.getSimpleName(), builtinCallee);
                isolateIndex = i;
            } else if (threadType.isAssignableFrom(type)) {
                VMError.guarantee(threadIndex == -1, "@%s built-in with more than one %s parameter: %s",
                                CEntryPoint.class.getSimpleName(), IsolateThread.class.getSimpleName(), builtinCallee);
                threadIndex = i;
            } else {
                throw VMError.shouldNotReachHere("@%s built-in currently may have only %s or %s parameters: %s",
                                CEntryPoint.class.getSimpleName(), Isolate.class.getSimpleName(), IsolateThread.class.getSimpleName(), builtinCallee);
            }
        }

        ValueNode[] args = kit.getInitialArguments().toArray(ValueNode.EMPTY_ARRAY);
        ValueNode[] builtinArgs = new ValueNode[builtinParamTypes.size()];
        if (isolateIndex != -1) {
            VMError.guarantee(executionContext.designatedIsolateIndex != -1 || executionContext.isolateCount == 1,
                            "@%s built-in %s needs exactly one %s parameter: %s", CEntryPoint.class.getSimpleName(), entryPointData.getBuiltin(), Isolate.class.getSimpleName(), builtinCallee);
            int index = (executionContext.designatedIsolateIndex != -1) ? executionContext.designatedIsolateIndex : executionContext.lastIsolateIndex;
            builtinArgs[isolateIndex] = args[index];
        }
        if (threadIndex != -1) {
            VMError.guarantee(executionContext.designatedThreadIndex != -1 || executionContext.threadCount == 1,
                            "@%s built-in %s needs exactly one %s parameter: %s", CEntryPoint.class.getSimpleName(), entryPointData.getBuiltin(), IsolateThread.class.getSimpleName(), builtinCallee);
            int index = (executionContext.designatedThreadIndex != -1) ? executionContext.designatedThreadIndex : executionContext.lastThreadIndex;
            builtinArgs[threadIndex] = args[index];
        }

        /* Invoke the target method. */
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(builtinCallee, InvokeKind.Static, kit.getFrameState(), kit.bci(), builtinArgs);

        /* Invoke the exception handler if an exception occurred in the target method. */
        kit.exceptionPart();
        generateExceptionHandler(method, kit, kit.exceptionObject(), invoke.getStackKind());
        kit.endInvokeWithException();

        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ImageSingletons.lookup(CInterfaceWrapper.class).tagCEntryPointEpilogue(kit, method);
        }

        /* Return the result. */
        kit.createReturn(invoke, aTargetMethod.getSignature().getReturnKind());
        return kit.finalizeGraph();
    }

    /**
     * The signature may contain Java object types. The only Java object types that we support at
     * the moment are Java enums (annotated with {@link CEnum}). This method replaces all Java enums
     * with suitable primitive types.
     */
    private EnumInfo[] adaptParameterTypes(NativeLibraries nativeLibraries, HostedGraphKit kit, List<AnalysisType> parameterTypes, List<AnalysisType> parameterLoadTypes) {
        EnumInfo[] parameterEnumInfos = null;
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (CInterfaceEnumTool.isPrimitiveOrWord(parameterTypes.get(i))) {
                continue;
            }

            EnumInfo enumInfo = getEnumInfo(nativeLibraries, targetMethod, parameterTypes.get(i), false);
            UserError.guarantee(enumInfo.hasCEnumLookupMethods(), "Enum class %s needs a method that is annotated with @%s because it is used as a parameter of an entry point method: %s",
                            parameterTypes.get(i), CEnumLookup.class.getSimpleName(), targetMethod);

            if (parameterEnumInfos == null) {
                parameterEnumInfos = new EnumInfo[parameterTypes.size()];
            }
            parameterEnumInfos[i] = enumInfo;

            /* The argument is a @CEnum, so change the type to a primitive type. */
            AnalysisType paramType = CInterfaceEnumTool.getCEnumValueType(enumInfo, kit.getMetaAccess());
            parameterLoadTypes.set(i, paramType);

            final int parameterIndex = i;
            FrameState initialState = kit.getGraph().start().stateAfter();
            Iterator<ValueNode> matchingNodes = initialState.values().filter(node -> ((ParameterNode) node).index() == parameterIndex).iterator();
            ValueNode parameterNode = matchingNodes.next();
            assert !matchingNodes.hasNext() && parameterNode.usages().filter(n -> n != initialState).isEmpty();
            parameterNode.setStamp(StampFactory.forKind(paramType.getJavaKind()));
        }
        return parameterEnumInfos;
    }

    private static EnumInfo getEnumInfo(NativeLibraries nativeLibraries, ResolvedJavaMethod method, AnalysisType type, boolean isReturnType) {
        ElementInfo typeInfo = nativeLibraries.findElementInfo(type);
        if (typeInfo instanceof EnumInfo enumInfo) {
            return enumInfo;
        }

        if (isReturnType) {
            throw UserError.abort("Entry point method return types are restricted to primitive types, word types and enumerations (@%s): %s, given type was %s",
                            CEnum.class.getSimpleName(), method, type);
        }
        throw UserError.abort("Entry point method parameter types are restricted to primitive types, word types and enumerations (@%s): %s, given type was %s",
                        CEnum.class.getSimpleName(), method, type);
    }

    /** Converts primitive argument values to Java enums if necessary, see {@link CEnum}. */
    private static void adaptArgumentValues(HostedGraphKit kit, List<AnalysisType> parameterTypes, EnumInfo[] parameterEnumInfos, ValueNode[] args) {
        if (parameterEnumInfos == null) {
            /* No conversion necessary. */
            return;
        }

        for (int i = 0; i < parameterEnumInfos.length; i++) {
            if (parameterEnumInfos[i] != null) {
                args[i] = CInterfaceEnumTool.singleton().createInvokeLookupEnum(kit, parameterTypes.get(i), parameterEnumInfos[i], args[i], false);
            }
        }
    }

    private InvokeWithExceptionNode generatePrologue(HostedGraphKit kit, List<AnalysisType> parameterTypes, Annotation[][] parameterAnnotations, ValueNode[] args) {
        Class<?> prologueClass = entryPointData.getPrologue();
        if (prologueClass == NoPrologue.class) {
            UserError.guarantee(isUninterruptible(targetMethod), "%s.%s is allowed only for methods annotated with @%s: %s",
                            CEntryPointOptions.class.getSimpleName(), NoPrologue.class.getSimpleName(), Uninterruptible.class.getSimpleName(), targetMethod);
            return null;
        }

        /* Generate a call to a custom prologue method, if one is registered. */
        if (prologueClass != CEntryPointOptions.AutomaticPrologue.class) {
            AnalysisType prologue = kit.getMetaAccess().lookupJavaType(prologueClass);
            AnalysisMethod[] prologueMethods = prologue.getDeclaredMethods(false);
            UserError.guarantee(prologueMethods.length == 1 && prologueMethods[0].isStatic(), "Prologue class must declare exactly one static method: %s -> %s", targetMethod, prologue);
            ValueNode[] prologueArgs = matchPrologueParameters(kit, parameterTypes, args, prologueMethods[0]);
            return createInvokeStaticWithFatalExceptionHandler(kit, prologueMethods[0], prologueArgs);
        }

        /* Find the IsolateThread argument that should be used as the context. */
        ExecutionContextParameters executionContext = findExecutionContextParameters(kit, parameterTypes, parameterAnnotations);
        int contextIndex = -1;
        if (executionContext.designatedThreadIndex != -1) {
            contextIndex = executionContext.designatedThreadIndex;
        } else if (executionContext.threadCount == 1) {
            contextIndex = executionContext.lastThreadIndex;
        } else {
            UserError.abort("@%s requires exactly one execution context parameter of type %s: %s", CEntryPoint.class.getSimpleName(), IsolateThread.class.getSimpleName(), targetMethod);
        }

        /* Generate the call to EnterPrologue.enter(...). */
        ValueNode contextValue = args[contextIndex];
        prologueClass = CEntryPointSetup.EnterPrologue.class;
        AnalysisMethod[] prologueMethods = kit.getMetaAccess().lookupJavaType(prologueClass).getDeclaredMethods(false);
        assert prologueMethods.length == 1 && prologueMethods[0].isStatic() : "Prologue class must declare exactly one static method";
        return createInvokeStaticWithFatalExceptionHandler(kit, prologueMethods[0], contextValue);
    }

    private static InvokeWithExceptionNode createInvokeStaticWithFatalExceptionHandler(SubstrateGraphKit kit, AnalysisMethod method, ValueNode... args) {
        UserError.guarantee(isUninterruptible(method), "The method %s must be annotated with @%s as it is used for a prologue, epilogue, or bailout.", Uninterruptible.class.getSimpleName(), method);

        /* Generate the call. */
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(method, InvokeKind.Static, kit.getFrameState(), kit.bci(), args);

        /* Exceptions in prologue, epilogue, or bailout are fatal. */
        kit.exceptionPart();
        kit.append(new DeadEndNode());
        kit.endInvokeWithException();
        return invoke;
    }

    private static final class ExecutionContextParameters {
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
                                    CEntryPoint.class.getSimpleName(), i, CEntryPoint.IsolateContext.class.getSimpleName(), Isolate.class.getSimpleName(), targetMethod);
                    designated = true;
                    isIsolate = true;
                } else if (ann.annotationType() == IsolateThreadContext.class) {
                    UserError.guarantee(isThread || isLong, "@%s parameter %d is annotated with @%s, but does not have type %s: %s",
                                    CEntryPoint.class.getSimpleName(), i, CEntryPoint.IsolateThreadContext.class.getSimpleName(), IsolateThread.class.getSimpleName(), targetMethod);
                    designated = true;
                    isThread = true;
                }
            }

            UserError.guarantee(!(isIsolate && isThread), "@%s parameter %d has a type as both an %s and a %s: %s",
                            CEntryPoint.class.getSimpleName(), i, Isolate.class.getSimpleName(), IsolateThread.class.getSimpleName(), targetMethod);

            if (isIsolate) {
                result.lastIsolateIndex = i;
                result.isolateCount++;
                if (designated) {
                    UserError.guarantee(result.designatedIsolateIndex == -1, "@%s has more than one designated %s parameter: %s",
                                    CEntryPoint.class.getSimpleName(), Isolate.class.getSimpleName(), targetMethod);
                    result.designatedIsolateIndex = i;
                }
            } else if (isThread) {
                result.lastThreadIndex = i;
                result.threadCount++;
                if (designated) {
                    UserError.guarantee(result.designatedThreadIndex == -1, "@%s has more than one designated %s parameter: %s",
                                    CEntryPoint.class.getSimpleName(), IsolateThread.class.getSimpleName(), targetMethod);
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
            UserError.guarantee(prologueType.isPrimitive() || kit.getWordTypes().isWord(prologueType), "Prologue method parameter types are restricted to primitive types and word types: %s -> %s",
                            targetMethod, prologueMethod);
            while (i < types.size() && !prologueType.isAssignableFrom(types.get(i))) {
                i++;
            }

            if (i >= types.size()) {
                throw UserError.abort("Unable to match signature of entry point method to that of prologue method: %s -> %s", targetMethod, prologueMethod);
            }
            prologueValues[p] = values[i];
            i++;
        }
        return prologueValues;
    }

    private void generateExceptionHandler(ResolvedJavaMethod method, HostedGraphKit kit, ExceptionObjectNode exception, JavaKind returnKind) {
        if (entryPointData.getExceptionHandler() == CEntryPoint.FatalExceptionHandler.class) {
            /* Exceptions are fatal errors by default. */
            CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, exception);
            kit.append(leave);
            kit.append(new LoweredDeadEndNode());
            return;
        }

        /* Determine which method was registered as the exception handler. */
        AnalysisType throwable = kit.getMetaAccess().lookupJavaType(Throwable.class);
        AnalysisType handler = kit.getMetaAccess().lookupJavaType(entryPointData.getExceptionHandler());
        AnalysisMethod[] handlerMethods = handler.getDeclaredMethods(false);
        UserError.guarantee(handlerMethods.length == 1 && handlerMethods[0].isStatic(), "Exception handler class must declare exactly one static method: %s -> %s", targetMethod, handler);
        UserError.guarantee(isUninterruptible(handlerMethods[0]), "Exception handler method must be annotated with @%s: %s", Uninterruptible.class.getSimpleName(), handlerMethods[0]);

        List<AnalysisType> handlerParameterTypes = handlerMethods[0].toParameterList();
        UserError.guarantee(handlerParameterTypes.size() == 1 && handlerParameterTypes.getFirst().isAssignableFrom(throwable),
                        "Exception handler method must have exactly one parameter of type Throwable: %s -> %s", targetMethod, handlerMethods[0]);

        /* Invoke the exception handler method. */
        InvokeWithExceptionNode handlerInvoke = kit.startInvokeWithException(handlerMethods[0], InvokeKind.Static, kit.getFrameState(), kit.bci(), exception);
        handlerInvoke.setUseForInlining(false);

        /*
         * The exception handler method finished successfully. Invoke the epilogue and return the
         * value that was returned from the exception handler method.
         */
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
        generateEpilogueAndReturn(method, kit, returnValue);

        /* The exception handler method threw an exception. This is a fatal error. */
        kit.exceptionPart();
        kit.append(new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, kit.exceptionObject()));
        kit.append(new LoweredDeadEndNode());
        kit.endInvokeWithException();
    }

    /** Converts a Java enum to a primitive value if necessary, see {@link CEnum}. */
    private ValueNode adaptReturnValue(HostedGraphKit kit, ValueNode value) {
        if (value.getStackKind().isPrimitive()) {
            /* No conversion necessary. */
            return value;
        }

        AnalysisType returnType = targetSignature.getReturnType();
        NativeLibraries nativeLibraries = NativeLibraries.singleton();
        EnumInfo enumInfo = getEnumInfo(nativeLibraries, targetMethod, returnType, true);

        /* Invoke the method that does the conversion from Java enum to primitive value. */
        InvokeWithExceptionNode invoke = CInterfaceEnumTool.singleton().startInvokeWithExceptionEnumToValue(kit, enumInfo, CInterfaceEnumTool.getCEnumValueType(enumInfo, kit.getMetaAccess()), value);
        invoke.setUseForInlining(false);
        ValueNode result = kit.getGraph().unique(new ZeroExtendNode(invoke, kit.getWordTypes().getWordKind().getBitCount()));

        /* Exceptions during the conversion are fatal. */
        kit.exceptionPart();
        kit.append(new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, kit.exceptionObject()));
        kit.append(new LoweredDeadEndNode());
        kit.endInvokeWithException();
        return result;
    }

    private void generateEpilogue(HostedGraphKit kit) {
        Class<?> epilogueClass = entryPointData.getEpilogue();
        if (epilogueClass == NoEpilogue.class) {
            UserError.guarantee(isUninterruptible(targetMethod), "%s.%s is allowed only for methods annotated with @%s: %s",
                            CEntryPointOptions.class.getSimpleName(), NoEpilogue.class.getSimpleName(), Uninterruptible.class.getSimpleName(), targetMethod);
            return;
        }

        AnalysisType epilogue = kit.getMetaAccess().lookupJavaType(epilogueClass);
        AnalysisMethod[] epilogueMethods = epilogue.getDeclaredMethods(false);
        UserError.guarantee(epilogueMethods.length == 1 && epilogueMethods[0].isStatic() && epilogueMethods[0].getSignature().getParameterCount(false) == 0,
                        "Epilogue class must declare exactly one static method without parameters: %s -> %s", targetMethod, epilogue);

        InvokeWithExceptionNode invoke = createInvokeStaticWithFatalExceptionHandler(kit, epilogueMethods[0]);
        invoke.setUseForInlining(false);
    }

    public boolean isNotPublished() {
        return entryPointData.getPublishAs().equals(CEntryPoint.Publish.NotPublished);
    }
}
