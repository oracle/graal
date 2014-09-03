/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.sparc.SPARCArithmetic.*;
import static com.oracle.graal.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.sparc.SPARCCompare.*;
import static com.oracle.graal.lir.sparc.SPARCControlFlow.*;
import static com.oracle.graal.lir.sparc.SPARCMathIntrinsicOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.sparc.SPARCMove.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.phases.util.*;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public abstract class SPARCLIRGenerator extends LIRGenerator {

    private class SPARCSpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(AllocatableValue result, Value input) {
            return SPARCLIRGenerator.this.createMove(result, input);
        }
    }

    public SPARCLIRGenerator(Providers providers, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(providers, cc, lirGenRes);
        lirGenRes.getLIR().setSpillMoveFactory(new SPARCSpillMoveFactory());
    }

    @Override
    public boolean canStoreConstant(Constant c) {
        // SPARC can only store integer null constants (via g0)
        switch (c.getKind()) {
            case Float:
            case Double:
                return false;
            default:
                return c.isDefaultForKind();
        }
    }

    @Override
    public boolean canInlineConstant(Constant c) {
        switch (c.getKind()) {
            case Int:
                return SPARCAssembler.isSimm13(c.asInt()) && !getCodeCache().needsDataPatch(c);
            case Long:
                return SPARCAssembler.isSimm13(c.asLong()) && !getCodeCache().needsDataPatch(c);
            case Object:
                return c.isNull();
            default:
                return false;
        }
    }

    @Override
    public Variable emitMove(Value input) {
        Variable result = newVariable(input.getLIRKind());
        emitMove(result, input);
        return result;
    }

    protected SPARCLIRInstruction createMove(AllocatableValue dst, Value src) {
        if (src instanceof SPARCAddressValue) {
            return new LoadAddressOp(dst, (SPARCAddressValue) src);
        } else if (isRegister(src) || isStackSlot(dst)) {
            return new MoveFromRegOp(dst, src);
        } else {
            return new MoveToRegOp(dst, src);
        }
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        append(createMove(dst, src));
    }

    @Override
    public void emitData(AllocatableValue dst, byte[] data) {
        append(new LoadDataAddressOp(dst, data));
    }

    @Override
    public SPARCAddressValue emitAddress(Value base, long displacement, Value index, int scale) {
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
        } else {
            baseRegister = asAllocatable(base);
        }

        AllocatableValue indexRegister;
        if (!index.equals(Value.ILLEGAL) && scale != 0) {
            if (isConstant(index)) {
                finalDisp += asConstant(index).asLong() * scale;
                indexRegister = Value.ILLEGAL;
            } else {
                if (scale != 1) {
                    Value longIndex = index.getKind() == Kind.Long ? index : emitSignExtend(index, 32, 64);
                    if (CodeUtil.isPowerOf2(scale)) {
                        indexRegister = emitShl(longIndex, Constant.forLong(CodeUtil.log2(scale)));
                    } else {
                        indexRegister = emitMul(longIndex, Constant.forLong(scale));
                    }
                } else {
                    indexRegister = asAllocatable(index);
                }

                // if (baseRegister.equals(Value.ILLEGAL)) {
                // baseRegister = asAllocatable(indexRegister);
                // } else {
                // Variable newBase = newVariable(Kind.Long);
                // emitMove(newBase, baseRegister);
                // baseRegister = newBase;
                // baseRegister = emitAdd(baseRegister, indexRegister);
                // }
            }
        } else {
            indexRegister = Value.ILLEGAL;
        }

        int displacementInt;

        // If we don't have an index register we can use a displacement, otherwise load the
        // displacement into a register and add it to the base.
        if (indexRegister.equals(Value.ILLEGAL)) {
            displacementInt = (int) finalDisp;
            assert SPARCAssembler.isSimm13(displacementInt) : displacementInt;
        } else {
            displacementInt = 0;
            if (baseRegister.equals(Value.ILLEGAL)) {
                baseRegister = load(Constant.forLong(finalDisp));
            } else {
                if (finalDisp == 0) {
                    // Nothing to do. Just use the base register.
                } else {
                    Variable longBaseRegister = newVariable(LIRKind.derivedReference(Kind.Long));
                    emitMove(longBaseRegister, baseRegister);
                    baseRegister = emitAdd(longBaseRegister, Constant.forLong(finalDisp));
                }
            }
        }

        LIRKind resultKind = getAddressKind(base, displacement, index);
        return new SPARCAddressValue(resultKind, baseRegister, indexRegister, displacementInt);
    }

    protected SPARCAddressValue asAddressValue(Value address) {
        if (address instanceof SPARCAddressValue) {
            return (SPARCAddressValue) address;
        } else {
            return emitAddress(address, 0, Value.ILLEGAL, 0);
        }
    }

    @Override
    public Value emitAddress(StackSlot address) {
        Variable result = newVariable(LIRKind.value(target().wordKind));
        append(new StackLoadAddressOp(result, address));
        return result;
    }

    @Override
    public void emitReturn(Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(input.getLIRKind());
            emitMove(operand, input);
        }
        append(new ReturnOp(operand));
    }

    @Override
    public void emitJump(LabelRef label) {
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        boolean mirrored = emitCompare(cmpKind, left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;

        Kind kind = left.getKind().getStackKind();
        switch (kind) {
            case Int:
            case Long:
            case Object:
                append(new BranchOp(finalCondition, trueDestination, falseDestination, kind));
                break;
            case Float:
            case Double:
                append(new BranchOp(finalCondition, trueDestination, falseDestination, kind, unorderedIsTrue));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability) {
        append(new BranchOp(ConditionFlag.CarrySet, overflow, noOverflow, Kind.Long));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        emitIntegerTest(left, right);
        append(new BranchOp(Condition.EQ, trueDestination, falseDestination, left.getKind().getStackKind()));
    }

    private void emitIntegerTest(Value a, Value b) {
        assert a.getKind().isNumericInteger();
        if (LIRValueUtil.isVariable(b)) {
            append(new SPARCTestOp(load(b), loadNonConst(a)));
        } else {
            append(new SPARCTestOp(load(a), loadNonConst(b)));
        }
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        boolean mirrored = emitCompare(cmpKind, left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;

        Variable result = newVariable(trueValue.getLIRKind());
        Kind kind = left.getKind().getStackKind();
        switch (kind) {
            case Int:
            case Long:
            case Object:
                append(new CondMoveOp(kind, result, finalCondition, load(trueValue), loadNonConst(falseValue)));
                break;
            case Float:
            case Double:
                append(new FloatCondMoveOp(kind, result, finalCondition, unorderedIsTrue, load(trueValue), load(falseValue)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
        return result;
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param cmpKind Kind how a and b have to be compared
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    protected boolean emitCompare(PlatformKind cmpKind, Value a, Value b) {
        Variable left;
        Value right;
        boolean mirrored;
        if (LIRValueUtil.isVariable(b)) {
            left = load(b);
            right = loadNonConst(a);
            mirrored = true;
        } else {
            left = load(a);
            right = loadNonConst(b);
            mirrored = false;
        }
        switch ((Kind) cmpKind) {
            case Short:
            case Char:
                append(new CompareOp(ICMP, emitSignExtend(left, 16, 32), emitSignExtend(right, 16, 32)));
                break;
            case Byte:
                append(new CompareOp(ICMP, emitSignExtend(left, 8, 32), emitSignExtend(right, 8, 32)));
                break;
            case Int:
                append(new CompareOp(ICMP, left, right));
                break;
            case Long:
                append(new CompareOp(LCMP, left, right));
                break;
            case Object:
                append(new CompareOp(ACMP, left, right));
                break;
            case Float:
                append(new CompareOp(FCMP, left, right));
                break;
            case Double:
                append(new CompareOp(DCMP, left, right));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return mirrored;
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        Variable result = newVariable(trueValue.getLIRKind());
        Kind kind = left.getKind().getStackKind();
        append(new CondMoveOp(kind, result, Condition.EQ, load(trueValue), loadNonConst(falseValue)));
        return result;
    }

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        if (SPARCAssembler.isWordDisp30(maxOffset)) {
            append(new SPARCCall.DirectNearForeignCallOp(linkage, result, arguments, temps, info));
        } else {
            append(new SPARCCall.DirectFarForeignCallOp(linkage, result, arguments, temps, info));
        }
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        append(new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, newVariable(key.getLIRKind())));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        // Making a copy of the switch value is necessary because jump table destroys the input
        // value
        Variable tmp = emitMove(key);
        append(new TableSwitchOp(lowKey, defaultTarget, targets, tmp, newVariable(LIRKind.value(target().wordKind))));
    }

    @Override
    public Value emitBitCount(Value operand) {
        Variable result = newVariable(LIRKind.derive(operand).changeType(Kind.Int));
        if (operand.getKind().getStackKind() == Kind.Int) {
            append(new SPARCBitManipulationOp(IPOPCNT, result, asAllocatable(operand), this));
        } else {
            append(new SPARCBitManipulationOp(LPOPCNT, result, asAllocatable(operand), this));
        }
        return result;
    }

    @Override
    public Value emitBitScanForward(Value operand) {
        Variable result = newVariable(LIRKind.derive(operand).changeType(Kind.Int));
        append(new SPARCBitManipulationOp(BSF, result, asAllocatable(operand), this));
        return result;
    }

    @Override
    public Value emitBitScanReverse(Value operand) {
        Variable result = newVariable(LIRKind.derive(operand).changeType(Kind.Int));
        if (operand.getKind().getStackKind() == Kind.Int) {
            append(new SPARCBitManipulationOp(IBSR, result, asAllocatable(operand), this));
        } else {
            append(new SPARCBitManipulationOp(LBSR, result, asAllocatable(operand), this));
        }
        return result;
    }

    @Override
    public Value emitMathAbs(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new BinaryRegConst(DAND, result, asAllocatable(input), Constant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)), null));
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new SPARCMathIntrinsicOp(SQRT, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new SPARCMathIntrinsicOp(LOG, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathCos(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new SPARCMathIntrinsicOp(COS, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathSin(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new SPARCMathIntrinsicOp(SIN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathTan(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new SPARCMathIntrinsicOp(TAN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new SPARCByteSwapOp(this, result, input));
        return result;
    }

    @Override
    public Value emitArrayEquals(Kind kind, Value array1, Value array2, Value length) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Value emitNegate(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        switch (input.getKind().getStackKind()) {
            case Long:
                append(new Unary2Op(LNEG, result, load(input)));
                break;
            case Int:
                append(new Unary2Op(INEG, result, load(input)));
                break;
            case Float:
                append(new Unary2Op(FNEG, result, load(input)));
                break;
            case Double:
                append(new Unary2Op(DNEG, result, load(input)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitNot(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        switch (input.getKind().getStackKind()) {
            case Int:
                append(new Unary2Op(INOT, result, load(input)));
                break;
            case Long:
                append(new Unary2Op(LNOT, result, load(input)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    private Variable emitBinary(SPARCArithmetic op, boolean commutative, Value a, Value b, LIRFrameState state) {
        if (isConstant(b)) {
            return emitBinaryConst(op, commutative, asAllocatable(a), asConstant(b), state);
        } else if (commutative && isConstant(a)) {
            return emitBinaryConst(op, commutative, asAllocatable(b), asConstant(a), state);
        } else {
            return emitBinaryVar(op, commutative, asAllocatable(a), asAllocatable(b), state);
        }
    }

    private Variable emitBinaryConst(SPARCArithmetic op, boolean commutative, AllocatableValue a, Constant b, LIRFrameState state) {
        switch (op) {
            case IADD:
            case LADD:
            case ISUB:
            case LSUB:
            case IAND:
            case LAND:
            case IOR:
            case LOR:
            case IXOR:
            case LXOR:
            case IMUL:
            case LMUL:
                if (NumUtil.isInt(b.asLong())) {
                    Variable result = newVariable(LIRKind.derive(a, b));
                    append(new BinaryRegConst(op, result, a, b, state));
                    return result;
                }
                break;
        }

        return emitBinaryVar(op, commutative, a, asAllocatable(b), state);
    }

    private Variable emitBinaryVar(SPARCArithmetic op, boolean commutative, AllocatableValue a, AllocatableValue b, LIRFrameState state) {
        Variable result = newVariable(LIRKind.derive(a, b));
        if (commutative) {
            append(new BinaryCommutative(op, result, a, b));
        } else {
            append(new BinaryRegReg(op, result, a, b, state));
        }
        return result;
    }

    @Override
    public Variable emitAdd(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(IADD, true, a, b, null);
            case Long:
                return emitBinary(LADD, true, a, b, null);
            case Float:
                return emitBinary(FADD, true, a, b, null);
            case Double:
                return emitBinary(DADD, true, a, b, null);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitSub(Value a, Value b) {
        Variable result = newVariable(LIRKind.derive(a, b));
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new Op2Stack(ISUB, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LSUB, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Stack(FSUB, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Stack(DSUB, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return result;
    }

    @Override
    public Variable emitMul(Value a, Value b) {
        Variable result = newVariable(LIRKind.derive(a, b));
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new BinaryRegReg(IMUL, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new BinaryRegReg(LMUL, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Stack(FMUL, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Stack(DMUL, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return result;
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitMulHigh(IMUL, a, b);
            case Long:
                return emitMulHigh(LMUL, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitMulHigh(IUMUL, a, b);
            case Long:
                return emitMulHigh(LUMUL, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private Value emitMulHigh(SPARCArithmetic opcode, Value a, Value b) {
        Variable result = newVariable(LIRKind.derive(a, b));
        MulHighOp mulHigh = new MulHighOp(opcode, load(a), load(b), result, newVariable(LIRKind.derive(a, b)));
        append(mulHigh);
        return result;
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        SPARCArithmetic op = null;
        switch (a.getKind().getStackKind()) {
            case Int:
                op = IDIV;
                break;
            case Long:
                op = LDIV;
                break;
            case Float:
                op = FDIV;
                break;
            case Double:
                op = DDIV;
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return emitBinary(op, false, a, b, state);
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        Variable result = newVariable(LIRKind.derive(a, b));
        Variable q = null;
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new RemOp(IREM, result, a, loadNonConst(b), state, this));
                break;
            case Long:
                append(new RemOp(LREM, result, a, loadNonConst(b), state, this));
                break;
            case Float:
                q = newVariable(LIRKind.value(Kind.Float));
                append(new Op2Stack(FDIV, q, a, b));
                append(new Unary2Op(F2I, q, q));
                append(new Unary2Op(I2F, q, q));
                append(new Op2Stack(FMUL, q, q, b));
                append(new Op2Stack(FSUB, result, a, q));
                break;
            case Double:
                q = newVariable(LIRKind.value(Kind.Double));
                append(new Op2Stack(DDIV, q, a, b));
                append(new Unary2Op(D2L, q, q));
                append(new Unary2Op(L2D, q, q));
                append(new Op2Stack(DMUL, q, q, b));
                append(new Op2Stack(DSUB, result, a, q));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return result;
    }

    @Override
    public Value emitURem(Value a, Value b, LIRFrameState state) {
        Variable result = newVariable(LIRKind.derive(a, b));
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new RemOp(IUREM, result, load(a), load(b), state, this));
                break;
            case Long:
                append(new RemOp(LUREM, result, load(a), loadNonConst(b), state, this));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;

    }

    @Override
    public Value emitUDiv(Value a, Value b, LIRFrameState state) {
        SPARCArithmetic op;
        Value actualA = a;
        Value actualB = b;
        switch (a.getKind().getStackKind()) {
            case Int:
                op = LUDIV;
                actualA = emitZeroExtend(actualA, 32, 64);
                actualB = emitZeroExtend(actualB, 32, 64);
                break;
            case Long:
                op = LUDIV;
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return emitBinary(op, false, actualA, actualB, state);
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        Variable result = newVariable(LIRKind.derive(a, b));
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new Op2Stack(IAND, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LAND, result, a, loadNonConst(b)));
                break;

            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return result;
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        Variable result = newVariable(LIRKind.derive(a, b));
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new Op2Stack(IOR, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LOR, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return result;
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        Variable result = newVariable(LIRKind.derive(a, b));
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new Op2Stack(IXOR, result, load(a), loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LXOR, result, load(a), loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    private Variable emitShift(SPARCArithmetic op, Value a, Value b) {
        Variable result = newVariable(LIRKind.derive(a, b).changeType(a.getPlatformKind()));
        AllocatableValue input = asAllocatable(a);
        if (isConstant(b)) {
            append(new BinaryRegConst(op, result, input, asConstant(b), null));
        } else {
            append(new BinaryRegReg(op, result, input, b));
        }
        return result;
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitShift(ISHL, a, b);
            case Long:
                return emitShift(LSHL, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitShift(ISHR, a, b);
            case Long:
                return emitShift(LSHR, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitShift(IUSHR, a, b);
            case Long:
                return emitShift(LUSHR, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private AllocatableValue emitConvertMove(LIRKind kind, AllocatableValue input) {
        Variable result = newVariable(kind);
        emitMove(result, input);
        return result;
    }

    private AllocatableValue emitConvert2Op(LIRKind kind, SPARCArithmetic op, AllocatableValue input) {
        Variable result = newVariable(kind);
        append(new Unary2Op(op, result, input));
        return result;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        switch (op) {
            case D2F:
                return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Float), D2F, input);
            case F2D:
                return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Double), F2D, input);
            case I2F:
                return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Float), I2F, input);
            case L2D:
                return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Double), L2D, input);
            case D2I: {
                AllocatableValue convertedFloatReg = emitConvert2Op(LIRKind.derive(input).changeType(Kind.Float), D2I, input);
                AllocatableValue convertedIntReg = newVariable(LIRKind.derive(convertedFloatReg).changeType(Kind.Int));
                emitMove(convertedIntReg, convertedFloatReg);
                return convertedIntReg;
            }
            case F2L: {
                AllocatableValue convertedDoubleReg = emitConvert2Op(LIRKind.derive(input).changeType(Kind.Double), F2L, input);
                AllocatableValue convertedLongReg = newVariable(LIRKind.derive(convertedDoubleReg).changeType(Kind.Long));
                emitMove(convertedLongReg, convertedDoubleReg);
                return convertedLongReg;
            }
            case F2I: {
                AllocatableValue convertedFloatReg = emitConvert2Op(LIRKind.derive(input).changeType(Kind.Float), F2I, input);
                AllocatableValue convertedIntReg = newVariable(LIRKind.derive(convertedFloatReg).changeType(Kind.Int));
                emitMove(convertedIntReg, convertedFloatReg);
                return convertedIntReg;
            }
            case D2L: {
                AllocatableValue convertedDoubleReg = emitConvert2Op(LIRKind.derive(input).changeType(Kind.Double), D2L, input);
                AllocatableValue convertedLongReg = newVariable(LIRKind.derive(convertedDoubleReg).changeType(Kind.Long));
                emitMove(convertedLongReg, convertedDoubleReg);
                return convertedLongReg;
            }
            case I2D: {
                AllocatableValue tmp = emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Long), I2L, input);
                return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Double), L2D, tmp);
            }
            case L2F: {
                AllocatableValue tmp = emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Double), L2D, input);
                return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Float), D2F, tmp);
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getKind() == Kind.Long && bits <= 32) {
            return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Int), L2I, asAllocatable(inputVal));
        } else {
            return inputVal;
        }
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (toBits > 32) {
            // sign extend to 64 bits
            switch (fromBits) {
                case 8:
                    return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Long), B2L, asAllocatable(inputVal));
                case 16:
                    return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Long), S2L, asAllocatable(inputVal));
                case 32:
                    return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Long), I2L, asAllocatable(inputVal));
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Int), B2I, asAllocatable(inputVal));
                case 16:
                    return emitConvert2Op(LIRKind.derive(inputVal).changeType(Kind.Int), S2I, asAllocatable(inputVal));
                case 32:
                    return inputVal;
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        }
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (fromBits > 32) {
            assert inputVal.getKind() == Kind.Long;
            Variable result = newVariable(LIRKind.derive(inputVal).changeType(Kind.Long));
            long mask = IntegerStamp.defaultMask(fromBits);
            append(new BinaryRegConst(SPARCArithmetic.LAND, result, asAllocatable(inputVal), Constant.forLong(mask), null));
            return result;
        } else {
            assert inputVal.getKind() == Kind.Int || inputVal.getKind() == Kind.Short || inputVal.getKind() == Kind.Byte || inputVal.getKind() == Kind.Char : inputVal.getKind();
            Variable result = newVariable(LIRKind.derive(inputVal).changeType(Kind.Int));
            long mask = IntegerStamp.defaultMask(fromBits);
            Constant constant = Constant.forInt((int) mask);
            if (fromBits == 32) {
                append(new ShiftOp(IUSHR, result, inputVal, Constant.forInt(0)));
            } else if (canInlineConstant(constant)) {
                append(new BinaryRegConst(SPARCArithmetic.IAND, result, asAllocatable(inputVal), constant, null));
            } else {
                Variable maskVar = newVariable(LIRKind.derive(inputVal).changeType(Kind.Int));
                emitMove(maskVar, constant);
                append(new BinaryRegReg(IAND, result, maskVar, asAllocatable(inputVal)));
            }
            if (toBits > 32) {
                Variable longResult = newVariable(LIRKind.derive(inputVal).changeType(Kind.Long));
                emitMove(longResult, result);
                return longResult;
            } else {
                return result;
            }
        }
    }

    @Override
    public AllocatableValue emitReinterpret(LIRKind to, Value inputVal) {
        Kind from = inputVal.getKind();
        AllocatableValue input = asAllocatable(inputVal);

        // These cases require a move between CPU and FPU registers:
        switch ((Kind) to.getPlatformKind()) {
            case Int:
                switch (from) {
                    case Float:
                    case Double:
                        return emitConvert2Op(to, MOV_F2I, input);
                }
                break;
            case Long:
                switch (from) {
                    case Float:
                    case Double:
                        return emitConvert2Op(to, MOV_D2L, input);
                }
                break;
            case Float:
                switch (from) {
                    case Int:
                    case Long:
                        return emitConvert2Op(to, MOV_I2F, input);
                }
                break;
            case Double:
                switch (from) {
                    case Int:
                    case Long:
                        return emitConvert2Op(to, MOV_L2D, input);
                }
                break;
        }

        // Otherwise, just emit an ordinary move instruction.
        // Instructions that move or generate 32-bit register values also set the upper 32
        // bits of the register to zero.
        // Consequently, there is no need for a special zero-extension move.
        return emitConvertMove(to, input);
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
        append(new ReturnOp(Value.ILLEGAL));
    }

}
