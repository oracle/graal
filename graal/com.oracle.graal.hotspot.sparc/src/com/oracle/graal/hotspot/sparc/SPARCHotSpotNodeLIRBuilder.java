/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.hotspot.HotSpotBackend.EXCEPTION_HANDLER_IN_CALLER;
import static jdk.internal.jvmci.hotspot.HotSpotVMConfig.config;
import static jdk.internal.jvmci.sparc.SPARC.g5;
import static jdk.internal.jvmci.sparc.SPARC.o7;
import jdk.internal.jvmci.code.BytecodeFrame;
import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.code.RegisterValue;
import jdk.internal.jvmci.hotspot.HotSpotResolvedJavaMethod;
import jdk.internal.jvmci.meta.AllocatableValue;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.Value;
import jdk.internal.jvmci.sparc.SPARCKind;

import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.gen.DebugInfoBuilder;
import com.oracle.graal.compiler.sparc.SPARCNodeLIRBuilder;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.hotspot.HotSpotDebugInfoBuilder;
import com.oracle.graal.hotspot.HotSpotLockStack;
import com.oracle.graal.hotspot.HotSpotNodeLIRBuilder;
import com.oracle.graal.hotspot.nodes.DirectCompareAndSwapNode;
import com.oracle.graal.hotspot.nodes.HotSpotDirectCallTargetNode;
import com.oracle.graal.hotspot.nodes.HotSpotIndirectCallTargetNode;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.lir.sparc.SPARCMove.CompareAndSwapOp;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.FullInfopointNode;
import com.oracle.graal.nodes.IndirectCallTargetNode;
import com.oracle.graal.nodes.SafepointNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.NodeValueMap;

public class SPARCHotSpotNodeLIRBuilder extends SPARCNodeLIRBuilder implements HotSpotNodeLIRBuilder {

    public SPARCHotSpotNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        super(graph, lirGen);
        assert gen instanceof SPARCHotSpotLIRGenerator;
        assert getDebugInfoBuilder() instanceof HotSpotDebugInfoBuilder;
        ((SPARCHotSpotLIRGenerator) gen).setLockStack(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()).lockStack());
    }

    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(StructuredGraph graph, NodeValueMap nodeValueMap) {
        HotSpotLockStack lockStack = new HotSpotLockStack(gen.getResult().getFrameMapBuilder(), LIRKind.value(SPARCKind.DWORD));
        return new HotSpotDebugInfoBuilder(nodeValueMap, lockStack);
    }

    private SPARCHotSpotLIRGenerator getGen() {
        return (SPARCHotSpotLIRGenerator) gen;
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        append(new SPARCHotSpotSafepointOp(info, getGen().config, gen));
    }

    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        AllocatableValue address = gen.asAllocatable(operand(x.getAddress()));
        AllocatableValue cmpValue = gen.asAllocatable(operand(x.expectedValue()));
        AllocatableValue newValue = gen.asAllocatable(operand(x.newValue()));
        LIRKind kind = cmpValue.getLIRKind();
        assert kind.equals(newValue.getLIRKind());

        Variable result = gen.newVariable(newValue.getLIRKind());
        append(new CompareAndSwapOp(result, address, cmpValue, newValue));
        setResult(x, result);
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind.isIndirect()) {
            append(new SPARCHotspotDirectVirtualCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, config()));
        } else {
            assert invokeKind.isDirect();
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.targetMethod();
            assert resolvedMethod.isConcrete() : "Cannot make direct call to abstract method.";
            append(new SPARCHotspotDirectStaticCallOp(callTarget.targetMethod(), result, parameters, temps, callState, invokeKind, config()));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        Value metaspaceMethodSrc = operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod());
        AllocatableValue metaspaceMethod = g5.asValue(metaspaceMethodSrc.getLIRKind());
        gen.emitMove(metaspaceMethod, metaspaceMethodSrc);

        Value targetAddressSrc = operand(callTarget.computedAddress());
        AllocatableValue targetAddress = o7.asValue(targetAddressSrc.getLIRKind());
        gen.emitMove(targetAddress, targetAddressSrc);
        append(new SPARCIndirectCallOp(callTarget.targetMethod(), result, parameters, temps, metaspaceMethod, targetAddress, callState, config()));
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new SPARCHotSpotPatchReturnAddressOp(gen.load(operand(address))));
    }

    public void emitJumpToExceptionHandler(ValueNode address) {
        append(new SPARCHotSpotJumpToExceptionHandlerOp(gen.load(operand(address))));
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        Variable handler = gen.load(operand(handlerInCallerPc));
        ForeignCallLinkage linkage = gen.getForeignCalls().lookupForeignCall(EXCEPTION_HANDLER_IN_CALLER);
        CallingConvention linkageCc = linkage.getOutgoingCallingConvention();
        assert linkageCc.getArgumentCount() == 2;
        RegisterValue exceptionFixed = (RegisterValue) linkageCc.getArgument(0);
        RegisterValue exceptionPcFixed = (RegisterValue) linkageCc.getArgument(1);
        gen.emitMove(exceptionFixed, operand(exception));
        gen.emitMove(exceptionPcFixed, operand(exceptionPc));
        Register thread = getGen().getProviders().getRegisters().getThreadRegister();
        SPARCHotSpotJumpToExceptionHandlerInCallerOp op = new SPARCHotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed, getGen().config.threadIsMethodHandleReturnOffset,
                        thread);
        append(op);
    }

    @Override
    protected void emitPrologue(StructuredGraph graph) {
        super.emitPrologue(graph);
        AllocatableValue var = getGen().getSafepointAddressValue();
        append(new SPARCHotSpotSafepointOp.SPARCLoadSafepointPollAddress(var, getGen().config));
        getGen().append(((HotSpotDebugInfoBuilder) getDebugInfoBuilder()).lockStack());
    }

    @Override
    public void visitFullInfopointNode(FullInfopointNode i) {
        if (i.getState() != null && i.getState().bci == BytecodeFrame.AFTER_BCI) {
            Debug.log("Ignoring InfopointNode for AFTER_BCI");
        } else {
            super.visitFullInfopointNode(i);
        }
    }
}
