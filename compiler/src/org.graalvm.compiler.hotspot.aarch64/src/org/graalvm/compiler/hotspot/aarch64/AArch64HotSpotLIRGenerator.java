/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

package org.graalvm.compiler.hotspot.aarch64;

import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Function;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.PrefetchMode;
import org.graalvm.compiler.core.aarch64.AArch64ArithmeticLIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRKindTool;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotDebugInfoBuilder;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerationResult;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerator;
import org.graalvm.compiler.hotspot.HotSpotLockStack;
import org.graalvm.compiler.hotspot.debug.BenchmarkCounters;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.aarch64.AArch64CCall;
import org.graalvm.compiler.lir.aarch64.AArch64Call;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow.StrategySwitchOp;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMapBuilder;
import org.graalvm.compiler.lir.aarch64.AArch64Move;
import org.graalvm.compiler.lir.aarch64.AArch64Move.StoreOp;
import org.graalvm.compiler.lir.aarch64.AArch64PrefetchOp;
import org.graalvm.compiler.lir.aarch64.AArch64RestoreRegistersOp;
import org.graalvm.compiler.lir.aarch64.AArch64SaveRegistersOp;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.MoveFactory;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AArch64 HotSpot.
 */
public class AArch64HotSpotLIRGenerator extends AArch64LIRGenerator implements HotSpotLIRGenerator {

    final GraalHotSpotVMConfig config;
    private HotSpotDebugInfoBuilder debugInfoBuilder;

    protected AArch64HotSpotLIRGenerator(HotSpotProviders providers, GraalHotSpotVMConfig config, LIRGenerationResult lirGenRes) {
        this(new AArch64LIRKindTool(), new AArch64ArithmeticLIRGenerator(null), new AArch64HotSpotMoveFactory(), providers, config, lirGenRes);
    }

