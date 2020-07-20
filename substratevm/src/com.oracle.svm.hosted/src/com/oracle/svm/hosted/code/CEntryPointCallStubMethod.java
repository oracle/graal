/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Iterator;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateContext;
import org.graalvm.nativeimage.c.function.CEntryPoint.IsolateThreadContext;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointBuiltins;
import com.oracle.svm.core.c.function.CEntryPointBuiltins.CEntryPointBuiltinImplementation;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoEpilogue;
import com.oracle.svm.core.c.function.CEntryPointOptions.NoPrologue;
import com.oracle.svm.core.c.function.CEntryPointSetup;
import com.oracle.svm.core.code.IsolateEnterStub;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode;
import com.oracle.svm.core.graal.nodes.CEntryPointLeaveNode.LeaveAction;
import com.oracle.svm.core.graal.nodes.CEntryPointPrologueBailoutNode;
import com.oracle.svm.core.graal.nodes.DeadEndNode;
import com.oracle.svm.core.graal.replacements.SubstrateGraphKit;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.c.info.EnumLookupInfo;
import com.oracle.svm.hosted.phases.CInterfaceEnumTool;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

public final class CEntryPointCallStubMethod implements ResolvedJavaMethod, GraphProvider {

    static CEntryPointCallStubMethod create(AnalysisMethod targetMethod, CEntryPointData entryPointData, AnalysisMetaAccess metaAccess) {
        ResolvedJavaMethod unwrappedMethod = targetMethod.getWrapped();
        MetaAccessProvider unwrappedMetaAccess = metaAccess.getWrapped();
        ResolvedJavaType declaringClass = unwrappedMetaAccess.lookupJavaType(IsolateEnterStub.class);
        ConstantPool constantPool = IsolateEnterStub.getConstantPool(unwrappedMetaAccess);
        return new CEntryPointCallStubMethod(entryPointData, unwrappedMethod, declaringClass, constantPool);
    }

    /**
     * Line numbers are bogus because this is generated code, but we need to include them in our
     * debug information. Otherwise, when setting a breakpoint, GDB will just pick a "nearby" code
     * location that has line number information, which can be in a different function.
     */
    private static final LineNumberTable lineNumberTable = new LineNumberTable(new int[]{1}, new int[]{0});

    private static final JavaKind cEnumParameterKind = JavaKind.Int;

    private final CEntryPointData entryPointData;
    private final ResolvedJavaMethod targetMethod;
    private final ResolvedJavaType holderClass;
    private final ConstantPool holderConstantPool;

    private StackTraceElement stackTraceElement;

    private CEntryPointCallStubMethod(CEntryPointData entryPointData, ResolvedJavaMethod targetMethod, ResolvedJavaType holderClass, ConstantPool holderConstantPool) {
        this.entryPointData = entryPointData;
        this.targetMethod = targetMethod;
        this.holderClass = holderClass;
        this.holderConstantPool = holderConstantPool;
    }

    @Override
    public String getName() {
        return SubstrateUtil.uniqueShortName(targetMethod);
    }

    @Override
    public Signature getSignature() {
        return targetMethod.getSignature();
    }

    @Override
    public Parameter[] getParameters() {
        return targetMethod.getParameters();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return holderClass;
    }

    @Override
    public ConstantPool getConstantPool() {
        return holderConstantPool;
    }

    private ResolvedJavaMethod lookupMethodInUniverse(UniverseMetaAccess metaAccess, ResolvedJavaMethod method) {
        ResolvedJavaMethod universeMethod = method;
        MetaAccessProvider wrappedMetaAccess = metaAccess.getWrapped();
        if (wrappedMetaAccess instanceof UniverseMetaAccess) {
            universeMethod = lookupMethodInUniverse((UniverseMetaAccess) wrappedMetaAccess, universeMethod);
        }
        return metaAccess.getUniverse().lookup(universeMethod);
    }

    AnalysisMethod lookupTargetMethod(AnalysisMetaAccess metaAccess) {
        return (AnalysisMethod) lookupMethodInUniverse(metaAccess, targetMethod);
    }

