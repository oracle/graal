/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.amd64.AMD64ArithmeticLIRGenerator;
import jdk.graal.compiler.core.amd64.AMD64LIRGenerator;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotDebugInfoBuilder;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime;
import jdk.graal.compiler.hotspot.HotSpotLIRGenerationResult;
import jdk.graal.compiler.hotspot.HotSpotLIRGenerator;
import jdk.graal.compiler.hotspot.HotSpotLockStack;
import jdk.graal.compiler.hotspot.debug.BenchmarkCounters;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.StandardOp.NoOp;
import jdk.graal.compiler.lir.SwitchStrategy;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.amd64.AMD64Move.MoveFromRegOp;
import jdk.graal.compiler.lir.amd64.AMD64PrefetchOp;
import jdk.graal.compiler.lir.amd64.AMD64ReadTimestampCounterWithProcid;
import jdk.graal.compiler.lir.amd64.AMD64RestoreRegistersOp;
import jdk.graal.compiler.lir.amd64.AMD64SaveRegistersOp;
import jdk.graal.compiler.lir.amd64.AMD64VZeroUpper;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.graal.compiler.lir.gen.BarrierSetLIRGenerator;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.MoveFactory;
import jdk.graal.compiler.lir.gen.MoveFactory.BackupSlotProvider;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterArray;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AMD64 HotSpot.
 */
public class AMD64HotSpotLIRGenerator extends AMD64LIRGenerator implements HotSpotLIRGenerator {

    final GraalHotSpotVMConfig config;
    private HotSpotDebugInfoBuilder debugInfoBuilder;

    protected AMD64HotSpotLIRGenerator(HotSpotProviders providers, GraalHotSpotVMConfig config, LIRGenerationResult lirGenRes) {
        this(providers, config, lirGenRes, new BackupSlotProvider(lirGenRes.getFrameMapBuilder()));
    }

    protected static BarrierSetLIRGenerator getBarrierSet(GraalHotSpotVMConfig config, Providers providers) {
        if (config.gc == HotSpotGraalRuntime.HotSpotGC.Z) {
            return new AMD64HotSpotZBarrierSetLIRGenerator(config, providers);
        }
        return null;
    }

    private AMD64HotSpotLIRGenerator(HotSpotProviders providers, GraalHotSpotVMConfig config, LIRGenerationResult lirGenRes, BackupSlotProvider backupSlotProvider) {
        this(new AMD64HotSpotLIRKindTool(), new AMD64ArithmeticLIRGenerator(null), getBarrierSet(config, providers), new AMD64HotSpotMoveFactory(backupSlotProvider), providers, config, lirGenRes);
    }

