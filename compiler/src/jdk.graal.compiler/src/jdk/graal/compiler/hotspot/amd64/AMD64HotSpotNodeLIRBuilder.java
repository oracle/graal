/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.graal.compiler.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;
import static jdk.vm.ci.amd64.AMD64.rbp;

import jdk.graal.compiler.core.amd64.AMD64NodeLIRBuilder;
import jdk.graal.compiler.core.amd64.AMD64NodeMatchRules;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.gen.DebugInfoBuilder;
import jdk.graal.compiler.hotspot.HotSpotDebugInfoBuilder;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotLIRGenerationResult;
import jdk.graal.compiler.hotspot.HotSpotLIRGenerator;
import jdk.graal.compiler.hotspot.HotSpotLockStack;
import jdk.graal.compiler.hotspot.HotSpotNodeLIRBuilder;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.nodes.HotSpotDirectCallTargetNode;
import jdk.graal.compiler.hotspot.nodes.HotSpotIndirectCallTargetNode;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.amd64.AMD64BreakpointOp;
import jdk.graal.compiler.lir.amd64.AMD64SaveRegistersOp;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.BreakpointNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.DirectCallTargetNode;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.NodeValueMap;
import jdk.graal.compiler.replacements.amd64.AMD64IntrinsicStubs;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotNodeLIRBuilder extends AMD64NodeLIRBuilder implements HotSpotNodeLIRBuilder {

    @SuppressWarnings("this-escape")
    public AMD64HotSpotNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AMD64NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
        assert gen instanceof AMD64HotSpotLIRGenerator : gen;
        assert getDebugInfoBuilder() instanceof HotSpotDebugInfoBuilder : getDebugInfoBuilder();
        ((AMD64HotSpotLIRGenerator) gen).setDebugInfoBuilder(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()));
    }

    private AMD64HotSpotLIRGenerator getGen() {
        return (AMD64HotSpotLIRGenerator) gen;
    }

    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
        HotSpotLockStack lockStack = new HotSpotLockStack(gen.getResult().getFrameMapBuilder(), LIRKind.value(AMD64Kind.QWORD));
        return new HotSpotDebugInfoBuilder(nodeValueMap, lockStack, (HotSpotLIRGenerator) gen);
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {
        CallingConvention incomingArguments = gen.getResult().getCallingConvention();

        HotSpotLIRGenerationResult result = getGen().getResult();
        Stub stub = result.getStub();
        if (stub != null && stub.getLinkage().getEffect() == HotSpotForeignCallLinkage.RegisterEffect.KILLS_NO_REGISTERS) {
            assert stub.getLinkage().getDescriptor().getTransition() != HotSpotForeignCallDescriptor.Transition.SAFEPOINT : stub;
            AMD64SaveRegistersOp saveOp = getGen().emitSaveAllRegisters(false);
            append(saveOp);
            result.setSaveOnEntry(saveOp);
        }

        Value[] params = new Value[incomingArguments.getArgumentCount() + 1];

        prologAssignParams(incomingArguments, params);
        params[params.length - 1] = rbp.asValue(LIRKind.value(AMD64Kind.QWORD));

        gen.emitIncomingValues(params);

        getGen().emitSaveRbp();

        getGen().append(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()).lockStack());

        prologSetParameterNodes(graph, params);
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        Register thread = getGen().getProviders().getRegisters().getThreadRegister();
        append(new AMD64HotSpotSafepointOp(info, getGen().config, this, thread));
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind.isIndirect()) {
            append(new AMD64HotspotDirectVirtualCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, getGen().config));
        } else {
            assert invokeKind.isDirect();
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
            assert resolvedMethod.isConcrete() : "Cannot make direct call to abstract method.";
            append(new AMD64HotSpotDirectStaticCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, getGen().config));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        if (callTarget instanceof HotSpotIndirectCallTargetNode) {
            Value metaspaceMethodSrc = operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod());
            Value targetAddressSrc = operand(callTarget.computedAddress());
            AllocatableValue metaspaceMethodDst = AMD64.rbx.asValue(metaspaceMethodSrc.getValueKind());
            AllocatableValue targetAddressDst = AMD64.rax.asValue(targetAddressSrc.getValueKind());
            gen.emitMove(metaspaceMethodDst, metaspaceMethodSrc);
            gen.emitMove(targetAddressDst, targetAddressSrc);
            append(new AMD64HotSpotIndirectCallOp(callTarget.targetMethod(), result, parameters, temps, metaspaceMethodDst, targetAddressDst, callState));
        } else {
            super.emitIndirectCall(callTarget, result, parameters, temps, callState);
        }
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new AMD64HotSpotPatchReturnAddressOp(gen.asAllocatable(operand(address))));
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        AllocatableValue handler = gen.asAllocatable(operand(handlerInCallerPc));
        ForeignCallLinkage linkage = gen.getForeignCalls().lookupForeignCall(EXCEPTION_HANDLER_IN_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2 : outgoingCc;
        RegisterValue exceptionFixed = (RegisterValue) outgoingCc.getArgument(0);
        RegisterValue exceptionPcFixed = (RegisterValue) outgoingCc.getArgument(1);
        gen.emitMove(exceptionFixed, operand(exception));
        gen.emitMove(exceptionPcFixed, operand(exceptionPc));
        AMD64HotSpotJumpToExceptionHandlerInCallerOp op = new AMD64HotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed);
        append(op);
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        if (i.getState() != null && i.getState().bci == BytecodeFrame.AFTER_BCI) {
            i.getDebug().log("Ignoring InfopointNode for AFTER_BCI");
        } else {
            super.visitFullInfopointNode(i);
        }
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments().size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments().get(i).stamp(NodeView.DEFAULT).javaType(gen.getMetaAccess());
        }

        Value[] parameters = visitInvokeArguments(gen.getRegisterConfig().getCallingConvention(HotSpotCallingConventionType.JavaCall, null, sig, gen), node.arguments());
        append(new AMD64BreakpointOp(parameters));
    }

    @Override
    public ForeignCallLinkage lookupGraalStub(ValueNode valueNode, ForeignCallDescriptor foreignCallDescriptor) {
        if (getGen().getResult().getStub() != null) {
            // Emit assembly for snippet stubs
            return null;
        }
        if (AMD64IntrinsicStubs.shouldInlineIntrinsic(valueNode, gen)) {
            // intrinsic can emit specialized code that is small enough to warrant being inlined
            return null;
        }
        return getGen().getForeignCalls().lookupForeignCall(foreignCallDescriptor);
    }
}