    private ResolvedJavaMethod unwrapMethodAndLookupInUniverse(UniverseMetaAccess metaAccess) {
        ResolvedJavaMethod unwrappedTargetMethod = targetMethod;
        while (unwrappedTargetMethod instanceof WrappedJavaMethod) {
            unwrappedTargetMethod = ((WrappedJavaMethod) unwrappedTargetMethod).getWrapped();
        }
        return lookupMethodInUniverse(metaAccess, unwrappedTargetMethod);
    }

    @Override
    public boolean allowRuntimeCompilation() {
        return false;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        if (entryPointData.getBuiltin() != CEntryPointData.DEFAULT_BUILTIN) {
            return buildBuiltinGraph(debug, method, providers);
        }

        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        NativeLibraries nativeLibraries = CEntryPointCallStubSupport.singleton().getNativeLibraries();
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);

        JavaType[] parameterTypes = method.toParameterTypes();
        JavaType[] parameterLoadTypes = Arrays.copyOf(parameterTypes, parameterTypes.length);
        EnumInfo[] parameterEnumInfos;

        parameterEnumInfos = adaptParameterTypes(providers, nativeLibraries, kit, parameterTypes, parameterLoadTypes, purpose);

        ValueNode[] args = kit.loadArguments(parameterLoadTypes).toArray(new ValueNode[0]);

        InvokeNode prologueInvoke = generatePrologue(providers, kit, parameterLoadTypes, targetMethod.getParameterAnnotations(), args);

        adaptArgumentValues(providers, kit, parameterTypes, parameterEnumInfos, args);

        ResolvedJavaMethod universeTargetMethod = unwrapMethodAndLookupInUniverse(metaAccess);

        int invokeBci = kit.bci();
        // Also support non-static test methods (they are not allowed to use the receiver)
        InvokeKind invokeKind = universeTargetMethod.isStatic() ? InvokeKind.Static : InvokeKind.Special;
        ValueNode[] invokeArgs = args;
        if (invokeKind != InvokeKind.Static) {
            invokeArgs = new ValueNode[args.length + 1];
            invokeArgs[0] = kit.createObject(null);
            System.arraycopy(args, 0, invokeArgs, 1, args.length);
        }
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(universeTargetMethod, invokeKind, kit.getFrameState(), invokeBci, invokeArgs);
        kit.exceptionPart();
        ExceptionObjectNode exception = kit.exceptionObject();
        generateExceptionHandler(providers, kit, exception, invoke.getStackKind());
        kit.endInvokeWithException();

        ValueNode returnValue = adaptReturnValue(method, providers, purpose, metaAccess, nativeLibraries, kit, invoke);

        InvokeNode epilogueInvoke = generateEpilogue(providers, kit);

        kit.createReturn(returnValue, returnValue.getStackKind());

        inlinePrologueAndEpilogue(kit, prologueInvoke, epilogueInvoke, invoke.getStackKind());