    protected AArch64HotSpotLIRGenerator(LIRKindTool lirKindTool, AArch64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, moveFactory, providers, lirGenRes);
        this.config = config;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
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
        if (AArch64Call.isNearCall(linkage)) {
            append(new AArch64Call.DirectNearForeignCallOp(linkage, result, arguments, temps, info, label));
        } else {
            append(new AArch64Call.DirectFarForeignCallOp(linkage, result, arguments, temps, info, label));
        }
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        throw GraalError.unimplemented();
    }

    @Override
    public void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args) {
        Value[] argLocations = new Value[args.length];
        getResult().getFrameMapBuilder().callsMethod(nativeCallingConvention);
        for (int i = 0; i < args.length; i++) {
            Value arg = args[i];
            AllocatableValue loc = nativeCallingConvention.getArgument(i);
            emitMove(loc, arg);
            argLocations[i] = loc;
        }
        Value ptr = emitLoadConstant(LIRKind.value(AArch64Kind.QWORD), JavaConstant.forLong(address));
        append(new AArch64CCall(nativeCallingConvention.getReturn(), ptr, argLocations));
    }

    /**
     * @param savedRegisters the registers saved by this operation which may be subject to pruning
     * @param savedRegisterLocations the slots to which the registers are saved
     */
    protected AArch64SaveRegistersOp emitSaveRegisters(Register[] savedRegisters, AllocatableValue[] savedRegisterLocations) {
        AArch64SaveRegistersOp save = new AArch64SaveRegistersOp(savedRegisters, savedRegisterLocations);
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
            kind = AArch64Kind.DOUBLE;
        }
        return getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(kind));
    }

    /**
     * Adds a node to the graph that saves all allocatable registers to the stack.
     *
     * @return the register save node
     */
    private AArch64SaveRegistersOp emitSaveAllRegisters(Register[] savedRegisters) {
        AllocatableValue[] savedRegisterLocations = new AllocatableValue[savedRegisters.length];
        for (int i = 0; i < savedRegisters.length; i++) {
            savedRegisterLocations[i] = allocateSaveRegisterLocation(savedRegisters[i]);
        }
        return emitSaveRegisters(savedRegisters, savedRegisterLocations);
    }

    protected void emitRestoreRegisters(AArch64SaveRegistersOp save) {
        append(new AArch64RestoreRegistersOp(save.getSlots().clone(), save));
    }

    @Override
    public VirtualStackSlot getLockSlot(int lockDepth) {
        return getLockStack().makeLockSlot(lockDepth);
    }

    private HotSpotLockStack getLockStack() {
        assert debugInfoBuilder != null && debugInfoBuilder.lockStack() != null;
        return debugInfoBuilder.lockStack();
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value x, Value y, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        Value localX = x;
        Value localY = y;
        if (localX instanceof HotSpotObjectConstant) {
            localX = asAllocatable(localX);
        }
        if (localY instanceof HotSpotObjectConstant) {
            localY = asAllocatable(localY);
        }
        super.emitCompareBranch(cmpKind, localX, localY, cond, unorderedIsTrue, trueDestination, falseDestination, trueDestinationProbability);
    }

    @Override
    protected boolean emitCompare(PlatformKind cmpKind, Value a, Value b, Condition condition, boolean unorderedIsTrue) {
        Value localA = a;
        Value localB = b;
        if (isConstantValue(a)) {
            Constant c = asConstant(a);
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(c)) {
                localA = AArch64.zr.asValue(LIRKind.value(AArch64Kind.DWORD));
            } else if (c instanceof HotSpotObjectConstant) {
                localA = asAllocatable(localA);
            }
        }
        if (isConstantValue(b)) {
            Constant c = asConstant(b);
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(c)) {
                localB = AArch64.zr.asValue(LIRKind.value(AArch64Kind.DWORD));
            } else if (c instanceof HotSpotObjectConstant) {
                localB = asAllocatable(localB);
            }
        }
        return super.emitCompare(cmpKind, localA, localB, condition, unorderedIsTrue);
    }

    @Override
    public Value emitCompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getValueKind(LIRKind.class);
        assert inputKind.getPlatformKind() == AArch64Kind.QWORD;
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(LIRKind.compressedReference(AArch64Kind.DWORD));
            append(new AArch64HotSpotMove.CompressPointer(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull));
            return result;
        } else {
            // metaspace pointer
            Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
            AllocatableValue base = Value.ILLEGAL;
            if (encoding.hasBase()) {
                base = emitLoadConstant(LIRKind.value(AArch64Kind.QWORD), JavaConstant.forLong(encoding.getBase()));
            }
            append(new AArch64HotSpotMove.CompressPointer(result, asAllocatable(pointer), base, encoding, nonNull));
            return result;
        }
    }

    @Override
    public Value emitUncompress(Value pointer, CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getValueKind(LIRKind.class);
        assert inputKind.getPlatformKind() == AArch64Kind.DWORD;
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(LIRKind.reference(AArch64Kind.QWORD));
            append(new AArch64HotSpotMove.UncompressPointer(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull));
            return result;
        } else {
            // metaspace pointer
            Variable result = newVariable(LIRKind.value(AArch64Kind.QWORD));
            AllocatableValue base = Value.ILLEGAL;
            if (encoding.hasBase()) {
                base = emitLoadConstant(LIRKind.value(AArch64Kind.QWORD), JavaConstant.forLong(encoding.getBase()));
            }
            append(new AArch64HotSpotMove.UncompressPointer(result, asAllocatable(pointer), base, encoding, nonNull));
            return result;
        }
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        if (address.getValueKind().getPlatformKind() == AArch64Kind.DWORD) {
            CompressEncoding encoding = config.getOopEncoding();
            Value uncompressed = emitUncompress(address, encoding, false);
            append(new AArch64Move.NullCheckOp(asAddressValue(uncompressed, AArch64Address.ANY_SIZE), state));
        } else {
            super.emitNullCheck(address, state);
        }
    }

    /**
     * Within {@link AArch64HotSpotCounterOp} ADDS is used to perform the benchmark counter
     * increment. Thus, in order for a constant to be directly used, it must fit in the immediate
     * operand of this instruction.
     */
    private Value transformBenchmarkCounterIncrement(Value increment) {
        if (isJavaConstant(increment) && AArch64ArithmeticLIRGenerator.isAddSubtractConstant(asJavaConstant(increment))) {
            return increment;
        } else {
            return asAllocatable(increment);
        }
    }

    @Override
    public LIRInstruction createBenchmarkCounter(String name, String group, Value increment) {
        if (BenchmarkCounters.enabled) {
            return new AArch64HotSpotCounterOp(name, group, transformBenchmarkCounterIncrement(increment), getProviders().getRegisters(), config);
        }
        throw GraalError.shouldNotReachHere("BenchmarkCounters are not enabled!");
    }

    @Override
    public LIRInstruction createMultiBenchmarkCounter(String[] names, String[] groups, Value[] increments) {
        if (BenchmarkCounters.enabled) {
            Value[] incrementValues = Arrays.stream(increments).map(this::transformBenchmarkCounterIncrement).toArray(Value[]::new);
            return new AArch64HotSpotCounterOp(names, groups, incrementValues, getProviders().getRegisters(), config);
        }
        throw GraalError.shouldNotReachHere("BenchmarkCounters are not enabled!");
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        append(new AArch64PrefetchOp(asAddressValue(address, AArch64Address.ANY_SIZE), PrefetchMode.PSTL1KEEP));
    }

    @Override
    public void beforeRegisterAllocation() {
        super.beforeRegisterAllocation();
        boolean hasDebugInfo = getResult().getLIR().hasDebugInfo();
        if (hasDebugInfo) {
            getResult().setDeoptimizationRescueSlot(((AArch64FrameMapBuilder) getResult().getFrameMapBuilder()).allocateDeoptimizationRescueSlot());
        }

        getResult().setMaxInterpreterFrameSize(debugInfoBuilder.maxInterpreterFrameSize());
    }

    private Label label;

    @Override
    public Variable emitForeignCall(ForeignCallLinkage linkage, LIRFrameState state, Value... args) {
        HotSpotForeignCallLinkage hotspotLinkage = (HotSpotForeignCallLinkage) linkage;
        boolean destroysRegisters = hotspotLinkage.destroysRegisters();

        AArch64SaveRegistersOp save = null;
        Stub stub = getStub();
        if (destroysRegisters && stub != null && stub.shouldSaveRegistersAroundCalls()) {
            Register[] savedRegisters = getRegisterConfig().getAllocatableRegisters().toArray();
            save = emitSaveAllRegisters(savedRegisters);
        }

        Variable result;
        LIRFrameState debugInfo = null;
        if (hotspotLinkage.needsDebugInfo()) {
            debugInfo = state;
            assert debugInfo != null || getStub() != null;
        }

        if (destroysRegisters || hotspotLinkage.needsJavaFrameAnchor()) {
            HotSpotRegistersProvider registers = getProviders().getRegisters();
            Register thread = registers.getThreadRegister();
            Variable scratch = newVariable(LIRKind.value(target().arch.getWordKind()));

            // We need a label for the return address.
            label = new Label();

            append(new AArch64HotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), config.threadLastJavaPcOffset(), thread, scratch, label));
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
            append(new AArch64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaPcOffset(), thread, label));

            // Clear it out so it's not being reused later.
            label = null;
        } else {
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
        }

        if (save != null) {
            HotSpotLIRGenerationResult generationResult = getResult();
            LIRFrameState key = currentRuntimeCallInfo;
            if (key == null) {
                key = LIRFrameState.NO_CALLEE_SAVE_INFO;
            }
            assert !generationResult.getCalleeSaveInfo().containsKey(key);
            generationResult.getCalleeSaveInfo().put(key, save);
            emitRestoreRegisters(save);
        }

        return result;
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        Value actionAndReason = emitJavaConstant(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0));
        Value speculation = emitJavaConstant(getMetaAccess().encodeSpeculation(SpeculationLog.NO_SPECULATION));
        moveDeoptValuesToThread(actionAndReason, speculation);
        append(new AArch64HotSpotDeoptimizeCallerOp(config));
    }

    @Override
    public void emitDeoptimizeWithExceptionInCaller(Value exception) {
        Register thread = getProviders().getRegisters().getThreadRegister();
        append(new AArch64HotSpotDeoptimizeWithExceptionCallerOp(config, exception, thread));
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
        moveDeoptValuesToThread(actionAndReason, failedSpeculation);
        append(new AArch64HotSpotDeoptimizeOp(state));
    }

    private void moveDeoptValuesToThread(Value actionAndReason, Value speculation) {
        moveValueToThread(actionAndReason, config.pendingDeoptimizationOffset);
        moveValueToThread(speculation, config.pendingFailedSpeculationOffset);
    }

    private void moveValueToThread(Value value, int offset) {
        LIRKind wordKind = LIRKind.value(target().arch.getWordKind());
        int bitMemoryTransferSize = value.getValueKind().getPlatformKind().getSizeInBytes() * Byte.SIZE;
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        AArch64AddressValue address = AArch64AddressValue.makeAddress(wordKind, bitMemoryTransferSize, thread, offset);
        append(new StoreOp((AArch64Kind) value.getPlatformKind(), address, asAllocatable(value), null));
    }

    @Override
    public void emitUnwind(Value exception) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionParameter = (RegisterValue) outgoingCc.getArgument(0);
        emitMove(exceptionParameter, exception);
        append(new AArch64HotSpotUnwindOp(config, exceptionParameter));
    }

    @Override
    public void emitReturn(JavaKind kind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(kind, input.getValueKind());
            emitMove(operand, input);
        }
        Register thread = getProviders().getRegisters().getThreadRegister();
        append(new AArch64HotSpotReturnOp(operand, getStub() != null, config, thread, getResult().requiresReservedStackAccessCheck()));
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

    @Override
    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key,
                    Function<Condition, ConditionFlag> converter) {
        return new AArch64HotSpotStrategySwitchOp(strategy, keyTargets, defaultTarget, key, converter);
    }

    public void setDebugInfoBuilder(HotSpotDebugInfoBuilder debugInfoBuilder) {
        this.debugInfoBuilder = debugInfoBuilder;
    }

    @Override
    public void emitZeroMemory(Value address, Value length, boolean isAligned) {
        final EnumSet<AArch64.Flag> flags = ((AArch64) target().arch).getFlags();

        boolean isDcZvaProhibited = true;
        int zvaLength = config.zvaLength;
        if (zvaLength != Integer.MAX_VALUE) {
            isDcZvaProhibited = 0 == zvaLength;
        } else {
            int dczidValue = config.psrInfoDczidValue;

            // ARMv8-A architecture reference manual D12.2.35 Data Cache Zero ID register says:
            // * BS, bits [3:0] indicate log2 of the DC ZVA block size in (4-byte) words.
            // * DZP, bit [4] of indicates whether use of DC ZVA instruction is prohibited.
            zvaLength = 4 << (dczidValue & 0xF);
            isDcZvaProhibited = ((dczidValue & 0x10) != 0);
        }

        // Use DC ZVA if it's not prohibited and AArch64 HotSpot flag UseBlockZeroing is on.
        boolean useDcZva = !isDcZvaProhibited && flags.contains(AArch64.Flag.UseBlockZeroing);

        emitZeroMemory(address, length, isAligned, useDcZva, zvaLength);
    }
}
