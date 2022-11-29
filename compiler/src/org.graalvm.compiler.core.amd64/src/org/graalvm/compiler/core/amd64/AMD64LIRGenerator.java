/*
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.BYTE;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PD;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.PS;
import static org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static org.graalvm.compiler.asm.amd64.AMD64MacroAssembler.ExtendMode.ZERO_EXTEND;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.asConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isIntConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import java.util.EnumSet;

import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.SSEOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.VexRMOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AVXKind;
import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.LIRKindTool;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.amd64.AMD64AESDecryptOp;
import org.graalvm.compiler.lir.amd64.AMD64AESEncryptOp;
import org.graalvm.compiler.lir.amd64.AMD64AddressValue;
import org.graalvm.compiler.lir.amd64.AMD64ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.lir.amd64.AMD64ArrayCompareToOp;
import org.graalvm.compiler.lir.amd64.AMD64ArrayCopyWithConversionsOp;
import org.graalvm.compiler.lir.amd64.AMD64ArrayEqualsOp;
import org.graalvm.compiler.lir.amd64.AMD64ArrayIndexOfOp;
import org.graalvm.compiler.lir.amd64.AMD64ArrayRegionCompareToOp;
import org.graalvm.compiler.lir.amd64.AMD64BigIntegerMultiplyToLenOp;
import org.graalvm.compiler.lir.amd64.AMD64Binary;
import org.graalvm.compiler.lir.amd64.AMD64BinaryConsumer;
import org.graalvm.compiler.lir.amd64.AMD64ByteSwapOp;
import org.graalvm.compiler.lir.amd64.AMD64CacheWritebackOp;
import org.graalvm.compiler.lir.amd64.AMD64CacheWritebackPostSyncOp;
import org.graalvm.compiler.lir.amd64.AMD64CalcStringAttributesOp;
import org.graalvm.compiler.lir.amd64.AMD64Call;
import org.graalvm.compiler.lir.amd64.AMD64CipherBlockChainingAESDecryptOp;
import org.graalvm.compiler.lir.amd64.AMD64CipherBlockChainingAESEncryptOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.BranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CmpBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CmpConstBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CmpDataBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CondMoveOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.CondSetOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.FloatCondSetOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.HashTableSwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.RangeTableSwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.TestBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.TestByteBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow.TestConstBranchOp;
import org.graalvm.compiler.lir.amd64.AMD64CounterModeAESCryptOp;
import org.graalvm.compiler.lir.amd64.AMD64EncodeArrayOp;
import org.graalvm.compiler.lir.amd64.AMD64GHASHProcessBlocksOp;
import org.graalvm.compiler.lir.amd64.AMD64HasNegativesOp;
import org.graalvm.compiler.lir.amd64.AMD64LFenceOp;
import org.graalvm.compiler.lir.amd64.AMD64Move;
import org.graalvm.compiler.lir.amd64.AMD64Move.CompareAndSwapOp;
import org.graalvm.compiler.lir.amd64.AMD64Move.MembarOp;
import org.graalvm.compiler.lir.amd64.AMD64Move.StackLeaOp;
import org.graalvm.compiler.lir.amd64.AMD64PauseOp;
import org.graalvm.compiler.lir.amd64.AMD64StringLatin1InflateOp;
import org.graalvm.compiler.lir.amd64.AMD64StringUTF16CompressOp;
import org.graalvm.compiler.lir.amd64.AMD64VectorizedMismatchOp;
import org.graalvm.compiler.lir.amd64.AMD64ZapRegistersOp;
import org.graalvm.compiler.lir.amd64.AMD64ZapStackOp;
import org.graalvm.compiler.lir.amd64.AMD64ZeroMemoryOp;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorCompareOp;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGenerator;
import org.graalvm.compiler.lir.gen.MoveFactory;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
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

    public AMD64LIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, moveFactory, providers, lirGenRes);
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

    private Value emitCompareAndSwap(boolean isLogic, LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
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
        append(new CompareAndSwapOp(memKind, aRes, addressValue, aRes, allocatableNewValue));

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
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder) {
        return LIRValueUtil.asVariable(emitCompareAndSwap(true, accessKind, address, expectedValue, newValue, trueValue, falseValue));
    }

    @Override
    public Value emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder) {
        return emitCompareAndSwap(false, accessKind, address, expectedValue, newValue, null, null);
    }

    public void emitCompareAndSwapBranch(ValueKind<?> kind, AMD64AddressValue address, Value expectedValue, Value newValue, Condition condition, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        assert kind.getPlatformKind().getSizeInBytes() <= expectedValue.getValueKind().getPlatformKind().getSizeInBytes();
        assert kind.getPlatformKind().getSizeInBytes() <= newValue.getValueKind().getPlatformKind().getSizeInBytes();
        assert condition == Condition.EQ || condition == Condition.NE;
        AMD64Kind memKind = (AMD64Kind) kind.getPlatformKind();
        RegisterValue raxValue = AMD64.rax.asValue(kind);
        emitMove(raxValue, expectedValue);
        append(new CompareAndSwapOp(memKind, raxValue, address, raxValue, asAllocatable(newValue)));
        append(new BranchOp(condition, trueLabel, falseLabel, trueLabelProbability));
    }

    @Override
    public Value emitAtomicReadAndAdd(LIRKind accessKind, Value address, Value delta) {
        Variable result = newVariable(toRegisterKind(accessKind));
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndAddOp((AMD64Kind) accessKind.getPlatformKind(), result, addressValue, asAllocatable(delta)));
        return result;
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRKind accessKind, Value address, Value newValue) {
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
                throw GraalError.shouldNotReachHere("unexpected kind: " + cmpKind);
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
            append(new AMD64VectorCompareOp(VexRMOp.VPTEST, getRegisterSize(left), asAllocatable(left), asAllocatable(right)));
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
                    assert unorderedIsTrue == AMD64ControlFlow.trueOnUnordered(finalCondition.negate());
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

    public abstract void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments);

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
    public Variable emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64ByteSwapOp(result, input));
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
    public Variable emitHasNegatives(EnumSet<?> runtimeCheckedCPUFeatures, Value array, Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64HasNegativesOp(this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures, result, asAllocatable(array), asAllocatable(length)));
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
        RegisterValue rZlen = AMD64.r11.asValue(zlen.getValueKind());

        emitMove(rX, x);
        emitMove(rXlen, xlen);
        emitMove(rY, y);
        emitMove(rYlen, ylen);
        emitMove(rZ, z);
        emitMove(rZlen, zlen);

        append(new AMD64BigIntegerMultiplyToLenOp(rX, rXlen, rY, rYlen, rZ, rZlen, getHeapBaseRegister()));
    }

    @SuppressWarnings("unchecked")
    protected boolean supports(EnumSet<?> runtimeCheckedCPUFeatures, CPUFeature feature) {
        assert runtimeCheckedCPUFeatures == null || runtimeCheckedCPUFeatures.isEmpty() || runtimeCheckedCPUFeatures.iterator().next() instanceof CPUFeature;
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
    public Variable emitArrayIndexOf(Stride stride, boolean findTwoConsecutive, boolean withMask, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayPointer, Value arrayOffset, Value arrayLength, Value fromIndex, Value... searchValues) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(AMD64ArrayIndexOfOp.movParamsAndCreate(stride, findTwoConsecutive, withMask, this, (EnumSet<CPUFeature>) runtimeCheckedCPUFeatures,
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
