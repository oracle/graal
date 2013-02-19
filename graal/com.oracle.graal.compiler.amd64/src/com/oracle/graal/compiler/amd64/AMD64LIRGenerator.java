/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.amd64.AMD64Arithmetic.*;
import static com.oracle.graal.lir.amd64.AMD64Compare.*;
import static com.oracle.graal.lir.amd64.AMD64BitManipulationOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.DivOp;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Op1Reg;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Op1Stack;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Op2Reg;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Op2Stack;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.ShiftOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Call.DirectCallOp;
import com.oracle.graal.lir.amd64.AMD64Call.IndirectCallOp;
import com.oracle.graal.lir.amd64.AMD64Compare.CompareOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.CondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.ReturnOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.SequentialSwitchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.SwitchRangesOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.TableSwitchOp;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.amd64.AMD64Move.LeaOp;
import com.oracle.graal.lir.amd64.AMD64Move.LoadOp;
import com.oracle.graal.lir.amd64.AMD64Move.MembarOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveToRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.NullCheckOp;
import com.oracle.graal.lir.amd64.AMD64Move.SpillMoveOp;
import com.oracle.graal.lir.amd64.AMD64Move.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.util.*;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public abstract class AMD64LIRGenerator extends LIRGenerator {

    public static final Descriptor ARITHMETIC_FREM = new Descriptor("arithmeticFrem", false, float.class, float.class, float.class);
    public static final Descriptor ARITHMETIC_DREM = new Descriptor("arithmeticDrem", false, double.class, double.class, double.class);

    private static final RegisterValue RAX_I = AMD64.rax.asValue(Kind.Int);
    private static final RegisterValue RAX_L = AMD64.rax.asValue(Kind.Long);
    private static final RegisterValue RDX_I = AMD64.rdx.asValue(Kind.Int);
    private static final RegisterValue RDX_L = AMD64.rdx.asValue(Kind.Long);
    private static final RegisterValue RCX_I = AMD64.rcx.asValue(Kind.Int);

    public static class AMD64SpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(Value result, Value input) {
            return new SpillMoveOp(result, input);
        }
    }

    public AMD64LIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, ResolvedJavaMethod method, LIR lir) {
        super(graph, runtime, target, frameMap, method, lir);
        lir.spillMoveFactory = new AMD64SpillMoveFactory();
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
        // there is no immediate move of 64-bit constants on Intel
        switch (c.getKind()) {
            case Long:
                return Util.isInt(c.asLong()) && !runtime.needsDataPatch(c);
            case Double:
                return false;
            case Object:
                return c.isNull();
            default:
                return true;
        }
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
    public Address makeAddress(LocationNode location, ValueNode object) {
        Value base = operand(object);
        Value index = Value.ILLEGAL;
        int scale = 1;
        int displacement = location.displacement();

        if (isConstant(base)) {
            if (asConstant(base).isNull()) {
                base = Value.ILLEGAL;
            } else if (asConstant(base).getKind() != Kind.Object) {
                long newDisplacement = displacement + asConstant(base).asLong();
                if (NumUtil.isInt(newDisplacement)) {
                    assert !runtime.needsDataPatch(asConstant(base));
                    displacement = (int) newDisplacement;
                    base = Value.ILLEGAL;
                } else {
                    Value newBase = newVariable(Kind.Long);
                    emitMove(base, newBase);
                    base = newBase;
                }
            }
        }

        if (location instanceof IndexedLocationNode) {
            IndexedLocationNode indexedLoc = (IndexedLocationNode) location;

            index = operand(indexedLoc.index());
            if (indexedLoc.indexScalingEnabled()) {
                scale = target().sizeInBytes(location.getValueKind());
            }
            if (isConstant(index)) {
                long newDisplacement = displacement + asConstant(index).asLong() * scale;
                // only use the constant index if the resulting displacement fits into a 32 bit
                // offset
                if (NumUtil.isInt(newDisplacement)) {
                    displacement = (int) newDisplacement;
                    index = Value.ILLEGAL;
                } else {
                    // create a temporary variable for the index, the pointer load cannot handle a
                    // constant index
                    Value newIndex = newVariable(Kind.Long);
                    emitMove(index, newIndex);
                    index = newIndex;
                }
            }
        }

        return new Address(location.getValueKind(), base, index, Address.Scale.fromInt(scale), displacement);
    }

    @Override
    public Variable emitMove(Value input) {
        Variable result = newVariable(input.getKind());
        emitMove(input, result);
        return result;
    }

    @Override
    public void emitMove(Value src, Value dst) {
        if (isRegister(src) || isStackSlot(dst)) {
            append(new MoveFromRegOp(dst, src));
        } else {
            append(new MoveToRegOp(dst, src));
        }
    }

    @Override
    public Variable emitLoad(Value loadAddress, boolean canTrap) {
        Variable result = newVariable(loadAddress.getKind());
        append(new LoadOp(result, loadAddress, canTrap ? state() : null));
        return result;
    }

    @Override
    public void emitStore(Value storeAddress, Value inputVal, boolean canTrap) {
        Value input = loadForStore(inputVal, storeAddress.getKind());
        append(new StoreOp(storeAddress, input, canTrap ? state() : null));
    }

    @Override
    public Variable emitLea(Value address) {
        Variable result = newVariable(target().wordKind);
        append(new LeaOp(result, address));
        return result;
    }

    @Override
    public void emitJump(LabelRef label, LIRFrameState info) {
        append(new JumpOp(label, info));
    }

    @Override
    public void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef label, LIRFrameState info) {
        boolean mirrored = emitCompare(left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        switch (left.getKind().getStackKind()) {
            case Int:
            case Long:
            case Object:
                append(new BranchOp(finalCondition, label, info));
                break;
            case Float:
            case Double:
                append(new FloatBranchOp(finalCondition, unorderedIsTrue, label, info));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, boolean negated, LabelRef label, LIRFrameState info) {
        emitIntegerTest(left, right);
        append(new BranchOp(negated ? Condition.NE : Condition.EQ, label, info));
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

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        Variable result = newVariable(trueValue.getKind());
        append(new CondMoveOp(result, Condition.EQ, load(trueValue), loadNonConst(falseValue)));
        return result;
    }

    private void emitIntegerTest(Value a, Value b) {
        assert a.getKind().getStackKind() == Kind.Int || a.getKind() == Kind.Long;
        if (LIRValueUtil.isVariable(b)) {
            append(new AMD64TestOp(load(b), loadNonConst(a)));
        } else {
            append(new AMD64TestOp(load(a), loadNonConst(b)));
        }
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
    public Variable emitNegate(Value input) {
        Variable result = newVariable(input.getKind());
        switch (input.getKind()) {
            case Int:
                append(new Op1Stack(INEG, result, input));
                break;
            case Long:
                append(new Op1Stack(LNEG, result, input));
                break;
            case Float:
                append(new Op2Reg(FXOR, result, input, Constant.forFloat(Float.intBitsToFloat(0x80000000))));
                break;
            case Double:
                append(new Op2Reg(DXOR, result, input, Constant.forDouble(Double.longBitsToDouble(0x8000000000000000L))));
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
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitSub(Value a, Value b) {
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
                append(new Op2Stack(FMUL, result, a, loadNonConst(b)));
                break;
            case Double:
                append(new Op2Stack(DMUL, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        if ((valueNode instanceof IntegerDivNode) || (valueNode instanceof IntegerRemNode)) {
            FixedBinaryNode divRem = (FixedBinaryNode) valueNode;
            FixedNode node = divRem.next();
            while (node instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) node;
                if (((fixedWithNextNode instanceof IntegerDivNode) || (fixedWithNextNode instanceof IntegerRemNode)) && fixedWithNextNode.getClass() != divRem.getClass()) {
                    FixedBinaryNode otherDivRem = (FixedBinaryNode) fixedWithNextNode;
                    if (otherDivRem.x() == divRem.x() && otherDivRem.y() == divRem.y() && operand(otherDivRem) == null) {
                        Value[] results = emitIntegerDivRem(operand(divRem.x()), operand(divRem.y()));
                        if (divRem instanceof IntegerDivNode) {
                            setResult(divRem, results[0]);
                            setResult(otherDivRem, results[1]);
                        } else {
                            setResult(divRem, results[1]);
                            setResult(otherDivRem, results[0]);
                        }
                        return true;
                    }
                }
                node = fixedWithNextNode.next();
            }
        }
        return false;
    }

    public Value[] emitIntegerDivRem(Value a, Value b) {
        switch (a.getKind()) {
            case Int:
                emitMove(a, RAX_I);
                append(new DivRemOp(IDIVREM, RAX_I, load(b), state()));
                return new Value[]{emitMove(RAX_I), emitMove(RDX_I)};
            case Long:
                emitMove(a, RAX_L);
                append(new DivRemOp(LDIVREM, RAX_L, load(b), state()));
                return new Value[]{emitMove(RAX_L), emitMove(RDX_L)};
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitDiv(Value a, Value b) {
        switch (a.getKind()) {
            case Int:
                emitMove(a, RAX_I);
                append(new DivOp(IDIV, RAX_I, RAX_I, load(b), state()));
                return emitMove(RAX_I);
            case Long:
                emitMove(a, RAX_L);
                append(new DivOp(LDIV, RAX_L, RAX_L, load(b), state()));
                return emitMove(RAX_L);
            case Float: {
                Variable result = newVariable(a.getKind());
                append(new Op2Stack(FDIV, result, a, loadNonConst(b)));
                return result;
            }
            case Double: {
                Variable result = newVariable(a.getKind());
                append(new Op2Stack(DDIV, result, a, loadNonConst(b)));
                return result;
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitRem(Value a, Value b) {
        switch (a.getKind()) {
            case Int:
                emitMove(a, RAX_I);
                append(new DivOp(IREM, RDX_I, RAX_I, load(b), state()));
                return emitMove(RDX_I);
            case Long:
                emitMove(a, RAX_L);
                append(new DivOp(LREM, RDX_L, RAX_L, load(b), state()));
                return emitMove(RDX_L);
            case Float: {
                RuntimeCallTarget stub = runtime.lookupRuntimeCall(ARITHMETIC_FREM);
                return emitCall(stub, stub.getCallingConvention(), false, a, b);
            }
            case Double: {
                RuntimeCallTarget stub = runtime.lookupRuntimeCall(ARITHMETIC_DREM);
                return emitCall(stub, stub.getCallingConvention(), false, a, b);
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitUDiv(Value a, Value b) {
        switch (a.getKind()) {
            case Int:
                emitMove(a, RAX_I);
                append(new DivOp(IUDIV, RAX_I, RAX_I, load(b), state()));
                return emitMove(RAX_I);
            case Long:
                emitMove(a, RAX_L);
                append(new DivOp(LUDIV, RAX_L, RAX_L, load(b), state()));
                return emitMove(RAX_L);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitURem(Value a, Value b) {
        switch (a.getKind()) {
            case Int:
                emitMove(a, RAX_I);
                append(new DivOp(IUREM, RDX_I, RAX_I, load(b), state()));
                return emitMove(RDX_I);
            case Long:
                emitMove(a, RAX_L);
                append(new DivOp(LUREM, RDX_L, RAX_L, load(b), state()));
                return emitMove(RDX_L);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Stack(IAND, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LAND, result, a, loadNonConst(b)));
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
                append(new Op2Stack(IOR, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LOR, result, a, loadNonConst(b)));
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
    public Variable emitShl(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new ShiftOp(ISHL, result, a, loadShiftCount(b)));
                break;
            case Long:
                append(new ShiftOp(LSHL, result, a, loadShiftCount(b)));
                break;
            default:
                GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new ShiftOp(ISHR, result, a, loadShiftCount(b)));
                break;
            case Long:
                append(new ShiftOp(LSHR, result, a, loadShiftCount(b)));
                break;
            default:
                GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new ShiftOp(IUSHR, result, a, loadShiftCount(b)));
                break;
            case Long:
                append(new ShiftOp(LUSHR, result, a, loadShiftCount(b)));
                break;
            default:
                GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    private Value loadShiftCount(Value value) {
        if (isConstant(value)) {
            return value;
        }
        // Non-constant shift count must be in RCX
        emitMove(value, RCX_I);
        return RCX_I;
    }

    @Override
    public Variable emitConvert(ConvertNode.Op opcode, Value inputVal) {
        Variable input = load(inputVal);
        Variable result = newVariable(opcode.to);
        switch (opcode) {
            case I2L:
                append(new Op1Reg(I2L, result, input));
                break;
            case L2I:
                append(new Op1Stack(L2I, result, input));
                break;
            case I2B:
                append(new Op1Stack(I2B, result, input));
                break;
            case I2C:
                append(new Op1Stack(I2C, result, input));
                break;
            case I2S:
                append(new Op1Stack(I2S, result, input));
                break;
            case F2D:
                append(new Op1Reg(F2D, result, input));
                break;
            case D2F:
                append(new Op1Reg(D2F, result, input));
                break;
            case I2F:
                append(new Op1Reg(I2F, result, input));
                break;
            case I2D:
                append(new Op1Reg(I2D, result, input));
                break;
            case F2I:
                append(new Op1Reg(F2I, result, input));
                break;
            case D2I:
                append(new Op1Reg(D2I, result, input));
                break;
            case L2F:
                append(new Op1Reg(L2F, result, input));
                break;
            case L2D:
                append(new Op1Reg(L2D, result, input));
                break;
            case F2L:
                append(new Op1Reg(F2L, result, input));
                break;
            case D2L:
                append(new Op1Reg(D2L, result, input));
                break;
            case MOV_I2F:
                append(new Op1Reg(MOV_I2F, result, input));
                break;
            case MOV_L2D:
                append(new Op1Reg(MOV_L2D, result, input));
                break;
            case MOV_F2I:
                append(new Op1Reg(MOV_F2I, result, input));
                break;
            case MOV_D2L:
                append(new Op1Reg(MOV_D2L, result, input));
                break;
            case UNSIGNED_I2L:
                // Instructions that move or generate 32-bit register values also set the upper 32
                // bits of the register to zero.
                // Consequently, there is no need for a special zero-extension move.
                emitMove(input, result);
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public void emitDeoptimizeOnOverflow(DeoptimizationAction action, DeoptimizationReason reason, Object deoptInfo) {
        LIRFrameState info = state();
        LabelRef stubEntry = createDeoptStub(action, reason, info, deoptInfo);
        append(new BranchOp(ConditionFlag.overflow, stubEntry, info));
    }

    @Override
    public void emitDeoptimize(DeoptimizationAction action, DeoptimizationReason reason, Object deoptInfo) {
        LIRFrameState info = state();
        LabelRef stubEntry = createDeoptStub(action, reason, info, deoptInfo);
        append(new JumpOp(stubEntry, info));
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target.arch.requiredBarriers(barriers);
        if (target.isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        append(new DirectCallOp(callTarget.target(), result, parameters, temps, callState));
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        // The current register allocator cannot handle variables at call sites, need a fixed
        // register.
        Value targetAddress = AMD64.rax.asValue();
        emitMove(operand(callTarget.computedAddress()), targetAddress);
        append(new IndirectCallOp(callTarget.target(), result, parameters, temps, targetAddress, callState));
    }

    @Override
    protected void emitCall(RuntimeCallTarget callTarget, Value result, Value[] arguments, Value[] temps, Value targetAddress, LIRFrameState info) {
        if (isConstant(targetAddress)) {
            append(new DirectCallOp(callTarget, result, arguments, temps, info));
        } else {
            append(new IndirectCallOp(callTarget, result, arguments, temps, targetAddress, info));
        }
    }

    @Override
    public void emitBitCount(Variable result, Value value) {
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new AMD64BitManipulationOp(IPOPCNT, result, value));
        } else {
            append(new AMD64BitManipulationOp(LPOPCNT, result, value));
        }
    }

    @Override
    public void emitBitScanForward(Variable result, Value value) {
        append(new AMD64BitManipulationOp(BSF, result, value));
    }

    @Override
    public void emitBitScanReverse(Variable result, Value value) {
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new AMD64BitManipulationOp(IBSR, result, value));
        } else {
            append(new AMD64BitManipulationOp(LBSR, result, value));
        }
    }

    @Override
    public void emitMathAbs(Variable result, Variable input) {
        append(new Op2Reg(DAND, result, input, Constant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL))));
    }

    @Override
    public void emitMathSqrt(Variable result, Variable input) {
        append(new AMD64MathIntrinsicOp(SQRT, result, input));
    }

    @Override
    public void emitMathLog(Variable result, Variable input, boolean base10) {
        append(new AMD64MathIntrinsicOp(base10 ? LOG10 : LOG, result, input));
    }

    @Override
    public void emitMathCos(Variable result, Variable input) {
        append(new AMD64MathIntrinsicOp(COS, result, input));
    }

    @Override
    public void emitMathSin(Variable result, Variable input) {
        append(new AMD64MathIntrinsicOp(SIN, result, input));
    }

    @Override
    public void emitMathTan(Variable result, Variable input) {
        append(new AMD64MathIntrinsicOp(TAN, result, input));
    }

    @Override
    public void emitByteSwap(Variable result, Value input) {
        append(new AMD64ByteSwapOp(result, input));
    }

    @Override
    protected void emitReturn(Value input) {
        append(new ReturnOp(input));
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
    protected LabelRef createDeoptStub(DeoptimizationAction action, DeoptimizationReason reason, LIRFrameState info, Object deoptInfo) {
        assert info.topFrame.getBCI() >= 0 : "invalid bci for deopt framestate";
        AMD64DeoptimizationStub stub = new AMD64DeoptimizationStub(action, reason, info, deoptInfo);
        lir.stubs.add(stub);
        return LabelRef.forLabel(stub.label);
    }

    @Override
    protected void emitNullCheckGuard(ValueNode object) {
        Variable value = load(operand(object));
        LIRFrameState info = state();
        append(new NullCheckOp(value, info));
    }

    @Override
    public void visitCompareAndSwap(CompareAndSwapNode node) {
        Kind kind = node.newValue().kind();
        assert kind == node.expected().kind();

        Value expected = loadNonConst(operand(node.expected()));
        Variable newValue = load(operand(node.newValue()));

        Address address;
        int displacement = node.displacement();
        Value index = operand(node.offset());
        if (isConstant(index) && NumUtil.isInt(asConstant(index).asLong() + displacement)) {
            assert !runtime.needsDataPatch(asConstant(index));
            displacement += (int) asConstant(index).asLong();
            address = new Address(kind, load(operand(node.object())), displacement);
        } else {
            address = new Address(kind, load(operand(node.object())), load(index), Address.Scale.Times1, displacement);
        }

        RegisterValue rax = AMD64.rax.asValue(kind);
        emitMove(expected, rax);
        append(new CompareAndSwapOp(rax, address, rax, newValue));

        Variable result = newVariable(node.kind());
        append(new CondMoveOp(result, Condition.EQ, load(Constant.TRUE), Constant.FALSE));
        setResult(node, result);
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments.size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments.get(i).stamp().javaType(runtime);
        }

        CallingConvention cc = frameMap.registerConfig.getCallingConvention(CallingConvention.Type.JavaCall, null, sig, target(), false);
        Value[] parameters = visitInvokeArguments(cc, node.arguments);
        append(new AMD64BreakpointOp(parameters));
    }
}