    protected AMD64HotSpotLIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, BarrierSetLIRGenerator barrierSetLIRGen, MoveFactory moveFactory,
                    HotSpotProviders providers, GraalHotSpotVMConfig config, LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, barrierSetLIRGen, moveFactory, providers, lirGenRes);
        int basicLockSize = config.basicLockSize;
        assert basicLockSize == 8 : basicLockSize;
        this.config = config;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    @Override
    public AVXSize getMaxVectorSize(EnumSet<?> runtimeCheckedCPUFeatures) {
        int maxVectorSize = config.maxVectorSize;
        if (supports(runtimeCheckedCPUFeatures, CPUFeature.AVX512VL)) {
            if (maxVectorSize < 0 || maxVectorSize >= 64) {
                return AVXSize.ZMM;
            }
        }
        if (supports(runtimeCheckedCPUFeatures, CPUFeature.AVX2)) {
            if (maxVectorSize < 0 || maxVectorSize >= 32) {
                return AVXSize.YMM;
            }
        }
        return AVXSize.XMM;
    }

    @Override
    protected int getAVX3Threshold() {
        return config.useAVX3Threshold;
    }

    /**
     * Utility for emitting the instruction to save RBP.
     */
    class SaveRbp {

        final NoOp placeholder;

        SaveRbp(NoOp placeholder) {
            this.placeholder = placeholder;
        }

        /**
         * Replaces this operation with the appropriate move for saving rbp.
         *
         * @param useStack specifies if rbp must be saved to the stack
         */
        public AllocatableValue finalize(boolean useStack) {
            assert !config.preserveFramePointer : "rbp has been pushed onto the stack";
            AllocatableValue dst;
            if (useStack) {
                dst = ((AMD64HotSpotFrameMapBuilder) getResult().getFrameMapBuilder()).getRBPSpillSlot();
            } else {
                dst = newVariable(LIRKind.value(AMD64Kind.QWORD));
            }
            placeholder.replace(getResult().getLIR(), new MoveFromRegOp(AMD64Kind.QWORD, dst, rbp.asValue(LIRKind.value(AMD64Kind.QWORD))));
            return dst;
        }

        public void remove() {
            placeholder.remove(getResult().getLIR());
        }
    }

    private SaveRbp saveRbp;

    protected void emitSaveRbp() {
        NoOp placeholder = new NoOp(getCurrentBlock(), getResult().getLIR().getLIRforBlock(getCurrentBlock()).size());
        append(placeholder);
        saveRbp = new SaveRbp(placeholder);
    }

    /**
     * Helper instruction to reserve a stack slot for the whole method. Note that the actual users
     * of the stack slot might be inserted after stack slot allocation. This dummy instruction
     * ensures that the stack slot is alive and gets a real stack slot assigned.
     */
    private static final class RescueSlotDummyOp extends LIRInstruction {
        public static final LIRInstructionClass<RescueSlotDummyOp> TYPE = LIRInstructionClass.create(RescueSlotDummyOp.class);

        @Alive({OperandFlag.STACK, OperandFlag.UNINITIALIZED}) private AllocatableValue slot;

        RescueSlotDummyOp(FrameMapBuilder frameMapBuilder, LIRKind kind) {
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

    private AllocatableValue getOrInitRescueSlot() {
        RescueSlotDummyOp op = getOrInitRescueSlotOp();
        return op.getSlot();
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
        assert debugInfoBuilder != null;
        assert debugInfoBuilder.lockStack() != null;
        return debugInfoBuilder.lockStack();
    }

    private Register findPollOnReturnScratchRegister() {
        RegisterConfig regConfig = getProviders().getCodeCache().getRegisterConfig();
        for (Register r : regConfig.getAllocatableRegisters()) {
            if (!r.equals(regConfig.getReturnRegister(JavaKind.Long)) && !r.equals(AMD64.rbp)) {
                return r;
            }
        }
        throw GraalError.shouldNotReachHereUnexpectedValue(regConfig); // ExcludeFromJacocoGeneratedReport
    }

    private Register pollOnReturnScratchRegister;

    @Override
    public void emitReturn(JavaKind kind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(kind, input.getValueKind());
            emitMove(operand, input);
        }
        if (pollOnReturnScratchRegister == null) {
            pollOnReturnScratchRegister = findPollOnReturnScratchRegister();
        }
        Register thread = getProviders().getRegisters().getThreadRegister();
        append(new AMD64HotSpotReturnOp(operand, getStub() != null, thread, pollOnReturnScratchRegister, config, getResult().requiresReservedStackAccessCheck()));
    }

    @Override
    public boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return getResult().getStub() != null;
    }

    private LIRFrameState currentRuntimeCallInfo;

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        currentRuntimeCallInfo = info;
        HotSpotForeignCallLinkage hsLinkage = (HotSpotForeignCallLinkage) linkage;
        AMD64 arch = (AMD64) target().arch;
        if (arch.getFeatures().contains(AMD64.CPUFeature.AVX) && hsLinkage.mayContainFP() && !hsLinkage.isCompiledStub()) {
            /*
             * If the target may contain FP ops, and it is not compiled by us, we may have an
             * AVX-SSE transition.
             *
             * We exclude the argument registers from the zeroing LIR instruction since it violates
             * the LIR semantics of @Temp that values must not be live. Note that the emitted
             * machine instruction actually zeros _all_ XMM registers which is fine since we know
             * that their upper half is not used.
             */
            append(new AMD64VZeroUpper(arguments, getRegisterConfig()));
        }
        super.emitForeignCallOp(linkage, targetAddress, result, arguments, temps, info);
    }

    /**
     * @param savedRegisters the registers saved by this operation which may be subject to pruning
     * @param savedRegisterLocations the slots to which the registers are saved
     */
    protected AMD64SaveRegistersOp emitSaveRegisters(Register[] savedRegisters, AllocatableValue[] savedRegisterLocations) {
        AMD64SaveRegistersOp save = new AMD64SaveRegistersOp(savedRegisters, savedRegisterLocations);
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
     * @return the register save node
     */
    private AMD64SaveRegistersOp emitSaveAllRegisters(boolean forSafepoint) {
        Register[] savedRegisters = getSaveableRegisters(forSafepoint);
        AllocatableValue[] savedRegisterLocations = new AllocatableValue[savedRegisters.length];
        for (int i = 0; i < savedRegisters.length; i++) {
            savedRegisterLocations[i] = allocateSaveRegisterLocation(savedRegisters[i]);
        }
        return emitSaveRegisters(savedRegisters, savedRegisterLocations);
    }

    /**
     * @param forSafepoint saveable registers must be describable for register map.
     */
    protected Register[] getSaveableRegisters(boolean forSafepoint) {
        RegisterArray allocatableRegisters = getResult().getRegisterAllocationConfig().getAllocatableRegisters();

        ArrayList<Register> registers = new ArrayList<>(allocatableRegisters.size());
        for (Register reg : allocatableRegisters) {
            // mask registers should not be saved at safepoints
            if (!forSafepoint || !reg.getRegisterCategory().equals(AMD64.MASK)) {
                registers.add(reg);
            }
        }

        return registers.toArray(new Register[registers.size()]);
    }

    protected void emitRestoreRegisters(AMD64SaveRegistersOp save) {
        append(new AMD64RestoreRegistersOp(save.getSlots().clone(), save));
    }

    /**
     * Gets the {@link Stub} this generator is generating code for or {@code null} if a stub is not
     * being generated.
     */
    public Stub getStub() {
        return getResult().getStub();
    }

    @Override
    public HotSpotLIRGenerationResult getResult() {
        return ((HotSpotLIRGenerationResult) super.getResult());
    }

    public void setDebugInfoBuilder(HotSpotDebugInfoBuilder debugInfoBuilder) {
        this.debugInfoBuilder = debugInfoBuilder;
    }

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... args) {
        HotSpotForeignCallLinkage hotspotLinkage = (HotSpotForeignCallLinkage) linkage;
        boolean destroysRegisters = hotspotLinkage.destroysRegisters();

        AMD64SaveRegistersOp save = null;
        Stub stub = getStub();
        if (destroysRegisters && stub != null && stub.shouldSaveRegistersAroundCalls()) {
            save = emitSaveAllRegisters(stub.getLinkage().getDescriptor().getTransition() == HotSpotForeignCallDescriptor.Transition.SAFEPOINT);
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
            append(new AMD64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaFpOffset(), config.threadLastJavaPcOffset(), thread));
        } else {
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
        }

        // Handle different return value locations
        if (stub != null && stub.getLinkage().getEffect() == HotSpotForeignCallLinkage.RegisterEffect.KILLS_NO_REGISTERS && result != null) {
            CallingConvention inCC = stub.getLinkage().getIncomingCallingConvention();
            if (!inCC.getReturn().equals(linkage.getOutgoingCallingConvention().getReturn())) {
                assert isStackSlot(inCC.getReturn());
                emitMove(inCC.getReturn(), result);
            }
        }

        if (save != null) {
            HotSpotLIRGenerationResult generationResult = getResult();
            LIRFrameState key = currentRuntimeCallInfo;
            if (key == null) {
                key = LIRFrameState.noCalleeSaveInfo();
            }
            assert !generationResult.getCalleeSaveInfo().containsKey(key);
            generationResult.getCalleeSaveInfo().put(key, save);
            emitRestoreRegisters(save);
        }

        return result;
    }

    @Override
    public void emitUnwind(Value exception) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        int argumentCount = outgoingCc.getArgumentCount();
        assert argumentCount == 2 : argumentCount;
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
        arithmeticLIRGen.emitStore(v.getValueKind(), address, v, null, MemoryOrderMode.PLAIN);
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new AMD64DeoptimizeOp(state));
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        Value actionAndReason = emitJavaConstant(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0));
        Value speculation = emitJavaConstant(getMetaAccess().encodeSpeculation(SpeculationLog.NO_SPECULATION));
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new AMD64HotSpotDeoptimizeCallerOp());
    }

    @Override
    public void emitDeoptimizeWithExceptionInCaller(Value exception) {
        append(new AMD64HotSpotDeoptimizeWithExceptionCallerOp(config, exception));
    }

    @Override
    public void beforeRegisterAllocation() {
        super.beforeRegisterAllocation();
        boolean hasDebugInfo = getResult().getLIR().hasDebugInfo();

        if (config.preserveFramePointer) {
            saveRbp.remove();
        } else {
            AllocatableValue savedRbp = saveRbp.finalize(hasDebugInfo);
            for (AMD64HotSpotRestoreRbpOp op : epilogueOps) {
                op.setSavedRbp(savedRbp);
            }
        }

        if (hasDebugInfo) {
            getResult().setDeoptimizationRescueSlot(((AMD64HotSpotFrameMapBuilder) getResult().getFrameMapBuilder()).getDeoptimizationRescueSlot());
        }
        getResult().setMaxInterpreterFrameSize(debugInfoBuilder.maxInterpreterFrameSize());

        if (BenchmarkCounters.enabled) {
            // ensure that the rescue slot is available
            LIRInstruction op = getOrInitRescueSlotOp();
            // insert dummy instruction into the start block
            LIR lir = getResult().getLIR();
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(lir.getControlFlowGraph().getStartBlock());
            instructions.add(1, op);
            lir.getDebug().dump(DebugContext.INFO_LEVEL, lir, "created rescue dummy op");
        }
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getValueKind(LIRKind.class);
        LIRKindTool lirKindTool = getLIRKindTool();
        LIRKind objectKind = lirKindTool.getObjectKind();
        assert inputKind.getPlatformKind() == objectKind.getPlatformKind() : Assertions.errorMessageContext("inputKind", inputKind, "lirKindToolObjectKind",
                        objectKind, "pointer", pointer);
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(lirKindTool.getNarrowOopKind());
            append(new AMD64Move.CompressPointerOp(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull, getLIRKindTool()));
            return result;
        } else {
            // metaspace pointer
            Variable result = newVariable(lirKindTool.getNarrowPointerKind());
            AllocatableValue base = Value.ILLEGAL;
            if (encoding.hasBase()) {
                base = emitLoadConstant(lirKindTool.getWordKind(), JavaConstant.forLong(encoding.getBase()));
            }
            append(new AMD64Move.CompressPointerOp(result, asAllocatable(pointer), base, encoding, nonNull, getLIRKindTool()));
            return result;
        }
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getValueKind(LIRKind.class);
        LIRKindTool lirKindTool = getLIRKindTool();
        assert inputKind.getPlatformKind() == lirKindTool.getNarrowOopKind().getPlatformKind() : Assertions.errorMessageContext("inputKind", inputKind, "lirKindToolObjectKind",
                        lirKindTool.getNarrowOopKind(), "pointer", pointer);
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(lirKindTool.getObjectKind());
            append(new AMD64Move.UncompressPointerOp(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull, lirKindTool));
            return result;
        } else {
            // metaspace pointer
            LIRKind uncompressedKind = lirKindTool.getWordKind();
            Variable result = newVariable(uncompressedKind);
            AllocatableValue base = Value.ILLEGAL;
            if (encoding.hasBase()) {
                base = emitLoadConstant(uncompressedKind, JavaConstant.forLong(encoding.getBase()));
            }
            append(new AMD64Move.UncompressPointerOp(result, asAllocatable(pointer), base, encoding, nonNull, lirKindTool));
            return result;
        }
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        if (address.getValueKind().getPlatformKind() == getLIRKindTool().getNarrowOopKind().getPlatformKind()) {
            CompressEncoding encoding = config.getOopEncoding();
            Value uncompressed;
            int shift = encoding.getShift();
            if (AMD64Address.isScaleShiftSupported(shift)) {
                LIRKind wordKind = LIRKind.unknownReference(target().arch.getWordKind());
                RegisterValue heapBase = getProviders().getRegisters().getHeapBaseRegister().asValue(wordKind);
                Stride stride = Stride.fromLog2(shift);
                uncompressed = new AMD64AddressValue(wordKind, heapBase, asAllocatable(address), stride, 0);
            } else {
                uncompressed = emitUncompress(address, encoding, false);
            }
            append(new AMD64Move.NullCheckOp(asAddressValue(uncompressed), state));
            return;
        }
        super.emitNullCheck(address, state);
    }

    @Override
    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        if (BenchmarkCounters.enabled) {
            return new AMD64HotSpotCounterOp(name, group, increment, getProviders().getRegisters(), config, getOrInitRescueSlot());
        }
        throw GraalError.shouldNotReachHere("BenchmarkCounters are not enabled!"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        if (BenchmarkCounters.enabled) {
            return new AMD64HotSpotCounterOp(names, groups, increments, getProviders().getRegisters(), config, getOrInitRescueSlot());
        }
        throw GraalError.shouldNotReachHere("BenchmarkCounters are not enabled!"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        append(new AMD64PrefetchOp(asAddressValue(address), config.allocatePrefetchInstr));
    }

    @Override
    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue key, AllocatableValue temp) {
        return new AMD64HotSpotStrategySwitchOp(strategy, keyTargets, defaultTarget, key, temp);
    }

    @Override
    public Value emitTimeStamp() {
        AMD64ReadTimestampCounterWithProcid timestamp = new AMD64ReadTimestampCounterWithProcid();
        append(timestamp);
        // Combine RDX and RAX into a single 64-bit register.
        AllocatableValue lo = timestamp.getLowResult();
        Value hi = getArithmetic().emitZeroExtend(timestamp.getHighResult(), 32, 64);
        return combineLoAndHi(lo, hi);
    }

    /**
     * Combines two 32 bit values to a 64 bit value: ( (hi << 32) | lo ).
     */
    private Value combineLoAndHi(Value lo, Value hi) {
        Value shiftedHi = getArithmetic().emitShl(hi, emitConstant(LIRKind.value(AMD64Kind.DWORD), JavaConstant.forInt(32)));
        return getArithmetic().emitOr(shiftedHi, lo);
    }

    @Override
    public int getArrayLengthOffset() {
        return config.arrayOopDescLengthOffset();
    }

    @Override
    public Register getHeapBaseRegister() {
        return getProviders().getRegisters().getHeapBaseRegister();
    }
}
