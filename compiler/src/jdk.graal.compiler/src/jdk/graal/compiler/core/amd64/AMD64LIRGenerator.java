/*
 * Copyright (c) 2009, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.BYTE;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PS;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AMD64MacroAssembler.ExtendMode.ZERO_EXTEND;
import static jdk.graal.compiler.lir.LIRValueUtil.asConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.asConstantValue;
import static jdk.graal.compiler.lir.LIRValueUtil.asJavaConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isConstantValue;
import static jdk.graal.compiler.lir.LIRValueUtil.isIntConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isJavaConstant;
import static jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp.supports;
import static jdk.vm.ci.amd64.AMD64.r8;
import static jdk.vm.ci.amd64.AMD64.r9;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.r11;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;
import static jdk.vm.ci.amd64.AMD64.rdi;
import static jdk.vm.ci.amd64.AMD64.rdx;
import static jdk.vm.ci.amd64.AMD64.rsi;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.StandardOp.JumpOp;
import jdk.graal.compiler.lir.StandardOp.NewScratchRegisterOp;
import jdk.graal.compiler.lir.SwitchStrategy;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AESDecryptOp;
import jdk.graal.compiler.lir.amd64.AMD64AESEncryptOp;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.lir.amd64.AMD64ArrayCompareToOp;
import jdk.graal.compiler.lir.amd64.AMD64ArrayCopyWithConversionsOp;
import jdk.graal.compiler.lir.amd64.AMD64ArrayEqualsOp;
import jdk.graal.compiler.lir.amd64.AMD64ArrayIndexOfOp;
import jdk.graal.compiler.lir.amd64.AMD64ArrayRegionCompareToOp;
import jdk.graal.compiler.lir.amd64.AMD64Base64DecodeOp;
import jdk.graal.compiler.lir.amd64.AMD64Base64EncodeOp;
import jdk.graal.compiler.lir.amd64.AMD64ChaCha20BlockOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerMulAddOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerLeftShiftOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerMontgomeryMultiplyOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerMontgomerySquareOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerMultiplyToLenOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerRightShiftOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerSquareToLenOp;
import jdk.graal.compiler.lir.amd64.AMD64BinaryConsumer;
import jdk.graal.compiler.lir.amd64.AMD64ByteSwapOp;
import jdk.graal.compiler.lir.amd64.AMD64CacheWritebackOp;
import jdk.graal.compiler.lir.amd64.AMD64CacheWritebackPostSyncOp;
import jdk.graal.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64CipherBlockChainingAESDecryptOp;
import jdk.graal.compiler.lir.amd64.AMD64CipherBlockChainingAESEncryptOp;
import jdk.graal.compiler.lir.amd64.AMD64CodepointIndexToByteIndexOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.BranchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.CmpBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.CmpConstBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.CmpDataBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.CondMoveOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.CondSetOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.FloatCondSetOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.HashTableSwitchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.RangeTableSwitchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.TestBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.TestByteBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.TestConstBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64CountPositivesOp;
import jdk.graal.compiler.lir.amd64.AMD64CRC32CUpdateBytesOp;
import jdk.graal.compiler.lir.amd64.AMD64CRC32UpdateBytesOp;
import jdk.graal.compiler.lir.amd64.AMD64CounterModeAESCryptOp;
import jdk.graal.compiler.lir.amd64.AMD64DilithiumAlmostInverseNttOp;
import jdk.graal.compiler.lir.amd64.AMD64DilithiumAlmostNttOp;
import jdk.graal.compiler.lir.amd64.AMD64DilithiumDecomposePolyOp;
import jdk.graal.compiler.lir.amd64.AMD64DilithiumMontMulByConstantOp;
import jdk.graal.compiler.lir.amd64.AMD64DilithiumNttMultOp;
import jdk.graal.compiler.lir.amd64.AMD64ElectronicCodeBookAESDecryptOp;
import jdk.graal.compiler.lir.amd64.AMD64ElectronicCodeBookAESEncryptOp;
import jdk.graal.compiler.lir.amd64.AMD64EncodeArrayOp;
import jdk.graal.compiler.lir.amd64.AMD64GaloisCounterModeAESCryptOp;
import jdk.graal.compiler.lir.amd64.AMD64GHASHProcessBlocksOp;
import jdk.graal.compiler.lir.amd64.AMD64HaltOp;
import jdk.graal.compiler.lir.amd64.AMD64IndexOfZeroOp;
import jdk.graal.compiler.lir.amd64.AMD64Kyber12To16Op;
import jdk.graal.compiler.lir.amd64.AMD64KyberAddPoly2Op;
import jdk.graal.compiler.lir.amd64.AMD64KyberAddPoly3Op;
import jdk.graal.compiler.lir.amd64.AMD64KyberBarrettReduceOp;
import jdk.graal.compiler.lir.amd64.AMD64KyberInverseNttOp;
import jdk.graal.compiler.lir.amd64.AMD64KyberNttMultOp;
import jdk.graal.compiler.lir.amd64.AMD64KyberNttOp;
import jdk.graal.compiler.lir.amd64.AMD64LFenceOp;
import jdk.graal.compiler.lir.amd64.AMD64MD5Op;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.amd64.AMD64Move.CompareAndSwapOp;
import jdk.graal.compiler.lir.amd64.AMD64Move.MembarOp;
import jdk.graal.compiler.lir.amd64.AMD64Move.StackLeaOp;
import jdk.graal.compiler.lir.amd64.AMD64PauseOp;
import jdk.graal.compiler.lir.amd64.AMD64Poly1305ProcessBlocksOp;
import jdk.graal.compiler.lir.amd64.AMD64ReadTimestampCounterWithProcid;
import jdk.graal.compiler.lir.amd64.AMD64SHA1Op;
import jdk.graal.compiler.lir.amd64.AMD64SHA256AVX2Op;
import jdk.graal.compiler.lir.amd64.AMD64SHA256Op;
import jdk.graal.compiler.lir.amd64.AMD64SHA3Op;
import jdk.graal.compiler.lir.amd64.AMD64SHA512Op;
import jdk.graal.compiler.lir.amd64.AMD64StringLatin1InflateOp;
import jdk.graal.compiler.lir.amd64.AMD64StringUTF16CompressOp;
import jdk.graal.compiler.lir.amd64.AMD64VectorizedHashCodeOp;
import jdk.graal.compiler.lir.amd64.AMD64VectorizedMismatchOp;
import jdk.graal.compiler.lir.amd64.AMD64ZapRegistersOp;
import jdk.graal.compiler.lir.amd64.AMD64ZapStackOp;
import jdk.graal.compiler.lir.amd64.AMD64ZeroMemoryOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64OpMaskCompareOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorCompareOp;
import jdk.graal.compiler.lir.gen.BarrierSetLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGenerator;
import jdk.graal.compiler.lir.gen.MoveFactory;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.nodes.StringCodepointIndexToByteIndexNode;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public abstract class AMD64LIRGenerator extends LIRGenerator {

    public AMD64LIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, BarrierSetLIRGeneratorTool barrierSetLIRGen, MoveFactory moveFactory, Providers providers,
                    LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, barrierSetLIRGen, moveFactory, providers, lirGenRes);
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((AMD64Kind) kind) {
            case BYTE:
                return JavaConstant.forByte((byte) dead);
            case WORD:
                return JavaConstant.forShort((short) dead);
            case DWORD:
                return JavaConstant.forInt((int) dead);
            case QWORD:
                return JavaConstant.forLong(dead);
            case SINGLE:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) dead));
            case MASK16:
            case MASK64:
                return JavaConstant.forLong(dead);
            default:
                assert ((AMD64Kind) kind).isXMM() : "kind " + kind + " not supported in zapping";
                // we don't support vector types, so just zap with double for all of them
                return JavaConstant.forDouble(Double.longBitsToDouble(dead));
        }
    }

    public AMD64AddressValue asAddressValue(Value address) {
        if (address instanceof AMD64AddressValue) {
            return (AMD64AddressValue) address;
        } else {
            if (address instanceof JavaConstant) {
                long displacement = ((JavaConstant) address).asLong();
                if (NumUtil.isInt(displacement)) {
                    return new AMD64AddressValue(address.getValueKind(), Value.ILLEGAL, (int) displacement);
                }
            }
            return new AMD64AddressValue(address.getValueKind(), asAllocatable(address), 0);
        }
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        Variable result = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new StackLeaOp(result, stackslot));
        return result;
    }

    /**
     * The AMD64 backend only uses DWORD and QWORD values in registers because of a performance
     * penalty when accessing WORD or BYTE registers. This function converts small integer kinds to
     * DWORD.
     */
    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        switch ((AMD64Kind) kind.getPlatformKind()) {
            case BYTE:
            case WORD:
                return kind.changeType(AMD64Kind.DWORD);
            default:
                return kind;
        }
    }

    protected Value loadNonInlinableConstant(Value value) {
        if (isConstantValue(value) && !getMoveFactory().canInlineConstant(asConstant(value))) {
            return emitMove(value);
        }
        return value;
    }

    private AllocatableValue asAllocatable(Value value, ValueKind<?> kind) {
        if (value.getValueKind().equals(kind)) {
            return asAllocatable(value);
        } else if (isRegister(value)) {
            return asRegister(value).asValue(kind);
        } else if (isConstantValue(value)) {
            return emitLoadConstant(kind, asConstant(value));
        } else {
            Variable variable = newVariable(kind);
            emitMove(variable, value);
            return variable;
        }
    }

    private Value emitCompareAndSwapHelper(boolean isLogic, LIRKind accessKind, Value address, Value expectedValue, Value newValue, BarrierType barrierType) {
        ValueKind<?> kind = newValue.getValueKind();
        GraalError.guarantee(kind.equals(expectedValue.getValueKind()), "%s != %s", kind, expectedValue.getValueKind());

        AMD64AddressValue addressValue = asAddressValue(address);
        LIRKind integerAccessKind = accessKind;
        Value reinterpretedExpectedValue = expectedValue;
        Value reinterpretedNewValue = newValue;
        boolean isXmm = ((AMD64Kind) accessKind.getPlatformKind()).isXMM();
        if (isXmm) {
            if (accessKind.getPlatformKind().equals(AMD64Kind.SINGLE)) {
                integerAccessKind = LIRKind.fromJavaKind(target().arch, JavaKind.Int);
            } else {
                integerAccessKind = LIRKind.fromJavaKind(target().arch, JavaKind.Long);
            }
            reinterpretedExpectedValue = arithmeticLIRGen.emitReinterpret(integerAccessKind, expectedValue);
            reinterpretedNewValue = arithmeticLIRGen.emitReinterpret(integerAccessKind, newValue);
        }
        AMD64Kind memKind = (AMD64Kind) integerAccessKind.getPlatformKind();
        RegisterValue aRes = rax.asValue(integerAccessKind);
        AllocatableValue allocatableNewValue = asAllocatable(reinterpretedNewValue, integerAccessKind);
        emitMove(aRes, reinterpretedExpectedValue);
        emitCompareAndSwapOp(isLogic, integerAccessKind, memKind, aRes, addressValue, allocatableNewValue, barrierType);
        return aRes;
    }

    private Value emitCompareAndSwap(boolean isLogic, LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, BarrierType barrierType) {
        Value aRes = emitCompareAndSwapHelper(isLogic, accessKind, address, expectedValue, newValue, barrierType);

        if (isLogic) {
            assert trueValue.getValueKind().equals(falseValue.getValueKind());
            return emitCondMoveOp(Condition.EQ, trueValue, falseValue, false, false);
        } else {
            if (((AMD64Kind) accessKind.getPlatformKind()).isXMM()) {
                return arithmeticLIRGen.emitReinterpret(accessKind, aRes);
            } else {
                Variable result = newVariable(newValue.getValueKind());
                emitMove(result, aRes);
                return result;
            }
        }
    }

    private AllocatableValue emitConvertNullToZero(Value value) {
        AllocatableValue result = newVariable(value.getValueKind());
        emitConvertNullToZero(result, value);
        return result;
    }

    @Override
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder,
                    BarrierType barrierType) {
        return LIRValueUtil.asVariable(emitCompareAndSwap(true, accessKind, address, expectedValue, newValue, trueValue, falseValue, barrierType));
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder, BarrierType barrierType) {
        return emitCompareAndSwap(false, accessKind, address, expectedValue, newValue, null, null, barrierType);
    }

    public void emitCompareAndSwapBranch(boolean isLogic, LIRKind kind, AMD64AddressValue address, Value expectedValue, Value newValue, Condition condition, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability, BarrierType barrierType) {
        GraalError.guarantee(kind.getPlatformKind().getSizeInBytes() <= expectedValue.getValueKind().getPlatformKind().getSizeInBytes(), "kind=%s, expectedValue=%s", kind, expectedValue);
        GraalError.guarantee(kind.getPlatformKind().getSizeInBytes() <= newValue.getValueKind().getPlatformKind().getSizeInBytes(), "kind=%s, newValue=%s", kind, newValue);
        GraalError.guarantee(condition == Condition.EQ || condition == Condition.NE, Assertions.errorMessage(condition, address, expectedValue, newValue));

        emitCompareAndSwapHelper(isLogic, kind, address, expectedValue, newValue, barrierType);
        append(new BranchOp(condition, trueLabel, falseLabel, trueLabelProbability));
    }

    protected void emitCompareAndSwapOp(boolean isLogic, LIRKind accessKind, AMD64Kind memKind, RegisterValue raxValue, AMD64AddressValue address, AllocatableValue newValue, BarrierType barrierType) {
        if (barrierType != BarrierType.NONE && getBarrierSet() instanceof AMD64ReadBarrierSetLIRGenerator readBarrierSet) {
            readBarrierSet.emitCompareAndSwapOp(this, isLogic, accessKind, memKind, raxValue, address, newValue, barrierType);
        } else {
            append(new CompareAndSwapOp(memKind, raxValue, address, raxValue, newValue));
        }
    }

    @Override
    public Value emitAtomicReadAndAdd(LIRKind accessKind, Value address, Value delta) {
        Variable result = newVariable(toRegisterKind(accessKind));
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndAddOp((AMD64Kind) accessKind.getPlatformKind(), result, addressValue, asAllocatable(delta)));
        return result;
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRKind accessKind, Value address, Value newValue, BarrierType barrierType) {
        if (barrierType != BarrierType.NONE && getBarrierSet() instanceof AMD64ReadBarrierSetLIRGenerator readBarrierSet) {
            return readBarrierSet.emitAtomicReadAndWrite(this, accessKind, address, newValue, barrierType);
        }
        Variable result = newVariable(toRegisterKind(accessKind));
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndWriteOp((AMD64Kind) accessKind.getPlatformKind(), result, addressValue, asAllocatable(newValue)));
        return result;
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        append(new AMD64Move.NullCheckOp(asAddressValue(address), state));
    }

    @Override
    public void emitJump(LabelRef label) {
        assert label != null;
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
        if (cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE) {
            boolean isSelfEqualsCheck = cond == Condition.EQ && !unorderedIsTrue && left.equals(right);
            Condition finalCond = emitFloatCompare(null, cmpKind, left, right, cond, unorderedIsTrue);
            append(new FloatBranchOp(finalCond, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability, isSelfEqualsCheck));
            return;
        }

        if (LIRValueUtil.isVariable(right)) {
            emitRawCompareBranch(OperandSize.get(cmpKind), asAllocatable(right), loadNonInlinableConstant(left), cond.mirror(), trueLabel, falseLabel, trueLabelProbability);
        } else {
            emitRawCompareBranch(OperandSize.get(cmpKind), asAllocatable(left), loadNonInlinableConstant(right), cond, trueLabel, falseLabel, trueLabelProbability);
        }
    }

    private void emitRawCompareBranch(OperandSize size, AllocatableValue left, Value right, Condition cond, LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
        if (isConstantValue(right)) {
            Constant c = LIRValueUtil.asConstant(right);
            if (JavaConstant.isNull(c)) {
                AMD64ArithmeticLIRGenerator arithmeticLIRGenerator = (AMD64ArithmeticLIRGenerator) arithmeticLIRGen;
                if (arithmeticLIRGenerator.mustReplaceNullWithNullRegister(c)) {
                    append(new CmpBranchOp(size, left, arithmeticLIRGenerator.getNullRegisterValue(), null, cond, trueLabel, falseLabel, trueLabelProbability));
                } else {
                    append(new TestBranchOp(size, left, left, null, cond, trueLabel, falseLabel, trueLabelProbability));
                }
                return;
            } else if (c instanceof VMConstant) {
                VMConstant vc = (VMConstant) c;
                if (size == DWORD && target().inlineObjects) {
                    append(new CmpConstBranchOp(DWORD, left, vc, null, cond, trueLabel, falseLabel, trueLabelProbability));
                } else {
                    append(new CmpDataBranchOp(size, left, vc, cond, trueLabel, falseLabel, trueLabelProbability));
                }
                return;
            } else if (c instanceof JavaConstant) {
                JavaConstant jc = (JavaConstant) c;
                if (jc.isDefaultForKind()) {
                    if (size == BYTE) {
                        append(new TestByteBranchOp(left, left, cond, trueLabel, falseLabel, trueLabelProbability));
                    } else {
                        append(new TestBranchOp(size, left, left, null, cond, trueLabel, falseLabel, trueLabelProbability));
                    }
                    return;
                } else if (NumUtil.is32bit(jc.asLong())) {
                    append(new CmpConstBranchOp(size, left, (int) jc.asLong(), null, cond, trueLabel, falseLabel, trueLabelProbability));
                    return;
                }
            }
        }

        // fallback: load, then compare
        append(new CmpBranchOp(size, left, asAllocatable(right), null, cond, trueLabel, falseLabel, trueLabelProbability));
    }

    public void emitCompareBranchMemory(AMD64Kind cmpKind, Value left, AMD64AddressValue right, LIRFrameState state, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        if (cmpKind.isXMM()) {
            GraalError.guarantee(cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE, "Must be float");
            Condition finalCond = emitFloatCompare(state, cmpKind, left, right, cond, unorderedIsTrue);
            append(new FloatBranchOp(finalCond, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
        } else {
            OperandSize size = OperandSize.get(cmpKind);
            if (isConstantValue(left)) {
                ConstantValue a = asConstantValue(left);
                if (JavaConstant.isNull(a.getConstant())) {
                    append(new CmpConstBranchOp(size, right, 0, state, cond.mirror(), trueLabel, falseLabel, trueLabelProbability));
                    return;
                } else if (a.getConstant() instanceof VMConstant && size == DWORD && target().inlineObjects) {
                    VMConstant vc = (VMConstant) a.getConstant();
                    append(new CmpConstBranchOp(size, right, vc, state, cond.mirror(), trueLabel, falseLabel, trueLabelProbability));
                    return;
                } else if (a.getConstant() instanceof JavaConstant && a.getJavaConstant().getJavaKind() != JavaKind.Object) {
                    long value = a.getJavaConstant().asLong();
                    if (NumUtil.is32bit(value)) {
                        append(new CmpConstBranchOp(size, right, (int) value, state, cond.mirror(), trueLabel, falseLabel, trueLabelProbability));
                        return;
                    }
                }
            }
            append(new CmpBranchOp(size, asAllocatable(left), right, state, cond, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpLIRKind, double overflowProbability) {
        append(new BranchOp(ConditionFlag.Overflow, overflow, noOverflow, overflowProbability));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        if (left.getPlatformKind().getVectorLength() > 1) {
            append(new AMD64OpMaskCompareOp(VexRMOp.VPTEST, getRegisterSize(left), asAllocatable(left), asAllocatable(right)));
            append(new BranchOp(Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
        } else {
            assert ((AMD64Kind) left.getPlatformKind()).isInteger();
            OperandSize size = left.getPlatformKind() == AMD64Kind.QWORD ? QWORD : DWORD;
            if (isJavaConstant(left) || isJavaConstant(right)) {
                Value x = isJavaConstant(right) ? left : right;
                Value y = isJavaConstant(right) ? right : left;
                long con = asJavaConstant(y).asLong();
                if (NumUtil.isUByte(con)) {
                    size = BYTE;
                } else if (NumUtil.isUInt(con)) {
                    size = DWORD;
                }
                if (size != QWORD || NumUtil.isInt(con)) {
                    append(new TestConstBranchOp(size, asAllocatable(x), (int) con, null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
                    return;
                }
            }

            if (isAllocatableValue(right)) {
                append(new TestBranchOp(size, asAllocatable(right), asAllocatable(left), null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
            } else {
                append(new TestBranchOp(size, asAllocatable(left), asAllocatable(right), null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
            }
        }
    }

    @Override
    public void emitOpMaskTestBranch(Value left, boolean negateLeft, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        emitOpMaskTest(left, right);
        // we can implicitly invert the left value by branching on BT instead of EQ
        append(new BranchOp(negateLeft ? Condition.BT : Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
    }

    @Override
    public void emitOpMaskOrTestBranch(Value left, Value right, boolean allZeros, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {
        emitOpMaskOrTest(left, right);
        // if (left | right) == 0, the ZF is set
        // if (left | right) == -1, the CF is set
        // allZeros selects the flag we branch on by selecting either EQ(ZF) or BT(CF)
        append(new BranchOp(allZeros ? Condition.EQ : Condition.BT, trueDestination, falseDestination, trueSuccessorProbability));
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        if (cmpKind != AMD64Kind.SINGLE && cmpKind != AMD64Kind.DOUBLE) {
            Condition finalCondition = emitIntegerCompare(cmpKind, left, right, cond);
            return emitCondMoveOp(finalCondition, trueValue, falseValue, false, false, false);
        }

        Condition finalCond = emitFloatCompare(null, cmpKind, left, right, cond, unorderedIsTrue);
        boolean finalUnordered = unorderedIsTrue;
        Value finalTrueValue = trueValue;
        Value finalFalseValue = falseValue;
        boolean isSelfEqualsCheck = finalCond == Condition.EQ && left.equals(right);
        if (!isSelfEqualsCheck && !finalUnordered && finalCond == Condition.EQ) {
            /*
             * @formatter:off
             *
             * 1. x NE_U y ? a : b can be emitted as:
             *
             * ucomisd x, y
             * cmovp b, a
             * cmovne b, a
             *
             * 2. x EQ_O y ? a : b can be negated into x NE_U y ? b : a
             *
             * 3. x EQ_U y ? a : b and x NE_O y ? a : b can be done without querying the parity flag
             *
             * @formatter:on
             */
            finalCond = Condition.NE;
            finalUnordered = true;
            finalTrueValue = falseValue;
            finalFalseValue = trueValue;
        }

        return emitCondMoveOp(finalCond, finalTrueValue, finalFalseValue, true, finalUnordered, isSelfEqualsCheck);
    }

    private Variable emitCondMoveOp(Condition condition, Value trueValue, Value falseValue, boolean isFloatComparison, boolean unorderedIsTrue) {
        return emitCondMoveOp(condition, trueValue, falseValue, isFloatComparison, unorderedIsTrue, false);
    }

    private Variable emitCondMoveOp(Condition condition, Value trueValue, Value falseValue, boolean isFloatComparison, boolean unorderedIsTrue, boolean isSelfEqualsCheck) {
        boolean isParityCheckNecessary = isFloatComparison && unorderedIsTrue != AMD64ControlFlow.trueOnUnordered(condition);
        Variable result = newVariable(LIRKind.mergeReferenceInformation(trueValue, falseValue));
        if (!isParityCheckNecessary && isIntConstant(trueValue, 1) && isIntConstant(falseValue, 0)) {
            if (isFloatComparison) {
                append(new FloatCondSetOp(result, condition));
            } else {
                append(new CondSetOp(result, condition));
            }
        } else if (!isParityCheckNecessary && isIntConstant(trueValue, 0) && isIntConstant(falseValue, 1)) {
            if (isFloatComparison) {
                append(new FloatCondSetOp(result, condition.negate()));
            } else {
                append(new CondSetOp(result, condition.negate()));
            }
        } else if (isFloatComparison) {
            append(new FloatCondMoveOp(result, condition, unorderedIsTrue, asAllocatable(trueValue), asAllocatable(falseValue), isSelfEqualsCheck));
        } else {
            append(new CondMoveOp(result, condition, asAllocatable(trueValue), loadNonInlinableConstant(falseValue)));
        }
        return result;
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        return emitCondMoveOp(Condition.EQ, asAllocatable(trueValue), loadNonInlinableConstant(falseValue), false, false);
    }

    protected static AVXSize getRegisterSize(Value a) {
        AMD64Kind kind = (AMD64Kind) a.getPlatformKind();
        if (kind.isXMM()) {
            return AVXKind.getRegisterSize(kind);
        } else {
            return AVXSize.XMM;
        }
    }

    private void emitIntegerTest(Value a, Value b) {
        if (a.getPlatformKind().getVectorLength() > 1) {
            append(new AMD64VectorCompareOp(VexRMOp.VPTEST, getRegisterSize(a), asAllocatable(a), asAllocatable(b)));
        } else {
            assert ((AMD64Kind) a.getPlatformKind()).isInteger();
            OperandSize size = a.getPlatformKind() == AMD64Kind.QWORD ? QWORD : DWORD;
            if (isJavaConstant(a) || isJavaConstant(b)) {
                Value x = isJavaConstant(b) ? a : b;
                Value y = isJavaConstant(b) ? b : a;
                long con = asJavaConstant(y).asLong();
                if (NumUtil.isUByte(con)) {
                    size = BYTE;
                } else if (NumUtil.isUInt(con)) {
                    size = DWORD;
                }
                if (size != QWORD || NumUtil.isInt(con)) {
                    AMD64MIOp op = size == BYTE ? AMD64MIOp.TESTB : AMD64MIOp.TEST;
                    append(new AMD64BinaryConsumer.ConstOp(op, size, asAllocatable(x), (int) con));
                    return;
                }
            }

            if (isAllocatableValue(b)) {
                append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(b), asAllocatable(a)));
            } else {
                append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(a), asAllocatable(b)));
            }
        }
    }

    @Override
    public Variable emitOpMaskTestMove(Value left, boolean negateLeft, Value right, Value trueValue, Value falseValue) {
        emitOpMaskTest(left, right);
        // we can implicitly invert the left value by moving on BT instead of EQ
        return emitCondMoveOp(negateLeft ? Condition.BT : Condition.EQ, asAllocatable(trueValue), asAllocatable(falseValue), false, false);
    }

    @Override
    public Variable emitOpMaskOrTestMove(Value left, Value right, boolean allZeros, Value trueValue, Value falseValue) {
        emitOpMaskOrTest(left, right);
        // if (left | right) == 0, the ZF is set
        // if (left | right) == -1, the CF is set
        // allZeros selects the flag we preform the move on by selecting either EQ(ZF) or BT(CF)
        return emitCondMoveOp(allZeros ? Condition.EQ : Condition.BT, asAllocatable(trueValue), asAllocatable(falseValue), false, false);
    }

    private void emitOpMaskTest(Value a, Value b) {
        GraalError.guarantee(AMD64Assembler.supportsFullAVX512(((AMD64) target().arch).getFeatures()), "AVX512 needed for opmask operations");
        AMD64Kind aKind = (AMD64Kind) a.getPlatformKind();
        AMD64Kind bKind = (AMD64Kind) b.getPlatformKind();
        GraalError.guarantee(aKind.isMask() && bKind.isMask(), "opmask test needs inputs to be opmasks");
        GraalError.guarantee(aKind == bKind, "input masks need to be of the same size");

        VexRROp op = switch ((AMD64Kind) a.getPlatformKind()) {
            case MASK8 -> VexRROp.KTESTB;
            case MASK16 -> VexRROp.KTESTW;
            case MASK32 -> VexRROp.KTESTD;
            case MASK64 -> VexRROp.KTESTQ;
            default -> throw GraalError.shouldNotReachHere("emitting opmask test for unknown mask kind " + a.getPlatformKind().name());
        };
        append(new AMD64OpMaskCompareOp(op, getRegisterSize(a), asAllocatable(a), asAllocatable(b)));
    }

    private void emitOpMaskOrTest(Value a, Value b) {
        GraalError.guarantee(AMD64Assembler.supportsFullAVX512(((AMD64) target().arch).getFeatures()), "AVX512 needed for opmask operations");
        AMD64Kind aKind = (AMD64Kind) a.getPlatformKind();
        AMD64Kind bKind = (AMD64Kind) b.getPlatformKind();
        GraalError.guarantee(aKind.isMask() && bKind.isMask(), "opmask ortest needs inputs to be opmasks");
        GraalError.guarantee(aKind == bKind, "input masks need to be of the same size");

        AMD64Assembler.VexRROp op = switch ((AMD64Kind) a.getPlatformKind()) {
            case MASK8 -> VexRROp.KORTESTB;
            case MASK16 -> VexRROp.KORTESTW;
            case MASK32 -> VexRROp.KORTESTD;
            case MASK64 -> VexRROp.KORTESTQ;
            default -> throw GraalError.shouldNotReachHere("emitting opmask test for unknown mask kind " + a.getPlatformKind().name());
        };
        append(new AMD64OpMaskCompareOp(op, getRegisterSize(a), asAllocatable(a), asAllocatable(b)));
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @param cond the condition of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private Condition emitIntegerCompare(PlatformKind cmpKind, Value a, Value b, Condition cond) {
        GraalError.guarantee(cmpKind != AMD64Kind.SINGLE && cmpKind != AMD64Kind.DOUBLE, "Must not be float");
        if (LIRValueUtil.isVariable(b)) {
            emitRawCompare(cmpKind, b, a);
            return cond.mirror();
        } else {
            emitRawCompare(cmpKind, a, b);
            return cond;
        }
    }

    private void emitRawCompare(PlatformKind cmpKind, Value left, Value right) {
        ((AMD64ArithmeticLIRGeneratorTool) arithmeticLIRGen).emitCompareOp((AMD64Kind) cmpKind, asAllocatable(left), loadNonInlinableConstant(right));
    }

    private Condition emitFloatCompare(LIRFrameState state, PlatformKind kind, Value left, Value right, Condition cond, boolean unordered) {
        GraalError.guarantee(kind == AMD64Kind.SINGLE || kind == AMD64Kind.DOUBLE, "Must be float");
        boolean commute;
        if (cond == Condition.EQ || cond == Condition.NE) {
            commute = LIRValueUtil.isVariable(right);
        } else {
            // If the condition is LT_O, LE_O, GT_U, GE_U, commute the inputs to avoid having to
            // query the parity flag
            commute = unordered != AMD64ControlFlow.trueOnUnordered(cond);
        }

        Value x = left;
        Value y = right;
        Condition c = cond;
        if (commute) {
            x = right;
            y = left;
            c = c.mirror();
        }

        OperandSize opSize = kind == AMD64Kind.SINGLE ? PS : PD;
        boolean useAVX = ((AMD64ArithmeticLIRGenerator) getArithmetic()).supportAVX();
        var avxEncoding = ((AMD64ArithmeticLIRGenerator) getArithmetic()).simdEncoding;
        if (y instanceof AMD64AddressValue addr) {
            if (useAVX) {
                VexRMOp op = (kind == AMD64Kind.SINGLE ? VexRMOp.VUCOMISS : VexRMOp.VUCOMISD).encoding(avxEncoding);
                append(new AMD64BinaryConsumer.MemoryAvxOp(op, AVXSize.XMM, asAllocatable(x), addr, state));
            } else {
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, opSize, asAllocatable(x), addr, state));
            }
        } else {
            if (x instanceof AMD64AddressValue) {
                x = arithmeticLIRGen.emitLoad(LIRKind.value(kind), x, state, MemoryOrderMode.PLAIN, MemoryExtendKind.DEFAULT);
            }
            if (useAVX) {
                VexRMOp op = (kind == AMD64Kind.SINGLE ? VexRMOp.VUCOMISS : VexRMOp.VUCOMISD).encoding(avxEncoding);
                append(new AMD64BinaryConsumer.AvxOp(op, AVXSize.XMM, asAllocatable(x), asAllocatable(y)));
            } else {
                append(new AMD64BinaryConsumer.Op(SSEOp.UCOMIS, opSize, asAllocatable(x), asAllocatable(y)));
            }
        }
        return c;
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset(getCodeCache());
        if (maxOffset != (int) maxOffset) {
            append(new AMD64Call.DirectFarForeignCallOp(linkage, result, arguments, temps, linkage.getAdditionalReturns(), info));
        } else {
            append(new AMD64Call.DirectNearForeignCallOp(linkage, result, arguments, temps, linkage.getAdditionalReturns(), info));
        }
    }

    @Override
    public Variable emitReverseBytes(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64ByteSwapOp(result, asAllocatable(input)));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value lengthA,
                    Value arrayB, Value lengthB) {
        LIRKind resultKind = LIRKind.value(AMD64Kind.DWORD);
        // AMD64ArrayCompareToOp emits pcmpestri, which implicitly uses rcx, rdx, and rax.
        // Pre-allocate the lengths and result into those fixed registers so the op can reuse them.
        RegisterValue raxRes = rax.asValue(resultKind);
        RegisterValue cntA = rcx.asValue(lengthA.getValueKind());
        RegisterValue cntB = rdx.asValue(lengthB.getValueKind());
        emitMove(cntA, lengthA);
        emitMove(cntB, lengthB);
        append(new AMD64ArrayCompareToOp(this, getAVX3Threshold(), strideA, strideB, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        raxRes, arrayA, cntA, arrayB, cntB));
        Variable result = newVariable(resultKind);
        emitMove(result, raxRes);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayRegionCompareTo(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64ArrayRegionCompareToOp(this, null, null, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB),
                        asAllocatable(length), dynamicStrides == null ? Value.ILLEGAL : asAllocatable(dynamicStrides), ZERO_EXTEND));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayRegionCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64ArrayRegionCompareToOp(this, strideA, strideB, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB),
                        asAllocatable(length), Value.ILLEGAL, ZERO_EXTEND));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitVectorizedMismatch(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value arrayB, Value length, Value stride) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        // Only stride stays fixed because shlq(length) takes its shift count from cl/rcx. The
        // array pointers and length remain allocatable @UseKill operands.
        RegisterValue regStride = rcx.asValue(stride.getValueKind());
        emitMove(regStride, stride);
        append(new AMD64VectorizedMismatchOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, result, asAllocatable(arrayA), asAllocatable(arrayB), asAllocatable(length), regStride));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitVectorizedHashCode(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayStart, Value length, Value initialValue, JavaKind arrayKind) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64VectorizedHashCodeOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, result, asAllocatable(arrayStart), asAllocatable(length), asAllocatable(initialValue), arrayKind));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayEquals(JavaKind commonElementKind,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA,
                    Value arrayB, Value offsetB,
                    Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        Stride stride = Stride.fromJavaKind(commonElementKind);
        int constLength = isJavaConstant(length) ? asJavaConstant(length).asInt() : -1;
        append(new AMD64ArrayEqualsOp(this, commonElementKind, stride, stride, stride, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB),
                        Value.ILLEGAL, asAllocatable(length), Value.ILLEGAL, ZERO_EXTEND, constLength));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayEquals(
                    Stride strideA, Stride strideB,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA,
                    Value arrayB, Value offsetB,
                    Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        int constLength = isJavaConstant(length) ? asJavaConstant(length).asInt() : -1;
        append(new AMD64ArrayEqualsOp(this, JavaKind.Byte, strideA, strideB, strideB, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB),
                        Value.ILLEGAL, asAllocatable(length), Value.ILLEGAL, ZERO_EXTEND, constLength));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayEqualsDynamicStrides(
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA,
                    Value arrayB, Value offsetB,
                    Value length,
                    Value dynamicStrides) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        int constLength = isJavaConstant(length) ? asJavaConstant(length).asInt() : -1;
        append(new AMD64ArrayEqualsOp(this, JavaKind.Byte, null, null, null, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB),
                        Value.ILLEGAL, asAllocatable(length), asAllocatable(dynamicStrides), ZERO_EXTEND, constLength));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayEqualsWithMask(
                    Stride strideA,
                    Stride strideB,
                    Stride strideMask,
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA,
                    Value arrayB, Value offsetB,
                    Value mask,
                    Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        int constLength = isJavaConstant(length) ? asJavaConstant(length).asInt() : -1;
        append(new AMD64ArrayEqualsOp(this, JavaKind.Byte, strideA, strideB, strideMask, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB),
                        asAllocatable(mask), asAllocatable(length), Value.ILLEGAL, ZERO_EXTEND, constLength));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayEqualsWithMaskDynamicStrides(
                    EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA,
                    Value arrayB, Value offsetB,
                    Value mask,
                    Value length,
                    Value dynamicStrides) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        int constLength = isJavaConstant(length) ? asJavaConstant(length).asInt() : -1;
        append(new AMD64ArrayEqualsOp(this, JavaKind.Byte, null, null, null, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB),
                        asAllocatable(mask), asAllocatable(length), asAllocatable(dynamicStrides), ZERO_EXTEND, constLength));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitArrayCopyWithConversion(Stride strideSrc, Stride strideDst, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length) {
        append(new AMD64ArrayCopyWithConversionsOp(this, strideSrc, strideDst, false, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        emitConvertNullToZero(arraySrc), asAllocatable(offsetSrc),
                        emitConvertNullToZero(arrayDst), asAllocatable(offsetDst),
                        asAllocatable(length), Value.ILLEGAL, AMD64MacroAssembler.ExtendMode.ZERO_EXTEND));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitArrayCopyWithConversion(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, Value dynamicStrides) {
        append(new AMD64ArrayCopyWithConversionsOp(this, null, null, false, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        emitConvertNullToZero(arraySrc), asAllocatable(offsetSrc),
                        emitConvertNullToZero(arrayDst), asAllocatable(offsetDst),
                        asAllocatable(length), isIllegal(dynamicStrides) ? Value.ILLEGAL : asAllocatable(dynamicStrides), AMD64MacroAssembler.ExtendMode.ZERO_EXTEND));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitArrayCopyWithReverseBytes(Stride stride, EnumSet<?> runtimeCheckedCPUFeatures, Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length) {
        append(new AMD64ArrayCopyWithConversionsOp(this, stride, stride, true, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        emitConvertNullToZero(arraySrc), asAllocatable(offsetSrc),
                        emitConvertNullToZero(arrayDst), asAllocatable(offsetDst),
                        asAllocatable(length), Value.ILLEGAL, AMD64MacroAssembler.ExtendMode.ZERO_EXTEND));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitCalcStringAttributes(CalcStringAttributesEncoding encoding, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value array, Value offset, Value length, boolean assumeValid) {
        Variable result = newVariable(LIRKind.value(encoding == CalcStringAttributesEncoding.UTF_8 || encoding.isUTF16() ? AMD64Kind.QWORD : AMD64Kind.DWORD));
        // AMD64CalcStringAttributesOp reuses offset as lengthTail, and the ordered 4-7 byte tail
        // load consumes that value via the implicit rcx shift count.
        RegisterValue regOffset = AMD64.rcx.asValue(offset.getValueKind());
        emitMove(regOffset, offset);
        append(new AMD64CalcStringAttributesOp(this, encoding, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        emitConvertNullToZero(array), regOffset, asAllocatable(length), result, assumeValid));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitEncodeArray(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value length, CharsetName charset) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64EncodeArrayOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, result, asAllocatable(src), asAllocatable(dst), asAllocatable(length), charset));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitCountPositives(EnumSet<?> runtimeCheckedCPUFeatures, Value array, Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64CountPositivesOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, getAVX3Threshold(), result, asAllocatable(array), asAllocatable(length)));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitBase64EncodeBlock(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value sp, Value sl, Value dst, Value dp, Value flags) {
        AllocatableValue rSrc = rdi.asValue(src.getValueKind());
        AllocatableValue rSp = rsi.asValue(sp.getValueKind());
        AllocatableValue rSl = rdx.asValue(sl.getValueKind());
        AllocatableValue rDst = rcx.asValue(dst.getValueKind());
        AllocatableValue rDp = r8.asValue(dp.getValueKind());
        AllocatableValue rFlags = r9.asValue(flags.getValueKind());
        emitMove(rSrc, src);
        emitMove(rSp, sp);
        emitMove(rSl, sl);
        emitMove(rDst, dst);
        emitMove(rDp, dp);
        emitMove(rFlags, flags);
        append(new AMD64Base64EncodeOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, rSrc, rSp, rSl, rDst, rDp, rFlags));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitBase64DecodeBlock(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value sp, Value sl, Value dst, Value dp, Value isURLFlag, Value isMimeFlag) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rSrc = rdi.asValue(src.getValueKind());
        AllocatableValue rSp = rsi.asValue(sp.getValueKind());
        AllocatableValue rSl = rdx.asValue(sl.getValueKind());
        AllocatableValue rDst = rcx.asValue(dst.getValueKind());
        AllocatableValue rDp = r8.asValue(dp.getValueKind());
        AllocatableValue rUrl = r9.asValue(isURLFlag.getValueKind());
        AllocatableValue rMime = rbx.asValue(isMimeFlag.getValueKind());
        emitMove(rSrc, src);
        emitMove(rSp, sp);
        emitMove(rSl, sl);
        emitMove(rDst, dst);
        emitMove(rDp, dp);
        emitMove(rUrl, isURLFlag);
        emitMove(rMime, isMimeFlag);
        append(new AMD64Base64DecodeOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, rResult, rSrc, rSp, rSl, rDst, rDp, rUrl, rMime));

        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(result, rResult);
        return result;
    }

    @Override
    public void emitAESEncrypt(Value from, Value to, Value key) {
        append(new AMD64AESEncryptOp(this, asAllocatable(from), asAllocatable(to), asAllocatable(key), getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
    }

    @Override
    public void emitAESDecrypt(Value from, Value to, Value key) {
        append(new AMD64AESDecryptOp(this, asAllocatable(from), asAllocatable(to), asAllocatable(key), getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
    }

    @Override
    public Variable emitCTRAESCrypt(Value inAddr, Value outAddr, Value kAddr, Value counterAddr, Value len, Value encryptedCounterAddr, Value usedPtr) {
        Variable result = newVariable(len.getValueKind());
        append(new AMD64CounterModeAESCryptOp(asAllocatable(inAddr),
                        asAllocatable(outAddr),
                        asAllocatable(kAddr),
                        asAllocatable(counterAddr),
                        asAllocatable(len),
                        asAllocatable(encryptedCounterAddr),
                        asAllocatable(usedPtr),
                        result,
                        getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitGaloisCounterModeAESCrypt(EnumSet<?> runtimeCheckedCPUFeatures, Value inAddr, Value len, Value ctAddr, Value outAddr, Value kAddr, Value stateAddr, Value subkeyHtblAddr,
                    Value counterAddr) {
        AllocatableValue rIn = rdi.asValue(inAddr.getValueKind());
        AllocatableValue rLen = rsi.asValue(len.getValueKind());
        AllocatableValue rCt = rdx.asValue(ctAddr.getValueKind());
        AllocatableValue rOut = rcx.asValue(outAddr.getValueKind());
        AllocatableValue rKey = r8.asValue(kAddr.getValueKind());
        AllocatableValue rState = r9.asValue(stateAddr.getValueKind());
        boolean useAVX512 = supports(target(), (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, CPUFeature.AES, CPUFeature.CLMUL, CPUFeature.AVX, CPUFeature.AVX2,
                        CPUFeature.AVX512F, CPUFeature.AVX512DQ, CPUFeature.AVX512BW, CPUFeature.AVX512VL, CPUFeature.AVX512_VAES, CPUFeature.AVX512_VPCLMULQDQ);
        AllocatableValue rSubkeyHtbl = (useAVX512 ? r10 : r11).asValue(subkeyHtblAddr.getValueKind());
        AllocatableValue rCounter = (useAVX512 ? r11 : rax).asValue(counterAddr.getValueKind());
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        emitMove(rIn, inAddr);
        emitMove(rLen, len);
        emitMove(rCt, ctAddr);
        emitMove(rOut, outAddr);
        emitMove(rKey, kAddr);
        emitMove(rState, stateAddr);
        emitMove(rSubkeyHtbl, subkeyHtblAddr);
        emitMove(rCounter, counterAddr);
        append(new AMD64GaloisCounterModeAESCryptOp(this,
                        (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        rIn,
                        rLen,
                        rCt,
                        rOut,
                        rKey,
                        rState,
                        rSubkeyHtbl,
                        rCounter,
                        rResult,
                        getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
        Variable result = newVariable(len.getValueKind());
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitCBCAESEncrypt(Value inAddr, Value outAddr, Value kAddr, Value rAddr, Value len) {
        Variable result = newVariable(len.getValueKind());
        append(new AMD64CipherBlockChainingAESEncryptOp(asAllocatable(inAddr),
                        asAllocatable(outAddr),
                        asAllocatable(kAddr),
                        asAllocatable(rAddr),
                        asAllocatable(len),
                        result,
                        getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
        return result;
    }

    @Override
    public Variable emitCBCAESDecrypt(Value inAddr, Value outAddr, Value kAddr, Value rAddr, Value len) {
        Variable result = newVariable(len.getValueKind());
        append(new AMD64CipherBlockChainingAESDecryptOp(asAllocatable(inAddr),
                        asAllocatable(outAddr),
                        asAllocatable(kAddr),
                        asAllocatable(rAddr),
                        asAllocatable(len),
                        result,
                        getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
        return result;
    }

    @Override
    public Variable emitECBAESEncrypt(Value inAddr, Value outAddr, Value kAddr, Value len) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rIn = rdi.asValue(inAddr.getValueKind());
        AllocatableValue rOut = rsi.asValue(outAddr.getValueKind());
        AllocatableValue rKey = rdx.asValue(kAddr.getValueKind());
        AllocatableValue rLen = rcx.asValue(len.getValueKind());
        emitMove(rIn, inAddr);
        emitMove(rOut, outAddr);
        emitMove(rKey, kAddr);
        emitMove(rLen, len);
        append(new AMD64ElectronicCodeBookAESEncryptOp(rIn,
                        rOut,
                        rKey,
                        rLen,
                        rResult,
                        getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
        Variable result = newVariable(len.getValueKind());
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitECBAESDecrypt(Value inAddr, Value outAddr, Value kAddr, Value len) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rIn = rdi.asValue(inAddr.getValueKind());
        AllocatableValue rOut = rsi.asValue(outAddr.getValueKind());
        AllocatableValue rKey = rdx.asValue(kAddr.getValueKind());
        AllocatableValue rLen = rcx.asValue(len.getValueKind());
        emitMove(rIn, inAddr);
        emitMove(rOut, outAddr);
        emitMove(rKey, kAddr);
        emitMove(rLen, len);
        append(new AMD64ElectronicCodeBookAESDecryptOp(rIn,
                        rOut,
                        rKey,
                        rLen,
                        rResult,
                        getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
        Variable result = newVariable(len.getValueKind());
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitChaCha20Block(Value state, Value result) {
        RegisterValue stateReg = AMD64.rdi.asValue(state.getValueKind());
        RegisterValue resultReg = AMD64.rsi.asValue(result.getValueKind());
        RegisterValue outputLengthReg = AMD64.rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        emitMove(stateReg, state);
        emitMove(resultReg, result);
        append(new AMD64ChaCha20BlockOp(stateReg, resultReg, outputLengthReg));
        Variable outputLength = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(outputLength, outputLengthReg);
        return outputLength;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitGHASHProcessBlocks(EnumSet<?> runtimeCheckedCPUFeatures, Value state, Value hashSubkey, Value data, Value blocks) {
        append(new AMD64GHASHProcessBlocksOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, asAllocatable(state), asAllocatable(hashSubkey), asAllocatable(data),
                        asAllocatable(blocks)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitPoly1305ProcessBlocks(EnumSet<?> runtimeCheckedCPUFeatures, Value input, Value length, Value accumulator, Value r) {
        RegisterValue rInput = rdi.asValue(input.getValueKind());
        RegisterValue rLength = rbx.asValue(length.getValueKind());
        RegisterValue rAccumulator = rcx.asValue(accumulator.getValueKind());
        RegisterValue rR = r8.asValue(r.getValueKind());

        emitMove(rInput, input);
        emitMove(rLength, length);
        emitMove(rAccumulator, accumulator);
        emitMove(rR, r);

        append(new AMD64Poly1305ProcessBlocksOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, rInput, rLength, rAccumulator, rR));
    }

    @Override
    public void emitBigIntegerMultiplyToLen(Value x, Value xlen, Value y, Value ylen, Value z, Value zlen) {
        RegisterValue rX = AMD64.rdi.asValue(x.getValueKind());
        RegisterValue rXlen = rax.asValue(xlen.getValueKind());
        RegisterValue rY = AMD64.rsi.asValue(y.getValueKind());
        RegisterValue rYlen = AMD64.rcx.asValue(ylen.getValueKind());
        RegisterValue rZ = AMD64.r8.asValue(z.getValueKind());
        RegisterValue rZlen = AMD64.r9.asValue(zlen.getValueKind());

        emitMove(rX, x);
        emitMove(rXlen, xlen);
        emitMove(rY, y);
        emitMove(rYlen, ylen);
        emitMove(rZ, z);
        emitMove(rZlen, zlen);

        append(new AMD64BigIntegerMultiplyToLenOp(this, rX, rXlen, rY, rYlen, rZ, rZlen));
    }

    @Override
    public Variable emitBigIntegerMulAdd(Value out, Value in, Value offset, Value len, Value k) {
        RegisterValue rOut = AMD64.rdi.asValue(out.getValueKind());
        RegisterValue rIn = AMD64.rsi.asValue(in.getValueKind());
        RegisterValue rOffset = AMD64.r11.asValue(offset.getValueKind());
        RegisterValue rLen = AMD64.rcx.asValue(len.getValueKind());
        RegisterValue rK = AMD64.r8.asValue(k.getValueKind());

        emitMove(rOut, out);
        emitMove(rIn, in);
        emitMove(rOffset, offset);
        emitMove(rLen, len);
        emitMove(rK, k);

        append(new AMD64BigIntegerMulAddOp(this, rOut, rIn, rOffset, rLen, rK));
        // result of AMD64BigIntegerMulAddOp is stored at rax
        Variable result = newVariable(len.getValueKind());
        emitMove(result, rax.asValue(len.getValueKind()));
        return result;
    }

    @Override
    public void emitBigIntegerSquareToLen(Value x, Value len, Value z, Value zlen) {
        RegisterValue rX = AMD64.rdi.asValue(x.getValueKind());
        RegisterValue rLen = AMD64.rsi.asValue(len.getValueKind());
        RegisterValue rZ = AMD64.r11.asValue(z.getValueKind());
        RegisterValue rZlen = AMD64.rcx.asValue(zlen.getValueKind());

        emitMove(rX, x);
        emitMove(rLen, len);
        emitMove(rZ, z);
        emitMove(rZlen, zlen);

        append(new AMD64BigIntegerSquareToLenOp(this, rX, rLen, rZ, rZlen));
    }

    @Override
    public void emitBigIntegerMontgomeryMultiply(Value a, Value b, Value n, Value len, Value inv, Value product) {
        RegisterValue rA = AMD64.rdi.asValue(a.getValueKind());
        RegisterValue rB = AMD64.rsi.asValue(b.getValueKind());
        RegisterValue rN = AMD64.rdx.asValue(n.getValueKind());
        RegisterValue rLen = AMD64.rcx.asValue(len.getValueKind());
        RegisterValue rInv = AMD64.r8.asValue(inv.getValueKind());
        RegisterValue rProduct = AMD64.r9.asValue(product.getValueKind());

        emitMove(rA, a);
        emitMove(rB, b);
        emitMove(rN, n);
        emitMove(rLen, len);
        emitMove(rInv, inv);
        emitMove(rProduct, product);

        append(new AMD64BigIntegerMontgomeryMultiplyOp(rA, rB, rN, rLen, rInv, rProduct));
    }

    @Override
    public void emitBigIntegerMontgomerySquare(Value a, Value n, Value len, Value inv, Value product) {
        RegisterValue rA = AMD64.rdi.asValue(a.getValueKind());
        RegisterValue rN = AMD64.rsi.asValue(n.getValueKind());
        RegisterValue rLen = AMD64.rdx.asValue(len.getValueKind());
        RegisterValue rInv = AMD64.rcx.asValue(inv.getValueKind());
        RegisterValue rProduct = AMD64.r8.asValue(product.getValueKind());

        emitMove(rA, a);
        emitMove(rN, n);
        emitMove(rLen, len);
        emitMove(rInv, inv);
        emitMove(rProduct, product);

        append(new AMD64BigIntegerMontgomerySquareOp(rA, rN, rLen, rInv, rProduct));
    }

    @Override
    public void emitBigIntegerLeftShiftWorker(Value newArr, Value oldArr, Value newIdx, Value shiftCount, Value numIter) {
        RegisterValue rNewArr = AMD64.rdi.asValue(newArr.getValueKind());
        RegisterValue rOldArr = AMD64.rsi.asValue(oldArr.getValueKind());
        RegisterValue rNewIdx = AMD64.rdx.asValue(newIdx.getValueKind());
        RegisterValue rShiftCount = AMD64.rcx.asValue(shiftCount.getValueKind());
        RegisterValue rNumIter = AMD64.r8.asValue(numIter.getValueKind());

        emitMove(rNewArr, newArr);
        emitMove(rOldArr, oldArr);
        emitMove(rNewIdx, newIdx);
        emitMove(rShiftCount, shiftCount);
        emitMove(rNumIter, numIter);

        append(new AMD64BigIntegerLeftShiftOp(rNewArr, rOldArr, rNewIdx, rShiftCount, rNumIter, getAVX3Threshold()));
    }

    @Override
    public void emitBigIntegerRightShiftWorker(Value newArr, Value oldArr, Value newIdx, Value shiftCount, Value numIter) {
        RegisterValue rNewArr = AMD64.rdi.asValue(newArr.getValueKind());
        RegisterValue rOldArr = AMD64.rsi.asValue(oldArr.getValueKind());
        RegisterValue rNewIdx = AMD64.rdx.asValue(newIdx.getValueKind());
        RegisterValue rShiftCount = AMD64.rcx.asValue(shiftCount.getValueKind());
        RegisterValue rNumIter = AMD64.r8.asValue(numIter.getValueKind());

        emitMove(rNewArr, newArr);
        emitMove(rOldArr, oldArr);
        emitMove(rNewIdx, newIdx);
        emitMove(rShiftCount, shiftCount);
        emitMove(rNumIter, numIter);

        append(new AMD64BigIntegerRightShiftOp(rNewArr, rOldArr, rNewIdx, rShiftCount, rNumIter, getAVX3Threshold()));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitSha1ImplCompress(EnumSet<?> runtimeCheckedCPUFeatures, Value buf, Value state) {
        append(new AMD64SHA1Op(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, asAllocatable(buf), asAllocatable(state)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitSha256ImplCompress(EnumSet<?> runtimeCheckedCPUFeatures, Value buf, Value state) {
        if (supports(target(), (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, CPUFeature.SHA)) {
            append(new AMD64SHA256Op(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, asAllocatable(buf), asAllocatable(state)));
        } else {
            RegisterValue rBuf = AMD64.rdi.asValue(buf.getValueKind());
            RegisterValue rState = AMD64.rsi.asValue(state.getValueKind());

            emitMove(rBuf, buf);
            emitMove(rState, state);

            append(new AMD64SHA256AVX2Op(rBuf, rState));
        }
    }

    @Override
    public void emitSha3ImplCompress(Value buf, Value state, Value blockSize) {
        append(new AMD64SHA3Op(asAllocatable(buf), asAllocatable(state), asAllocatable(blockSize)));
    }

    @Override
    public void emitSha512ImplCompress(Value buf, Value state) {
        RegisterValue rBuf = AMD64.rdi.asValue(buf.getValueKind());
        RegisterValue rState = AMD64.rsi.asValue(state.getValueKind());

        emitMove(rBuf, buf);
        emitMove(rState, state);

        append(new AMD64SHA512Op(rBuf, rState));
    }

    @Override
    public void emitMD5ImplCompress(Value buf, Value state) {
        append(new AMD64MD5Op(this, asAllocatable(buf), asAllocatable(state)));
    }

    @Override
    public Variable emitDilithiumAlmostNtt(Value coeffs, Value zetas) {
        LIRKind resultKind = LIRKind.value(AMD64Kind.DWORD);
        RegisterValue rResult = AMD64.rax.asValue(resultKind);
        RegisterValue rCoeffs = AMD64.rdi.asValue(coeffs.getValueKind());
        RegisterValue rZetas = AMD64.rsi.asValue(zetas.getValueKind());

        emitMove(rCoeffs, coeffs);
        emitMove(rZetas, zetas);

        append(new AMD64DilithiumAlmostNttOp(rResult, rCoeffs, rZetas));
        Variable result = newVariable(resultKind);
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitDilithiumAlmostInverseNtt(Value coeffs, Value zetas) {
        LIRKind resultKind = LIRKind.value(AMD64Kind.DWORD);
        RegisterValue rResult = AMD64.rax.asValue(resultKind);
        RegisterValue rCoeffs = AMD64.rdi.asValue(coeffs.getValueKind());
        RegisterValue rZetas = AMD64.rsi.asValue(zetas.getValueKind());

        emitMove(rCoeffs, coeffs);
        emitMove(rZetas, zetas);

        append(new AMD64DilithiumAlmostInverseNttOp(rResult, rCoeffs, rZetas));
        Variable result = newVariable(resultKind);
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitDilithiumNttMult(Value product, Value coeffs1, Value coeffs2) {
        LIRKind resultKind = LIRKind.value(AMD64Kind.DWORD);
        RegisterValue rResult = AMD64.rax.asValue(resultKind);
        RegisterValue rProduct = AMD64.rdi.asValue(product.getValueKind());
        RegisterValue rCoeffs1 = AMD64.rsi.asValue(coeffs1.getValueKind());
        RegisterValue rCoeffs2 = AMD64.rdx.asValue(coeffs2.getValueKind());

        emitMove(rProduct, product);
        emitMove(rCoeffs1, coeffs1);
        emitMove(rCoeffs2, coeffs2);

        append(new AMD64DilithiumNttMultOp(rResult, rProduct, rCoeffs1, rCoeffs2));
        Variable result = newVariable(resultKind);
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitDilithiumMontMulByConstant(Value coeffs, Value constant) {
        LIRKind resultKind = LIRKind.value(AMD64Kind.DWORD);
        RegisterValue rResult = AMD64.rax.asValue(resultKind);
        RegisterValue rCoeffs = AMD64.rdi.asValue(coeffs.getValueKind());
        RegisterValue rConstant = AMD64.rsi.asValue(constant.getValueKind());

        emitMove(rCoeffs, coeffs);
        emitMove(rConstant, constant);

        append(new AMD64DilithiumMontMulByConstantOp(rResult, rCoeffs, rConstant));
        Variable result = newVariable(resultKind);
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitDilithiumDecomposePoly(Value input, Value lowPart, Value highPart, Value twoGamma2, Value multiplier) {
        LIRKind resultKind = LIRKind.value(AMD64Kind.DWORD);
        RegisterValue rResult = AMD64.rax.asValue(resultKind);
        RegisterValue rInput = AMD64.rdi.asValue(input.getValueKind());
        RegisterValue rLowPart = AMD64.rsi.asValue(lowPart.getValueKind());
        RegisterValue rHighPart = AMD64.rdx.asValue(highPart.getValueKind());
        RegisterValue rTwoGamma2 = AMD64.rcx.asValue(twoGamma2.getValueKind());
        RegisterValue rMultiplier = AMD64.r8.asValue(multiplier.getValueKind());

        emitMove(rInput, input);
        emitMove(rLowPart, lowPart);
        emitMove(rHighPart, highPart);
        emitMove(rTwoGamma2, twoGamma2);
        emitMove(rMultiplier, multiplier);

        append(new AMD64DilithiumDecomposePolyOp(rResult, rInput, rLowPart, rHighPart, rTwoGamma2, rMultiplier));
        Variable result = newVariable(resultKind);
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitKyberNtt(Value poly, Value zetas) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rPoly = rdi.asValue(poly.getValueKind());
        AllocatableValue rZetas = rsi.asValue(zetas.getValueKind());

        emitMove(rPoly, poly);
        emitMove(rZetas, zetas);

        append(new AMD64KyberNttOp(rResult, rPoly, rZetas));
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitKyberInverseNtt(Value poly, Value zetas) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rPoly = rdi.asValue(poly.getValueKind());
        AllocatableValue rZetas = rsi.asValue(zetas.getValueKind());

        emitMove(rPoly, poly);
        emitMove(rZetas, zetas);

        append(new AMD64KyberInverseNttOp(rResult, rPoly, rZetas));
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitKyberNttMult(Value resultArray, Value ntta, Value nttb, Value zetas) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rResultArray = rdi.asValue(resultArray.getValueKind());
        AllocatableValue rNtta = rsi.asValue(ntta.getValueKind());
        AllocatableValue rNttb = rdx.asValue(nttb.getValueKind());
        AllocatableValue rZetas = rcx.asValue(zetas.getValueKind());

        emitMove(rResultArray, resultArray);
        emitMove(rNtta, ntta);
        emitMove(rNttb, nttb);
        emitMove(rZetas, zetas);

        append(new AMD64KyberNttMultOp(rResult, rResultArray, rNtta, rNttb, rZetas));
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitKyberAddPoly2(Value resultArray, Value a, Value b) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rResultArray = rdi.asValue(resultArray.getValueKind());
        AllocatableValue rA = rsi.asValue(a.getValueKind());
        AllocatableValue rB = rdx.asValue(b.getValueKind());

        emitMove(rResultArray, resultArray);
        emitMove(rA, a);
        emitMove(rB, b);

        append(new AMD64KyberAddPoly2Op(rResult, rResultArray, rA, rB));
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitKyberAddPoly3(Value resultArray, Value a, Value b, Value c) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rResultArray = rdi.asValue(resultArray.getValueKind());
        AllocatableValue rA = rsi.asValue(a.getValueKind());
        AllocatableValue rB = rdx.asValue(b.getValueKind());
        AllocatableValue rC = rcx.asValue(c.getValueKind());

        emitMove(rResultArray, resultArray);
        emitMove(rA, a);
        emitMove(rB, b);
        emitMove(rC, c);

        append(new AMD64KyberAddPoly3Op(rResult, rResultArray, rA, rB, rC));
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitKyber12To16(Value condensed, Value index, Value parsed, Value parsedLength) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rCondensed = rdi.asValue(condensed.getValueKind());
        AllocatableValue rIndex = rsi.asValue(index.getValueKind());
        AllocatableValue rParsed = rdx.asValue(parsed.getValueKind());
        AllocatableValue rParsedLength = rcx.asValue(parsedLength.getValueKind());

        emitMove(rCondensed, condensed);
        emitMove(rIndex, index);
        emitMove(rParsed, parsed);
        emitMove(rParsedLength, parsedLength);

        append(new AMD64Kyber12To16Op(rResult, rCondensed, rIndex, rParsed, rParsedLength));
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(result, rResult);
        return result;
    }

    @Override
    public Variable emitKyberBarrettReduce(Value coeffs) {
        AllocatableValue rResult = rax.asValue(LIRKind.value(AMD64Kind.DWORD));
        AllocatableValue rCoeffs = rdi.asValue(coeffs.getValueKind());

        emitMove(rCoeffs, coeffs);

        append(new AMD64KyberBarrettReduceOp(rResult, rCoeffs));
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        emitMove(result, rResult);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitCRC32UpdateBytes(EnumSet<?> runtimeCheckedCPUFeatures, Value crc, Value bufferAddress, Value length) {
        RegisterValue rResult = AMD64.rdi.asValue(crc.getValueKind());
        RegisterValue rCrc = AMD64.rdi.asValue(crc.getValueKind());
        RegisterValue rBuf = AMD64.rsi.asValue(bufferAddress.getValueKind());
        RegisterValue rLen = AMD64.rdx.asValue(length.getValueKind());
        emitMove(rCrc, crc);
        emitMove(rBuf, bufferAddress);
        emitMove(rLen, length);
        append(new AMD64CRC32UpdateBytesOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, rResult, rCrc, rBuf, rLen));
        Variable result = newVariable(crc.getValueKind());
        emitMove(result, rResult);
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitCRC32CUpdateBytes(EnumSet<?> runtimeCheckedCPUFeatures, Value crc, Value bufferAddress, Value length) {
        RegisterValue rResult = AMD64.rdi.asValue(crc.getValueKind());
        RegisterValue rCrc = AMD64.rdi.asValue(crc.getValueKind());
        RegisterValue rBuf = AMD64.rsi.asValue(bufferAddress.getValueKind());
        RegisterValue rLen = AMD64.rdx.asValue(length.getValueKind());
        emitMove(rCrc, crc);
        emitMove(rBuf, bufferAddress);
        emitMove(rLen, length);
        append(new AMD64CRC32CUpdateBytesOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, rResult, rCrc, rBuf, rLen));
        Variable result = newVariable(crc.getValueKind());
        emitMove(result, rResult);
        return result;
    }

    /**
     * Return the maximum size of vector registers used in SSE/AVX instructions.
     */
    @SuppressWarnings("unchecked")
    @Override
    public AVXSize getMaxVectorSize(EnumSet<?> runtimeCheckedCPUFeatures) {
        if (supports(target(), (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, AMD64.CPUFeature.AVX512VL)) {
            return AVXSize.ZMM;
        }
        if (supports(target(), (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, AMD64.CPUFeature.AVX2)) {
            return AVXSize.YMM;
        }
        return AVXSize.XMM;
    }

    /**
     * Return the minimal array size for using AVX3 instructions.
     */
    protected int getAVX3Threshold() {
        return 4096;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayIndexOf(Stride stride, ArrayIndexOfVariant variant, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayPointer, Value arrayOffset, Value arrayLength, Value fromIndex, Value... searchValues) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        int nValues = searchValues.length;
        int constOffset = isConstantValue(arrayOffset) && asConstantValue(arrayOffset).isJavaConstant() &&
                        asConstantValue(arrayOffset).getJavaConstant().asLong() >= 0 &&
                        asConstantValue(arrayOffset).getJavaConstant().asLong() <= Integer.MAX_VALUE
                                        ? (int) asConstantValue(arrayOffset).getJavaConstant().asLong()
                                        : -1;
        Value searchValue1 = asAllocatable(searchValues[0]);
        Value searchValue2 = nValues > 1 ? asAllocatable(searchValues[1]) : Value.ILLEGAL;
        Value searchValue3 = nValues > 2 ? asAllocatable(searchValues[2]) : Value.ILLEGAL;
        Value searchValue4 = nValues > 3 ? asAllocatable(searchValues[3]) : Value.ILLEGAL;
        append(new AMD64ArrayIndexOfOp(stride, variant, constOffset, nValues, this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, emitConvertNullToZero(arrayPointer), asAllocatable(arrayOffset), asAllocatable(arrayLength), asAllocatable(fromIndex),
                        searchValue1, searchValue2, searchValue3, searchValue4));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitIndexOfZero(Stride stride, EnumSet<?> runtimeCheckedCPUFeatures, Value arrayPointer) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.QWORD));
        append(AMD64IndexOfZeroOp.movParamsAndCreate(stride, this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, result, arrayPointer));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitStringLatin1Inflate(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value len) {
        RegisterValue rsrc = AMD64.rsi.asValue(src.getValueKind());
        RegisterValue rdst = AMD64.rdi.asValue(dst.getValueKind());
        RegisterValue rlen = AMD64.rdx.asValue(len.getValueKind());

        emitMove(rsrc, src);
        emitMove(rdst, dst);
        emitMove(rlen, len);

        append(new AMD64StringLatin1InflateOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, getAVX3Threshold(), rsrc, rdst, rlen));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitStringUTF16Compress(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value len) {
        RegisterValue rsrc = AMD64.rsi.asValue(src.getValueKind());
        RegisterValue rdst = AMD64.rdi.asValue(dst.getValueKind());
        RegisterValue rlen = AMD64.rdx.asValue(len.getValueKind());

        emitMove(rsrc, src);
        emitMove(rdst, dst);
        emitMove(rlen, len);

        LIRKind reskind = LIRKind.value(AMD64Kind.DWORD);
        RegisterValue rres = rax.asValue(reskind);

        append(new AMD64StringUTF16CompressOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, getAVX3Threshold(), rres, rsrc, rdst, rlen));

        Variable res = newVariable(reskind);
        emitMove(res, rres);
        return res;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitCodepointIndexToByteIndex(StringCodepointIndexToByteIndexNode.InputEncoding inputEncoding, EnumSet<?> runtimeCheckedCPUFeatures, Value array, Value offset, Value length,
                    Value index) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64CodepointIndexToByteIndexOp(this, inputEncoding, (EnumSet<AMD64.CPUFeature>) runtimeCheckedCPUFeatures,
                        emitConvertNullToZero(array), asAllocatable(offset), asAllocatable(length), asAllocatable(index), result));
        return result;
    }

    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue key, AllocatableValue temp) {
        return new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, temp);
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, AllocatableValue key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        // a temp is needed for loading object constants
        boolean needsTemp = !LIRKind.isValue(key);
        append(createStrategySwitchOp(strategy, keyTargets, defaultTarget, key, needsTemp ? newVariable(key.getValueKind()) : Value.ILLEGAL));
    }

    @Override
    protected void emitRangeTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, SwitchStrategy remainingStrategy, LabelRef[] remainingTargets, AllocatableValue key,
                    boolean inputMayBeOutOfRange, boolean mayEmitThreadedCode) {
        AllocatableValue scratch = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new NewScratchRegisterOp(scratch));
        append(new RangeTableSwitchOp(this, lowKey, defaultTarget, targets, remainingStrategy, remainingTargets, key, scratch, inputMayBeOutOfRange, mayEmitThreadedCode));
    }

    @Override
    protected void emitHashTableSwitch(JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value, Value hash) {
        Variable scratch = newVariable(LIRKind.value(target().arch.getWordKind()));
        Variable entryScratch = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new HashTableSwitchOp(keys, defaultTarget, targets, value, asAllocatable(hash), scratch, entryScratch));
    }

    @Override
    public void emitPause() {
        append(new AMD64PauseOp());
    }

    @Override
    public void emitSpinWait() {
        append(new AMD64PauseOp());
    }

    @Override
    public void emitHalt() {
        append(new AMD64HaltOp());
    }

    @Override
    public void emitCacheWriteback(Value address) {
        append(new AMD64CacheWritebackOp(asAddressValue(address)));
    }

    @Override
    public void emitCacheWritebackSync(boolean isPreSync) {
        // only need a post sync barrier on AMD64
        if (!isPreSync) {
            append(new AMD64CacheWritebackPostSyncOp());
        }
    }

    @Override
    public LIRInstruction createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        return new AMD64ZapRegistersOp(zappedRegisters, zapValues);
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        return new AMD64ZapStackOp(zappedStack, zapValues);
    }

    @Override
    public void emitSpeculationFence() {
        append(new AMD64LFenceOp());
    }

    @Override
    public void emitProtectionKeyRegisterWrite(Value value) {
        RegisterValue raxValue = AMD64.rax.asValue(value.getValueKind());
        emitMove(raxValue, value);
        append(new AMD64WriteDataToUserPageKeyRegister(raxValue));
    }

    @Override
    public Value emitProtectionKeyRegisterRead() {
        AMD64ReadDataFromUserPageKeyRegister rdpkru = new AMD64ReadDataFromUserPageKeyRegister();
        append(rdpkru);
        return emitReadRegister(rax, rdpkru.retVal.getValueKind());
    }

    @Override
    public void emitZeroMemory(Value address, Value length, boolean isAligned) {
        RegisterValue lengthReg = AMD64.rcx.asValue(length.getValueKind());
        emitMove(lengthReg, length);
        append(new AMD64ZeroMemoryOp(asAddressValue(address), lengthReg));
    }

    public boolean supportsCPUFeature(AMD64.CPUFeature feature) {
        return ((AMD64) target().arch).getFeatures().contains(feature);
    }

    public boolean usePopCountInstruction() {
        return supportsCPUFeature(CPUFeature.POPCNT);
    }

    public boolean useCountLeadingZerosInstruction() {
        return supportsCPUFeature(CPUFeature.LZCNT);
    }

    public boolean useCountTrailingZerosInstruction() {
        return supportsCPUFeature(CPUFeature.BMI1);
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
    protected Value combineLoAndHi(Value lo, Value hi) {
        Value shiftedHi = getArithmetic().emitShl(hi, emitConstant(LIRKind.value(AMD64Kind.DWORD), JavaConstant.forInt(32)));
        return getArithmetic().emitOr(shiftedHi, lo);
    }
}
