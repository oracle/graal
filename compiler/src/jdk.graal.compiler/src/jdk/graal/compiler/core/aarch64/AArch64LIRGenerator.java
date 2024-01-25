/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.aarch64;

import static jdk.graal.compiler.lir.LIRValueUtil.asJavaConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.asVariable;
import static jdk.graal.compiler.lir.LIRValueUtil.isIntConstant;
import static jdk.vm.ci.aarch64.AArch64.sp;

import java.util.EnumSet;
import java.util.function.Function;

import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.LIRKindTool;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.SwitchStrategy;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64AESDecryptOp;
import jdk.graal.compiler.lir.aarch64.AArch64AESEncryptOp;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.aarch64.AArch64ArithmeticOp;
import jdk.graal.compiler.lir.aarch64.AArch64ArrayCompareToOp;
import jdk.graal.compiler.lir.aarch64.AArch64ArrayCopyWithConversionsOp;
import jdk.graal.compiler.lir.aarch64.AArch64ArrayEqualsOp;
import jdk.graal.compiler.lir.aarch64.AArch64ArrayIndexOfOp;
import jdk.graal.compiler.lir.aarch64.AArch64ArrayRegionCompareToOp;
import jdk.graal.compiler.lir.aarch64.AArch64AtomicMove;
import jdk.graal.compiler.lir.aarch64.AArch64AtomicMove.AtomicReadAndWriteOp;
import jdk.graal.compiler.lir.aarch64.AArch64AtomicMove.CompareAndSwapOp;
import jdk.graal.compiler.lir.aarch64.AArch64BigIntegerMulAddOp;
import jdk.graal.compiler.lir.aarch64.AArch64BigIntegerMultiplyToLenOp;
import jdk.graal.compiler.lir.aarch64.AArch64BigIntegerSquareToLenOp;
import jdk.graal.compiler.lir.aarch64.AArch64ByteSwap;
import jdk.graal.compiler.lir.aarch64.AArch64CacheWritebackOp;
import jdk.graal.compiler.lir.aarch64.AArch64CacheWritebackPostSyncOp;
import jdk.graal.compiler.lir.aarch64.AArch64CalcStringAttributesOp;
import jdk.graal.compiler.lir.aarch64.AArch64CipherBlockChainingAESDecryptOp;
import jdk.graal.compiler.lir.aarch64.AArch64CipherBlockChainingAESEncryptOp;
import jdk.graal.compiler.lir.aarch64.AArch64Compare;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow.BranchOp;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow.CompareBranchZeroOp;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow.CondMoveOp;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow.CondSetOp;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow.HashTableSwitchOp;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow.RangeTableSwitchOp;
import jdk.graal.compiler.lir.aarch64.AArch64ControlFlow.StrategySwitchOp;
import jdk.graal.compiler.lir.aarch64.AArch64CountPositivesOp;
import jdk.graal.compiler.lir.aarch64.AArch64CounterModeAESCryptOp;
import jdk.graal.compiler.lir.aarch64.AArch64EncodeArrayOp;
import jdk.graal.compiler.lir.aarch64.AArch64GHASHProcessBlocksOp;
import jdk.graal.compiler.lir.aarch64.AArch64HaltOp;
import jdk.graal.compiler.lir.aarch64.AArch64MD5Op;
import jdk.graal.compiler.lir.aarch64.AArch64Move;
import jdk.graal.compiler.lir.aarch64.AArch64Move.MembarOp;
import jdk.graal.compiler.lir.aarch64.AArch64PauseOp;
import jdk.graal.compiler.lir.aarch64.AArch64SHA1Op;
import jdk.graal.compiler.lir.aarch64.AArch64SHA256Op;
import jdk.graal.compiler.lir.aarch64.AArch64SHA3Op;
import jdk.graal.compiler.lir.aarch64.AArch64SHA512Op;
import jdk.graal.compiler.lir.aarch64.AArch64SpeculativeBarrier;
import jdk.graal.compiler.lir.aarch64.AArch64StringLatin1InflateOp;
import jdk.graal.compiler.lir.aarch64.AArch64StringUTF16CompressOp;
import jdk.graal.compiler.lir.aarch64.AArch64VectorizedHashCodeOp;
import jdk.graal.compiler.lir.aarch64.AArch64VectorizedMismatchOp;
import jdk.graal.compiler.lir.aarch64.AArch64ZapRegistersOp;
import jdk.graal.compiler.lir.aarch64.AArch64ZapStackOp;
import jdk.graal.compiler.lir.aarch64.AArch64ZeroMemoryOp;
import jdk.graal.compiler.lir.gen.BarrierSetLIRGenerator;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGenerator;
import jdk.graal.compiler.lir.gen.MoveFactory;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public abstract class AArch64LIRGenerator extends LIRGenerator {

    public AArch64LIRGenerator(LIRKindTool lirKindTool, AArch64ArithmeticLIRGenerator arithmeticLIRGen, BarrierSetLIRGenerator barrierSetLIRGen, MoveFactory moveFactory, Providers providers,
                    LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, barrierSetLIRGen, moveFactory, providers, lirGenRes);
    }

    /**
     * If val denotes the stackpointer, move it to another location. This is necessary since most
     * ops cannot handle the stackpointer as input or output.
     */
    public AllocatableValue moveSp(AllocatableValue val) {
        if (val instanceof RegisterValue && ((RegisterValue) val).getRegister().equals(sp)) {
            assert val.getPlatformKind() == AArch64Kind.QWORD : "Stackpointer must be long";
            return emitMove(val);
        }
        return val;
    }

    @Override
    public AArch64BarrierSetLIRGenerator getBarrierSet() {
        return (AArch64BarrierSetLIRGenerator) super.getBarrierSet();
    }

    /**
     * AArch64 cannot use anything smaller than a word in any instruction other than load and store.
     */
    @Override
    public <K extends ValueKind<K>> K toRegisterKind(K kind) {
        switch ((AArch64Kind) kind.getPlatformKind()) {
            case BYTE:
            case WORD:
                return kind.changeType(AArch64Kind.DWORD);
            default:
                return kind;
        }
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        append(new AArch64Move.NullCheckOp(asAddressValue(address, AArch64Address.ANY_SIZE), state));
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        Variable result = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new AArch64Move.StackLoadAddressOp(result, stackslot));
        return result;
    }

    public AArch64AddressValue asAddressValue(Value address, int bitTransferSize) {
        assert address.getPlatformKind() == AArch64Kind.QWORD : address;

        if (address instanceof AArch64AddressValue) {
            return (AArch64AddressValue) address;
        } else {
            return AArch64AddressValue.makeAddress(address.getValueKind(), bitTransferSize, asAllocatable(address));
        }
    }

    /**
     * Returns the appropriate value to use within a comparison if the value is a pointer constant;
     * if not, returns the original value.
     */
    protected Value getCompareValueForConstantPointer(Value v) {
        if (LIRValueUtil.isNullConstant(v)) {
            AArch64ArithmeticLIRGenerator arithLir = ((AArch64ArithmeticLIRGenerator) arithmeticLIRGen);
            JavaConstant constant = asJavaConstant(v);
            if (arithLir.mustReplaceNullWithNullRegister(constant)) {
                return arithLir.getNullRegisterValue();
            }
        }
        return v;
    }

    @Override
    public Variable emitLogicCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue, MemoryOrderMode memoryOrder,
                    BarrierType barrierType) {
        emitCompareAndSwap(true, accessKind, address, expectedValue, newValue, memoryOrder, barrierType);
        assert trueValue.getValueKind().equals(falseValue.getValueKind());
        assert isIntConstant(trueValue, 1) && isIntConstant(falseValue, 0) : trueValue + " " + falseValue;
        Variable result = newVariable(LIRKind.combine(trueValue, falseValue));
        append(new CondSetOp(result, ConditionFlag.EQ));
        return result;
    }

    @Override
    public Variable emitValueCompareAndSwap(LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder, BarrierType barrierType) {
        return emitCompareAndSwap(false, accessKind, address, expectedValue, newValue, memoryOrder, barrierType);
    }

    private Variable emitCompareAndSwap(boolean isLogicVariant, LIRKind accessKind, Value address, Value expectedValue, Value newValue, MemoryOrderMode memoryOrder, BarrierType barrierType) {
        /*
         * Atomic instructions only operate on the general (CPU) registers. Hence, float and double
         * values must temporarily use general registers of the equivalent size.
         */
        LIRKind integerAccessKind = accessKind;
        Value reinterpretedExpectedValue = expectedValue;
        Value reinterpretedNewValue = newValue;
        boolean isFPKind = ((AArch64Kind) integerAccessKind.getPlatformKind()).isSIMD();
        if (isFPKind) {
            if (accessKind.getPlatformKind().equals(AArch64Kind.SINGLE)) {
                integerAccessKind = LIRKind.value(AArch64Kind.DWORD);
            } else {
                assert accessKind.getPlatformKind().equals(AArch64Kind.DOUBLE);
                integerAccessKind = LIRKind.value(AArch64Kind.QWORD);
            }
            reinterpretedExpectedValue = arithmeticLIRGen.emitReinterpret(integerAccessKind, expectedValue);
            reinterpretedNewValue = arithmeticLIRGen.emitReinterpret(integerAccessKind, newValue);
        }
        AArch64Kind memKind = (AArch64Kind) integerAccessKind.getPlatformKind();
        Variable result = newVariable(toRegisterKind(integerAccessKind));
        AllocatableValue allocatableExpectedValue = asAllocatable(reinterpretedExpectedValue);
        AllocatableValue allocatableNewValue = asAllocatable(reinterpretedNewValue);
        emitCompareAndSwapOp(isLogicVariant, address, memoryOrder, memKind, result, allocatableExpectedValue, allocatableNewValue, barrierType);
        if (isLogicVariant) {
            // the returned value is unused
            return null;
        } else {
            // original result is an integer kind, so must convert to FP if necessary
            return isFPKind ? asVariable(arithmeticLIRGen.emitReinterpret(accessKind, result)) : result;
        }
    }

    protected void emitCompareAndSwapOp(boolean isLogicVariant, Value address, MemoryOrderMode memoryOrder, AArch64Kind memKind, Variable result, AllocatableValue allocatableExpectedValue,
                    AllocatableValue allocatableNewValue, BarrierType barrierType) {
        if (barrierType != BarrierType.NONE && getBarrierSet() != null) {
            getBarrierSet().emitCompareAndSwapOp(isLogicVariant, address, memoryOrder, memKind, result, allocatableExpectedValue, allocatableNewValue, barrierType);
        } else {
            append(new CompareAndSwapOp(memKind, memoryOrder, isLogicVariant, result, allocatableExpectedValue, allocatableNewValue, asAllocatable(address)));
        }
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRKind accessKind, Value address, Value newValue, BarrierType barrierType) {
        if (barrierType != BarrierType.NONE && getBarrierSet() != null) {
            return getBarrierSet().emitAtomicReadAndWrite(accessKind, address, newValue, barrierType);
        } else {
            Variable result = newVariable(toRegisterKind(accessKind));
            append(new AtomicReadAndWriteOp((AArch64Kind) accessKind.getPlatformKind(), result, asAllocatable(address), asAllocatable(newValue)));
            return result;
        }
    }

    @Override
    public Value emitAtomicReadAndAdd(LIRKind accessKind, Value address, Value delta) {
        Variable result = newVariable(toRegisterKind(accessKind));
        append(AArch64AtomicMove.createAtomicReadAndAdd(this, (AArch64Kind) accessKind.getPlatformKind(), result, asAllocatable(address), delta));
        return result;
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    @Override
    public void emitJump(LabelRef label) {
        assert label != null;
        append(new StandardOp.JumpOp(label));
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpKind, double overflowProbability) {
        append(new AArch64ControlFlow.BranchOp(ConditionFlag.VS, overflow, noOverflow, overflowProbability));
    }

    /**
     * Branches to label if (left & right) == 0. If negated is true branchse on non-zero instead.
     *
     * @param left Integer kind. Non null.
     * @param right Integer kind. Non null.
     * @param trueDestination destination if left & right == 0. Non null.
     * @param falseDestination destination if left & right != 0. Non null
     * @param trueSuccessorProbability hoistoric probability that comparison is true
     */
    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {
        assert ((AArch64Kind) left.getPlatformKind()).isInteger() && left.getPlatformKind() == right.getPlatformKind() : right + " " + left;
        ((AArch64ArithmeticLIRGenerator) getArithmetic()).emitBinary(AArch64.zr.asValue(LIRKind.combine(left, right)), AArch64ArithmeticOp.TST, true, left, right);
        append(new AArch64ControlFlow.BranchOp(ConditionFlag.EQ, trueDestination, falseDestination, trueSuccessorProbability));
    }

    @Override
    public void emitOpMaskTestBranch(Value left, boolean negateLeft, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {
        throw GraalError.unsupportedArchitecture(target().arch);
    }

    @Override
    public void emitOpMaskOrTestBranch(Value left, Value right, boolean allZeros, LabelRef trueDestination, LabelRef falseDestination, double trueSuccessorProbability) {
        throw GraalError.unsupportedArchitecture(target().arch);
    }

    /**
     * Conditionally move trueValue into new variable if cond + unorderedIsTrue is true, else
     * falseValue.
     *
     * @param left Arbitrary value. Has to have same type as right. Non null.
     * @param right Arbitrary value. Has to have same type as left. Non null.
     * @param cond condition that decides whether to move trueValue or falseValue into result. Non
     *            null.
     * @param unorderedIsTrue defines whether floating-point comparisons consider unordered true or
     *            not. Ignored for integer comparisons.
     * @param trueValue arbitrary value same type as falseValue. Non null.
     * @param falseValue arbitrary value same type as trueValue. Non null.
     * @return value containing trueValue if cond + unorderedIsTrue is true, else falseValue. Non
     *         null.
     */
    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, final Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        boolean mirrored = emitCompare(cmpKind, left, right, cond, unorderedIsTrue);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        // Note mirroring does *not* affect unorderedIsTrue
        ConditionFlag cmpCondition = toConditionFlag(((AArch64Kind) cmpKind).isInteger(), finalCondition, unorderedIsTrue);
        Variable result = newVariable(LIRKind.mergeReferenceInformation(trueValue, falseValue));

        if (isIntConstant(trueValue, 1) && isIntConstant(falseValue, 0)) {
            append(new CondSetOp(result, cmpCondition));
        } else if (isIntConstant(trueValue, 0) && isIntConstant(falseValue, 1)) {
            append(new CondSetOp(result, cmpCondition.negate()));
        } else {
            append(new CondMoveOp(result, cmpCondition, asAllocatable(trueValue), asAllocatable(falseValue)));
        }
        return result;
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, final Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        Value leftVal = getCompareValueForConstantPointer(left);
        Value rightVal = getCompareValueForConstantPointer(right);

        if (cond == Condition.EQ || cond == Condition.NE) {
            // try to use cbz instruction for comparisons against zero
            boolean leftZero = LIRValueUtil.isNullConstant(leftVal) || isIntConstant(leftVal, 0);
            boolean rightZero = LIRValueUtil.isNullConstant(rightVal) || isIntConstant(rightVal, 0);

            if (rightZero || leftZero) {
                Value cbzValue = rightZero ? leftVal : rightVal;
                LabelRef cbzTrueDest;
                LabelRef cbzFalseDest;
                double cbzProbability;
                if (cond == Condition.EQ) {
                    cbzTrueDest = trueDestination;
                    cbzFalseDest = falseDestination;
                    cbzProbability = trueDestinationProbability;
                } else {
                    // flip the destinations and the probability
                    cbzTrueDest = falseDestination;
                    cbzFalseDest = trueDestination;
                    cbzProbability = 1 - trueDestinationProbability;
                }

                append(new CompareBranchZeroOp(asAllocatable(cbzValue), cbzTrueDest, cbzFalseDest,
                                cbzProbability));
                return;
            }
        }

        boolean mirrored = emitCompare(cmpKind, leftVal, rightVal, cond, unorderedIsTrue);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        // Note mirroring does *not* affect unorderedIsTrue
        ConditionFlag cmpCondition = toConditionFlag(((AArch64Kind) cmpKind).isInteger(), finalCondition, unorderedIsTrue);
        append(new BranchOp(cmpCondition, trueDestination, falseDestination, trueDestinationProbability));
    }

    private static ConditionFlag toConditionFlag(boolean isInt, Condition cond, boolean unorderedIsTrue) {
        return isInt ? toIntConditionFlag(cond) : toFloatConditionFlag(cond, unorderedIsTrue);
    }

    /**
     * Takes a Condition and unorderedIsTrue flag and returns the correct AArch64 specific
     * ConditionFlag. Note: This is only correct if the emitCompare code for floats correctly
     * handles 'EQ && unorderedIsTrue' and 'NE && !unorderedIsTrue'!
     */
    private static ConditionFlag toFloatConditionFlag(Condition cond, boolean unorderedIsTrue) {
        switch (cond) {
            case LT:
                return unorderedIsTrue ? ConditionFlag.LT : ConditionFlag.LO;
            case LE:
                return unorderedIsTrue ? ConditionFlag.LE : ConditionFlag.LS;
            case GE:
                return unorderedIsTrue ? ConditionFlag.PL : ConditionFlag.GE;
            case GT:
                return unorderedIsTrue ? ConditionFlag.HI : ConditionFlag.GT;
            case EQ:
                return ConditionFlag.EQ;
            case NE:
                return ConditionFlag.NE;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(cond); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Takes a Condition and returns the correct AArch64 specific ConditionFlag.
     */
    private static ConditionFlag toIntConditionFlag(Condition cond) {
        switch (cond) {
            case EQ:
                return ConditionFlag.EQ;
            case NE:
                return ConditionFlag.NE;
            case LT:
                return ConditionFlag.LT;
            case LE:
                return ConditionFlag.LE;
            case GT:
                return ConditionFlag.GT;
            case GE:
                return ConditionFlag.GE;
            case AE:
                return ConditionFlag.HS;
            case BE:
                return ConditionFlag.LS;
            case AT:
                return ConditionFlag.HI;
            case BT:
                return ConditionFlag.LO;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(cond); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * This method emits the compare instruction, and may mirror (switch) the operands. It returns
     * true if it did so.
     *
     * @param a the left operand of the comparison. Has to have same type as b. Non null.
     * @param b the right operand of the comparison. Has to have same type as a. Non null.
     * @return true if mirrored (i.e. "b cmp a" instead of "a cmp b" was done).
     */
    protected boolean emitCompare(PlatformKind cmpKind, Value a, Value b, Condition condition, boolean unorderedIsTrue) {
        Value left;
        Value right;
        boolean mirrored;
        AArch64Kind kind = (AArch64Kind) cmpKind;

        Value aVal = getCompareValueForConstantPointer(a);
        Value bVal = getCompareValueForConstantPointer(b);
        /*
         * AArch64 compares 32 or 64 bits. Note currently the size of the comparison within
         * AArch64Compare is based on the size of the operands, not the comparison size provided.
         *
         * This minimum comparison size is defined in
         * AArch64LoweringProviderMixin::smallestCompareWidth.
         */
        assert aVal.getPlatformKind() == bVal.getPlatformKind() : aVal + " " + bVal;
        int cmpBitSize = cmpKind.getSizeInBytes() * Byte.SIZE;
        GraalError.guarantee(cmpBitSize >= 32 && cmpKind == aVal.getPlatformKind(), "Unexpected comparison parameters.");

        /*
         * The AArch64 integer comparison instruction left operand can be the stack pointer register
         * (sp), but not the right operand.
         */
        boolean aIsStackPointer;
        boolean bIsStackPointer;
        boolean aIsConstant;
        boolean bIsConstant;
        if (kind.isInteger()) {
            aIsStackPointer = ValueUtil.isRegister(aVal) && ValueUtil.asRegister(aVal).equals(AArch64.sp);
            bIsStackPointer = ValueUtil.isRegister(bVal) && ValueUtil.asRegister(bVal).equals(AArch64.sp);
            aIsConstant = AArch64Compare.CompareOp.isCompareConstant(aVal);
            bIsConstant = AArch64Compare.CompareOp.isCompareConstant(bVal);
        } else {
            assert kind.isSIMD();
            // sp is an integer register
            aIsStackPointer = false;
            bIsStackPointer = false;
            aIsConstant = AArch64Compare.FloatCompareOp.isCompareConstant(aVal, condition, unorderedIsTrue);
            bIsConstant = AArch64Compare.FloatCompareOp.isCompareConstant(bVal, condition, unorderedIsTrue);
        }

        if (aIsStackPointer && bIsStackPointer) {
            /*
             * If both a and b are sp, this cannot be encoded in an AArch64 comparison. Hence, sp
             * must be moved to a register.
             */
            left = right = emitMove(aVal);
            mirrored = false;
        } else if (bIsStackPointer || (aIsConstant && !bIsConstant)) {
            left = bVal;
            right = aVal;
            mirrored = true;
        } else {
            left = aVal;
            right = bIsConstant ? bVal : asAllocatable(bVal);
            mirrored = false;
        }
        left = asAllocatable(left);
        append(kind.isInteger() ? new AArch64Compare.CompareOp(left, right) : new AArch64Compare.FloatCompareOp(left, right, condition, unorderedIsTrue));
        return mirrored;
    }

    /**
     * Moves trueValue into result if (left & right) == 0, else falseValue.
     *
     * @param left Integer kind. Non null.
     * @param right Integer kind. Non null.
     * @param trueValue Arbitrary value, same type as falseValue. Non null.
     * @param falseValue Arbitrary value, same type as trueValue. Non null.
     * @return virtual register containing trueValue if (left & right) == 0, else falseValue.
     */
    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        assert left.getPlatformKind() == right.getPlatformKind() && ((AArch64Kind) left.getPlatformKind()).isInteger() : left + " " + right;
        assert trueValue.getPlatformKind() == falseValue.getPlatformKind() : Assertions.errorMessage(trueValue, falseValue);
        ((AArch64ArithmeticLIRGenerator) getArithmetic()).emitBinary(AArch64.zr.asValue(LIRKind.combine(left, right)), AArch64ArithmeticOp.TST, true, left, right);
        Variable result = newVariable(LIRKind.mergeReferenceInformation(trueValue, falseValue));

        if (isIntConstant(trueValue, 1) && isIntConstant(falseValue, 0)) {
            append(new CondSetOp(result, ConditionFlag.EQ));
        } else if (isIntConstant(trueValue, 0) && isIntConstant(falseValue, 1)) {
            append(new CondSetOp(result, ConditionFlag.NE));
        } else {
            append(new CondMoveOp(result, ConditionFlag.EQ, asAllocatable(trueValue), asAllocatable(falseValue)));
        }
        return result;
    }

    @Override
    public Variable emitOpMaskTestMove(Value leftVal, boolean negateLeft, Value right, Value trueValue, Value falseValue) {
        throw GraalError.unsupportedArchitecture(target().arch);
    }

    @Override
    public Variable emitOpMaskOrTestMove(Value leftVal, Value right, boolean allZeros, Value trueValue, Value falseValue) {
        throw GraalError.unsupportedArchitecture(target().arch);
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, AllocatableValue key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        append(createStrategySwitchOp(strategy, keyTargets, defaultTarget, key, AArch64LIRGenerator::toIntConditionFlag));
    }

    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, AllocatableValue key, Function<Condition, ConditionFlag> converter) {
        return new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, converter);
    }

    @Override
    protected void emitRangeTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue key) {
        append(new RangeTableSwitchOp(lowKey, defaultTarget, targets, key));
    }

    @Override
    protected void emitHashTableSwitch(JavaConstant[] keys, LabelRef defaultTarget, LabelRef[] targets, AllocatableValue value, Value hash) {
        append(new HashTableSwitchOp(keys, defaultTarget, targets, value, asAllocatable(hash)));
    }

    @Override
    public Variable emitReverseBytes(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AArch64ByteSwap.ByteSwapOp(result, asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitArrayCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value lengthA, Value arrayB, Value lengthB) {
        LIRKind resultKind = LIRKind.value(AArch64Kind.DWORD);
        // DMS TODO: check calling conversion and registers used
        RegisterValue res = AArch64.r0.asValue(resultKind);
        RegisterValue cntA = AArch64.r1.asValue(lengthA.getValueKind());
        RegisterValue cntB = AArch64.r2.asValue(lengthB.getValueKind());
        emitMove(cntA, lengthA);
        emitMove(cntB, lengthB);
        append(new AArch64ArrayCompareToOp(this, strideA, strideB, res, arrayA, cntA, arrayB, cntB));
        Variable result = newVariable(resultKind);
        emitMove(result, res);
        return result;
    }

    private AllocatableValue emitConvertNullToZero(Value value) {
        AllocatableValue result = newVariable(LIRKind.unknownReference(target().arch.getWordKind()));
        emitConvertNullToZero(result, value);
        return result;
    }

    @Override
    public Variable emitArrayRegionCompareTo(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64ArrayRegionCompareToOp(this, strideA, strideB, result,
                        emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB), asAllocatable(length), null));
        return result;
    }

    @Override
    public Variable emitArrayRegionCompareTo(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64ArrayRegionCompareToOp(this, null, null, result,
                        emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB), asAllocatable(length), asAllocatable(dynamicStrides)));
        return result;
    }

    @Override
    public void emitArrayCopyWithConversion(Stride strideSrc, Stride strideDst, EnumSet<?> runtimeCheckedCPUFeatures, Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length) {
        append(new AArch64ArrayCopyWithConversionsOp(this, strideSrc, strideDst,
                        emitConvertNullToZero(arrayDst), asAllocatable(offsetDst), emitConvertNullToZero(arraySrc), asAllocatable(offsetSrc), asAllocatable(length), null));
    }

    @Override
    public void emitArrayCopyWithConversion(EnumSet<?> runtimeCheckedCPUFeatures, Value arraySrc, Value offsetSrc, Value arrayDst, Value offsetDst, Value length, Value dynamicStrides) {
        append(new AArch64ArrayCopyWithConversionsOp(this, null, null,
                        emitConvertNullToZero(arrayDst), asAllocatable(offsetDst), emitConvertNullToZero(arraySrc), asAllocatable(offsetSrc), asAllocatable(length), asAllocatable(dynamicStrides)));
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        GraalError.guarantee(!kind.isNumericFloat(), "Float arrays comparison (bitwise_equal || both_NaN) isn't supported on AARCH64");
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        Stride stride = Stride.fromJavaKind(kind);
        append(new AArch64ArrayEqualsOp(this, stride, stride, stride, result,
                        emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB), asAllocatable(length), null, null));
        return result;
    }

    @Override
    public Variable emitArrayEquals(Stride strideA, Stride strideB, EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64ArrayEqualsOp(this, strideA, strideB, strideB, result,
                        emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB), asAllocatable(length), null, null));
        return result;
    }

    @Override
    public Variable emitArrayEqualsDynamicStrides(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value length, Value dynamicStrides) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64ArrayEqualsOp(this, null, null, null, result,
                        emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB), asAllocatable(length), null, asAllocatable(dynamicStrides)));
        return result;
    }

    @Override
    public Variable emitArrayEqualsWithMask(Stride strideA, Stride strideB, Stride strideMask, EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB,
                    Value mask, Value length) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64ArrayEqualsOp(this, strideA, strideB, strideMask, result,
                        emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB), asAllocatable(length), asAllocatable(mask), null));
        return result;
    }

    @Override
    public Variable emitArrayEqualsWithMaskDynamicStrides(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value offsetA, Value arrayB, Value offsetB, Value mask, Value length,
                    Value dynamicStrides) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64ArrayEqualsOp(this, null, null, null, result,
                        emitConvertNullToZero(arrayA), asAllocatable(offsetA), emitConvertNullToZero(arrayB), asAllocatable(offsetB), asAllocatable(length), asAllocatable(mask),
                        asAllocatable(dynamicStrides)));
        return result;
    }

    @Override
    public Variable emitArrayIndexOf(Stride stride, ArrayIndexOfVariant variant, EnumSet<?> runtimeCheckedCPUFeatures,
                    Value arrayPointer, Value arrayOffset, Value arrayLength, Value fromIndex, Value... searchValues) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        AllocatableValue[] allocatableSearchValues = new AllocatableValue[searchValues.length];
        for (int i = 0; i < searchValues.length; i++) {
            allocatableSearchValues[i] = asAllocatable(searchValues[i]);
        }
        append(new AArch64ArrayIndexOfOp(stride, variant, this, result, emitConvertNullToZero(arrayPointer), asAllocatable(arrayOffset), asAllocatable(arrayLength),
                        asAllocatable(fromIndex), allocatableSearchValues));
        return result;
    }

    @Override
    public Variable emitEncodeArray(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value length, CharsetName charset) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64EncodeArrayOp(this, result, asAllocatable(src), asAllocatable(dst), asAllocatable(length), charset));
        return result;
    }

    protected abstract int getVMPageSize();

    protected int getSoftwarePrefetchHintDistance() {
        return -1;
    }

    @Override
    public Variable emitCountPositives(EnumSet<?> runtimeCheckedCPUFeatures, Value array, Value length) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64CountPositivesOp(this, result, asAllocatable(array), asAllocatable(length), getVMPageSize(), getSoftwarePrefetchHintDistance()));
        return result;
    }

    @Override
    public void emitAESEncrypt(Value from, Value to, Value key) {
        append(new AArch64AESEncryptOp(asAllocatable(from), asAllocatable(to), asAllocatable(key), getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
    }

    @Override
    public void emitAESDecrypt(Value from, Value to, Value key) {
        append(new AArch64AESDecryptOp(asAllocatable(from), asAllocatable(to), asAllocatable(key), getArrayLengthOffset() - getArrayBaseOffset(JavaKind.Int)));
    }

    @Override
    public Variable emitCTRAESCrypt(Value inAddr, Value outAddr, Value kAddr, Value counterAddr, Value len, Value encryptedCounterAddr, Value usedPtr) {
        Variable result = newVariable(len.getValueKind());
        append(new AArch64CounterModeAESCryptOp(asAllocatable(inAddr),
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
        append(new AArch64CipherBlockChainingAESEncryptOp(this,
                        asAllocatable(inAddr),
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
        append(new AArch64CipherBlockChainingAESDecryptOp(this,
                        asAllocatable(inAddr),
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
        append(new AArch64GHASHProcessBlocksOp(this, asAllocatable(state), asAllocatable(hashSubkey), asAllocatable(data), asAllocatable(blocks)));
    }

    @Override
    public void emitBigIntegerMultiplyToLen(Value x, Value xlen, Value y, Value ylen, Value z, Value zlen) {
        append(new AArch64BigIntegerMultiplyToLenOp(asAllocatable(x), asAllocatable(xlen), asAllocatable(y), asAllocatable(ylen), asAllocatable(z), asAllocatable(zlen)));
    }

    @Override
    public Variable emitBigIntegerMulAdd(Value out, Value in, Value offset, Value len, Value k) {
        Variable result = newVariable(len.getValueKind());
        append(new AArch64BigIntegerMulAddOp(this, asAllocatable(out), asAllocatable(in), asAllocatable(offset), asAllocatable(len), asAllocatable(k), asAllocatable(result)));
        return result;
    }

    @Override
    public void emitBigIntegerSquareToLen(Value x, Value len, Value z, Value zlen) {
        append(new AArch64BigIntegerSquareToLenOp(asAllocatable(x), asAllocatable(len), asAllocatable(z), asAllocatable(zlen)));
    }

    @Override
    public void emitSha1ImplCompress(Value buf, Value state) {
        append(new AArch64SHA1Op(this, asAllocatable(buf), asAllocatable(state)));
    }

    @Override
    public void emitSha256ImplCompress(Value buf, Value state) {
        append(new AArch64SHA256Op(this, asAllocatable(buf), asAllocatable(state)));
    }

    @Override
    public void emitSha3ImplCompress(Value buf, Value state, Value blockSize) {
        append(new AArch64SHA3Op(this, asAllocatable(buf), asAllocatable(state), asAllocatable(blockSize)));
    }

    @Override
    public void emitSha512ImplCompress(Value buf, Value state) {
        append(new AArch64SHA512Op(this, asAllocatable(buf), asAllocatable(state)));
    }

    @Override
    public void emitMD5ImplCompress(Value buf, Value state) {
        append(new AArch64MD5Op(this, asAllocatable(buf), asAllocatable(state)));
    }

    @Override
    public Variable emitCalcStringAttributes(CalcStringAttributesEncoding encoding, EnumSet<?> runtimeCheckedCPUFeatures, Value array, Value offset, Value length, boolean assumeValid) {
        Variable result = newVariable(LIRKind.value(encoding == CalcStringAttributesEncoding.UTF_8 || encoding == CalcStringAttributesEncoding.UTF_16 ? AArch64Kind.QWORD : AArch64Kind.DWORD));
        append(new AArch64CalcStringAttributesOp(this, encoding, emitConvertNullToZero(array), asAllocatable(offset), asAllocatable(length), result, assumeValid));
        return result;
    }

    @Override
    public void emitStringLatin1Inflate(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value len) {
        append(new AArch64StringLatin1InflateOp(this, asAllocatable(src), asAllocatable(dst), asAllocatable(len)));
    }

    @Override
    public Variable emitStringUTF16Compress(EnumSet<?> runtimeCheckedCPUFeatures, Value src, Value dst, Value len) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64StringUTF16CompressOp(this, asAllocatable(src), asAllocatable(dst), asAllocatable(len), result));
        return result;
    }

    @Override
    public Variable emitVectorizedMismatch(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayA, Value arrayB, Value length, Value stride) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64VectorizedMismatchOp(this, result, asAllocatable(arrayA), asAllocatable(arrayB), asAllocatable(length), asAllocatable(stride)));
        return result;
    }

    @Override
    public Variable emitVectorizedHashCode(EnumSet<?> runtimeCheckedCPUFeatures, Value arrayStart, Value length, Value initialValue, JavaKind arrayKind) {
        Variable result = newVariable(LIRKind.value(AArch64Kind.DWORD));
        append(new AArch64VectorizedHashCodeOp(this,
                        result, asAllocatable(arrayStart), asAllocatable(length), asAllocatable(initialValue), arrayKind));
        return result;
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        AArch64Kind aarch64Kind = (AArch64Kind) kind;
        switch (aarch64Kind) {
            case BYTE:
                return JavaConstant.forByte((byte) dead);
            case WORD:
                return JavaConstant.forShort((short) dead);
            case DWORD:
                return JavaConstant.forInt((int) dead);
            case QWORD:
                return JavaConstant.forLong(dead);
            default:
                /*
                 * Floating Point and SIMD values are assigned either a float or a double constant
                 * based on their size. Note that in the case of a 16 byte SIMD value, such as
                 * V128_Byte, only the bottom 8 bytes are zapped.
                 */
                assert aarch64Kind.isSIMD();
                if (aarch64Kind.getSizeInBytes() <= AArch64Kind.SINGLE.getSizeInBytes()) {
                    return JavaConstant.forFloat(Float.intBitsToFloat((int) dead));
                } else {
                    return JavaConstant.forDouble(Double.longBitsToDouble(dead));
                }
        }
    }

    @Override
    public void emitPause() {
        append(new AArch64PauseOp());
    }

    @Override
    public void emitHalt() {
        append(new AArch64HaltOp());
    }

    @Override
    public void emitCacheWriteback(Value address) {
        append(new AArch64CacheWritebackOp(asAddressValue(address, AArch64Address.ANY_SIZE)));
    }

    @Override
    public void emitCacheWritebackSync(boolean isPreSync) {
        // only need a post sync barrier on AArch64
        if (!isPreSync) {
            append(new AArch64CacheWritebackPostSyncOp());
        }
    }

    @Override
    public LIRInstruction createZapRegisters(Register[] zappedRegisters, JavaConstant[] zapValues) {
        return new AArch64ZapRegistersOp(zappedRegisters, zapValues);
    }

    @Override
    public LIRInstruction createZapArgumentSpace(StackSlot[] zappedStack, JavaConstant[] zapValues) {
        return new AArch64ZapStackOp(zappedStack, zapValues);
    }

    @Override
    public void emitSpeculationFence() {
        append(new AArch64SpeculativeBarrier());
    }

    @Override
    public void emitZeroMemory(Value address, Value length, boolean isAligned) {
        emitZeroMemory(address, length, isAligned, false, -1);
    }

    protected final void emitZeroMemory(Value address, Value length, boolean isAligned, boolean useDcZva, int zvaLength) {
        RegisterValue regAddress = AArch64.r0.asValue(address.getValueKind());
        RegisterValue regLength = AArch64.r1.asValue(length.getValueKind());
        emitMove(regAddress, address);
        emitMove(regLength, length);
        append(new AArch64ZeroMemoryOp(regAddress, regLength, isAligned, useDcZva, zvaLength));
    }
}
