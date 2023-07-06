/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodType;
import java.util.List;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.DowncallStubsHolder;
import com.oracle.svm.core.foreign.ForeignFunctionsRuntime;
import com.oracle.svm.core.foreign.LinkToNativeSupportImpl;
import com.oracle.svm.core.foreign.NativeEntryPointInfo;
import com.oracle.svm.core.foreign.Target_jdk_internal_foreign_abi_NativeEntryPoint;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * A stub for foreign downcalls. The "work repartition" for downcalls is as follows:
 * <ul>
 * <li>Transform "high-level" (e.g. structs) arguments into "low-level" arguments (i.e. int, long,
 * float, double, pointer) which fit in a register --- done by HotSpot's implementation using method
 * handles (or specialized classes);</li>
 * <li>Unbox the arguments (the arguments are in an array of Objects, due to funneling through
 * {@link LinkToNativeSupportImpl#linkToNative}) --- done by
 * {@link DowncallStub#adaptArguments};</li>
 * <li>Further adapt arguments as to satisfy SubstrateVM's backends --- done by
 * {@link DowncallStub#adaptArguments}, see {@link AbiUtils.Adaptation}</li>
 * <li>Transform HotSpot's memory moves into ones for SubstrateVM --- done by
 * {@link AbiUtils#toMemoryAssignment}</li>
 * <li>Perform a C-function call:</li>
 * <ul>
 * <li>Setup the frame anchor and capture call state --- Implemented in
 * {@link CFunctionSnippets#prologueSnippet}, {@link CFunctionSnippets#epilogueSnippet} and
 * {@link ForeignFunctionsRuntime#captureCallState};</li>
 * <li>Setup registers, stack and return buffer according to the aforementioned assignments ---
 * Implemented in the backend, e.g.
 * {@link com.oracle.svm.core.graal.amd64.SubstrateAMD64RegisterConfig#getCallingConvention} and
 * {@link com.oracle.svm.core.graal.amd64.SubstrateAMD64Backend.SubstrateAMD64NodeLIRBuilder#emitInvoke}</li>
 * </ul>
 * <li>If needed, box the return --- done by {@link DowncallStub#adaptReturnValue}.</li>
 * </ul>
 * Call state capture is done in the call epilogue to prevent the runtime environment from modifying
 * the call state, which could happen if a safepoint was inserted between the downcall and the
 * capture.
 */
@Platforms(Platform.HOSTED_ONLY.class)
class DowncallStub extends NonBytecodeMethod {
    public static Signature createSignature(MetaAccessProvider metaAccess) {
        return SimpleSignature.fromKinds(new JavaKind[]{JavaKind.Object}, JavaKind.Object, metaAccess);
    }

    private final NativeEntryPointInfo nep;

    DowncallStub(NativeEntryPointInfo nep, MetaAccessProvider metaAccess) {
        super(
                        DowncallStubsHolder.stubName(nep),
                        true,
                        metaAccess.lookupJavaType(DowncallStubsHolder.class),
                        createSignature(metaAccess),
                        DowncallStubsHolder.getConstantPool(metaAccess));
        this.nep = nep;
    }

    /**
     * The arguments follow the structure described in
     * {@link LinkToNativeSupportImpl#linkToNative(Object...)}.
     */
    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method, purpose);
        FrameStateBuilder state = kit.getFrameState();
        boolean deoptimizationTarget = MultiMethod.isDeoptTarget(method);
        List<ValueNode> arguments = kit.loadArguments(getSignature().toParameterTypes(null));
        AbiUtils.Adaptation[] adaptations = AbiUtils.singleton().adaptArguments(nep);

        assert arguments.size() == 1;
        var argumentsAndNep = adaptArguments(kit, nep.linkMethodType(), arguments.get(0), adaptations);
        arguments = argumentsAndNep.getLeft();
        ValueNode runtimeNep = argumentsAndNep.getRight();

        MethodType callType = adaptMethodType(nep, adaptations);
        assert callType.parameterCount() == arguments.size();

        int callAddressIndex = nep.callAddressIndex();
        ValueNode callAddress = arguments.remove(callAddressIndex);
        callType = callType.dropParameterTypes(callAddressIndex, callAddressIndex + 1);

        ForeignCallDescriptor captureFunction = null;
        ValueNode captureMask = null;
        ValueNode captureAddress = null;
        if (nep.capturesCallState()) {
            captureFunction = ForeignFunctionsRuntime.CAPTURE_CALL_STATE;
            captureMask = kit.createLoadField(runtimeNep, kit.getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(Target_jdk_internal_foreign_abi_NativeEntryPoint.class, "captureMask")));

            assert nep.captureAddressIndex() > nep.callAddressIndex();
            /*
             * We already removed 1 element from the list, so we must offset the index by one as
             * well
             */
            int captureAddressIndex = nep.captureAddressIndex() - 1;
            captureAddress = arguments.remove(captureAddressIndex);
            callType = callType.dropParameterTypes(captureAddressIndex, captureAddressIndex + 1);
        }
        assert callType.parameterCount() == arguments.size();

        state.clearLocals();
        SubstrateCallingConventionType cc = SubstrateCallingConventionKind.Native.toType(true)
                        .withParametersAssigned(nep.parametersAssignment())
                        /* Assignment might be null, in which case this is a no-op */
                        .withReturnSaving(nep.returnsAssignment());

        CFunction.Transition transition = nep.skipsTransition() ? CFunction.Transition.NO_TRANSITION : CFunction.Transition.TO_NATIVE;

        ValueNode returnValue = kit.createCFunctionCallWithCapture(
                        callAddress,
                        arguments,
                        SimpleSignature.fromMethodType(callType, kit.getMetaAccess()),
                        VMThreads.StatusSupport.getNewThreadStatus(transition),
                        deoptimizationTarget,
                        cc,
                        captureFunction,
                        captureMask,
                        captureAddress);

        returnValue = adaptReturnValue(kit, nep.linkMethodType(), returnValue);

        kit.createReturn(returnValue, JavaKind.Object);

        return kit.finalizeGraph();
    }

    private static ValueNode adaptArgument(HostedGraphKit kit, ValueNode argument, Class<?> type, AbiUtils.Adaptation adaptation) {
        argument = kit.createUnboxing(argument, JavaKind.fromJavaClass(type));
        if (adaptation != null) {
            argument = adaptation.apply(argument);
        }
        return argument;
    }

    private static MethodType adaptMethodType(NativeEntryPointInfo nep, AbiUtils.Adaptation[] adaptations) {
        MethodType mt = nep.linkMethodType();

        Class<?>[] parameters = new Class<?>[mt.parameterCount()];
        for (int i = 0; i < mt.parameterCount(); ++i) {
            Class<?> parameterType = mt.parameterType(i);
            assert parameterType.isPrimitive() : parameterType;

            AbiUtils.Adaptation adaptation = adaptations[i];
            if (adaptation != null) {
                parameterType = adaptation.apply(parameterType);
            }

            parameters[i] = parameterType;
        }

        return MethodType.methodType(mt.returnType(), parameters);
    }

    private static Pair<List<ValueNode>, ValueNode> adaptArguments(HostedGraphKit kit, MethodType signature, ValueNode argumentsArray, AbiUtils.Adaptation[] adaptations) {
        var args = kit.loadArrayElements(argumentsArray, JavaKind.Object, signature.parameterCount() + 1);
        assert adaptations.length == signature.parameterCount() : adaptations.length + " " + signature.parameterCount();
        assert args.size() == signature.parameterCount() + 1 : args.size() + " " + (signature.parameterCount() + 1);
        // We have to drop the NEP, which is the last argument
        ValueNode nep = args.remove(args.size() - 1);
        for (int i = 0; i < args.size(); ++i) {
            args.set(i, adaptArgument(kit, args.get(i), signature.parameterType(i), adaptations[i]));
        }
        return Pair.create(args, nep);
    }

    private static ValueNode adaptReturnValue(HostedGraphKit kit, MethodType signature, ValueNode invokeValue) {
        ValueNode returnValue = invokeValue;
        JavaKind returnKind = JavaKind.fromJavaClass(signature.returnType());
        if (returnKind.equals(JavaKind.Void)) {
            return kit.createObject(null);
        }

        var boxed = kit.getMetaAccess().lookupJavaType(returnKind.toBoxedJavaClass());
        returnValue = kit.createBoxing(returnValue, returnKind, boxed);
        return returnValue;
    }
}
