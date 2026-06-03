/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.foreign;

import static com.oracle.svm.util.AnnotationUtil.newAnnotationValue;

import java.lang.invoke.MethodType;
import java.util.List;

import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.DowncallStubsHolder;
import com.oracle.svm.core.foreign.ForeignFunctionsRuntime;
import com.oracle.svm.core.foreign.NativeEntryPointInfo;
import com.oracle.svm.core.foreign.Target_jdk_internal_foreign_abi_NativeEntryPoint;
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.util.GuestAccess;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A stub for foreign downcalls.
 * <p>
 * The "work repartition" for downcalls is as follows:
 * <ul>
 * <li>Transform "high-level" (e.g. structs) arguments into "low-level" arguments (i.e. int, long,
 * float, double, pointer) which fit in a register --- done by HotSpot's implementation using method
 * handles (or specialized classes);</li>
 * <li>Unbox the arguments (the arguments are in an array of Objects, due to funneling through
 * {@link ForeignFunctionsRuntime#linkToNative}) --- done by
 * {@link ForeignGraphKit#unboxArguments};</li>
 * <li>Further adapt arguments as to satisfy SubstrateVM's backends --- done by
 * {@link AbiUtils#adapt}</li>
 * <li>Perform a C-function call:</li>
 * <ul>
 * <li>Setup the frame anchor and capture call state --- Implemented in
 * {@link CFunctionSnippets#prologueSnippet}, {@link CFunctionSnippets#captureSnippet},
 * {@link CFunctionSnippets#epilogueSnippet} and
 * {@link ForeignFunctionsRuntime#captureCallState};</li>
 * <li>Setup registers, stack and return buffer --- Implemented in the backend, e.g.
 * {@link com.oracle.svm.core.graal.amd64.SubstrateAMD64RegisterConfig#getCallingConvention} and
 * {@link com.oracle.svm.core.graal.amd64.SubstrateAMD64Backend.SubstrateAMD64NodeLIRBuilder#emitInvoke}</li>
 * </ul>
 * <li>If needed, box the return --- done by {@link ForeignGraphKit#boxAndReturn}.</li>
 * </ul>
 * Call state capture is done in the call epilogue to prevent the runtime environment from modifying
 * the call state, which could happen if a safepoint was inserted between the downcall and the
 * capture.
 * <p>
 * The downcall stubs conform to the signature from {@link NativeEntryPointInfo}. This is necessary
 * for the intrinsification of downcall handles in which case a direct call to the downcall stub
 * will be emitted (or the stub will even be inlined). As a consequence, downcall stubs cannot
 * directly be called from the method handle interpreter which invokes varargs method
 * {@code java.lang.invoke.MethodHandle#linkToNative}. That part goes through
 * {@link DowncallStubInvoker}.
 */
@SuppressWarnings("javadoc")
@BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+7/src/hotspot/share/prims/nativeEntryPoint.cpp")
@BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+18/src/hotspot/cpu/x86/downcallLinker_x86_64.cpp")
@BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25+17/src/hotspot/cpu/aarch64/downcallLinker_aarch64.cpp")
class DowncallStub extends NonBytecodeMethod {
    static ResolvedSignature<ResolvedJavaType> createSignature(MetaAccessProvider metaAccess, MethodType mt) {
        Class<?>[] array = mt.parameterArray();
        ResolvedJavaType[] parameterTypes = new ResolvedJavaType[array.length + 1];
        for (int i = 0; i < parameterTypes.length - 1; i++) {
            parameterTypes[i] = metaAccess.lookupJavaType(array[i]);
        }
        parameterTypes[parameterTypes.length - 1] = metaAccess.lookupJavaType(Target_jdk_internal_foreign_abi_NativeEntryPoint.class);
        return ResolvedSignature.fromArray(parameterTypes, metaAccess.lookupJavaType(mt.returnType()));
    }

    private final NativeEntryPointInfo nepi;

    DowncallStub(NativeEntryPointInfo nepi, MetaAccessProvider metaAccess) {
        super(DowncallStubsHolder.stubName(nepi),
                        true,
                        metaAccess.lookupJavaType(DowncallStubsHolder.class),
                        createSignature(metaAccess, nepi.methodType()),
                        DowncallStubsHolder.getConstantPool(metaAccess));
        this.nepi = nepi;
    }

    MethodType getMethodType() {
        return nepi.methodType();
    }

    private static final List<AnnotationValue> INJECTED_ANNOTATIONS_FOR_ALLOW_HEAP_ACCESS = List.of(
                    newAnnotationValue(GuestAccess.elements().Uninterruptible,
                                    "reason", "See DowncallStub.getInjectedAnnotations.",
                                    "calleeMustBe", false));

    @Override
    public List<AnnotationValue> getInjectedAnnotations() {
        /*
         * When allowHeapAccess is enabled, a HeapMemorySegmentImpl may be passed to a downcall. In
         * that case, the downcall stub will generate a native pointer to the backing object. Thus,
         * ensure that no safepoint is generated in the stub, so that a GC cannot move the backing
         * object while the native pointer to it still exists.
         */
        if (nepi.allowHeapAccess()) {
            return INJECTED_ANNOTATIONS_FOR_ALLOW_HEAP_ACCESS;
        }
        return List.of();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        AbiUtils abiUtils = AbiUtils.singleton();

        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method);
        FrameStateBuilder state = kit.getFrameState();
        boolean deoptimizationTarget = SubstrateCompilationDirectives.isDeoptTarget(method);
        List<ValueNode> initialArguments = kit.getInitialArguments();
        /*
         * The downcall stub expects at least 2 args: (1) the C function address and (2) the
         * captureMask
         */
        assert initialArguments.size() >= 2;

        ValueNode runtimeNep = initialArguments.getLast();
        List<ValueNode> arguments = initialArguments.subList(0, initialArguments.size() - 1);

        AbiUtils.Adapter.Result.FullNativeAdaptation adapted = abiUtils.adapt(arguments, nepi);
        for (var node : adapted.nodesToAppendToGraph()) {
            kit.append(node);
        }

        ValueNode callAddress = adapted.getArgument(AbiUtils.Adapter.Extracted.CallTarget);

        ForeignCallDescriptor captureFunction = null;
        ValueNode captureMask = null;
        ValueNode captureAddress = null;
        if (nepi.capturesCallState()) {
            captureFunction = ForeignFunctionsRuntime.CAPTURE_CALL_STATE;
            captureMask = kit.createLoadField(runtimeNep,
                            kit.getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(Target_jdk_internal_foreign_abi_NativeEntryPoint.class, "captureMask")));
            captureAddress = adapted.getArgument(AbiUtils.Adapter.Extracted.CaptureBufferAddress);
        }

        state.clearLocals();
        SubstrateCallingConventionType cc = SubstrateCallingConventionType.makeCustom(true, adapted.parametersAssignment().toArray(AssignedLocation.EMPTY_ARRAY),
                        adapted.returnsAssignment().toArray(AssignedLocation.EMPTY_ARRAY));

        CFunction.Transition transition = nepi.skipsTransition() ? CFunction.Transition.NO_TRANSITION : CFunction.Transition.TO_NATIVE;

        ValueNode returnValue = kit.createCFunctionCallWithCapture(
                        callAddress,
                        adapted.arguments(),
                        ResolvedSignature.fromMethodType(adapted.callType(), kit.getMetaAccess()),
                        VMThreads.StatusSupport.getNewThreadStatus(transition),
                        deoptimizationTarget,
                        cc,
                        captureFunction,
                        captureMask,
                        captureAddress);

        kit.createReturn(returnValue, nepi.methodType());

        return kit.finalizeGraph();
    }
}

/**
 * This method just invokes a downcall stub with a specific signature. The actual downcall stub to
 * be invoked needs to be provided as a function pointer in the first argument.
 * <p>
 * This invoker is required for the common case where downcall handles are created run time and
 * invoked via the method handle interpreter. The interpreter will call varargs method
 * {@code java.lang.invoke.MethodHandle#linkToNative} to invoke the stub. This downcall stub
 * invoker's responsibilities are therefore to:
 * <ul>
 * <li>Unbox the arguments (the arguments are in an array of Objects, due to funneling through
 * {@link ForeignFunctionsRuntime#linkToNative}) --- done by
 * {@link ForeignGraphKit#unboxArguments};</li>
 * <li>Call the {@link DowncallStub downcall stub}.</li>
 * <li>If needed, box the return value --- done by {@link ForeignGraphKit#boxAndReturn}.</li>
 * </ul>
 */
class DowncallStubInvoker extends NonBytecodeMethod {

    /** Signature of the {@link DowncallStub}s that this invoker is able to invoke. */
    private final Signature targetSignature;

    DowncallStubInvoker(Signature targetSignature, MetaAccessProvider metaAccess, WordTypes wordTypes) {
        super(createName(targetSignature),
                        true,
                        metaAccess.lookupJavaType(DowncallStubsHolder.class),
                        createSignature(metaAccess, wordTypes),
                        DowncallStubsHolder.getConstantPool(metaAccess));
        this.targetSignature = targetSignature;
    }

    private static Signature createSignature(MetaAccessProvider metaAccess, WordTypes wordTypes) {
        JavaKind wordKind = wordTypes.getWordKind();
        JavaKind[] args = new JavaKind[2];
        args[0] = wordKind; // the method (downcall stub) to call
        args[1] = JavaKind.Object; // an Object[]; the arguments array for the target
        return ResolvedSignature.fromKinds(args, JavaKind.Object, metaAccess);
    }

    private static String createName(Signature targetSignature) {
        return "downcall_invoker_" + ForeignGraphKit.signatureToIdentifier(targetSignature);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method);

        var invokeSignature = kit.getMetaAccess().getUniverse().lookup(targetSignature, getDeclaringClass());

        List<ValueNode> initialArguments = kit.getInitialArguments();

        // we expect two args, i.e., the methodAddress and an Object[]
        assert initialArguments.size() == 2;
        ValueNode methodAddress = initialArguments.get(0);
        int n = targetSignature.getParameterCount(false);
        List<ValueNode> arguments = kit.loadArrayElements(initialArguments.get(1), JavaKind.Object, n);

        // the last argument (i.e. 'args[n-1]') is the run-time NativeEntryPoint object
        assert arguments.getLast().getStackKind().isObject();
        assert targetSignature.getParameterKind(n - 1).isObject();

        ValueNode[] unboxedArguments = kit.unboxArguments(arguments, targetSignature);
        ValueNode returnValue = createMethodCall(kit, invokeSignature.getReturnType(), invokeSignature.toParameterTypes(null), methodAddress, unboxedArguments);

        kit.boxAndReturn(returnValue, targetSignature.getReturnKind());
        return kit.finalizeGraph();
    }

    private static ValueNode createMethodCall(ForeignGraphKit kit, JavaType returnType, JavaType[] paramTypes, ValueNode methodAddress, ValueNode[] args) {
        StampPair returnStamp = StampFactory.forDeclaredType(kit.getAssumptions(), returnType, false);
        CallTargetNode callTarget = new IndirectCallTargetNode(methodAddress, args, returnStamp, paramTypes,
                        null, SubstrateCallingConventionKind.Java.toType(true), InvokeKind.Static);

        InvokeWithExceptionNode invoke = kit.startInvokeWithException(callTarget, kit.getFrameState(), kit.bci());
        kit.exceptionPart();
        ExceptionObjectNode exception = kit.exceptionObject();
        kit.append(new UnwindNode(exception));
        kit.endInvokeWithException();
        return invoke;
    }

    @Override
    public boolean shouldBeInlined() {
        return true;
    }

    @Override
    public boolean canBeInlined() {
        return true;
    }
}
