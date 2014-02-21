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
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.HotSpotVMConfig.CompressEncoding;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotMove.CompareAndSwapCompressedOp;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotMove.LoadCompressedPointer;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotMove.StoreCompressedConstantOp;
import com.oracle.graal.hotspot.amd64.AMD64HotSpotMove.StoreCompressedPointer;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.CondMoveOp;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.amd64.AMD64Move.LoadOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.StoreConstantOp;
import com.oracle.graal.lir.amd64.AMD64Move.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotLIRGenerator extends AMD64LIRGenerator implements HotSpotLIRGenerator {

    private final HotSpotVMConfig config;

    protected AMD64HotSpotLIRGenerator(StructuredGraph graph, HotSpotProviders providers, HotSpotVMConfig config, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, providers, frameMap, cc, lir);
        assert config.basicLockSize == 8;
        this.config = config;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
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

        final NoOp placeholder;

        /**
         * The slot reserved for saving RBP.
         */
        final StackSlot reservedSlot;

        public SaveRbp(NoOp placeholder) {
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
        params[params.length - 1] = rbp.asValue(Kind.Long);

        emitIncomingValues(params);

        saveRbp = new SaveRbp(new NoOp(currentBlock, lir.lir(currentBlock).size()));
        append(saveRbp.placeholder);

        for (ParameterNode param : graph.getNodes(ParameterNode.class)) {
            Value paramValue = params[param.index()];
            assert paramValue.getKind() == param.kind().getStackKind();
            setResult(param, emitMove(paramValue));
        }
    }

    private Register findPollOnReturnScratchRegister() {
        RegisterConfig regConfig = getProviders().getCodeCache().getRegisterConfig();
        for (Register r : regConfig.getAllocatableRegisters(Kind.Long)) {
            if (r != regConfig.getReturnRegister(Kind.Long) && r != AMD64.rbp) {
                return r;
            }
        }
        throw GraalInternalError.shouldNotReachHere();
    }

    private Register pollOnReturnScratchRegister;

    @Override
    protected void emitReturn(Value input) {
        if (pollOnReturnScratchRegister == null) {
            pollOnReturnScratchRegister = findPollOnReturnScratchRegister();
        }
        append(new AMD64HotSpotReturnOp(input, getStub() != null, pollOnReturnScratchRegister));
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
    Map<LIRFrameState, SaveRegistersOp> calleeSaveInfo = new HashMap<>();

    private LIRFrameState currentRuntimeCallInfo;

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        currentRuntimeCallInfo = info;
        super.emitForeignCall(linkage, result, arguments, temps, info);
    }

    protected AMD64SaveRegistersOp emitSaveRegisters(Register[] savedRegisters, StackSlot[] savedRegisterLocations) {
        AMD64SaveRegistersOp save = new AMD64SaveRegistersOp(savedRegisters, savedRegisterLocations, true);
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
                        PlatformKind kind = target().arch.getLargestStorableKind(savedRegisters[i].getRegisterCategory());
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
            assert info != null || stub != null;
            Register thread = getProviders().getRegisters().getThreadRegister();
            append(new AMD64HotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), thread));
            result = super.emitForeignCall(linkage, info, args);
            append(new AMD64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaFpOffset(), thread));
        } else {
            result = super.emitForeignCall(linkage, info, args);
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
            PlatformKind kind = target().arch.getLargestStorableKind(zappedRegisters[i].getRegisterCategory());
            assert kind != Kind.Illegal;
            zapValues[i] = zapValueForKind(kind);
        }
        calleeSaveInfo.put(currentRuntimeCallInfo, emitZapRegisters(zappedRegisters, zapValues));
        return true;
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        LIRFrameState info = state(i);
        append(new AMD64HotSpotSafepointOp(info, config, this));
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
            assert !getCodeCache().needsDataPatch(asConstant(index));
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
    public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments) {
        Value[] argLocations = new Value[args.length];
        frameMap.callsMethod(nativeCallingConvention);
        // TODO(mg): in case a native function uses floating point varargs, the ABI requires that
        // RAX contains the length of the varargs
        AllocatableValue numberOfFloatingPointArgumentsRegister = AMD64.rax.asValue();
        emitMove(numberOfFloatingPointArgumentsRegister, Constant.forInt(numberOfFloatingPointArguments));
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = nativeCallingConvention.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }
        Value ptr = emitMove(Constant.forLong(address));
        append(new AMD64CCall(nativeCallingConvention.getReturn(), ptr, numberOfFloatingPointArgumentsRegister, argLocations));
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
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionParameter = (RegisterValue) outgoingCc.getArgument(0);
        emitMove(exceptionParameter, exception);
        append(new AMD64HotSpotUnwindOp(exceptionParameter));
    }

    private void moveDeoptValuesToThread(Value actionAndReason, Value speculation) {
        moveValueToThread(actionAndReason, runtime().getConfig().pendingDeoptimizationOffset);
        moveValueToThread(speculation, runtime().getConfig().pendingFailedSpeculationOffset);
    }

    private void moveValueToThread(Value v, int offset) {
        Kind wordKind = getProviders().getCodeCache().getTarget().wordKind;
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        AMD64AddressValue address = new AMD64AddressValue(v.getKind(), thread, offset);
        emitStore(v.getKind(), address, v, null);
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, DeoptimizingNode deopting) {
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new AMD64DeoptimizeOp(state(deopting)));
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        moveDeoptValuesToThread(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0), Constant.NULL_OBJECT);
        append(new AMD64HotSpotDeoptimizeCallerOp());
    }

    @Override
    public void emitPatchReturnAddress(ValueNode address) {
        append(new AMD64HotSpotPatchReturnAddressOp(load(operand(address))));
    }

    @Override
    public void emitJumpToExceptionHandlerInCaller(ValueNode handlerInCallerPc, ValueNode exception, ValueNode exceptionPc) {
        Variable handler = load(operand(handlerInCallerPc));
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(EXCEPTION_HANDLER_IN_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionFixed = (RegisterValue) outgoingCc.getArgument(0);
        RegisterValue exceptionPcFixed = (RegisterValue) outgoingCc.getArgument(1);
        emitMove(exceptionFixed, operand(exception));
        emitMove(exceptionPcFixed, operand(exceptionPc));
        Register thread = getProviders().getRegisters().getThreadRegister();
        AMD64HotSpotJumpToExceptionHandlerInCallerOp op = new AMD64HotSpotJumpToExceptionHandlerInCallerOp(handler, exceptionFixed, exceptionPcFixed, config.threadIsMethodHandleReturnOffset, thread);
        append(op);
    }

    @Override
    public void beforeRegisterAllocation() {
        super.beforeRegisterAllocation();
        boolean hasDebugInfo = lir.hasDebugInfo();
        AllocatableValue savedRbp = saveRbp.finalize(hasDebugInfo);
        if (hasDebugInfo) {
            deoptimizationRescueSlot = frameMap.allocateSpillSlot(Kind.Long);
        }

        for (AMD64HotSpotEpilogueOp op : epilogueOps) {
            op.savedRbp = savedRbp;
        }
    }

    /**
     * Returns whether or not the input access should be (de)compressed.
     */
    private boolean isCompressedOperation(Kind kind, Access access) {
        return access != null && access.isCompressible() && ((kind == Kind.Long && config.useCompressedClassPointers) || (kind == Kind.Object && config.useCompressedOops));
    }

    /**
     * @return a compressed version of the incoming constant
     */
    protected static Constant compress(Constant c, CompressEncoding encoding) {
        if (c.getKind() == Kind.Long) {
            return Constant.forIntegerKind(Kind.Int, (int) (((c.asLong() - encoding.base) >> encoding.shift) & 0xffffffffL), c.getPrimitiveAnnotation());
        } else if (c.getKind() == Kind.Object) {
            return Constant.forNarrowOop(c.asObject());
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitLoad(Kind kind, Value address, Access access) {
        AMD64AddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind.getStackKind());
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        /**
         * Currently, the (de)compression of pointers applies conditionally to some objects (oops,
         * kind==Object) and some addresses (klass pointers, kind==Long). Initially, the input
         * operation is checked to discover if it has been tagged as a potential "compression"
         * candidate. Consequently, depending on the appropriate kind, the specific (de)compression
         * functions are being called.
         */
        if (isCompressedOperation(kind, access)) {
            if (kind == Kind.Object) {
                append(new LoadCompressedPointer(kind, result, getProviders().getRegisters().getHeapBaseRegister().asValue(), loadAddress, state, config.getOopEncoding()));
            } else if (kind == Kind.Long) {
                Variable scratch = config.getKlassEncoding().base != 0 ? newVariable(Kind.Long) : null;
                append(new LoadCompressedPointer(kind, result, scratch, loadAddress, state, config.getKlassEncoding()));
            } else {
                throw GraalInternalError.shouldNotReachHere("can't handle: " + access);
            }
        } else {
            append(new LoadOp(kind, result, loadAddress, state));
        }
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value inputVal, Access access) {
        AMD64AddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        boolean isCompressed = isCompressedOperation(kind, access);
        if (isConstant(inputVal)) {
            Constant c = asConstant(inputVal);
            if (canStoreConstant(c, isCompressed)) {
                if (isCompressed) {
                    if (c.getKind() == Kind.Object) {
                        Constant value = c.isNull() ? c : compress(c, config.getOopEncoding());
                        append(new StoreCompressedConstantOp(kind, storeAddress, value, state));
                    } else if (c.getKind() == Kind.Long) {
                        // It's always a good idea to directly store compressed constants since they
                        // have to be materialized as 64 bits encoded otherwise.
                        Constant value = compress(c, config.getKlassEncoding());
                        append(new StoreCompressedConstantOp(kind, storeAddress, value, state));
                    } else {
                        throw GraalInternalError.shouldNotReachHere("can't handle: " + access);
                    }
                    return;
                } else {
                    append(new StoreConstantOp(kind, storeAddress, c, state));
                    return;
                }
            }
        }
        Variable input = load(inputVal);
        if (isCompressed) {
            if (kind == Kind.Object) {
                if (input.getKind() == Kind.Object) {
                    Variable scratch = newVariable(Kind.Long);
                    Register heapBaseReg = getProviders().getRegisters().getHeapBaseRegister();
                    append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, config.getOopEncoding(), heapBaseReg));
                } else {
                    // the input oop is already compressed
                    append(new StoreOp(input.getKind(), storeAddress, input, state));
                }
            } else if (kind == Kind.Long) {
                Variable scratch = newVariable(Kind.Long);
                Register heapBaseReg = getProviders().getRegisters().getHeapBaseRegister();
                append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, config.getKlassEncoding(), heapBaseReg));
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
        if (config.useCompressedOops && node.isCompressible()) {
            Variable scratch = newVariable(Kind.Long);
            Register heapBaseReg = getProviders().getRegisters().getHeapBaseRegister();
            append(new CompareAndSwapCompressedOp(raxRes, addressValue, raxRes, newValue, scratch, config.getOopEncoding(), heapBaseReg));
        } else {
            append(new CompareAndSwapOp(raxRes, addressValue, raxRes, newValue));
        }
        Variable result = newVariable(node.kind());
        append(new CondMoveOp(result, Condition.EQ, load(Constant.TRUE), Constant.FALSE));
        setResult(node, result);
    }

    @Override
    public void visitInfopointNode(InfopointNode i) {
        if (i.getState() != null && i.getState().bci == FrameState.AFTER_BCI) {
            Debug.log("Ignoring InfopointNode for AFTER_BCI");
        } else {
            super.visitInfopointNode(i);
        }
    }

    public void emitPrefetchAllocate(ValueNode address, ValueNode distance) {
        AMD64AddressValue addr = emitAddress(operand(address), 0, loadNonConst(operand(distance)), 1);
        append(new AMD64PrefetchOp(addr, config.allocatePrefetchInstr));
    }
}
