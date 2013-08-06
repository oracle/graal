/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.sparc.SPARCControlFlow.*;
import com.oracle.graal.lir.sparc.SPARCMove.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.ConvertNode.Op;
import com.oracle.graal.nodes.java.*;

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

    public SPARCLIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, runtime, target, frameMap, cc, lir);
        lir.spillMoveFactory = new SPARCSpillMoveFactory();
    }

    @Override
    protected void emitNode(ValueNode node) {
        if (node instanceof LIRGenLowerable) {
            ((LIRGenLowerable) node).generate(this);
        } else {
            super.emitNode(node);
        }
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
                return SPARCAssembler.isSimm13(c.asInt()) && !runtime.needsDataPatch(c);
            case Long:
                return SPARCAssembler.isSimm13(c.asLong()) && !runtime.needsDataPatch(c);
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
                    // Variable longIndex = newVariable(Kind.Long);
                    Variable longIndex = emitConvert(Op.I2L, index);
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
    public void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef label) {
        boolean mirrored = emitCompare(left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        Kind kind = left.getKind().getStackKind();
        switch (kind) {
            case Int:
            case Long:
            case Object:
                append(new BranchOp(finalCondition, label, kind));
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
    public void emitOverflowCheckBranch(LabelRef label, boolean negated) {
        // append(new BranchOp(negated ? ConditionFlag.NoOverflow : ConditionFlag.Overflow, label));
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, boolean negated, LabelRef label) {
        emitIntegerTest(left, right);
        append(new BranchOp(negated ? Condition.NE : Condition.EQ, label, left.getKind().getStackKind()));
    }

    private void emitIntegerTest(Value a, Value b) {
        assert a.getKind().getStackKind() == Kind.Int || a.getKind() == Kind.Long;
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
    protected void emitSequentialSwitch(Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Value key) {
        // Making a copy of the switch value is necessary because jump table destroys the input
        // value
        assert key.getKind() == Kind.Int || key.getKind() == Kind.Long || key.getKind() == Kind.Object : key.getKind();
        append(new SequentialSwitchOp(keyConstants, keyTargets, defaultTarget, key, newVariable(key.getKind())));
    }

    @Override
    protected void emitSwitchRanges(int[] lowKeys, int[] highKeys, LabelRef[] targets, LabelRef defaultTarget, Value key) {
        append(new SwitchRangesOp(lowKeys, highKeys, targets, defaultTarget, key));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        // Making a copy of the switch value is necessary because jump table destroys the input
        // value
        Variable tmp = emitMove(key);
        append(new TableSwitchOp(lowKey, defaultTarget, targets, tmp, newVariable(target.wordKind)));
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
    public void emitMathAbs(Variable result, Variable input) {
        append(new BinaryRegConst(DAND, result, input, Constant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL))));
    }

    @Override
    public void emitMathSqrt(Variable result, Variable input) {
        append(new SPARCMathIntrinsicOp(SQRT, result, input));
    }

    @Override
    public void emitMathLog(Variable result, Variable input, boolean base10) {
        append(new SPARCMathIntrinsicOp(LOG, result, input));
    }

    @Override
    public void emitMathCos(Variable result, Variable input) {
        append(new SPARCMathIntrinsicOp(COS, result, input));
    }

    @Override
    public void emitMathSin(Variable result, Variable input) {
        append(new SPARCMathIntrinsicOp(SIN, result, input));
    }

    @Override
    public void emitMathTan(Variable result, Variable input) {
        append(new SPARCMathIntrinsicOp(TAN, result, input));
    }

    @Override
    public void emitByteSwap(Variable result, Value input) {
        append(new SPARCByteSwapOp(result, input));
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

    @Override
    public Variable emitConvert(Op opcode, Value inputVal) {
        Variable input = load(inputVal);
        Variable result = newVariable(opcode.to);
        switch (opcode) {
            case I2L:
                append(new Unary2Op(I2L, result, input));
                break;
            case L2I:
                append(new Unary2Op(L2I, result, input));
                break;
            case I2B:
                append(new Unary2Op(I2B, result, input));
                break;
            case I2C:
                append(new Unary2Op(I2C, result, input));
                break;
            case I2S:
                append(new Unary2Op(I2S, result, input));
                break;
            case F2D:
                append(new Unary2Op(F2D, result, input));
                break;
            case D2F:
                append(new Unary2Op(D2F, result, input));
                break;
            case I2F:
                append(new Unary2Op(I2F, result, input));
                break;
            case I2D:
                append(new Unary2Op(I2D, result, input));
                break;
            case F2I:
                append(new Unary2Op(F2I, result, input));
                break;
            case D2I:
                append(new Unary2Op(D2I, result, input));
                break;
            case L2F:
                append(new Unary2Op(L2F, result, input));
                break;
            case L2D:
                append(new Unary2Op(L2D, result, input));
                break;
            case F2L:
                append(new Unary2Op(F2L, result, input));
                break;
            case D2L:
                append(new Unary2Op(D2L, result, input));
                break;
            case MOV_I2F:
                append(new Unary2Op(MOV_I2F, result, input));
                break;
            case MOV_L2D:
                append(new Unary2Op(MOV_L2D, result, input));
                break;
            case MOV_F2I:
                append(new Unary2Op(MOV_F2I, result, input));
                break;
            case MOV_D2L:
                append(new Unary2Op(MOV_D2L, result, input));
                break;
            case UNSIGNED_I2L:
                // Instructions that move or generate 32-bit register values also set the upper 32
                // bits of the register to zero.
                // Consequently, there is no need for a special zero-extension move.
                emitMove(result, input);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target.arch.requiredBarriers(barriers);
        if (target.isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    @Override
    public void emitDeoptimize(DeoptimizationAction action, DeoptimizingNode deopting) {
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
            sig[i] = node.arguments().get(i).stamp().javaType(runtime);
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
        assert v.kind() == Kind.Object;
        append(new NullCheckOp(load(operand(v)), state(deopting)));
    }

    @Override
    public void visitInfopointNode(InfopointNode i) {
        throw new InternalError("NYI");
    }
}
