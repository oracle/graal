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
import static com.oracle.graal.lir.sparc.SPARCMathIntrinsicOp.IntrinsicOpcode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.sparc.SPARCArithmetic.BinaryCommutative;
import com.oracle.graal.lir.sparc.SPARCArithmetic.BinaryRegConst;
import com.oracle.graal.lir.sparc.SPARCArithmetic.BinaryRegReg;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Op1Stack;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Op2Stack;
import com.oracle.graal.lir.sparc.SPARCArithmetic.RemOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Unary2Op;
import com.oracle.graal.lir.sparc.SPARCCompare.CompareOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.BranchOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.CondMoveOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.ReturnOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.TableSwitchOp;
import com.oracle.graal.lir.sparc.SPARCMove.LoadAddressOp;
import com.oracle.graal.lir.sparc.SPARCMove.MembarOp;
import com.oracle.graal.lir.sparc.SPARCMove.MoveFromRegOp;
import com.oracle.graal.lir.sparc.SPARCMove.MoveToRegOp;
import com.oracle.graal.lir.sparc.SPARCMove.NullCheckOp;
import com.oracle.graal.lir.sparc.SPARCMove.StackLoadAddressOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;
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

    public SPARCLIRGenerator(StructuredGraph graph, Providers providers, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, providers, frameMap, cc, lir);
        lir.setSpillMoveFactory(new SPARCSpillMoveFactory());
    }

    @Override
    public boolean canStoreConstant(Constant c, boolean isCompressed) {
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
        Variable result = newVariable(input.getKind());
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
                    Value longIndex = emitSignExtend(index, 32, 64);
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
            // TODO What if displacement if too big?
            displacementInt = (int) finalDisp;
        } else {
            displacementInt = 0;
            if (baseRegister.equals(Value.ILLEGAL)) {
                baseRegister = load(Constant.forLong(finalDisp));
            } else {
                Variable longBaseRegister = newVariable(Kind.Long);
                emitMove(longBaseRegister, baseRegister);  // FIXME get rid of this move
                baseRegister = emitAdd(longBaseRegister, Constant.forLong(finalDisp));
            }
        }

        return new SPARCAddressValue(target().wordKind, baseRegister, indexRegister, displacementInt);
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
        Variable result = newVariable(target().wordKind);
        append(new StackLoadAddressOp(result, address));
        return result;
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        // No peephole optimizations for now
        return false;
    }

    @Override
    protected void emitReturn(Value input) {
        append(new ReturnOp(input));
    }

    @Override
    public void emitJump(LabelRef label) {
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        boolean mirrored = emitCompare(left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        Kind kind = left.getKind().getStackKind();
        switch (kind) {
            case Int:
            case Long:
            case Object:
                append(new BranchOp(finalCondition, trueDestination, falseDestination, kind));
                break;
            // case Float:
            // append(new CompareOp(FCMP, x, y));
            // append(new BranchOp(condition, label));
            // break;
            // case Double:
            // append(new CompareOp(DCMP, x, y));
            // append(new BranchOp(condition, label));
            // break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability) {
        // append(new BranchOp(negated ? ConditionFlag.NoOverflow : ConditionFlag.Overflow, label));
        throw GraalInternalError.unimplemented();
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
    public Variable emitConditionalMove(Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        boolean mirrored = emitCompare(left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;

        Variable result = newVariable(trueValue.getKind());
        switch (left.getKind().getStackKind()) {
            case Int:
            case Long:
            case Object:
                append(new CondMoveOp(result, finalCondition, load(trueValue), loadNonConst(falseValue)));
                break;
            case Float:
            case Double:
                append(new FloatCondMoveOp(result, finalCondition, unorderedIsTrue, load(trueValue), load(falseValue)));
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
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompare(Value a, Value b) {
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
        switch (left.getKind().getStackKind()) {
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
        Variable result = newVariable(trueValue.getKind());
        append(new CondMoveOp(result, Condition.EQ, load(trueValue), loadNonConst(falseValue)));
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
    protected void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        // a temp is needed for loading long and object constants
        boolean needsTemp = key.getKind() == Kind.Long || key.getKind() == Kind.Object;
        append(new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, needsTemp ? newVariable(key.getKind()) : Value.ILLEGAL));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        // Making a copy of the switch value is necessary because jump table destroys the input
        // value
        Variable tmp = emitMove(key);
        append(new TableSwitchOp(lowKey, defaultTarget, targets, tmp, newVariable(target().wordKind)));
    }

    @Override
    public void emitBitCount(Variable result, Value operand) {
        if (operand.getKind().getStackKind() == Kind.Int) {
            append(new SPARCBitManipulationOp(IPOPCNT, result, asAllocatable(operand), this));
        } else {
            append(new SPARCBitManipulationOp(LPOPCNT, result, asAllocatable(operand), this));
        }
    }

    @Override
    public void emitBitScanForward(Variable result, Value operand) {
        append(new SPARCBitManipulationOp(BSF, result, asAllocatable(operand), this));
    }

    @Override
    public void emitBitScanReverse(Variable result, Value operand) {
        if (operand.getKind().getStackKind() == Kind.Int) {
            append(new SPARCBitManipulationOp(IBSR, result, asAllocatable(operand), this));
        } else {
            append(new SPARCBitManipulationOp(LBSR, result, asAllocatable(operand), this));
        }
    }

    @Override
    public Value emitMathAbs(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new BinaryRegConst(DAND, result, asAllocatable(input), Constant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL))));
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new SPARCMathIntrinsicOp(SQRT, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        Variable result = newVariable(input.getPlatformKind());
        append(new SPARCMathIntrinsicOp(LOG, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathCos(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new SPARCMathIntrinsicOp(COS, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathSin(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new SPARCMathIntrinsicOp(SIN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathTan(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new SPARCMathIntrinsicOp(TAN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public void emitByteSwap(Variable result, Value input) {
        append(new SPARCByteSwapOp(result, input));
    }

    @Override
    public void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented();
    }

    @Override
    public Value emitNegate(Value input) {
        Variable result = newVariable(input.getKind());
        switch (input.getKind().getStackKind()) {
            case Int:
                append(new Op1Stack(INEG, result, input));
                break;
            case Float:
                append(new Op1Stack(FNEG, result, input));
                break;
            case Double:
                append(new Op1Stack(DNEG, result, input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitNot(Value input) {
        Variable result = newVariable(input.getKind());
        switch (input.getKind().getStackKind()) {
            case Int:
                append(new Op1Stack(INOT, result, input));
                break;
            case Long:
                append(new Op1Stack(LNOT, result, input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    private Variable emitBinary(SPARCArithmetic op, boolean commutative, Value a, Value b) {
        if (isConstant(b)) {
            return emitBinaryConst(op, commutative, asAllocatable(a), asConstant(b));
        } else if (commutative && isConstant(a)) {
            return emitBinaryConst(op, commutative, asAllocatable(b), asConstant(a));
        } else {
            return emitBinaryVar(op, commutative, asAllocatable(a), asAllocatable(b));
        }
    }

    private Variable emitBinaryConst(SPARCArithmetic op, boolean commutative, AllocatableValue a, Constant b) {
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
                    Variable result = newVariable(a.getKind());
                    append(new BinaryRegConst(op, result, a, b));
                    return result;
                }
                break;
        }

        return emitBinaryVar(op, commutative, a, asAllocatable(b));
    }

    private Variable emitBinaryVar(SPARCArithmetic op, boolean commutative, AllocatableValue a, AllocatableValue b) {
        Variable result = newVariable(a.getKind());
        if (commutative) {
            append(new BinaryCommutative(op, result, a, b));
        } else {
            append(new BinaryRegReg(op, result, a, b));
        }
        return result;
    }

    @Override
    public Variable emitAdd(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(IADD, true, a, b);
            case Long:
                return emitBinary(LADD, true, a, b);
            case Float:
                return emitBinary(FADD, true, a, b);
            case Double:
                return emitBinary(DADD, true, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitSub(Value a, Value b) {
        Variable result = newVariable(a.getKind());
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
        Variable result = newVariable(a.getKind());
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
    public Value emitDiv(Value a, Value b, DeoptimizingNode deopting) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new BinaryRegReg(IDIV, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new BinaryRegReg(LDIV, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Stack(FDIV, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Stack(DDIV, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return result;
    }

    @Override
    public Value emitRem(Value a, Value b, DeoptimizingNode deopting) {
        LIRFrameState state = state(deopting);
        Variable result = newVariable(a.getKind());
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new RemOp(IREM, result, a, loadNonConst(b), state, this));
                break;
            case Long:
                append(new RemOp(LREM, result, a, loadNonConst(b), state, this));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return result;
    }

    @Override
    public Value emitUDiv(Value a, Value b, DeoptimizingNode deopting) {
        // LIRFrameState state = state(deopting);
        switch (a.getKind().getStackKind()) {
            case Int:
                // emitDivRem(IUDIV, a, b, state);
                // return emitMove(RAX_I);
            case Long:
                // emitDivRem(LUDIV, a, b, state);
                // return emitMove(RAX_L);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitURem(Value a, Value b, DeoptimizingNode deopting) {
        // LIRFrameState state = state(deopting);
        switch (a.getKind().getStackKind()) {
            case Int:
                // emitDivRem(IUREM, a, b, state);
                // return emitMove(RDX_I);
            case Long:
                // emitDivRem(LUREM, a, b, state);
                // return emitMove(RDX_L);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
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
        Variable result = newVariable(a.getKind());
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
        Variable result = newVariable(a.getKind());
        switch (a.getKind().getStackKind()) {
            case Int:
                append(new Op2Stack(IXOR, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LXOR, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    private Variable emitShift(SPARCArithmetic op, Value a, Value b) {
        Variable result = newVariable(a.getPlatformKind());
        AllocatableValue input = asAllocatable(a);
        if (isConstant(b)) {
            append(new BinaryRegConst(op, result, input, asConstant(b)));
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

    private AllocatableValue emitConvertMove(PlatformKind kind, AllocatableValue input) {
        Variable result = newVariable(kind);
        emitMove(result, input);
        return result;
    }

    private AllocatableValue emitConvert2Op(PlatformKind kind, SPARCArithmetic op, AllocatableValue input) {
        Variable result = newVariable(kind);
        append(new Unary2Op(op, result, input));
        return result;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        switch (op) {
            case D2F:
                return emitConvert2Op(Kind.Float, D2F, input);
            case D2I:
                return emitConvert2Op(Kind.Int, D2I, input);
            case D2L:
                return emitConvert2Op(Kind.Long, D2L, input);
            case F2D:
                return emitConvert2Op(Kind.Double, F2D, input);
            case F2I:
                return emitConvert2Op(Kind.Int, F2I, input);
            case F2L:
                return emitConvert2Op(Kind.Long, F2L, input);
            case I2D:
                return emitConvert2Op(Kind.Double, I2D, input);
            case I2F:
                return emitConvert2Op(Kind.Float, I2F, input);
            case L2D:
                return emitConvert2Op(Kind.Double, L2D, input);
            case L2F:
                return emitConvert2Op(Kind.Float, L2F, input);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getKind() == Kind.Long && bits <= 32) {
            return emitConvert2Op(Kind.Int, L2I, asAllocatable(inputVal));
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
            if (fromBits == 32) {
                return emitConvert2Op(Kind.Long, I2L, asAllocatable(inputVal));
            } else if (fromBits < 32) {
                // TODO implement direct x2L sign extension conversions
                Value intVal = emitSignExtend(inputVal, fromBits, 32);
                return emitSignExtend(intVal, 32, toBits);
            } else {
                throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    return emitConvert2Op(Kind.Int, I2B, asAllocatable(inputVal));
                case 16:
                    return emitConvert2Op(Kind.Int, I2S, asAllocatable(inputVal));
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
            Variable result = newVariable(Kind.Long);
            long mask = IntegerStamp.defaultMask(fromBits);
            append(new BinaryRegConst(SPARCArithmetic.LAND, result, asAllocatable(inputVal), Constant.forLong(mask)));
            return result;
        } else {
            assert inputVal.getKind() == Kind.Int;
            Variable result = newVariable(Kind.Int);
            int mask = (int) IntegerStamp.defaultMask(fromBits);
            append(new BinaryRegConst(SPARCArithmetic.IAND, result, asAllocatable(inputVal), Constant.forInt(mask)));
            if (toBits > 32) {
                Variable longResult = newVariable(Kind.Long);
                emitMove(longResult, result);
                return longResult;
            } else {
                return result;
            }
        }
    }

    @Override
    public AllocatableValue emitReinterpret(PlatformKind to, Value inputVal) {
        Kind from = inputVal.getKind();
        AllocatableValue input = asAllocatable(inputVal);

        // These cases require a move between CPU and FPU registers:
        switch ((Kind) to) {
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
    public void emitDeoptimize(Value actionAndReason, Value speculation, DeoptimizingNode deopting) {
        append(new ReturnOp(Value.ILLEGAL));
    }

    @Override
    public void visitCompareAndSwap(LoweredCompareAndSwapNode i, Value address) {
        throw new InternalError("NYI");
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments().size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments().get(i).stamp().javaType(getMetaAccess());
        }

        Value[] parameters = visitInvokeArguments(frameMap.registerConfig.getCallingConvention(CallingConvention.Type.JavaCall, null, sig, target(), false), node.arguments());
        append(new SPARCBreakpointOp(parameters));
    }

    @Override
    public void emitUnwind(Value operand) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitNullCheck(ValueNode v, DeoptimizingNode deopting) {
        assert v.getKind() == Kind.Object;
        append(new NullCheckOp(load(operand(v)), state(deopting)));
    }

    @Override
    public void visitInfopointNode(InfopointNode i) {
        throw new InternalError("NYI");
    }
}
