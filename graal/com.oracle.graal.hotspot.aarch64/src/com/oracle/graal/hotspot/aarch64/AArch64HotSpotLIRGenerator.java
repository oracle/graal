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

import static com.oracle.graal.lir.LIRValueUtil.asConstant;
import static com.oracle.graal.lir.LIRValueUtil.isConstantValue;

import java.util.function.Function;

import com.oracle.graal.asm.Label;
import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.asm.aarch64.AArch64Address.AddressingMode;
import com.oracle.graal.asm.aarch64.AArch64Assembler.ConditionFlag;
import com.oracle.graal.compiler.aarch64.AArch64ArithmeticLIRGenerator;
import com.oracle.graal.compiler.aarch64.AArch64LIRGenerator;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.HotSpotDebugInfoBuilder;
import com.oracle.graal.hotspot.HotSpotForeignCallLinkage;
import com.oracle.graal.hotspot.HotSpotLIRGenerationResult;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.HotSpotLockStack;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.meta.HotSpotRegistersProvider;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.VirtualStackSlot;
import com.oracle.graal.lir.aarch64.AArch64AddressValue;
import com.oracle.graal.lir.aarch64.AArch64Call;
import com.oracle.graal.lir.aarch64.AArch64ControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.aarch64.AArch64FrameMapBuilder;
import com.oracle.graal.lir.aarch64.AArch64Move.StoreOp;
import com.oracle.graal.lir.gen.LIRGenerationResult;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCompressedNullConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AArch64 HotSpot.
 */
public class AArch64HotSpotLIRGenerator extends AArch64LIRGenerator implements HotSpotLIRGenerator {

    final HotSpotVMConfig config;
    private HotSpotDebugInfoBuilder debugInfoBuilder;

    protected AArch64HotSpotLIRGenerator(HotSpotProviders providers, HotSpotVMConfig config, LIRGenerationResult lirGenRes) {
        this(new AArch64HotSpotLIRKindTool(), new AArch64ArithmeticLIRGenerator(), new AArch64HotSpotMoveFactory(), providers, config, lirGenRes);
    }

    protected AArch64HotSpotLIRGenerator(LIRKindTool lirKindTool, AArch64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, HotSpotProviders providers, HotSpotVMConfig config,
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

    @SuppressWarnings("unused") private LIRFrameState currentRuntimeCallInfo;

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        currentRuntimeCallInfo = info;
        if (AArch64Call.isNearCall(linkage)) {
            append(new AArch64Call.DirectNearForeignCallOp(linkage, result, arguments, temps, info, label));
        } else {
            append(new AArch64Call.DirectFarForeignCallOp(linkage, result, arguments, temps, info, label));
        }
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public SaveRegistersOp emitSaveAllRegisters() {
        throw JVMCIError.unimplemented();
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
            localX = load(localX);
        }
        if (localY instanceof HotSpotObjectConstant) {
            localY = load(localY);
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
                localA = load(localA);
            }
        }
        if (isConstantValue(b)) {
            Constant c = asConstant(b);
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(c)) {
                localB = AArch64.zr.asValue(LIRKind.value(AArch64Kind.DWORD));
            } else if (c instanceof HotSpotObjectConstant) {
                localB = load(localB);
            }
        }
        return super.emitCompare(cmpKind, localA, localB, condition, unorderedIsTrue);
    }

    @Override
    public Value emitCompress(Value pointer, HotSpotVMConfig.CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getLIRKind();
        assert inputKind.getPlatformKind() == AArch64Kind.QWORD;
        if (inputKind.isReference(0)) {
            // oop
            Variable result = newVariable(LIRKind.reference(AArch64Kind.DWORD));
            append(new AArch64HotSpotMove.CompressPointer(result, asAllocatable(pointer), getProviders().getRegisters().getHeapBaseRegister().asValue(), encoding, nonNull));
            return result;
        } else {
            // metaspace pointer
            Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
            AllocatableValue base = Value.ILLEGAL;
            if (encoding.base != 0) {
                base = emitLoadConstant(LIRKind.value(AArch64Kind.QWORD), JavaConstant.forLong(encoding.base));
            }
            append(new AArch64HotSpotMove.CompressPointer(result, asAllocatable(pointer), base, encoding, nonNull));
            return result;
        }
    }

    @Override
    public Value emitUncompress(Value pointer, HotSpotVMConfig.CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getLIRKind();
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
            if (encoding.base != 0) {
                base = emitLoadConstant(LIRKind.value(AArch64Kind.QWORD), JavaConstant.forLong(encoding.base));
            }
            append(new AArch64HotSpotMove.UncompressPointer(result, asAllocatable(pointer), base, encoding, nonNull));
            return result;
        }
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        append(new AArch64PrefetchOp(asAddressValue(address), config.allocatePrefetchInstr));
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
        Variable result;
        LIRFrameState debugInfo = null;
        if (hotspotLinkage.needsDebugInfo()) {
            debugInfo = state;
            assert debugInfo != null || getStub() != null;
        }

        if (linkage.destroysRegisters() || hotspotLinkage.needsJavaFrameAnchor()) {
            HotSpotRegistersProvider registers = getProviders().getRegisters();
            Register thread = registers.getThreadRegister();
            Variable scratch = newVariable(LIRKind.value(target().arch.getWordKind()));

            // We need a label for the return address.
            label = new Label();

            append(new AArch64HotSpotCRuntimeCallPrologueOp(config.threadLastJavaSpOffset(), config.threadLastJavaPcOffset(), config.threadLastJavaFpOffset(), thread, scratch, label));
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
            append(new AArch64HotSpotCRuntimeCallEpilogueOp(config.threadLastJavaSpOffset(), config.threadLastJavaFpOffset(), thread));

            // Clear it out so it's not being reused later.
            label = null;
        } else {
            result = super.emitForeignCall(hotspotLinkage, debugInfo, args);
        }

        return result;
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        Value actionAndReason = emitJavaConstant(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0));
        Value nullValue = emitConstant(LIRKind.reference(AArch64Kind.QWORD), JavaConstant.NULL_POINTER);
        moveDeoptValuesToThread(actionAndReason, nullValue);
        append(new AArch64HotSpotDeoptimizeCallerOp(config));
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
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        final int transferSize = value.getLIRKind().getPlatformKind().getSizeInBytes();
        final int scaledDisplacement = offset >> NumUtil.log2Ceil(transferSize);
        AArch64AddressValue address = new AArch64AddressValue(value.getLIRKind(), thread, Value.ILLEGAL, scaledDisplacement, true, AddressingMode.IMMEDIATE_SCALED);
        append(new StoreOp((AArch64Kind) value.getPlatformKind(), address, loadReg(value), null));
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
            operand = resultOperandFor(kind, input.getLIRKind());
            emitMove(operand, input);
        }
        append(new AArch64HotSpotReturnOp(operand, getStub() != null, config));
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
    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, AllocatableValue scratchValue,
                    Function<Condition, ConditionFlag> converter) {
        return new AArch64HotSpotStrategySwitchOp(strategy, keyTargets, defaultTarget, key, scratchValue, converter);
    }

    public void setDebugInfoBuilder(HotSpotDebugInfoBuilder debugInfoBuilder) {
        this.debugInfoBuilder = debugInfoBuilder;
    }

}
