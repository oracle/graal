/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.hotspot.amd64.AMD64HotSpotUnwindOp.*;

import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Address.*;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Move.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.*;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
final class AMD64HotSpotLIRGenerator extends AMD64LIRGenerator implements HotSpotLIRGenerator {

    private HotSpotRuntime runtime() {
        return (HotSpotRuntime) runtime;
    }

    AMD64HotSpotLIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, ResolvedJavaMethod method, LIR lir) {
        super(graph, runtime, target, frameMap, method, lir);
    }

    /**
     * The slot reserved for storing the original return address when a frame is marked for
     * deoptimization. The return address slot in the callee is overwritten with the address of a
     * deoptimization stub.
     */
    StackSlot deoptimizationRescueSlot;

    /**
     * The position at which the instruction for saving RBP should be inserted.
     */
    Block saveRbpBlock;
    int saveRbpIndex;

    /**
     * The slot reserved for saving RBP.
     */
    StackSlot rbpSlot;

    /**
     * List of epilogue operations that need to restore RBP.
     */
    List<AMD64HotSpotEpilogueOp> epilogueOps = new ArrayList<>(2);

    @SuppressWarnings("hiding")
    @Override
    protected DebugInfoBuilder createDebugInfoBuilder(NodeMap<Value> nodeOperands) {
        assert runtime().config.basicLockSize == 8;
        HotSpotLockStack lockStack = new HotSpotLockStack(frameMap, Kind.Long);
        return new HotSpotDebugInfoBuilder(nodeOperands, lockStack);
    }

    @Override
    public StackSlot getLockSlot(int lockDepth) {
        return ((HotSpotDebugInfoBuilder) debugInfoBuilder).lockStack().makeLockSlot(lockDepth);
    }

    @Override
    protected void emitPrologue() {

        CallingConvention incomingArguments = createCallingConvention();

        RegisterValue rbpParam = rbp.asValue(Kind.Long);
        Value[] params = new Value[incomingArguments.getArgumentCount() + 1];
        for (int i = 0; i < params.length - 1; i++) {
            params[i] = toStackKind(incomingArguments.getArgument(i));
            if (isStackSlot(params[i])) {
                StackSlot slot = ValueUtil.asStackSlot(params[i]);
                if (slot.isInCallerFrame() && !lir.hasArgInCallerFrame()) {
                    lir.setHasArgInCallerFrame();
                }
            }
        }
        params[params.length - 1] = rbpParam;

        ParametersOp paramsOp = new ParametersOp(params);
        append(paramsOp);
        saveRbpBlock = currentBlock;
        saveRbpIndex = lir.lir(saveRbpBlock).size();
        append(paramsOp); // placeholder
        rbpSlot = frameMap.allocateSpillSlot(Kind.Long);
        assert rbpSlot.getRawOffset() == -16 : rbpSlot.getRawOffset();

        for (LocalNode local : graph.getNodes(LocalNode.class)) {
            Value param = params[local.index()];
            assert param.getKind() == local.kind().getStackKind();
            setResult(local, emitMove(param));
        }
    }

    @Override
    protected void emitReturn(Value input) {
        AMD64HotSpotReturnOp op = new AMD64HotSpotReturnOp(input);
        epilogueOps.add(op);
        append(op);
    }

    @Override
    protected boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return runtime().asStub(method) != null;
    }

    @Override
    protected CallingConvention createCallingConvention() {
        Stub stub = runtime().asStub(method);
        if (stub != null) {
            return stub.getLinkage().getCallingConvention();
        }

        if (graph.getEntryBCI() == StructuredGraph.INVOCATION_ENTRY_BCI) {
            return super.createCallingConvention();
        } else {
            return frameMap.registerConfig.getCallingConvention(JavaCallee, method.getSignature().getReturnType(null), new JavaType[]{runtime.lookupJavaType(long.class)}, target, false);
        }
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state();
        append(new AMD64SafepointOp(info, runtime().config, this));
    }

    @Override
    public void visitExceptionObject(ExceptionObjectNode x) {
        HotSpotVMConfig config = runtime().config;
        RegisterValue thread = runtime().threadRegister().asValue();
        Value exception = emitLoad(Kind.Object, thread, config.threadExceptionOopOffset, Value.ILLEGAL, 0, false);
        emitStore(Kind.Object, thread, config.threadExceptionOopOffset, Value.ILLEGAL, 0, Constant.NULL_OBJECT, false);
        emitStore(Kind.Long, thread, config.threadExceptionPcOffset, Value.ILLEGAL, 0, Constant.LONG_0, false);
        setResult(x, exception);
    }

    @SuppressWarnings("hiding")
    @Override
    public void visitDirectCompareAndSwap(DirectCompareAndSwapNode x) {
        Kind kind = x.newValue().kind();
        assert kind == x.expectedValue().kind();

        Value expected = loadNonConst(operand(x.expectedValue()));
        Variable newVal = load(operand(x.newValue()));

        int disp = 0;
        AMD64AddressValue address;
        Value index = operand(x.offset());
        if (ValueUtil.isConstant(index) && NumUtil.isInt(ValueUtil.asConstant(index).asLong() + disp)) {
            assert !runtime.needsDataPatch(asConstant(index));
            disp += (int) ValueUtil.asConstant(index).asLong();
            address = new AMD64AddressValue(kind, load(operand(x.object())), disp);
        } else {
            address = new AMD64AddressValue(kind, load(operand(x.object())), load(index), Scale.Times1, disp);
        }

        RegisterValue rax = AMD64.rax.asValue(kind);
        emitMove(rax, expected);
        append(new CompareAndSwapOp(rax, address, rax, newVal));

        Variable result = newVariable(x.kind());
        emitMove(result, rax);
        setResult(x, result);
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        append(new AMD64TailcallOp(args, address));

    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        InvokeKind invokeKind = ((HotSpotDirectCallTargetNode) callTarget).invokeKind();
        if (invokeKind == InvokeKind.Interface || invokeKind == InvokeKind.Virtual) {
            append(new AMD64HotspotDirectVirtualCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind));
        } else {
            assert invokeKind == InvokeKind.Static || invokeKind == InvokeKind.Special;
            HotSpotResolvedJavaMethod resolvedMethod = (HotSpotResolvedJavaMethod) callTarget.target();
            Constant metaspaceMethod = resolvedMethod.getMetaspaceMethodConstant();
            append(new AMD64HotspotDirectStaticCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind, metaspaceMethod));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        Value metaspaceMethod = AMD64.rbx.asValue();
        emitMove(metaspaceMethod, operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod()));
        Value targetAddress = AMD64.rax.asValue();
        emitMove(targetAddress, operand(callTarget.computedAddress()));
        append(new AMD64IndirectCallOp(callTarget.target(), result, parameters, temps, metaspaceMethod, targetAddress, callState));
    }

    @Override
    public void emitUnwind(Value exception) {
        RegisterValue exceptionParameter = EXCEPTION.asValue();
        emitMove(exceptionParameter, exception);
        AMD64HotSpotUnwindOp op = new AMD64HotSpotUnwindOp(exceptionParameter);
        epilogueOps.add(op);
        append(op);
    }

    @Override
    public void emitDeoptimize(DeoptimizationAction action, DeoptimizationReason reason) {
        append(new AMD64DeoptimizeOp(action, reason, state()));
    }

    @Override
    public void beforeRegisterAllocation() {
        assert rbpSlot != null;
        RegisterValue rbpParam = rbp.asValue(Kind.Long);
        AllocatableValue savedRbp;
        LIRInstruction saveRbp;
        if (lir.hasDebugInfo()) {
            savedRbp = rbpSlot;
            deoptimizationRescueSlot = frameMap.allocateSpillSlot(Kind.Long);
        } else {
            frameMap.freeSpillSlot(rbpSlot);
            savedRbp = newVariable(Kind.Long);
        }

        for (AMD64HotSpotEpilogueOp op : epilogueOps) {
            op.savedRbp = savedRbp;
        }

        saveRbp = new MoveFromRegOp(savedRbp, rbpParam);
        lir.lir(saveRbpBlock).set(saveRbpIndex, saveRbp);
    }
}
