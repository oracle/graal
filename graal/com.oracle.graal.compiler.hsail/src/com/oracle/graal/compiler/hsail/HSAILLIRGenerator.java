/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.hsail.HSAILArithmetic.*;
import static com.oracle.graal.lir.hsail.HSAILBitManipulationOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.hsail.HSAILCompare.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.lir.hsail.HSAILArithmetic.Op1Reg;
import com.oracle.graal.lir.hsail.HSAILArithmetic.Op2Reg;
import com.oracle.graal.lir.hsail.HSAILControlFlow.*;
import com.oracle.graal.lir.hsail.HSAILMove.*;
import com.oracle.graal.phases.util.*;

/**
 * This class implements the HSAIL specific portion of the LIR generator.
 */
public abstract class HSAILLIRGenerator extends LIRGenerator {

    public class HSAILSpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(AllocatableValue dst, Value src) {
            return HSAILLIRGenerator.this.createMove(dst, src);
        }
    }

    public HSAILLIRGenerator(Providers providers, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(providers, cc, lirGenRes);
        lirGenRes.getLIR().setSpillMoveFactory(new HSAILSpillMoveFactory());
    }

    @Override
    public boolean canStoreConstant(Constant c, boolean isCompressed) {
        // Operand b must be in the .reg state space.
        return false;
    }

    @Override
    public boolean canInlineConstant(Constant c) {
        switch (c.getKind()) {
            case Long:
                return NumUtil.isInt(c.asLong()) && !getCodeCache().needsDataPatch(c);
            case Object:
                return c.isNull();
            default:
                return true;
        }
    }

    @Override
    public Variable emitMove(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        emitMove(result, input);
        return result;
    }

    protected HSAILLIRInstruction createMove(AllocatableValue dst, Value src) {
        if (src instanceof HSAILAddressValue) {
            return new LeaOp(dst, (HSAILAddressValue) src);
        } else if (isRegister(src) || isStackSlot(dst)) {
            return new MoveFromRegOp(dst.getKind(), dst, src);
        } else {
            return new MoveToRegOp(dst.getKind(), dst, src);
        }
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        append(createMove(dst, src));
    }

    public void emitData(AllocatableValue dst, byte[] data) {
        throw GraalInternalError.unimplemented();
    }

    public HSAILAddressValue asAddressValue(Value address) {
        if (address instanceof HSAILAddressValue) {
            return (HSAILAddressValue) address;
        } else {
            return emitAddress(address, 0, Value.ILLEGAL, 0);
        }
    }

    public HSAILAddressValue emitAddress(Value base, long displacement, Value index, int scale) {
        AllocatableValue baseRegister;
        long finalDisp = displacement;

        if (isConstant(base)) {
            if (asConstant(base).isNull()) {
                baseRegister = Value.ILLEGAL;
            } else if (asConstant(base).getKind() != Kind.Object) {
                finalDisp += asConstant(base).asLong();
                baseRegister = Value.ILLEGAL;
            } else {
                baseRegister = load(base);
            }
        } else if (base.equals(Value.ILLEGAL)) {
            baseRegister = Value.ILLEGAL;
        } else {
            baseRegister = asAllocatable(base);
        }
        if (!index.equals(Value.ILLEGAL)) {
            if (isConstant(index)) {
                finalDisp += asConstant(index).asLong() * scale;
            } else {
                Value indexRegister;
                Value convertedIndex = index.getKind() == Kind.Long ? index : this.emitSignExtend(index, 32, 64);
                if (scale != 1) {
                    indexRegister = emitUMul(convertedIndex, Constant.forInt(scale));
                } else {
                    indexRegister = convertedIndex;
                }
                if (baseRegister.equals(Value.ILLEGAL)) {
                    baseRegister = asAllocatable(indexRegister);
                } else {
                    baseRegister = emitAdd(baseRegister, indexRegister);
                }
            }
        }
        return new HSAILAddressValue(target().wordKind, baseRegister, finalDisp);
    }

    @Override
    public Variable emitAddress(StackSlot address) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitJump(LabelRef label) {
        append(new JumpOp(label));
    }

    public static HSAILCompare mapKindToCompareOp(Kind kind) {
        switch (kind) {
            case Int:
                return ICMP;
            case Long:
                return LCMP;
            case Float:
                return FCMP;
            case Double:
                return DCMP;
            case Object:
                return ACMP;
            default:
                throw GraalInternalError.shouldNotReachHere("" + kind);
        }
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        // We don't have to worry about mirroring the condition on HSAIL.
        Condition finalCondition = cond;
        Variable result = newVariable(left.getKind());
        Kind kind = left.getKind().getStackKind();
        switch (kind) {
            case Int:
            case Long:
            case Object:
                append(new CompareBranchOp(mapKindToCompareOp(kind), finalCondition, left, right, result, result, trueDestination, falseDestination, false));
                break;
            case Float:
            case Double:
                append(new CompareBranchOp(mapKindToCompareOp(kind), finalCondition, left, right, result, result, trueDestination, falseDestination, unorderedIsTrue));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        Variable result = emitAnd(left, right);
        Variable dummyResult = newVariable(left.getKind());
        append(new CompareBranchOp(mapKindToCompareOp(left.getKind()), Condition.EQ, result, Constant.forInt(0), dummyResult, dummyResult, trueDestination, falseDestination, false));
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        Condition finalCondition = cond;
        Variable result = newVariable(trueValue.getKind());
        Kind kind = left.getKind().getStackKind();
        switch (kind) {
            case Int:
            case Long:
            case Object:
                append(new CondMoveOp(mapKindToCompareOp(kind), load(left), load(right), result, finalCondition, load(trueValue), load(falseValue)));
                break;
            case Float:
            case Double:
                append(new FloatCondMoveOp(mapKindToCompareOp(kind), load(left), load(right), result, finalCondition, unorderedIsTrue, load(trueValue), load(falseValue)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + left.getKind());
        }
        return result;
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Generates the LIR instruction for a negation operation.
     *
     * @param input the value that is being negated
     * @return Variable that represents the result of the negation
     */
    @Override
    public Variable emitNegate(Value input) {
        Variable result = newVariable(input.getKind());
        switch (input.getKind()) {
            case Int:
                // Note: The Int case also handles the negation of shorts, bytes, and chars because
                // Java treats these types as ints at the bytecode level.
                append(new Op1Reg(INEG, result, input));
                break;
            case Long:
                append(new Op1Reg(LNEG, result, input));
                break;
            case Double:
                append(new Op1Reg(DNEG, result, input));
                break;
            case Float:
                append(new Op1Reg(FNEG, result, input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;

    }

    /**
     * Generates the LIR instruction for a bitwise NOT operation.
     *
     * @param input the source operand
     * @return Variable that represents the result of the operation
     */
    @Override
    public Variable emitNot(Value input) {
        Variable result = newVariable(input.getKind());
        switch (input.getKind()) {
            case Int:
                // Note: The Int case also covers other primitive integral types smaller than an int
                // (char, byte, short) because Java treats these types as ints.
                append(new Op1Reg(INOT, result, input));
                break;
            case Long:
                append(new Op1Reg(LNOT, result, input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;

    }

    public Variable emitTestAddressAdd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IADD, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LADD, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Reg(FADD, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Reg(DADD, result, a, loadNonConst(b)));
                break;
            case Object:
                throw GraalInternalError.shouldNotReachHere();
            default:
                throw GraalInternalError.shouldNotReachHere();
        }

        return result;
    }

    @Override
    public Variable emitAdd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IADD, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LADD, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Reg(FADD, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Reg(DADD, result, a, loadNonConst(b)));
                break;
            case Object:
                append(new Op2Reg(OADD, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitSub(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(ISUB, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Reg(FSUB, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LSUB, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Reg(DSUB, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitMul(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IMUL, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LMUL, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Reg(FMUL, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Reg(DMUL, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    public Variable emitUMul(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(LUMUL, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LUMUL, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IDIV, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LDIV, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Reg(FDIV, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Reg(DDIV, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;

    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IREM, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LREM, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Reg(FREM, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Reg(DREM, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitUDiv(Value a, Value b, LIRFrameState state) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Variable emitURem(Value a, Value b, LIRFrameState state) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IAND, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LAND, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IOR, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LOR, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IXOR, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LXOR, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    /**
     * Generates the LIR instruction for a shift left operation.
     *
     * @param a The value that is being shifted
     * @param b The shift amount
     * @return Variable that represents the result of the operation
     */
    @Override
    public Variable emitShl(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                // Note: The Int case also covers the shifting of bytes, shorts and chars because
                // Java treats these types as ints at the bytecode level.
                append(new ShiftOp(ISHL, result, a, b));
                break;
            case Long:
                append(new ShiftOp(LSHL, result, a, b));
                break;
            default:
                GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    /**
     * Generates the LIR instruction for a shift right operation.
     *
     * @param a The value that is being shifted
     * @param b The shift amount
     * @return Variable that represents the result of the operation
     */
    @Override
    public Variable emitShr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                // Note: The Int case also covers the shifting of bytes, shorts and chars because
                // Java treats these types as ints at the bytecode level.
                append(new ShiftOp(ISHR, result, a, b));
                break;
            case Long:
                append(new ShiftOp(LSHR, result, a, b));
                break;
            default:
                GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    /**
     * Generates the LIR instruction for an unsigned shift right operation.
     *
     * @param a The value that is being shifted
     * @param b The shift amount
     * @return Variable that represents the result of the operation
     */
    @Override
    public Variable emitUShr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new ShiftOp(IUSHR, result, a, b));
                break;
            case Long:
                append(new ShiftOp(LUSHR, result, a, b));
                break;
            default:
                GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        Variable input = load(inputVal);

        String from;
        switch (op) {
            case D2F:
            case D2I:
            case D2L:
                from = "f64";
                break;
            case F2D:
            case F2I:
            case F2L:
                from = "f32";
                break;
            case I2D:
            case I2F:
                from = "s32";
                break;
            case L2D:
            case L2F:
                from = "s64";
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }

        Variable result;
        String to;
        switch (op) {
            case D2I:
            case F2I:
                to = "s32";
                result = newVariable(Kind.Int);
                break;
            case D2L:
            case F2L:
                to = "s64";
                result = newVariable(Kind.Long);
                break;
            case F2D:
            case I2D:
            case L2D:
                to = "f64";
                result = newVariable(Kind.Double);
                break;
            case D2F:
            case I2F:
            case L2F:
                to = "f32";
                result = newVariable(Kind.Float);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }

        append(new ConvertOp(result, input, to, from));
        return result;
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        Variable input = load(inputVal);
        Variable result = newVariable(bits > 32 ? Kind.Long : Kind.Int);
        append(new ConvertOp(result, input, "s" + bits, input.getKind() == Kind.Long ? "s64" : "s32"));
        return result;
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        Variable input = load(inputVal);
        Variable result = newVariable(toBits > 32 ? Kind.Long : Kind.Int);
        append(new ConvertOp(result, input, "s" + toBits, "s" + fromBits));
        return result;
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        Variable input = load(inputVal);
        Variable result = newVariable(toBits > 32 ? Kind.Long : Kind.Int);
        append(new ConvertOp(result, input, "u" + toBits, "u" + fromBits));
        return result;
    }

    @Override
    public Value emitReinterpret(PlatformKind to, Value inputVal) {
        Variable result = newVariable(to);
        emitMove(result, inputVal);
        return result;
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
        append(new ReturnOp(Value.ILLEGAL));
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        append(new MembarOp(necessaryBarriers));
    }

    @Override
    public void emitBitCount(Variable result, Value value) {
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new HSAILBitManipulationOp(IPOPCNT, result, value));
        } else {
            append(new HSAILBitManipulationOp(LPOPCNT, result, value));
        }
    }

    @Override
    public void emitBitScanForward(Variable result, Value value) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitBitScanReverse(Variable result, Value value) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * Emits the LIR code for the {@link HSAILArithmetic#ABS} operation.
     *
     * @param input the source operand
     * @return Value representing the result of the operation
     */
    @Override
    public Value emitMathAbs(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new Op1Reg(ABS, result, input));
        return result;
    }

    /**
     * Emits the LIR code for the {@link HSAILArithmetic#CEIL} operation.
     *
     * @param input the source operand
     * @return Value representing the result of the operation
     */
    public Value emitMathCeil(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new Op1Reg(CEIL, result, input));
        return result;
    }

    /**
     * Emits the LIR code for the {@link HSAILArithmetic#FLOOR} operation.
     *
     * @param input the source operand
     * @return Value representing the result of the operation
     */
    public Value emitMathFloor(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new Op1Reg(FLOOR, result, input));
        return result;
    }

    /**
     * Emits the LIR code for the {@link HSAILArithmetic#RINT} operation.
     *
     * @param input the source operand
     * @return Value representing the result of the operation
     */
    public Value emitMathRint(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new Op1Reg(RINT, result, input));
        return result;
    }

    /**
     * Emits the LIR code for the {@link HSAILArithmetic#SQRT} operation.
     *
     * @param input the source operand
     * @return value representing the result of the operation
     */
    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new Op1Reg(SQRT, result, input));
        return result;
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Value emitMathCos(Value input) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Value emitMathSin(Value input) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Value emitMathTan(Value input) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitByteSwap(Variable result, Value input) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitReturn(Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(input.getKind());
            emitMove(operand, input);
        }
        append(new ReturnOp(operand));
    }

    /**
     * This routine handles the LIR code generation for switch nodes by calling
     * emitSequentialSwitch.
     *
     * This routine overrides LIRGenerator.emitSwitch( ) which calls emitSequentialSwitch or
     * emitTableSwitch based on a heuristic.
     *
     * The recommended approach in HSAIL for generating performant code for switch statements is to
     * emit a series of cascading compare and branches. Thus this routines always calls
     * emitSequentialSwitch, which implements this approach.
     *
     * Note: Only IntegerSwitchNodes are currently supported. The IntegerSwitchNode is the node that
     * Graal generates for any switch construct appearing in Java bytecode.
     */
    @Override
    public void emitStrategySwitch(Constant[] keyConstants, double[] keyProbabilities, LabelRef[] keyTargets, LabelRef defaultTarget, Variable value) {
        emitStrategySwitch(new SwitchStrategy.SequentialStrategy(keyProbabilities, keyConstants), value, keyTargets, defaultTarget);
    }

    /**
     * Generates the LIR instruction for a switch construct that is meant to be assembled into a
     * series of cascading compare and branch instructions. This is currently the recommended way of
     * generating performant HSAIL code for switch constructs.
     *
     * In Java bytecode the keys for switch statements are always ints.
     *
     * The x86 backend also adds support for handling keys of type long or Object but these two
     * special cases are for handling the TypeSwitchNode, which is a node that the JVM produces for
     * handling operations related to method dispatch. We haven't yet added support for the
     * TypeSwitchNode, so for the time being we have added a check to ensure that the keys are of
     * type int. This also allows us to flag any test cases/execution paths that may trigger the
     * creation of a TypeSwitchNode which we don't support yet.
     *
     *
     * @param strategy the strategy used for this switch.
     * @param keyTargets array of branch targets for each of the cases.
     * @param defaultTarget the branch target for the default case.
     * @param key the key that is compared against the key constants in the case statements.
     */
    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        if ((key.getKind() == Kind.Int) || (key.getKind() == Kind.Long)) {
            // Append the LIR instruction for generating compare and branch instructions.
            append(new StrategySwitchOp(strategy, keyTargets, defaultTarget, key));
        } else {
            // Throw an exception if the keys aren't ints.
            throw GraalInternalError.unimplemented("Switch statements are only supported for keys of type int or long, not " + key.getKind());
        }
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitUnwind(Value operand) {
        throw GraalInternalError.unimplemented();
    }

}
