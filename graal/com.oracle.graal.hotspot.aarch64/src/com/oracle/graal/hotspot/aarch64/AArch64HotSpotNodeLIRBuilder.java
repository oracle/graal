/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.aarch64;

import static com.oracle.graal.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static jdk.vm.ci.hotspot.HotSpotVMConfig.config;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.fp;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.inlineCacheRegister;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.metaspaceMethodRegister;

import com.oracle.graal.compiler.aarch64.AArch64NodeLIRBuilder;
import com.oracle.graal.compiler.aarch64.AArch64NodeMatchRules;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.gen.DebugInfoBuilder;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.hotspot.HotSpotDebugInfoBuilder;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.HotSpotLockStack;
import com.oracle.graal.hotspot.HotSpotNodeLIRBuilder;
import com.oracle.graal.hotspot.nodes.DirectCompareAndSwapNode;
import com.oracle.graal.hotspot.nodes.HotSpotDirectCallTargetNode;
import com.oracle.graal.hotspot.nodes.HotSpotIndirectCallTargetNode;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.aarch64.AArch64BreakpointOp;
import com.oracle.graal.lir.aarch64.AArch64Move.CompareAndSwapOp;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.BreakpointNode;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.FullInfopointNode;
import com.oracle.graal.nodes.IndirectCallTargetNode;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.SafepointNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.NodeValueMap;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AArch64 HotSpot.
 */
public class AArch64HotSpotNodeLIRBuilder extends AArch64NodeLIRBuilder implements HotSpotNodeLIRBuilder {

    public AArch64HotSpotNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AArch64NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
        assert gen instanceof AArch64HotSpotLIRGenerator;
        assert getDebugInfoBuilder() instanceof HotSpotDebugInfoBuilder;
        ((AArch64HotSpotLIRGenerator) gen).setDebugInfoBuilder(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()));
    }

    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
        HotSpotLockStack lockStack = new HotSpotLockStack(gen.getResult().getFrameMapBuilder(), LIRKind.value(AArch64Kind.QWORD));
        return new HotSpotDebugInfoBuilder(nodeValueMap, lockStack, (HotSpotLIRGenerator) gen);
    }

    private AArch64HotSpotLIRGenerator getGen() {
        return (AArch64HotSpotLIRGenerator) gen;
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {
        CallingConvention incomingArguments = gen.getResult().getCallingConvention();
        Value[] params = new Value[incomingArguments.getArgumentCount() + 2];
        for (int i = 0; i < incomingArguments.getArgumentCount(); i++) {
            params[i] = incomingArguments.getArgument(i);
            if (isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !gen.getResult().getLIR().hasArgInCallerFrame()) {
                    gen.getResult().getLIR().setHasArgInCallerFrame();
                }
            }
        }
        params[params.length - 2] = fp.asValue(LIRKind.value(AArch64Kind.QWORD));
        params[params.length - 1] = lr.asValue(LIRKind.value(AArch64Kind.QWORD));

        gen.emitIncomingValues(params);

        for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
            Value paramValue = params[param.index()];
            assert paramValue.getLIRKind().equals(getLIRGeneratorTool().getLIRKind(param.stamp())) : paramValue.getLIRKind() + " != " + param.stamp();
            setResult(param, gen.emitMove(paramValue));
        }
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        Variable scratch = gen.newVariable(LIRKind.value(getGen().target().arch.getWordKind()));
        append(new AArch64HotSpotSafepointOp(info, getGen().config, scratch));
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind.isIndirect()) {
            append(new AArch64HotSpotDirectVirtualCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, config()));
        } else {
            assert invokeKind.isDirect();
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
            assert resolvedMethod.isConcrete() : "Cannot make direct call to abstract method.";
            append(new AArch64HotSpotDirectStaticCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, config()));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        Value metaspaceMethodSrc = operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod());
        Value targetAddressSrc = operand(callTarget.computedAddress());
        AllocatableValue metaspaceMethodDst = metaspaceMethodRegister.asValue(metaspaceMethodSrc.getLIRKind());
        AllocatableValue targetAddressDst = inlineCacheRegister.asValue(targetAddressSrc.getLIRKind());
        gen.emitMove(metaspaceMethodDst, metaspaceMethodSrc);
        gen.emitMove(targetAddressDst, targetAddressSrc);
        append(new AArch64IndirectCallOp(callTarget.targetMethod(), result, parameters, temps, metaspaceMethodDst, targetAddressDst, callState, config()));
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new AArch64HotSpotPatchReturnAddressOp(gen.load(operand(address))));
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        Variable handler = gen.load(operand(handlerInCallerPc));
        ForeignCallLinkage linkage = gen.getForeignCalls().lookupForeignCall(EXCEPTION_HANDLER_IN_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionFixed = (RegisterValue) outgoingCc.getArgument(0);
        RegisterValue exceptionPcFixed = (RegisterValue) outgoingCc.getArgument(1);
        gen.emitMove(exceptionFixed, operand(exception));
        gen.emitMove(exceptionPcFixed, operand(exceptionPc));
        Register thread = getGen().getProviders().getRegisters().getThreadRegister();
        AArch64HotSpotJumpToExceptionHandlerInCallerOp op = new AArch64HotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed,
                        getGen().config.threadIsMethodHandleReturnOffset, thread, config());
        append(op);
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        if (i.getState() != null && i.getState().bci == BytecodeFrame.AFTER_BCI) {
            Debug.log("Ignoring InfopointNode for AFTER_BCI");
        } else {
            super.visitFullInfopointNode(i);
        }
    }

    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        AllocatableValue address = gen.asAllocatable(operand(x.getAddress()));
        AllocatableValue cmpValue = gen.asAllocatable(operand(x.expectedValue()));
        AllocatableValue newValue = gen.asAllocatable(operand(x.newValue()));
        LIRKind kind = cmpValue.getLIRKind();
        assert kind.equals(newValue.getLIRKind());

        Variable result = gen.newVariable(newValue.getLIRKind());
        Variable scratch = gen.newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new CompareAndSwapOp(result, cmpValue, newValue, address, scratch));
        setResult(x, result);
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments().size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments().get(i).stamp().javaType(gen.getMetaAccess());
        }

        Value[] parameters = visitInvokeArguments(gen.getResult().getFrameMapBuilder().getRegisterConfig().getCallingConvention(HotSpotCallingConventionType.JavaCall, null, sig, gen.target()),
                        node.arguments());
        append(new AArch64BreakpointOp(parameters));
    }
}
