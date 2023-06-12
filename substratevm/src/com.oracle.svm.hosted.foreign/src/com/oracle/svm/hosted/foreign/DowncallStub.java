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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.foreign.AbiUtils;
import com.oracle.svm.core.foreign.DowncallStubsHolder;
import com.oracle.svm.core.foreign.NativeEntryPointInfo;
import com.oracle.svm.core.foreign.Target_com_oracle_svm_core_methodhandles_Util_java_lang_invoke_MethodHandle;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode.CapturableState;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

/**
 * A stub for foreign downcalls. The stubs responsibilities are:
 * <ul>
 * <li>Unbox the arguments;</li>
 * <li>Perform a C-function call :</li>
 * <ul>
 * <li>Provide it with a memory assignment for the arguments;</li>
 * <li>If a buffered return is required, provide the return assignment as well.</li>
 * </ul>
 * <li>If (part of) the call state must be captured, perform the necessary calls to get said call
 * state and store the result in the appropriate place</li>
 * </ul>
 * In particular, this latest point means that not safepoint must be placed between the requested
 * call and the subsequent calls/memory accesses done to capture the call state. This is done by
 * doing said capture in {@link com.oracle.svm.core.nodes.CFunctionEpilogueNode}, which follows the
 * method invocation without interruption. See the associated lowering
 * {@link CFunctionSnippets.CFunctionEpilogueLowering#lower(CFunctionEpilogueNode, LoweringTool)}.
 * <p>
 * Note that the stub *does not* perform most of the argument preprocessing, e.g. if the first
 * argument to the native function is a memory segment which must be split across 3 int registers
 * according to the ABI, this function expects to receive this argument as three integers, not as a
 * memory segment.
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
     * {@link Target_com_oracle_svm_core_methodhandles_Util_java_lang_invoke_MethodHandle#linkToNative(Object...)}.
     */
    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method, purpose);
        FrameStateBuilder state = kit.getFrameState();
        boolean deoptimizationTarget = MultiMethod.isDeoptTarget(method);
        List<ValueNode> arguments = kit.loadArguments(getSignature().toParameterTypes(null));
        AbiUtils.Adaptation[] adaptations = AbiUtils.singleton().adaptArguments(nep);

        assert arguments.size() == 1;
        arguments = adaptArguments(kit, nep.linkMethodType(), arguments.get(0), adaptations);
        MethodType callType = adaptMethodType(nep, adaptations);
        assert callType.parameterCount() == arguments.size();

        int callAddressIndex = nep.callAddressIndex();
        ValueNode callAddress = arguments.remove(callAddressIndex);
        callType = callType.dropParameterTypes(callAddressIndex, callAddressIndex + 1);

        ValueNode captureAddress = null;
        if (nep.capturesCallState()) {
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
                        capturedStates(nep),
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

    private static List<ValueNode> adaptArguments(HostedGraphKit kit, MethodType signature, ValueNode argumentsArray, AbiUtils.Adaptation[] adaptations) {
        var args = kit.liftArray(argumentsArray, JavaKind.Object, signature.parameterCount() + 1);
        assert adaptations.length == signature.parameterCount() : adaptations.length + " " + signature.parameterCount();
        assert args.size() == signature.parameterCount() + 1 : args.size() + " " + (signature.parameterCount() + 1);
        // We have to drop the NEP, which is the last argument
        args.remove(args.size() - 1);
        for (int i = 0; i < args.size(); ++i) {
            args.set(i, adaptArgument(kit, args.get(i), signature.parameterType(i), adaptations[i]));
        }
        return args;
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

    /**
     * Transform HotSpot capturable states into substrate capturable states.
     */
    private static Set<CapturableState> capturedStates(NativeEntryPointInfo nep) {
        Set<CapturableState> states = new HashSet<>();

        for (var state : jdk.internal.foreign.abi.CapturableState.values()) {
            if (nep.requiresCapture(state)) {
                /* This error should have been caught earlier... */
                VMError.guarantee(
                                AbiUtils.singleton().captureIsSupported(state),
                                "%s does not support capture of %s when performing a foreign call.",
                                AbiUtils.singleton().name(),
                                state.stateName());
                var mappedState = switch (state) {
                    case GET_LAST_ERROR -> CapturableState.GET_LAST_ERROR;
                    case WSA_GET_LAST_ERROR -> CapturableState.WSA_GET_LAST_ERROR;
                    case ERRNO -> CapturableState.ERRNO;
                };
                states.add(mappedState);
            }
        }

        return states;
    }
}
