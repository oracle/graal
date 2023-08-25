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

import java.util.List;

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
import com.oracle.svm.core.graal.code.AssignedLocation;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.snippets.CFunctionSnippets;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.hosted.code.NonBytecodeMethod;
import com.oracle.svm.hosted.code.SimpleSignature;
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
 * {@link ForeignGraphKit#unboxArguments};</li>
 * <li>Further adapt arguments as to satisfy SubstrateVM's backends --- done by
 * {@link AbiUtils.adapt}</li>
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
        ForeignGraphKit kit = new ForeignGraphKit(debug, providers, method, purpose);
        FrameStateBuilder state = kit.getFrameState();
        boolean deoptimizationTarget = MultiMethod.isDeoptTarget(method);
        List<ValueNode> arguments = kit.loadArguments(getSignature().toParameterTypes(null));

        assert arguments.size() == 1;
        var argumentsAndNep = kit.unpackArgumentsAndExtractNEP(arguments.get(0), nep.methodType());
        arguments = argumentsAndNep.getLeft();
        ValueNode runtimeNep = argumentsAndNep.getRight();

        AbiUtils.Adapter.AdaptationResult adapted = AbiUtils.singleton().adapt(kit.unboxArguments(arguments, this.nep.methodType()), this.nep);

        ValueNode callAddress = adapted.getArgument(AbiUtils.Adapter.Extracted.CallTarget);

        ForeignCallDescriptor captureFunction = null;
        ValueNode captureMask = null;
        ValueNode captureAddress = null;
        if (nep.capturesCallState()) {
            captureFunction = ForeignFunctionsRuntime.CAPTURE_CALL_STATE;
            captureMask = kit.createLoadField(runtimeNep, kit.getMetaAccess().lookupJavaField(ReflectionUtil.lookupField(Target_jdk_internal_foreign_abi_NativeEntryPoint.class, "captureMask")));
            captureAddress = adapted.getArgument(AbiUtils.Adapter.Extracted.CaptureBufferAddress);
        }

        state.clearLocals();
        SubstrateCallingConventionType cc = SubstrateCallingConventionType.makeCustom(true, adapted.parametersAssignment().toArray(new AssignedLocation[0]),
                        adapted.returnsAssignment().toArray(new AssignedLocation[0]));

        CFunction.Transition transition = nep.skipsTransition() ? CFunction.Transition.NO_TRANSITION : CFunction.Transition.TO_NATIVE;

        ValueNode returnValue = kit.createCFunctionCallWithCapture(
                        callAddress,
                        adapted.arguments(),
                        SimpleSignature.fromMethodType(adapted.callType(), kit.getMetaAccess()),
                        VMThreads.StatusSupport.getNewThreadStatus(transition),
                        deoptimizationTarget,
                        cc,
                        captureFunction,
                        captureMask,
                        captureAddress);

        kit.boxAndReturn(returnValue, nep.methodType());

        return kit.finalizeGraph();
    }
}
