/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.HotSpotBackend.FETCH_UNROLL_INFO;
import static com.oracle.graal.hotspot.HotSpotBackend.UNCOMMON_TRAP;
import static jdk.vm.ci.amd64.AMD64.rbp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.hotspot.HotSpotVMConfig.CompressEncoding;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.compiler.amd64.AMD64ArithmeticLIRGenerator;
import com.oracle.graal.compiler.amd64.AMD64LIRGenerator;
import com.oracle.graal.compiler.amd64.AMD64MoveFactoryBase.BackupSlotProvider;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.HotSpotLockStack;
import com.oracle.graal.hotspot.debug.BenchmarkCounters;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.VirtualStackSlot;
import com.oracle.graal.lir.amd64.AMD64AddressValue;
import com.oracle.graal.lir.amd64.AMD64CCall;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.amd64.AMD64FrameMapBuilder;
import com.oracle.graal.lir.amd64.AMD64Move;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromRegOp;
import com.oracle.graal.lir.amd64.AMD64RestoreRegistersOp;
import com.oracle.graal.lir.amd64.AMD64SaveRegistersOp;
import com.oracle.graal.lir.amd64.AMD64ZapRegistersOp;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotLIRGenerator extends AMD64LIRGenerator implements HotSpotLIRGenerator {

    final HotSpotVMConfig config;
    private HotSpotLockStack lockStack;

    protected AMD64HotSpotLIRGenerator(HotSpotProviders providers, HotSpotVMConfig config, CallingConvention cc, LIRGenerationResult lirGenRes) {
        this(providers, config, cc, lirGenRes, new BackupSlotProvider(lirGenRes.getFrameMapBuilder()));
    }

    private AMD64HotSpotLIRGenerator(HotSpotProviders providers, HotSpotVMConfig config, CallingConvention cc, LIRGenerationResult lirGenRes, BackupSlotProvider backupSlotProvider) {
        this(new AMD64HotSpotLIRKindTool(), new AMD64ArithmeticLIRGenerator(), new AMD64HotSpotMoveFactory(backupSlotProvider, lirGenRes.getFrameMapBuilder()), providers, config, cc, lirGenRes);
    }

    protected AMD64HotSpotLIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, HotSpotProviders providers, HotSpotVMConfig config,
                    CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, moveFactory, providers, cc, lirGenRes);
        assert config.basicLockSize == 8;
        this.config = config;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

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
            AMD64FrameMapBuilder frameMapBuilder = (AMD64FrameMapBuilder) getResult().getFrameMapBuilder();
            this.reservedSlot = frameMapBuilder.allocateRBPSpillSlot();
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
                ((AMD64FrameMapBuilder) getResult().getFrameMapBuilder()).freeRBPSpillSlot();
                dst = newVariable(LIRKind.value(AMD64Kind.QWORD));
            }

            placeholder.replace(getResult().getLIR(), new MoveFromRegOp(AMD64Kind.QWORD, dst, rbp.asValue(LIRKind.value(AMD64Kind.QWORD))));
            return dst;
        }
    }

    private SaveRbp saveRbp;

    protected void emitSaveRbp() {
        NoOp placeholder = new NoOp(getCurrentBlock(), getResult().getLIR().getLIRforBlock(getCurrentBlock()).size());
        append(placeholder);
        saveRbp = new SaveRbp(placeholder);
    }

    protected SaveRbp getSaveRbp() {
        return saveRbp;
    }

    /**
     * Helper instruction to reserve a stack slot for the whole method. Note that the actual users
     * of the stack slot might be inserted after stack slot allocation. This dummy instruction
     * ensures that the stack slot is alive and gets a real stack slot assigned.
     */
    private static final class RescueSlotDummyOp extends LIRInstruction {
        public static final LIRInstructionClass<RescueSlotDummyOp> TYPE = LIRInstructionClass.create(RescueSlotDummyOp.class);

        @Alive({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) private AllocatableValue slot;

        public RescueSlotDummyOp(FrameMapBuilder frameMapBuilder, LIRKind kind) {
            super(TYPE);
            slot = frameMapBuilder.allocateSpillSlot(kind);
        }

        public AllocatableValue getSlot() {
            return slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    private RescueSlotDummyOp rescueSlotOp;

    private VirtualStackSlot getOrInitRescueSlot() {
        RescueSlotDummyOp op = getOrInitRescueSlotOp();
        return (VirtualStackSlot) op.getSlot();
    }

    private RescueSlotDummyOp getOrInitRescueSlotOp() {
        if (rescueSlotOp == null) {
            // create dummy instruction to keep the rescue slot alive
            rescueSlotOp = new RescueSlotDummyOp(getResult().getFrameMapBuilder(), getLIRKindTool().getWordKind());
        }
        return rescueSlotOp;
    }

    /**
     * List of epilogue operations that need to restore RBP.
     */
    List<AMD64HotSpotRestoreRbpOp> epilogueOps = new ArrayList<>(2);

    @Override
    public <I extends LIRInstruction> I append(I op) {
        I ret = super.append(op);
        if (op instanceof AMD64HotSpotRestoreRbpOp) {
            epilogueOps.add((AMD64HotSpotRestoreRbpOp) op);
        }
        return ret;
    }

    @Override
    public VirtualStackSlot getLockSlot(int lockDepth) {
        return getLockStack().makeLockSlot(lockDepth);
    }

    private HotSpotLockStack getLockStack() {
        assert lockStack != null;
        return lockStack;
    }

    protected void setLockStack(HotSpotLockStack lockStack) {
        assert this.lockStack == null;
        this.lockStack = lockStack;
    }

    private Register findPollOnReturnScratchRegister() {
        RegisterConfig regConfig = getProviders().getCodeCache().getRegisterConfig();
        for (Register r : regConfig.getAllocatableRegisters()) {
            if (!r.equals(regConfig.getReturnRegister(JavaKind.Long)) && !r.equals(AMD64.rbp)) {
                return r;
            }
        }
        throw JVMCIError.shouldNotReachHere();
    }

    private Register pollOnReturnScratchRegister;

    @Override
    public void emitReturn(JavaKind kind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(kind, input.getLIRKind());
            emitMove(operand, input);
        }
        if (pollOnReturnScratchRegister == null) {
            pollOnReturnScratchRegister = findPollOnReturnScratchRegister();
        }
        append(new AMD64HotSpotReturnOp(operand, getStub() != null, pollOnReturnScratchRegister, config));
    }

    @Override
    public boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return ((AMD64HotSpotLIRGenerationResult) getResult()).getStub() != null;
    }

    private LIRFrameState currentRuntimeCallInfo;

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        currentRuntimeCallInfo = info;
        super.emitForeignCallOp(linkage, result, arguments, temps, info);
    }

    public void emitLeaveCurrentStackFrame(SaveRegistersOp saveRegisterOp) {
        append(new AMD64HotSpotLeaveCurrentStackFrameOp(saveRegisterOp));
    }

    public void emitLeaveDeoptimizedStackFrame(Value frameSize, Value initialInfo) {
        Variable frameSizeVariable = load(frameSize);
        Variable initialInfoVariable = load(initialInfo);
        append(new AMD64HotSpotLeaveDeoptimizedStackFrameOp(frameSizeVariable, initialInfoVariable));
    }

    public void emitEnterUnpackFramesStackFrame(Value framePc, Value senderSp, Value senderFp, SaveRegistersOp saveRegisterOp) {
        Register threadRegister = getProviders().getRegisters().getThreadRegister();
        Variable framePcVariable = load(framePc);
        Variable senderSpVariable = load(senderSp);
        Variable senderFpVariable = load(senderFp);
        append(new AMD64HotSpotEnterUnpackFramesStackFrameOp(threadRegister, config.threadLastJavaSpOffset(), config.threadLastJavaPcOffset(), config.threadLastJavaFpOffset(), framePcVariable,
                        senderSpVariable, senderFpVariable, saveRegisterOp));
    }

    public void emitLeaveUnpackFramesStackFrame(SaveRegistersOp saveRegisterOp) {
        Register threadRegister = getProviders().getRegisters().getThreadRegister();
        append(new AMD64HotSpotLeaveUnpackFramesStackFrameOp(threadRegister, config.threadLastJavaSpOffset(), config.threadLastJavaPcOffset(), config.threadLastJavaFpOffset(), saveRegisterOp));
    }

    @Override
    public Value emitCardTableShift() {
        Variable result = newVariable(LIRKind.value(AMD64Kind.QWORD));
        append(new AMD64HotSpotCardTableShiftOp(result, config));
        return result;
    }

    @Override
    public Value emitCardTableAddress() {
        Variable result = newVariable(LIRKind.value(AMD64Kind.QWORD));
        append(new AMD64HotSpotCardTableAddressOp(result, config));
        return result;
    }

    /**
     * @param savedRegisters the registers saved by this operation which may be subject to pruning
     * @param savedRegisterLocations the slots to which the registers are saved
     * @param supportsRemove determines if registers can be pruned
     */
    protected AMD64SaveRegistersOp emitSaveRegisters(Register[] savedRegisters, AllocatableValue[] savedRegisterLocations, boolean supportsRemove) {
        AMD64SaveRegistersOp save = new AMD64SaveRegistersOp(savedRegisters, savedRegisterLocations, supportsRemove);
        append(save);
        return save;
    }

    /**
     * Allocate a stack slot for saving a register.
     */
    protected VirtualStackSlot allocateSaveRegisterLocation(Register register) {
        PlatformKind kind = target().arch.getLargestStorableKind(register.getRegisterCategory());
        if (kind.getVectorLength() > 1) {
            // we don't use vector registers, so there is no need to save them
            kind = AMD64Kind.DOUBLE;
        }
        return getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(kind));
    }

    /**
     * Adds a node to the graph that saves all allocatable registers to the stack.
     *
     * @param supportsRemove determines if registers can be pruned
     * @return the register save node
     */
    private AMD64SaveRegistersOp emitSaveAllRegisters(Register[] savedRegisters, boolean supportsRemove) {
        AllocatableValue[] savedRegisterLocations = new AllocatableValue[savedRegisters.length];
        for (int i = 0; i < savedRegisters.length; i++) {
            savedRegisterLocations[i] = allocateSaveRegisterLocation(savedRegisters[i]);
        }
        return emitSaveRegisters(savedRegisters, savedRegisterLocations, supportsRemove);
    }

    @Override
    public SaveRegistersOp emitSaveAllRegisters() {
        // We are saving all registers.
        // TODO Save upper half of YMM registers.
        return emitSaveAllRegisters(target().arch.getAvailableValueRegisters(), false);
    }

    protected void emitRestoreRegisters(AMD64SaveRegistersOp save) {
        append(new AMD64RestoreRegistersOp(save.getSlots().clone(), save));
    }

    /**
     * Gets the {@link Stub} this generator is generating code for or {@code null} if a stub is not
     * being generated.
     */
    public Stub getStub() {
        return ((AMD64HotSpotLIRGenerationResult) getResult()).getStub();
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... args) {
        HotSpotForeignCallLinkage hotspotLinkage = (HotSpotForeignCallLinkage) linkage;
        boolean destroysRegisters = hotspotLinkage.destroysRegisters();

        AMD64SaveRegistersOp save = null;
        Stub stub = getStub();
        if (destroysRegisters) {
            if (stub != null && stub.preservesRegisters()) {
                Register[] savedRegisters = getResult().getFrameMapBuilder().getRegisterConfig().getAllocatableRegisters();
                save = emitSaveAllRegisters(savedRegisters, true);
            }
        }

        Variable result;
        LIRFrameState debugInfo = null;
        if (hotspotLinkage.needsDebugInfo()) {
            debugInfo = state;
            assert debugInfo != null || stub != null;
        }

        if (hotspotLinkage.needsJavaFrameAnchor()) {
            Register thread = getProviders().getRegisters().getThreadRegister();
            append(new AMD64HotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), thread));
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
            append(new AMD64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaFpOffset(), thread));
        } else {
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
        }

        if (destroysRegisters) {
            if (stub != null) {
                if (stub.preservesRegisters()) {
                    AMD64HotSpotLIRGenerationResult generationResult = (AMD64HotSpotLIRGenerationResult) getResult();
                    assert !generationResult.getCalleeSaveInfo().containsKey(currentRuntimeCallInfo);
                    generationResult.getCalleeSaveInfo().put(currentRuntimeCallInfo, save);
                    emitRestoreRegisters(save);
                } else {
                    assert zapRegisters();
                }
            }
        }

        return result;
    }

    public Value emitUncommonTrapCall(Value trapRequest, SaveRegistersOp saveRegisterOp) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(UNCOMMON_TRAP);

        Register thread = getProviders().getRegisters().getThreadRegister();
        append(new AMD64HotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), thread));
        Variable result = super.emitForeignCall(linkage, null, thread.asValue(LIRKind.value(AMD64Kind.QWORD)), trapRequest);
        append(new AMD64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaFpOffset(), thread));

        Map<LIRFrameState, SaveRegistersOp> calleeSaveInfo = ((AMD64HotSpotLIRGenerationResult) getResult()).getCalleeSaveInfo();
        assert !calleeSaveInfo.containsKey(currentRuntimeCallInfo);
        calleeSaveInfo.put(currentRuntimeCallInfo, saveRegisterOp);

        return result;
    }

    public Value emitDeoptimizationFetchUnrollInfoCall(SaveRegistersOp saveRegisterOp) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(FETCH_UNROLL_INFO);

        Register thread = getProviders().getRegisters().getThreadRegister();
        append(new AMD64HotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), thread));
        Variable result = super.emitForeignCall(linkage, null, thread.asValue(LIRKind.value(AMD64Kind.QWORD)));
        append(new AMD64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaFpOffset(), thread));

        Map<LIRFrameState, SaveRegistersOp> calleeSaveInfo = ((AMD64HotSpotLIRGenerationResult) getResult()).getCalleeSaveInfo();
        assert !calleeSaveInfo.containsKey(currentRuntimeCallInfo);
        calleeSaveInfo.put(currentRuntimeCallInfo, saveRegisterOp);

        return result;
    }

    protected AMD64ZapRegistersOp emitZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        AMD64ZapRegistersOp zap = new AMD64ZapRegistersOp(zappedRegisters, zapValues);
        append(zap);
        return zap;
    }

    protected boolean zapRegisters() {
        Register[] zappedRegisters = getResult().getFrameMapBuilder().getRegisterConfig().getAllocatableRegisters();
        JavaConstant[] zapValues = new JavaConstant[zappedRegisters.length];
        for (int i = 0; i < zappedRegisters.length; i++) {
            PlatformKind kind = target().arch.getLargestStorableKind(zappedRegisters[i].getRegisterCategory());
            zapValues[i] = zapValueForKind(kind);
        }
        ((AMD64HotSpotLIRGenerationResult) getResult()).getCalleeSaveInfo().put(currentRuntimeCallInfo, emitZapRegisters(zappedRegisters, zapValues));
        return true;
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        append(new AMD64TailcallOp(args, address));
    }

    @Override
    public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments) {
        Value[] argLocations = new Value[args.length];
        getResult().getFrameMapBuilder().callsMethod(nativeCallingConvention);
        // TODO(mg): in case a native function uses floating point varargs, the ABI requires that
        // RAX contains the length of the varargs
        PrimitiveConstant intConst = JavaConstant.forInt(numberOfFloatingPointArguments);
        AllocatableValue numberOfFloatingPointArgumentsRegister = AMD64.rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        emitMoveConstant(numberOfFloatingPointArgumentsRegister, intConst);
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = nativeCallingConvention.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }
        Value ptr = emitLoadConstant(LIRKind.value(AMD64Kind.QWORD), JavaConstant.forLong(address));
        append(new AMD64CCall(nativeCallingConvention.getReturn(), ptr, numberOfFloatingPointArgumentsRegister, argLocations));
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
        moveValueToThread(actionAndReason, config.pendingDeoptimizationOffset);
        moveValueToThread(speculation, config.pendingFailedSpeculationOffset);
    }

    private void moveValueToThread(Value v, int offset) {
        LIRKind wordKind = LIRKind.value(target().arch.getWordKind());
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        AMD64AddressValue address = new AMD64AddressValue(wordKind, thread, offset);
        arithmeticLIRGen.emitStore(v.getLIRKind(), address, v, null);
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new AMD64DeoptimizeOp(state));
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        Value actionAndReason = emitJavaConstant(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0));
        Value nullValue = emitConstant(LIRKind.reference(AMD64Kind.QWORD), JavaConstant.NULL_POINTER);
        moveDeoptValuesToThread(actionAndReason, nullValue);
        append(new AMD64HotSpotDeoptimizeCallerOp());
    }

    @Override
    public void beforeRegisterAllocation() {
        super.beforeRegisterAllocation();
        boolean hasDebugInfo = getResult().getLIR().hasDebugInfo();
        AllocatableValue savedRbp = saveRbp.finalize(hasDebugInfo);
        if (hasDebugInfo) {
            ((AMD64HotSpotLIRGenerationResult) getResult()).setDeoptimizationRescueSlot(((AMD64FrameMapBuilder) getResult().getFrameMapBuilder()).allocateDeoptimizationRescueSlot());
        }

        for (AMD64HotSpotRestoreRbpOp op : epilogueOps) {
            op.setSavedRbp(savedRbp);
        }
        if (BenchmarkCounters.enabled) {
            // ensure that the rescue slot is available
            LIRInstruction op = getOrInitRescueSlotOp();
            // insert dummy instruction into the start block
            LIR lir = getResult().getLIR();
            List<LIRInstruction> instructions = lir.getLIRforBlock(lir.getControlFlowGraph().getStartBlock());
            instructions.add(1, op);
            Debug.dump(lir, "created rescue dummy op");
        }
    }

    public void emitPushInterpreterFrame(Value frameSize, Value framePc, Value senderSp, Value initialInfo) {
        Variable frameSizeVariable = load(frameSize);
        Variable framePcVariable = load(framePc);
        Variable senderSpVariable = load(senderSp);
        Variable initialInfoVariable = load(initialInfo);
        append(new AMD64HotSpotPushInterpreterFrameOp(frameSizeVariable, framePcVariable, senderSpVariable, initialInfoVariable, config));
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getLIRKind();
        assert inputKind.getPlatformKind() == AMD64Kind.QWORD;
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(LIRKind.reference(AMD64Kind.DWORD));
            append(new AMD64HotSpotMove.CompressPointer(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull));
            return result;
        } else {
            // metaspace pointer
            Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
            AllocatableValue base = Value.ILLEGAL;
            if (encoding.base != 0) {
                base = emitLoadConstant(LIRKind.value(AMD64Kind.QWORD), JavaConstant.forLong(encoding.base));
            }
            append(new AMD64HotSpotMove.CompressPointer(result, asAllocatable(pointer), base, encoding, nonNull));
            return result;
        }
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getLIRKind();
        assert inputKind.getPlatformKind() == AMD64Kind.DWORD;
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(LIRKind.reference(AMD64Kind.QWORD));
            append(new AMD64HotSpotMove.UncompressPointer(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull));
            return result;
        } else {
            // metaspace pointer
            Variable result = newVariable(LIRKind.value(AMD64Kind.QWORD));
            AllocatableValue base = Value.ILLEGAL;
            if (encoding.base != 0) {
                base = emitLoadConstant(LIRKind.value(AMD64Kind.QWORD), JavaConstant.forLong(encoding.base));
            }
            append(new AMD64HotSpotMove.UncompressPointer(result, asAllocatable(pointer), base, encoding, nonNull));
            return result;
        }
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        if (address.getLIRKind().getPlatformKind() == AMD64Kind.DWORD) {
            CompressEncoding encoding = config.getOopEncoding();
            Value uncompressed;
            if (encoding.shift <= 3) {
                LIRKind wordKind = LIRKind.unknownReference(target().arch.getWordKind());
                uncompressed = new AMD64AddressValue(wordKind, getProviders().getRegisters().getHeapBaseRegister().asValue(wordKind), asAllocatable(address), Scale.fromInt(1 << encoding.shift), 0);
            } else {
                uncompressed = emitUncompress(address, encoding, false);
            }
            append(new AMD64Move.NullCheckOp(asAddressValue(uncompressed), state));
        } else {
            super.emitNullCheck(address, state);
        }
    }

    @Override
    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        if (BenchmarkCounters.enabled) {
            return new AMD64HotSpotCounterOp(name, group, increment, getProviders().getRegisters(), config, getOrInitRescueSlot());
        }
        return null;
    }

    @Override
    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        if (BenchmarkCounters.enabled) {
            return new AMD64HotSpotCounterOp(names, groups, increments, getProviders().getRegisters(), config, getOrInitRescueSlot());
        }
        return null;
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        append(new AMD64PrefetchOp(asAddressValue(address), config.allocatePrefetchInstr));
    }

    @Override
    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, AllocatableValue temp) {
        return new AMD64HotSpotStrategySwitchOp(strategy, keyTargets, defaultTarget, key, temp);
    }
}
