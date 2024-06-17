/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.EnumSet;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRROp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.memory.BarrierType;
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
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerMulAddOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerMultiplyToLenOp;
import jdk.graal.compiler.lir.amd64.AMD64BigIntegerSquareToLenOp;
import jdk.graal.compiler.lir.amd64.AMD64Binary;
import jdk.graal.compiler.lir.amd64.AMD64BinaryConsumer;
import jdk.graal.compiler.lir.amd64.AMD64ByteSwapOp;
import jdk.graal.compiler.lir.amd64.AMD64CacheWritebackOp;
import jdk.graal.compiler.lir.amd64.AMD64CacheWritebackPostSyncOp;
import jdk.graal.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64CipherBlockChainingAESDecryptOp;
import jdk.graal.compiler.lir.amd64.AMD64CipherBlockChainingAESEncryptOp;
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
import jdk.graal.compiler.lir.amd64.AMD64CounterModeAESCryptOp;
import jdk.graal.compiler.lir.amd64.AMD64EncodeArrayOp;
import jdk.graal.compiler.lir.amd64.AMD64GHASHProcessBlocksOp;
import jdk.graal.compiler.lir.amd64.AMD64HaltOp;
import jdk.graal.compiler.lir.amd64.AMD64LFenceOp;
import jdk.graal.compiler.lir.amd64.AMD64MD5Op;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.amd64.AMD64Move.CompareAndSwapOp;
import jdk.graal.compiler.lir.amd64.AMD64Move.MembarOp;
import jdk.graal.compiler.lir.amd64.AMD64Move.StackLeaOp;
import jdk.graal.compiler.lir.amd64.AMD64PauseOp;
import jdk.graal.compiler.lir.amd64.AMD64SHA1Op;
import jdk.graal.compiler.lir.amd64.AMD64SHA256AVX2Op;
import jdk.graal.compiler.lir.amd64.AMD64SHA256Op;
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

    private Value emitCompareAndSwap(boolean isLogic, LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, BarrierType barrierType) {
        ValueKind<?> kind = newValue.getValueKind();
        assert kind.equals(expectedValue.getValueKind());

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
        RegisterValue aRes = AMD64.rax.asValue(integerAccessKind);
        AllocatableValue allocatableNewValue = asAllocatable(reinterpretedNewValue, integerAccessKind);
        emitMove(aRes, reinterpretedExpectedValue);
        emitCompareAndSwapOp(isLogic, integerAccessKind, memKind, aRes, addressValue, allocatableNewValue, barrierType);

        if (isLogic) {
            assert trueValue.getValueKind().equals(falseValue.getValueKind());
            return emitCondMoveOp(Condition.EQ, trueValue, falseValue, false, false);
        } else {
            if (isXmm) {
                return arithmeticLIRGen.emitReinterpret(accessKind, aRes);
            } else {
                Variable result = newVariable(kind);
                emitMove(result, aRes);
                return result;
            }
        }
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
        assert kind.getPlatformKind().getSizeInBytes() <= expectedValue.getValueKind().getPlatformKind().getSizeInBytes() : kind + " " + expectedValue;
        assert kind.getPlatformKind().getSizeInBytes() <= newValue.getValueKind().getPlatformKind().getSizeInBytes() : kind + " " + newValue;
        assert condition == Condition.EQ || condition == Condition.NE : Assertions.errorMessage(condition, address, expectedValue, newValue);
        AMD64Kind memKind = (AMD64Kind) kind.getPlatformKind();
        RegisterValue raxValue = AMD64.rax.asValue(kind);
        emitMove(raxValue, expectedValue);
        emitCompareAndSwapOp(isLogic, kind, memKind, raxValue, address, asAllocatable(newValue), barrierType);
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
            Condition finalCondition = emitCompare(cmpKind, left, right, cond);
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability, isSelfEqualsCheck));
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
            if (cmpKind == AMD64Kind.SINGLE) {
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PS, asAllocatable(left), right, state));
                append(new FloatBranchOp(cond, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
            } else if (cmpKind == AMD64Kind.DOUBLE) {
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PD, asAllocatable(left), right, state));
                append(new FloatBranchOp(cond, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
            } else {
                throw GraalError.shouldNotReachHere("unexpected kind: " + cmpKind); // ExcludeFromJacocoGeneratedReport
            }
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
            if (isJavaConstant(right) && NumUtil.is32bit(asJavaConstant(right).asLong())) {
                append(new TestConstBranchOp(size, asAllocatable(left), (int) asJavaConstant(right).asLong(), null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
            } else if (isJavaConstant(left) && NumUtil.is32bit(asJavaConstant(left).asLong())) {
                append(new TestConstBranchOp(size, asAllocatable(right), (int) asJavaConstant(left).asLong(), null, Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
            } else if (isAllocatableValue(right)) {
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
        boolean isFloatComparison = cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE;

        Condition finalCondition = cond;
        Value finalTrueValue = trueValue;
        Value finalFalseValue = falseValue;
        if (isFloatComparison) {
            // eliminate the parity check in case of a float comparison
            Value finalLeft = left;
            Value finalRight = right;
            if (unorderedIsTrue != AMD64ControlFlow.trueOnUnordered(finalCondition)) {
                if (unorderedIsTrue == AMD64ControlFlow.trueOnUnordered(finalCondition.mirror())) {
                    finalCondition = finalCondition.mirror();
                    finalLeft = right;
                    finalRight = left;
                } else if (finalCondition != Condition.EQ && finalCondition != Condition.NE) {
                    // negating EQ and NE does not make any sense as we would need to negate
                    // unorderedIsTrue as well (otherwise, we would no longer fulfill the Java
                    // NaN semantics)
                    assert unorderedIsTrue == AMD64ControlFlow.trueOnUnordered(finalCondition.negate()) : Assertions.errorMessage(cmpKind, left, right, cond, unorderedIsTrue, finalCondition);
                    finalCondition = finalCondition.negate();
                    finalTrueValue = falseValue;
                    finalFalseValue = trueValue;
                }
            }
            emitRawCompare(cmpKind, finalLeft, finalRight);
        } else {
            finalCondition = emitCompare(cmpKind, left, right, cond);
        }

        boolean isSelfEqualsCheck = isFloatComparison && finalCondition == Condition.EQ && left.equals(right);
        return emitCondMoveOp(finalCondition, finalTrueValue, finalFalseValue, isFloatComparison, unorderedIsTrue, isSelfEqualsCheck);
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
                if (unorderedIsTrue == AMD64ControlFlow.trueOnUnordered(condition.negate())) {
                    append(new FloatCondSetOp(result, condition.negate()));
                } else {
                    append(new FloatCondSetOp(result, condition));
                    Variable negatedResult = newVariable(result.getValueKind());
                    append(new AMD64Binary.ConstOp(AMD64BinaryArithmetic.XOR, OperandSize.get(result.getPlatformKind()), negatedResult, result, 1));
                    result = negatedResult;
                }
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
            if (isJavaConstant(b) && NumUtil.is32bit(asJavaConstant(b).asLong())) {
                append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(a), (int) asJavaConstant(b).asLong()));
            } else if (isJavaConstant(a) && NumUtil.is32bit(asJavaConstant(a).asLong())) {
                append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(b), (int) asJavaConstant(a).asLong()));
            } else if (isAllocatableValue(b)) {
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
    private Condition emitCompare(PlatformKind cmpKind, Value a, Value b, Condition cond) {
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

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value targetAddress, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        if (maxOffset != (int) maxOffset) {
            append(new AMD64Call.DirectFarForeignCallOp(linkage, result, arguments, temps, info));
        } else {
            append(new AMD64Call.DirectNearForeignCallOp(linkage, result, arguments, temps, info));
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
        RegisterValue raxRes = AMD64.rax.asValue(resultKind);
        RegisterValue cntA = AMD64.rcx.asValue(lengthA.getValueKind());
        RegisterValue cntB = AMD64.rdx.asValue(lengthB.getValueKind());
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
        append(AMD64ArrayRegionCompareToOp.movParamsAndCreate(this, null, null, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, arrayA, offsetA, arrayB, offsetB, length, dynamicStrides,
                        ZERO_EXTEND));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitArrayRegionCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(AMD64ArrayRegionCompareToOp.movParamsAndCreate(this, strideA, strideB, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, arrayA, offsetA, arrayB, offsetB, length, null,
                        ZERO_EXTEND));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitVectorizedMismatch(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value arrayB, Value length, Value stride) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(AMD64VectorizedMismatchOp.movParamsAndCreate(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, result, arrayA, arrayB, length, stride));
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
        append(AMD64ArrayEqualsOp.movParamsAndCreate(this, commonElementKind, stride, stride, stride,
                        (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, arrayA, offsetA, arrayB, offsetB, null, length, null,
                        ZERO_EXTEND));
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
        append(AMD64ArrayEqualsOp.movParamsAndCreate(this, strideA, strideB, strideB,
                        (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, arrayA, offsetA, arrayB, offsetB, null, length,
                        ZERO_EXTEND));
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
        append(AMD64ArrayEqualsOp.movParamsAndCreate(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, arrayA, offsetA, arrayB, offsetB, null, length, dynamicStrides,
                        ZERO_EXTEND));
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
        append(AMD64ArrayEqualsOp.movParamsAndCreate(this, strideA, strideB, strideMask,
                        (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, arrayA, offsetA, arrayB, offsetB, mask, length,
                        ZERO_EXTEND));
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
        append(AMD64ArrayEqualsOp.movParamsAndCreate(this,
                        (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, arrayA, offsetA, arrayB, offsetB, mask, length, dynamicStrides,
                        ZERO_EXTEND));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitArrayCopyWithConversion(Stride strideSrc, Stride strideDst, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length) {
        append(AMD64ArrayCopyWithConversionsOp.movParamsAndCreate(this, strideSrc, strideDst, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        arraySrc, offsetSrc, arrayDst, offsetDst, length,
                        ZERO_EXTEND));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void emitArrayCopyWithConversion(EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, Value dynamicStrides) {
        append(AMD64ArrayCopyWithConversionsOp.movParamsAndCreate(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, arraySrc, offsetSrc, arrayDst, offsetDst, length, dynamicStrides,
                        ZERO_EXTEND));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Variable emitCalcStringAttributes(CalcStringAttributesEncoding encoding, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value array, Value offset, Value length, boolean assumeValid) {
        Variable result = newVariable(LIRKind.value(encoding == CalcStringAttributesEncoding.UTF_8 || encoding == CalcStringAttributesEncoding.UTF_16 ? AMD64Kind.QWORD : AMD64Kind.DWORD));
        append(AMD64CalcStringAttributesOp.movParamsAndCreate(this, encoding, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, array, offset, length, result, assumeValid));
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
    public void emitGHASHProcessBlocks(Value state, Value hashSubkey, Value data, Value blocks) {
        append(new AMD64GHASHProcessBlocksOp(this, asAllocatable(state), asAllocatable(hashSubkey), asAllocatable(data), asAllocatable(blocks)));
    }

    @Override
    public void emitBigIntegerMultiplyToLen(Value x, Value xlen, Value y, Value ylen, Value z, Value zlen) {
        RegisterValue rX = AMD64.rdi.asValue(x.getValueKind());
        RegisterValue rXlen = AMD64.rax.asValue(xlen.getValueKind());
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

        append(new AMD64BigIntegerMultiplyToLenOp(rX, rXlen, rY, rYlen, rZ, rZlen, getHeapBaseRegister()));
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

        append(new AMD64BigIntegerMulAddOp(rOut, rIn, rOffset, rLen, rK, getHeapBaseRegister()));
        // result of AMD64BigIntegerMulAddOp is stored at rax
        Variable result = newVariable(len.getValueKind());
        emitMove(result, AMD64.rax.asValue(len.getValueKind()));
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

        append(new AMD64BigIntegerSquareToLenOp(rX, rLen, rZ, rZlen, getHeapBaseRegister()));
    }

    @Override
    public void emitSha1ImplCompress(Value buf, Value state) {
        append(new AMD64SHA1Op(this, asAllocatable(buf), asAllocatable(state)));
    }

    @Override
    public void emitSha256ImplCompress(Value buf, Value state) {
        if (supportsCPUFeature(CPUFeature.SHA)) {
            append(new AMD64SHA256Op(this, asAllocatable(buf), asAllocatable(state)));
        } else {
            RegisterValue rBuf = AMD64.rdi.asValue(buf.getValueKind());
            RegisterValue rState = AMD64.rsi.asValue(state.getValueKind());

            emitMove(rBuf, buf);
            emitMove(rState, state);

            append(new AMD64SHA256AVX2Op(rBuf, rState));
        }
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

    @SuppressWarnings("unchecked")
    protected boolean supports(EnumSet<?> runtimeCheckedCPUFeatures, CPUFeature feature) {
        assert runtimeCheckedCPUFeatures == null || runtimeCheckedCPUFeatures.isEmpty() ||
                        runtimeCheckedCPUFeatures.iterator().next() instanceof CPUFeature : Assertions.errorMessage(runtimeCheckedCPUFeatures);
        EnumSet<CPUFeature> typedFeatures = (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures;
        return typedFeatures != null && typedFeatures.contains(feature) || ((AMD64) target().arch).getFeatures().contains(feature);
    }

    /**
     * Return the maximum size of vector registers used in SSE/AVX instructions.
     */
    @Override
    public AVXSize getMaxVectorSize(EnumSet<?> runtimeCheckedCPUFeatures) {
        if (supports(runtimeCheckedCPUFeatures, AMD64.CPUFeature.AVX512VL)) {
            return AVXSize.ZMM;
        }
        if (supports(runtimeCheckedCPUFeatures, AMD64.CPUFeature.AVX2)) {
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
        append(AMD64ArrayIndexOfOp.movParamsAndCreate(stride, variant, this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
                        result, arrayPointer, arrayOffset, arrayLength, fromIndex, searchValues));
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
        RegisterValue rres = AMD64.rax.asValue(reskind);

        append(new AMD64StringUTF16CompressOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, getAVX3Threshold(), rres, rsrc, rdst, rlen));

        Variable res = newVariable(reskind);
        emitMove(res, rres);
        return res;
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
    protected void emitRangeTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue key) {
        Variable scratch = newVariable(LIRKind.value(target().arch.getWordKind()));
        Variable idxScratch = newVariable(key.getValueKind());
        append(new RangeTableSwitchOp(lowKey, defaultTarget, targets, key, scratch, idxScratch));
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
        RegisterValue rax = AMD64.rax.asValue(value.getValueKind());
        emitMove(rax, value);
        append(new AMD64WriteDataToUserPageKeyRegister(rax));
    }

    @Override
    public Value emitProtectionKeyRegisterRead() {
        AMD64ReadDataFromUserPageKeyRegister rdpkru = new AMD64ReadDataFromUserPageKeyRegister();
        append(rdpkru);
        return emitReadRegister(AMD64.rax, rdpkru.retVal.getValueKind());
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

    public boolean supportsCPUFeature(String feature) {
        try {
            return ((AMD64) target().arch).getFeatures().contains(AMD64.CPUFeature.valueOf(feature));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
