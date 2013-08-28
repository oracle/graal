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
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.ParametersOp;
import com.oracle.graal.lir.StandardOp.PlaceholderOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.CondMoveOp;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapCompressedOp;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.amd64.AMD64Move.LoadCompressedPointer;
import com.oracle.graal.lir.amd64.AMD64Move.LoadOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.StoreCompressedPointer;
import com.oracle.graal.lir.amd64.AMD64Move.StoreConstantOp;
import com.oracle.graal.lir.amd64.AMD64Move.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotLIRGenerator extends AMD64LIRGenerator implements HotSpotLIRGenerator {

    private HotSpotRuntime runtime() {
        return (HotSpotRuntime) runtime;
    }

    protected AMD64HotSpotLIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, runtime, target, frameMap, cc, lir);
    }

    /**
     * The slot reserved for storing the original return address when a frame is marked for
     * deoptimization. The return address slot in the callee is overwritten with the address of a
     * deoptimization stub.
     */
    StackSlot deoptimizationRescueSlot;

    /**
     * Utility for emitting the instruction to save RBP.
     */
    class SaveRbp {

        final PlaceholderOp placeholder;

        /**
         * The slot reserved for saving RBP.
         */
        final StackSlot reservedSlot;

        public SaveRbp(PlaceholderOp placeholder) {
            this.placeholder = placeholder;
            this.reservedSlot = frameMap.allocateSpillSlot(Kind.Long);
            assert reservedSlot.getRawOffset() == -16 : reservedSlot.getRawOffset();
        }

        /**
         * Replaces this operation with the appropriate move for saving rbp.
         * 
         * @param useStack specifies if rbp must be saved to the stack
         */
        public AllocatableValue finalize(boolean useStack) {
            AllocatableValue dst;
            if (useStack) {
                dst = reservedSlot;
            } else {
                frameMap.freeSpillSlot(reservedSlot);
                dst = newVariable(Kind.Long);
            }

            placeholder.replace(lir, new MoveFromRegOp(dst, rbp.asValue(Kind.Long)));
            return dst;
        }
    }

    private SaveRbp saveRbp;

    /**
     * List of epilogue operations that need to restore RBP.
     */
    List<AMD64HotSpotEpilogueOp> epilogueOps = new ArrayList<>(2);

    @Override
    public void append(LIRInstruction op) {
        super.append(op);
        if (op instanceof AMD64HotSpotEpilogueOp) {
            epilogueOps.add((AMD64HotSpotEpilogueOp) op);
        }
    }

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

        CallingConvention incomingArguments = cc;

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

        saveRbp = new SaveRbp(new PlaceholderOp(currentBlock, lir.lir(currentBlock).size()));
        append(saveRbp.placeholder);

        for (LocalNode local : graph.getNodes(LocalNode.class)) {
            Value param = params[local.index()];
            assert param.getKind() == local.kind().getStackKind();
            setResult(local, emitMove(param));
        }
    }

    @Override
    protected void emitReturn(Value input) {
        append(new AMD64HotSpotReturnOp(input));
    }

    @Override
    protected boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return graph.start() instanceof StubStartNode;
    }

    /**
     * Map from debug infos that need to be updated with callee save information to the operations
     * that provide the information.
     */
    Map<LIRFrameState, AMD64RegistersPreservationOp> calleeSaveInfo = new HashMap<>();

    private LIRFrameState currentRuntimeCallInfo;

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        currentRuntimeCallInfo = info;
        super.emitForeignCall(linkage, result, arguments, temps, info);
    }

    protected AMD64SaveRegistersOp emitSaveRegisters(Register[] savedRegisters, StackSlot[] savedRegisterLocations) {
        AMD64SaveRegistersOp save = new AMD64SaveRegistersOp(savedRegisters, savedRegisterLocations);
        append(save);
        return save;
    }

    protected void emitRestoreRegisters(AMD64SaveRegistersOp save) {
        append(new AMD64RestoreRegistersOp(save.getSlots().clone(), save));
    }

    Stub getStub() {
        if (graph.start() instanceof StubStartNode) {
            return ((StubStartNode) graph.start()).getStub();
        }
        return null;
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, DeoptimizingNode info, Value... args) {
        Stub stub = getStub();
        boolean destroysRegisters = linkage.destroysRegisters();

        AMD64SaveRegistersOp save = null;
        StackSlot[] savedRegisterLocations = null;
        if (destroysRegisters) {
            if (stub != null) {
                if (stub.preservesRegisters()) {
                    Register[] savedRegisters = frameMap.registerConfig.getAllocatableRegisters();
                    savedRegisterLocations = new StackSlot[savedRegisters.length];
                    for (int i = 0; i < savedRegisters.length; i++) {
                        PlatformKind kind = target.arch.getLargestStorableKind(savedRegisters[i].getRegisterCategory());
                        assert kind != Kind.Illegal;
                        StackSlot spillSlot = frameMap.allocateSpillSlot(kind);
                        savedRegisterLocations[i] = spillSlot;
                    }
                    save = emitSaveRegisters(savedRegisters, savedRegisterLocations);
                }
            }
        }

        Variable result;

        if (linkage.canDeoptimize()) {
            assert info != null;
            append(new AMD64HotSpotCRuntimeCallPrologueOp());
            result = super.emitForeignCall(linkage, info, args);
            append(new AMD64HotSpotCRuntimeCallEpilogueOp());
        } else {
            result = super.emitForeignCall(linkage, null, args);
        }

        if (destroysRegisters) {
            if (stub != null) {
                if (stub.preservesRegisters()) {
                    assert !calleeSaveInfo.containsKey(currentRuntimeCallInfo);
                    calleeSaveInfo.put(currentRuntimeCallInfo, save);

                    emitRestoreRegisters(save);
                } else {
                    assert zapRegisters();
                }
            }
        }

        return result;
    }

    protected AMD64ZapRegistersOp emitZapRegisters(Register[] zappedRegisters, Constant[] zapValues) {
        AMD64ZapRegistersOp zap = new AMD64ZapRegistersOp(zappedRegisters, zapValues);
        append(zap);
        return zap;
    }

    protected boolean zapRegisters() {
        Register[] zappedRegisters = frameMap.registerConfig.getAllocatableRegisters();
        Constant[] zapValues = new Constant[zappedRegisters.length];
        for (int i = 0; i < zappedRegisters.length; i++) {
            PlatformKind kind = target.arch.getLargestStorableKind(zappedRegisters[i].getRegisterCategory());
            assert kind != Kind.Illegal;
            zapValues[i] = zapValueForKind(kind);
        }
        calleeSaveInfo.put(currentRuntimeCallInfo, emitZapRegisters(zappedRegisters, zapValues));
        return true;
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        append(new AMD64SafepointOp(info, runtime().config, this));
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
            assert !Modifier.isAbstract(resolvedMethod.getModifiers()) : "Cannot make direct call to abstract method.";
            Constant metaspaceMethod = resolvedMethod.getMetaspaceMethodConstant();
            append(new AMD64HotspotDirectStaticCallOp(callTarget.target(), result, parameters, temps, callState, invokeKind, metaspaceMethod));
        }
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        if (callTarget instanceof HotSpotIndirectCallTargetNode) {
            AllocatableValue metaspaceMethod = AMD64.rbx.asValue();
            emitMove(metaspaceMethod, operand(((HotSpotIndirectCallTargetNode) callTarget).metaspaceMethod()));
            AllocatableValue targetAddress = AMD64.rax.asValue();
            emitMove(targetAddress, operand(callTarget.computedAddress()));
            append(new AMD64IndirectCallOp(callTarget.target(), result, parameters, temps, metaspaceMethod, targetAddress, callState));
        } else {
            super.emitIndirectCall(callTarget, result, parameters, temps, callState);
        }
    }

    @Override
    public void emitUnwind(Value exception) {
        ForeignCallLinkage linkage = getRuntime().lookupForeignCall(HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionParameter = (RegisterValue) outgoingCc.getArgument(0);
        emitMove(exceptionParameter, exception);
        append(new AMD64HotSpotUnwindOp(exceptionParameter));
    }

    @Override
    public void emitDeoptimize(DeoptimizationAction action, DeoptimizingNode deopting) {
        append(new AMD64DeoptimizeOp(action, deopting.getDeoptimizationReason(), state(deopting)));
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        append(new AMD64HotSpotDeoptimizeCallerOp(action, reason));
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new AMD64HotSpotPatchReturnAddressOp(load(operand(address))));
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        Variable handler = load(operand(handlerInCallerPc));
        ForeignCallLinkage linkage = getRuntime().lookupForeignCall(EXCEPTION_HANDLER_IN_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionFixed = (RegisterValue) outgoingCc.getArgument(0);
        RegisterValue exceptionPcFixed = (RegisterValue) outgoingCc.getArgument(1);
        emitMove(exceptionFixed, operand(exception));
        emitMove(exceptionPcFixed, operand(exceptionPc));
        AMD64HotSpotJumpToExceptionHandlerInCallerOp op = new AMD64HotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed);
        append(op);
    }

    @Override
    public void beforeRegisterAllocation() {
        boolean hasDebugInfo = lir.hasDebugInfo();
        AllocatableValue savedRbp = saveRbp.finalize(hasDebugInfo);
        if (hasDebugInfo) {
            deoptimizationRescueSlot = frameMap.allocateSpillSlot(Kind.Long);
        }

        for (AMD64HotSpotEpilogueOp op : epilogueOps) {
            op.savedRbp = savedRbp;
        }
    }

    private static boolean isCompressCandidate(DeoptimizingNode access) {
        return access != null && ((HeapAccess) access).isCompressible();
    }

    @Override
    public Variable emitLoad(Kind kind, Value address, DeoptimizingNode access) {
        AMD64AddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind);
        assert access == null || access instanceof HeapAccess;
        if (isCompressCandidate(access)) {
            if (runtime().config.useCompressedOops && kind == Kind.Object) {
                append(new LoadCompressedPointer(kind, result, runtime().heapBaseRegister().asValue(), loadAddress, access != null ? state(access) : null, runtime().config.narrowOopBase,
                                runtime().config.narrowOopShift, runtime().config.logMinObjAlignment));
            } else if (runtime().config.useCompressedKlassPointers && kind == Kind.Long) {
                append(new LoadCompressedPointer(kind, result, runtime().heapBaseRegister().asValue(), loadAddress, access != null ? state(access) : null, runtime().config.narrowKlassBase,
                                runtime().config.narrowKlassShift, runtime().config.logKlassAlignment));
            } else {
                append(new LoadOp(kind, result, loadAddress, access != null ? state(access) : null));
            }
        } else {
            append(new LoadOp(kind, result, loadAddress, access != null ? state(access) : null));
        }
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value inputVal, DeoptimizingNode access) {
        AMD64AddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = access != null ? state(access) : null;
        if (isConstant(inputVal)) {
            Constant c = asConstant(inputVal);
            if (canStoreConstant(c)) {
                if (inputVal.getKind() == Kind.Object) {
                    append(new StoreConstantOp(kind, storeAddress, c, state, runtime().config.useCompressedOops && isCompressCandidate(access)));
                } else if (inputVal.getKind() == Kind.Long) {
                    append(new StoreConstantOp(kind, storeAddress, c, state, runtime().config.useCompressedKlassPointers && isCompressCandidate(access)));
                } else {
                    append(new StoreConstantOp(kind, storeAddress, c, state, false));
                }
                return;
            }
        }
        Variable input = load(inputVal);
        if (isCompressCandidate(access)) {
            if (runtime().config.useCompressedOops && kind == Kind.Object) {
                if (input.getKind() == Kind.Object) {
                    Variable scratch = newVariable(Kind.Long);
                    append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, runtime().config.narrowOopBase, runtime().config.narrowOopShift, runtime().config.logMinObjAlignment));
                } else {
                    // the input oop is already compressed
                    append(new StoreOp(input.getKind(), storeAddress, input, state));
                }
            } else if (runtime().config.useCompressedKlassPointers && kind == Kind.Long) {
                Variable scratch = newVariable(Kind.Long);
                append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, runtime().config.narrowKlassBase, runtime().config.narrowKlassShift, runtime().config.logKlassAlignment));
            } else {
                append(new StoreOp(kind, storeAddress, input, state));
            }
        } else {
            append(new StoreOp(kind, storeAddress, input, state));
        }
    }

    @Override
    public void visitCompareAndSwap(LoweredCompareAndSwapNode node, Value address) {
        Kind kind = node.getNewValue().kind();
        assert kind == node.getExpectedValue().kind();
        Value expected = loadNonConst(operand(node.getExpectedValue()));
        Variable newValue = load(operand(node.getNewValue()));
        AMD64AddressValue addressValue = asAddressValue(address);
        RegisterValue raxRes = AMD64.rax.asValue(kind);
        emitMove(raxRes, expected);
        if (runtime().config.useCompressedOops && node.isCompressible()) {
            Variable scratch = newVariable(Kind.Long);
            append(new CompareAndSwapCompressedOp(raxRes, addressValue, raxRes, newValue, scratch, runtime().config.narrowOopBase, runtime().config.narrowOopShift, runtime().config.logMinObjAlignment));
        } else {
            append(new CompareAndSwapOp(raxRes, addressValue, raxRes, newValue));
        }
        Variable result = newVariable(node.kind());
        append(new CondMoveOp(result, Condition.EQ, load(Constant.TRUE), Constant.FALSE));
        setResult(node, result);
    }
}
