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
import static com.oracle.graal.lir.LIRValueUtil.*;
import static com.oracle.graal.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.sparc.SPARCMathIntrinsicOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.sparc.SPARCArithmetic.*;
import static com.oracle.graal.lir.sparc.SPARCCompare.*;

import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.DeoptimizationAction;
import com.oracle.graal.api.code.ForeignCallLinkage;
import com.oracle.graal.api.code.StackSlot;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.compiler.gen.LIRGenerator;
import com.oracle.graal.compiler.target.LIRGenLowerable;
import com.oracle.graal.graph.GraalInternalError;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.sparc.SPARCAddressValue;
import com.oracle.graal.lir.sparc.SPARCArithmetic.BinaryRegConst;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Op1Stack;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Op2Stack;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Op2Reg;
import com.oracle.graal.lir.sparc.SPARCArithmetic.ShiftOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Unary1Op;
import com.oracle.graal.lir.sparc.SPARCArithmetic.Unary2Op;
import com.oracle.graal.lir.sparc.SPARCBitManipulationOp;
import com.oracle.graal.lir.sparc.SPARCBreakpointOp;
import com.oracle.graal.lir.sparc.SPARCByteSwapOp;
import com.oracle.graal.lir.sparc.SPARCCompare.CompareOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.BranchOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.CondMoveOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.ReturnOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.SequentialSwitchOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.SwitchRangesOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.TableSwitchOp;
import com.oracle.graal.lir.sparc.SPARCMathIntrinsicOp;
import com.oracle.graal.lir.sparc.SPARCMove.LoadOp;
import com.oracle.graal.lir.sparc.SPARCMove.MembarOp;
import com.oracle.graal.lir.sparc.SPARCMove.MoveFromRegOp;
import com.oracle.graal.lir.sparc.SPARCMove.MoveToRegOp;
import com.oracle.graal.lir.sparc.SPARCMove.NullCheckOp;
import com.oracle.graal.lir.sparc.SPARCMove.StackLoadAddressOp;
import com.oracle.graal.lir.sparc.SPARCMove.StoreOp;
import com.oracle.graal.lir.sparc.SPARCTestOp;
import com.oracle.graal.nodes.BreakpointNode;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.DirectCallTargetNode;
import com.oracle.graal.nodes.IndirectCallTargetNode;
import com.oracle.graal.nodes.InfopointNode;
import com.oracle.graal.nodes.SafepointNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.ConvertNode.Op;
import com.oracle.graal.nodes.java.CompareAndSwapNode;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public class SPARCLIRGenerator extends LIRGenerator {

    public static class SPARCSpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(AllocatableValue result, Value input) {
            throw new InternalError("NYI");
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
    public Variable emitMove(Value input) {
        Variable result = newVariable(input.getKind());
        emitMove(result, input);
        return result;
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        // XXX SPARCAddress loads
        /*
         * if (src instanceof SPARCAddressValue) { public LoadOp(Kind kind, AllocatableValue result,
         * SPARCAddressValue address, LIRFrameState state) {
         * 
         * return new LoadOp(result.getKind(), result, (SPARCAddressValue) src); }
         */
        if (isRegister(src) || isStackSlot(dst)) {
            append(new MoveFromRegOp(dst, src));
        } else {
            append(new MoveToRegOp(dst, src));
        }
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
        switch (left.getKind().getStackKind()) {
            case Int:
                append(new CompareOp(ICMP, left, right));
                append(new BranchOp(cond, label));
                break;
            case Long:
                append(new CompareOp(LCMP, left, right));
                append(new BranchOp(cond, label));
                break;
            case Float:
                append(new CompareOp(FCMP, left, right));
                append(new BranchOp(cond, label));
                break;
            case Double:
                append(new CompareOp(DCMP, left, right));
                append(new BranchOp(cond, label));
                break;
            case Object:
                append(new CompareOp(ACMP, left, right));
                append(new BranchOp(cond, label));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef label, boolean negated) {
        // append(new BranchOp(negated ? ConditionFlag.NoOverflow : ConditionFlag.Overflow, label));
        throw GraalInternalError.shouldNotReachHere("emitOverflowCheckBranch: unimp");
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, boolean negated, LabelRef label) {
        emitIntegerTest(left, right);
        append(new BranchOp(negated ? Condition.NE : Condition.EQ, label));
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
    public Variable load(Value value) {
        if (!isVariable(value)) {
            return emitMove(value);
        }
        return (Variable) value;
    }

    @Override
    public Value loadNonConst(Value value) {
        if (isConstant(value) && !canInlineConstant((Constant) value)) {
            return emitMove(value);
        }
        return value;
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
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        throw new InternalError("NYI");
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        throw new InternalError("NYI");
    }

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        throw new InternalError("NYI");
    }

    @Override
    protected void emitSequentialSwitch(Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Value key) {
        // Making a copy of the switch value is necessary because jump table destroys the input
        // value
        if (key.getKind() == Kind.Int || key.getKind() == Kind.Long) {
            append(new SequentialSwitchOp(keyConstants, keyTargets, defaultTarget, key, Value.ILLEGAL));
        } else {
            assert key.getKind() == Kind.Object : key.getKind();
            append(new SequentialSwitchOp(keyConstants, keyTargets, defaultTarget, key, newVariable(Kind.Object)));
        }
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
            append(new SPARCBitManipulationOp(IPOPCNT, result, asAllocatable(operand)));
        } else {
            append(new SPARCBitManipulationOp(LPOPCNT, result, asAllocatable(operand)));
        }
    }

    @Override
    public void emitBitScanForward(Variable result, Value operand) {
        append(new SPARCBitManipulationOp(BSF, result, asAllocatable(operand)));
    }

    @Override
    public void emitBitScanReverse(Variable result, Value operand) {
        if (operand.getKind().getStackKind() == Kind.Int) {
            append(new SPARCBitManipulationOp(IBSR, result, asAllocatable(operand)));
        } else {
            append(new SPARCBitManipulationOp(LBSR, result, asAllocatable(operand)));
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
    public boolean canInlineConstant(Constant c) {
        switch (c.getKind()) {
            case Long:
                return NumUtil.isInt(c.asLong()) && !runtime.needsDataPatch(c);
            case Object:
                return c.isNull();
            default:
                return true;
        }
    }

    @Override
    public boolean canStoreConstant(Constant c) {
        throw new InternalError("NYI");
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

        if (!Value.ILLEGAL.equals(index) && scale != 0) {
            if (isConstant(index)) {
                finalDisp += asConstant(index).asLong() * scale;
            } else {
                Value indexRegister;
                if (scale != 1) {
                    indexRegister = emitMul(index, Constant.forInt(scale));
                } else {
                    indexRegister = index;
                }

                if (Value.ILLEGAL.equals(baseRegister)) {
                    baseRegister = asAllocatable(indexRegister);
                } else {
                    Variable newBase = newVariable(Kind.Int);
                    emitMove(newBase, baseRegister);
                    baseRegister = newBase;
                    baseRegister = emitAdd(baseRegister, indexRegister);
                }
            }
        }

        return new SPARCAddressValue(target().wordKind, baseRegister, (int) finalDisp);
    }

    private SPARCAddressValue asAddress(Value address) {
        if (address instanceof SPARCAddressValue) {
            return (SPARCAddressValue) address;
        } else {
            return emitAddress(address, 0, Value.ILLEGAL, 0);
        }
    }

    @Override
    public Variable emitLoad(Kind kind, Value address, DeoptimizingNode deopting) {
        SPARCAddressValue loadAddress = asAddress(address);
        Variable result = newVariable(kind);
        append(new LoadOp(kind, result, loadAddress, deopting != null ? state(deopting) : null));
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value inputVal, DeoptimizingNode deopting) {
        SPARCAddressValue storeAddress = asAddress(address);
        Variable input = load(inputVal);
        append(new StoreOp(kind, storeAddress, input, deopting != null ? state(deopting) : null));
    }

    @Override
    public Value emitAddress(StackSlot address) {
        Variable result = newVariable(target().wordKind);
        append(new StackLoadAddressOp(result, address));
        return result;
    }

    @Override
    public Value emitNegate(Value input) {
        Variable result = newVariable(input.getKind());
        switch (input.getKind()) {
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
    public Variable emitAdd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Stack(IADD, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LADD, result, a, loadNonConst(b)));
                break;
            case Float:
                append(new Op2Stack(FADD, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Stack(DADD, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind() + " prim: " + a.getKind().isPrimitive());
        }
        return result;
    }

    @Override
    public Value emitSub(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
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
    public Value emitMul(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IMUL, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LMUL, result, a, loadNonConst(b)));
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
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IDIV, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LDIV, result, a, loadNonConst(b)));
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
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Reg(IREM, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Reg(LREM, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
        }
        return result;
    }

    @Override
    public Value emitUDiv(Value a, Value b, DeoptimizingNode deopting) {
        @SuppressWarnings("unused")
        LIRFrameState state = state(deopting);
        switch (a.getKind()) {
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
        @SuppressWarnings("unused")
        LIRFrameState state = state(deopting);
        switch (a.getKind()) {
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
    public Value emitAnd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
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
    public Value emitOr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
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
    public Value emitXor(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
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

    @Override
    public Value emitShl(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Stack(ISHL, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op1Stack(LSHL, result, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitShr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Stack(ISHR, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op1Stack(LSHR, result, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitUShr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new ShiftOp(IUSHR, result, a, b));
                break;
            case Long:
                append(new ShiftOp(LUSHR, result, a, b));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitConvert(Op opcode, Value inputVal) {
        Variable input = load(inputVal);
        Variable result = newVariable(opcode.to);
        switch (opcode) {
            case I2L:
                append(new Unary2Op(I2L, result, input));
                break;
            case L2I:
                append(new Unary1Op(L2I, result, input));
                break;
            case I2B:
                append(new Unary2Op(I2B, result, input));
                break;
            case I2C:
                append(new Unary1Op(I2C, result, input));
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
    public void visitCompareAndSwap(CompareAndSwapNode i) {
        throw new InternalError("NYI");
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
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