        return kit.finalizeGraph();
    }

    private StructuredGraph buildBuiltinGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers) {
        ResolvedJavaMethod universeTargetMethod = unwrapMethodAndLookupInUniverse((UniverseMetaAccess) providers.getMetaAccess());

        UserError.guarantee(entryPointData.getPrologue() == CEntryPointData.DEFAULT_PROLOGUE,
                        "@%s method declared as built-in must not have a custom prologue: %s", CEntryPoint.class.getSimpleName(), universeTargetMethod);
        UserError.guarantee(entryPointData.getEpilogue() == CEntryPointData.DEFAULT_EPILOGUE,
                        "@%s method declared as built-in must not have a custom epilogue: %s", CEntryPoint.class.getSimpleName(), universeTargetMethod);
        UserError.guarantee(entryPointData.getExceptionHandler() == CEntryPointData.DEFAULT_EXCEPTION_HANDLER,
                        "@%s method declared as built-in must not have a custom exception handler: %s", CEntryPoint.class.getSimpleName(), universeTargetMethod);

        UniverseMetaAccess metaAccess = (UniverseMetaAccess) providers.getMetaAccess();
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);

        ExecutionContextParameters executionContext = findExecutionContextParameters(providers, universeTargetMethod.toParameterTypes(), universeTargetMethod.getParameterAnnotations());

        final CEntryPoint.Builtin builtin = entryPointData.getBuiltin();
        ResolvedJavaMethod builtinCallee = null;
        for (ResolvedJavaMethod candidate : metaAccess.lookupJavaType(CEntryPointBuiltins.class).getDeclaredMethods()) {
            CEntryPointBuiltinImplementation annotation = candidate.getAnnotation(CEntryPointBuiltinImplementation.class);
            if (annotation != null && annotation.builtin().equals(builtin)) {
                VMError.guarantee(builtinCallee == null, "More than one candidate for @" + CEntryPoint.class.getSimpleName() + " built-in " + builtin);
                builtinCallee = candidate;
            }
        }
        VMError.guarantee(builtinCallee != null, "No candidate for @" + CEntryPoint.class.getSimpleName() + " built-in " + builtin);

        ResolvedJavaType isolateType = providers.getMetaAccess().lookupJavaType(Isolate.class);
        ResolvedJavaType threadType = providers.getMetaAccess().lookupJavaType(IsolateThread.class);
        int builtinIsolateIndex = -1;
        int builtinThreadIndex = -1;
        JavaType[] builtinParamTypes = builtinCallee.toParameterTypes();
        for (int i = 0; i < builtinParamTypes.length; i++) {
            ResolvedJavaType type = (ResolvedJavaType) builtinParamTypes[i];
            if (isolateType.isAssignableFrom(type)) {
                VMError.guarantee(builtinIsolateIndex == -1, "@" + CEntryPoint.class.getSimpleName() + " built-in with more than one " +
                                Isolate.class.getSimpleName() + " parameter: " + builtinCallee.format("%H.%n(%p)"));
                builtinIsolateIndex = i;
            } else if (threadType.isAssignableFrom(type)) {
                VMError.guarantee(builtinThreadIndex == -1, "@" + CEntryPoint.class.getSimpleName() + " built-in with more than one " +
                                IsolateThread.class.getSimpleName() + " parameter: " + builtinCallee.format("%H.%n(%p)"));
                builtinThreadIndex = i;
            } else {
                VMError.shouldNotReachHere("@" + CEntryPoint.class.getSimpleName() + " built-in currently may have only " + Isolate.class.getSimpleName() +
                                " or " + IsolateThread.class.getSimpleName() + " parameters: " + builtinCallee.format("%H.%n(%p)"));
            }
        }

        ValueNode[] args = kit.loadArguments(method.toParameterTypes()).toArray(new ValueNode[0]);

        ValueNode[] builtinArgs = new ValueNode[builtinParamTypes.length];
        if (builtinIsolateIndex != -1) {
            VMError.guarantee(executionContext.designatedIsolateIndex != -1 || executionContext.isolateCount == 1,
                            "@" + CEntryPoint.class.getSimpleName() + " built-in " + entryPointData.getBuiltin() + " needs exactly one " +
                                            Isolate.class.getSimpleName() + " parameter: " + builtinCallee.format("%H.%n(%p)"));
            int index = (executionContext.designatedIsolateIndex != -1) ? executionContext.designatedIsolateIndex : executionContext.lastIsolateIndex;
            builtinArgs[builtinIsolateIndex] = args[index];
        }
        if (builtinThreadIndex != -1) {
            VMError.guarantee(executionContext.designatedThreadIndex != -1 || executionContext.threadCount == 1,
                            "@" + CEntryPoint.class.getSimpleName() + " built-in " + entryPointData.getBuiltin() + " needs exactly one " +
                                            IsolateThread.class.getSimpleName() + " parameter: " + builtinCallee.format("%H.%n(%p)"));
            int index = (executionContext.designatedThreadIndex != -1) ? executionContext.designatedThreadIndex : executionContext.lastThreadIndex;
            builtinArgs[builtinThreadIndex] = args[index];
        }

        int invokeBci = kit.bci();
        InvokeWithExceptionNode invoke = kit.startInvokeWithException(builtinCallee, InvokeKind.Static, kit.getFrameState(), invokeBci, builtinArgs);
        kit.exceptionPart();
        ExceptionObjectNode exception = kit.exceptionObject();

        generateExceptionHandler(providers, kit, exception, invoke.getStackKind());
        kit.endInvokeWithException();

        kit.createReturn(invoke, universeTargetMethod.getSignature().getReturnKind());

        return kit.finalizeGraph();
    }

    private EnumInfo[] adaptParameterTypes(HostedProviders providers, NativeLibraries nativeLibraries, HostedGraphKit kit,
                    JavaType[] parameterTypes, JavaType[] parameterLoadTypes, Purpose purpose) {

        EnumInfo[] parameterEnumInfos = null;
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!parameterTypes[i].getJavaKind().isPrimitive() && !providers.getWordTypes().isWord(parameterTypes[i])) {
                ElementInfo typeInfo = nativeLibraries.findElementInfo((ResolvedJavaType) parameterTypes[i]);
                if (typeInfo instanceof EnumInfo) {
                    UserError.guarantee(typeInfo.getChildren().stream().anyMatch(EnumLookupInfo.class::isInstance),
                                    "Enum class %s needs a method that is annotated with @%s because it is used as a parameter of an entry point method: %s",
                                    parameterTypes[i],
                                    CEnumLookup.class.getSimpleName(),
                                    targetMethod);

                    if (parameterEnumInfos == null) {
                        parameterEnumInfos = new EnumInfo[parameterTypes.length];
                    }
                    parameterEnumInfos[i] = (EnumInfo) typeInfo;

                    parameterLoadTypes[i] = providers.getMetaAccess().lookupJavaType(cEnumParameterKind.toJavaClass());

                    final int parameterIndex = i;
                    FrameState initialState = kit.getGraph().start().stateAfter();
                    Iterator<ValueNode> matchingNodes = initialState.values().filter(node -> ((ParameterNode) node).index() == parameterIndex).iterator();
                    ValueNode parameterNode = matchingNodes.next();
                    assert !matchingNodes.hasNext() && parameterNode.usages().filter(n -> n != initialState).isEmpty();
                    parameterNode.setStamp(StampFactory.forKind(cEnumParameterKind));
                } else if (purpose != Purpose.ANALYSIS) {
                    // for analysis test cases: abort only during compilation
                    throw UserError.abort("Entry point method parameter types are restricted to primitive types, word types and enumerations (@%s): %s",
                                    CEnum.class.getSimpleName(), targetMethod);
                }
            }
        }
        return parameterEnumInfos;
    }

    private static void adaptArgumentValues(HostedProviders providers, HostedGraphKit kit, JavaType[] parameterTypes, EnumInfo[] parameterEnumInfos, ValueNode[] args) {
        if (parameterEnumInfos != null) {
            // These methods must be called after the prologue established a safe context
            for (int i = 0; i < parameterEnumInfos.length; i++) {
                if (parameterEnumInfos[i] != null) {
                    CInterfaceEnumTool tool = new CInterfaceEnumTool(providers.getMetaAccess(), providers.getSnippetReflection());
                    args[i] = tool.createEnumLookupInvoke(kit, (ResolvedJavaType) parameterTypes[i], parameterEnumInfos[i], cEnumParameterKind, args[i]);
                }
            }
        }
    }

    private InvokeNode generatePrologue(HostedProviders providers, SubstrateGraphKit kit, JavaType[] parameterTypes, Annotation[][] parameterAnnotations, ValueNode[] args) {
        Class<?> prologueClass = entryPointData.getPrologue();
        if (prologueClass == NoPrologue.class) {
            UserError.guarantee(targetMethod.getAnnotation(Uninterruptible.class) != null,
                            "%s.%s is allowed only for methods annotated with @%s: %s",
                            CEntryPointOptions.class.getSimpleName(),
                            NoPrologue.class.getSimpleName(),
                            Uninterruptible.class.getSimpleName(),
                            targetMethod);
            return null;
        }
        if (prologueClass != CEntryPointOptions.AutomaticPrologue.class) {
            ResolvedJavaType prologue = providers.getMetaAccess().lookupJavaType(prologueClass);
            ResolvedJavaMethod[] prologueMethods = prologue.getDeclaredMethods();
            UserError.guarantee(prologueMethods.length == 1 && prologueMethods[0].isStatic(),
                            "Prologue class must declare exactly one static method: %s -> %s",
                            targetMethod,
                            prologue);
            ValueNode[] prologueArgs = matchPrologueParameters(providers, parameterTypes, args, prologueMethods[0]);
            return kit.createInvoke(prologueMethods[0], InvokeKind.Static, kit.getFrameState(), kit.bci(), prologueArgs);
        }

        // Automatically choose prologue from signature and annotations and call
        ExecutionContextParameters executionContext = findExecutionContextParameters(providers, parameterTypes, parameterAnnotations);
        int contextIndex = -1;
        if (executionContext.designatedThreadIndex != -1) {
            contextIndex = executionContext.designatedThreadIndex;
        } else if (executionContext.threadCount == 1) {
            contextIndex = executionContext.lastThreadIndex;
        } else {
            UserError.abort("@" + CEntryPoint.class.getSimpleName() + " requires exactly one execution context parameter of type %s: %s",
                            IsolateThread.class.getSimpleName(), targetMethod);
        }
        ValueNode contextValue = args[contextIndex];
        prologueClass = CEntryPointSetup.EnterPrologue.class;
        ResolvedJavaMethod[] prologueMethods = providers.getMetaAccess().lookupJavaType(prologueClass).getDeclaredMethods();
        assert prologueMethods.length == 1 && prologueMethods[0].isStatic() : "Prologue class must declare exactly one static method";
        return kit.createInvoke(prologueMethods[0], InvokeKind.Static, kit.getFrameState(), kit.bci(), contextValue);
    }

    private static class ExecutionContextParameters {
        int isolateCount = 0;
        int lastIsolateIndex = -1;
        int designatedIsolateIndex = -1;

        int threadCount = 0;
        int lastThreadIndex = -1;
        int designatedThreadIndex = -1;
    }

    private ExecutionContextParameters findExecutionContextParameters(HostedProviders providers, JavaType[] parameterTypes, Annotation[][] parameterAnnotations) {
        ResolvedJavaType isolateType = providers.getMetaAccess().lookupJavaType(Isolate.class);
        ResolvedJavaType threadType = providers.getMetaAccess().lookupJavaType(IsolateThread.class);

        ExecutionContextParameters result = new ExecutionContextParameters();
        for (int i = 0; i < parameterTypes.length; i++) {
            ResolvedJavaType declaredType = (ResolvedJavaType) parameterTypes[i];
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

    private ValueNode[] matchPrologueParameters(HostedProviders providers, JavaType[] types, ValueNode[] values, ResolvedJavaMethod prologueMethod) {
        JavaType[] prologueTypes = prologueMethod.toParameterTypes();
        ValueNode[] prologueValues = new ValueNode[prologueTypes.length];
        int i = 0;
        for (int p = 0; p < prologueTypes.length; p++) {
            ResolvedJavaType prologueType = (ResolvedJavaType) prologueTypes[p];
            UserError.guarantee(prologueType.isPrimitive() || providers.getWordTypes().isWord(prologueType),
                            "Prologue method parameter types are restricted to primitive types and word types: %s -> %s",
                            targetMethod,
                            prologueMethod);
            while (i < types.length && !prologueType.isAssignableFrom((ResolvedJavaType) types[i])) {
                i++;
            }
            if (i >= types.length) {
                throw UserError.abort("Unable to match signature of entry point method to that of prologue method: %s -> %s",
                                targetMethod,
                                prologueMethod);
            }
            prologueValues[p] = values[i];
            i++;
        }
        return prologueValues;
    }

    private void generateExceptionHandler(HostedProviders providers, SubstrateGraphKit kit, ExceptionObjectNode exception, JavaKind returnKind) {
        if (entryPointData.getExceptionHandler() == CEntryPoint.FatalExceptionHandler.class) {
            kit.appendStateSplitProxy(exception.stateAfter());
            CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, exception);
            kit.append(leave);
            kit.append(new DeadEndNode());
        } else {
            ResolvedJavaType throwable = providers.getMetaAccess().lookupJavaType(Throwable.class);
            ResolvedJavaType handler = providers.getMetaAccess().lookupJavaType(entryPointData.getExceptionHandler());
            ResolvedJavaMethod[] handlerMethods = handler.getDeclaredMethods();
            UserError.guarantee(handlerMethods.length == 1 && handlerMethods[0].isStatic(),
                            "Exception handler class must declare exactly one static method: % -> %s", targetMethod, handler);
            JavaType[] handlerParameterTypes = handlerMethods[0].toParameterTypes();
            UserError.guarantee(handlerParameterTypes.length == 1 &&
                            ((ResolvedJavaType) handlerParameterTypes[0]).isAssignableFrom(throwable),
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
            InvokeNode epilogueInvoke = generateEpilogue(providers, kit);

            kit.createReturn(returnValue, returnValue.getStackKind());
            kit.exceptionPart(); // fail-safe for exceptions in exception handler
            kit.append(new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, kit.exceptionObject()));
            kit.append(new DeadEndNode());
            kit.endInvokeWithException();

            kit.inline(epilogueInvoke, "Inline epilogue.", "GraphBuilding");
        }
    }

    private ValueNode adaptReturnValue(ResolvedJavaMethod method, HostedProviders providers, Purpose purpose,
                    UniverseMetaAccess metaAccess, NativeLibraries nativeLibraries, HostedGraphKit kit, ValueNode invokeValue) {

        ValueNode returnValue = invokeValue;
        if (returnValue.getStackKind().isPrimitive()) {
            return returnValue;
        }
        JavaType returnType = method.getSignature().getReturnType(null);
        ElementInfo typeInfo = nativeLibraries.findElementInfo((ResolvedJavaType) returnType);
        if (typeInfo instanceof EnumInfo) {
            IsNullNode isNull = kit.unique(new IsNullNode(returnValue));
            kit.startIf(isNull, BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY);
            kit.thenPart();
            ResolvedJavaType enumExceptionType = metaAccess.lookupJavaType(RuntimeException.class);
            NewInstanceNode enumException = kit.append(new NewInstanceNode(enumExceptionType, true));
            Iterator<ResolvedJavaMethod> enumExceptionCtor = Arrays.stream(enumExceptionType.getDeclaredConstructors()).filter(
                            c -> c.getSignature().getParameterCount(false) == 1 && c.getSignature().getParameterType(0, null).equals(metaAccess.lookupJavaType(String.class))).iterator();
            ConstantNode enumExceptionMessage = kit.createConstant(kit.getConstantReflection().forString("null return value cannot be converted to a C enum value"), JavaKind.Object);
            kit.createJavaCallWithExceptionAndUnwind(InvokeKind.Special, enumExceptionCtor.next(), enumException, enumExceptionMessage);
            assert !enumExceptionCtor.hasNext();
            kit.appendStateSplitProxy(kit.getFrameState());
            CEntryPointLeaveNode leave = new CEntryPointLeaveNode(LeaveAction.ExceptionAbort, enumException);
            kit.append(leave);
            kit.append(new DeadEndNode());
            kit.endIf();

            // Always return enum values as a signed word because it should never be a problem if
            // the caller expects a narrower integer type and the various checks already handle
            // replacements with word types
            CInterfaceEnumTool tool = new CInterfaceEnumTool(providers.getMetaAccess(), providers.getSnippetReflection());
            JavaKind cEnumReturnType = providers.getWordTypes().getWordKind();
            assert !cEnumReturnType.isUnsigned() : "requires correct representation of signed values";
            returnValue = tool.createEnumValueInvoke(kit, (EnumInfo) typeInfo, cEnumReturnType, returnValue);
        } else if (purpose != Purpose.ANALYSIS) {
            // for analysis test cases: abort only during compilation
            throw UserError.abort("Entry point method return types are restricted to primitive types, word types and enumerations (@%s): %s",
                            CEnum.class.getSimpleName(), targetMethod);
        }
        return returnValue;
    }

    private InvokeNode generateEpilogue(HostedProviders providers, SubstrateGraphKit kit) {
        Class<?> epilogueClass = entryPointData.getEpilogue();
        if (epilogueClass == NoEpilogue.class) {
            UserError.guarantee(targetMethod.getAnnotation(Uninterruptible.class) != null,
                            "%s.%s is allowed only for methods annotated with @%s: %s",
                            CEntryPointOptions.class.getSimpleName(),
                            NoEpilogue.class.getSimpleName(),
                            Uninterruptible.class.getSimpleName(),
                            targetMethod);
            return null;
        }
        ResolvedJavaType epilogue = providers.getMetaAccess().lookupJavaType(epilogueClass);
        ResolvedJavaMethod[] epilogueMethods = epilogue.getDeclaredMethods();
        UserError.guarantee(epilogueMethods.length == 1 && epilogueMethods[0].isStatic() && epilogueMethods[0].getSignature().getParameterCount(false) == 0,
                        "Epilogue class must declare exactly one static method without parameters: %s -> %s", targetMethod, epilogue);
        return kit.createInvoke(epilogueMethods[0], InvokeKind.Static, kit.getFrameState(), kit.bci());
    }

    private static void inlinePrologueAndEpilogue(SubstrateGraphKit kit, InvokeNode prologueInvoke, InvokeNode epilogueInvoke, JavaKind returnKind) {
        if (prologueInvoke != null) {
            FixedNode next = prologueInvoke.next();
            FrameState stateAfterPrologue = prologueInvoke.stateAfter();
            if (stateAfterPrologue == null) {
                stateAfterPrologue = kit.getFrameState().create(prologueInvoke.bci(), null);
            } else {
                stateAfterPrologue = stateAfterPrologue.duplicateWithVirtualState();
            }
            kit.inline(prologueInvoke, "Inline prologue.", "GraphBuilding");
            if (next.isAlive() && next.predecessor() instanceof AbstractMergeNode) {
                AbstractMergeNode merge = (AbstractMergeNode) next.predecessor();
                if (merge.stateAfter() == null) {
                    merge.setStateAfter(stateAfterPrologue);
                }
            }
            NodeIterable<CEntryPointPrologueBailoutNode> bailoutNodes = kit.getGraph().getNodes().filter(CEntryPointPrologueBailoutNode.class);
            for (CEntryPointPrologueBailoutNode node : bailoutNodes) {
                ValueNode result = node.getResult();
                switch (returnKind) {
                    case Float:
                        assert result.getStackKind().isNumericFloat();
                        result = kit.unique(new FloatConvertNode(FloatConvert.D2F, result));
                        break;
                    case Byte:
                    case Char:
                    case Short:
                    case Int:
                        assert result.getStackKind().isNumericInteger();
                        result = kit.unique(new NarrowNode(result, returnKind.getBitCount()));
                        break;
                    default:
                        // no conversion necessary
                        break;
                }
                ReturnNode returnNode = kit.add(new ReturnNode(result));
                node.replaceAndDelete(returnNode);
            }
        }

        if (epilogueInvoke != null && epilogueInvoke.isAlive()) {
            kit.inline(epilogueInvoke, "Inline epilogue.", "GraphBuilding");
        }
    }

    @Override
    public int getModifiers() {
        return Modifier.PUBLIC | Modifier.STATIC;
    }

    @Override
    public byte[] getCode() {
        return null;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    public int getMaxLocals() {
        return 2 * getSignature().getParameterCount(true);
    }

    @Override
    public int getMaxStackSize() {
        return 2;
    }

    @Override
    public boolean isSynthetic() {
        return false;
    }

    @Override
    public boolean isVarArgs() {
        return false;
    }

    @Override
    public boolean isBridge() {
        return false;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public boolean isClassInitializer() {
        return false;
    }

    @Override
    public boolean isConstructor() {
        return false;
    }

    @Override
    public boolean canBeStaticallyBound() {
        return true;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return new ExceptionHandler[0];
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        if (stackTraceElement == null) {
            stackTraceElement = new StackTraceElement(getDeclaringClass().toJavaName(true), getName(), "generated", 0);
        }
        return stackTraceElement;
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw VMError.unimplemented();
    }

    @Override
    public void reprofile() {
        throw VMError.unimplemented();
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw VMError.unimplemented();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean canBeInlined() {
        return false;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return false;
    }

    @Override
    public boolean shouldBeInlined() {
        return false;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return lineNumberTable;
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return null;
    }

    @Override
    public Constant getEncoding() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw VMError.unimplemented();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw VMError.unimplemented();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    @Override
    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }
}
