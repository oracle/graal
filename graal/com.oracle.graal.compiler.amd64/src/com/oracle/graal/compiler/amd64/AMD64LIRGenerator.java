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
import static com.oracle.graal.lir.amd64.AMD64BitManipulationOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.amd64.AMD64Compare.*;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Address.Scale;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.BinaryCommutative;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.BinaryMemory;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.BinaryRegConst;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.BinaryRegReg;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.BinaryRegStack;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.BinaryRegStackConst;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.DivRemOp;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.FPDivRemOp;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Unary1Op;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Unary2MemoryOp;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Unary2Op;
import com.oracle.graal.lir.amd64.AMD64Compare.CompareMemoryOp;
import com.oracle.graal.lir.amd64.AMD64Compare.CompareOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.CondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.ReturnOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.TableSwitchOp;
import com.oracle.graal.lir.amd64.AMD64Move.LeaDataOp;
import com.oracle.graal.lir.amd64.AMD64Move.LeaOp;
import com.oracle.graal.lir.amd64.AMD64Move.MembarOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveToRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.StackLeaOp;
import com.oracle.graal.lir.amd64.AMD64Move.ZeroExtendLoadOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.phases.util.*;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public abstract class AMD64LIRGenerator extends LIRGenerator {

    private static final RegisterValue RAX_I = AMD64.rax.asValue(Kind.Int);
    private static final RegisterValue RAX_L = AMD64.rax.asValue(Kind.Long);
    private static final RegisterValue RDX_I = AMD64.rdx.asValue(Kind.Int);
    private static final RegisterValue RDX_L = AMD64.rdx.asValue(Kind.Long);
    private static final RegisterValue RCX_I = AMD64.rcx.asValue(Kind.Int);

    private class AMD64SpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(AllocatableValue result, Value input) {
            return AMD64LIRGenerator.this.createMove(result, input);
        }
    }

    public AMD64LIRGenerator(Providers providers, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(providers, cc, lirGenRes);
        lirGenRes.getLIR().setSpillMoveFactory(new AMD64SpillMoveFactory());
    }

    @Override
    public boolean canStoreConstant(Constant c, boolean isCompressed) {
        // there is no immediate move of 64-bit constants on Intel
        switch (c.getKind()) {
            case Long:
                if (isCompressed) {
                    return true;
                }
                return Util.isInt(c.asLong()) && !getCodeCache().needsDataPatch(c);
            case Double:
                return false;
            case Object:
                if (isCompressed) {
                    return true;
                }
                return c.isNull();
            default:
                return true;
        }
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
        PlatformKind kind;
        if (input instanceof Constant) {
            kind = input.getKind().getStackKind();
        } else {
            kind = input.getPlatformKind();
        }
        Variable result = newVariable(kind);
        emitMove(result, input);
        return result;
    }

    protected AMD64LIRInstruction createMove(AllocatableValue dst, Value src) {
        if (src instanceof AMD64AddressValue) {
            return new LeaOp(dst, (AMD64AddressValue) src);
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
        append(new LeaDataOp(dst, data));
    }

    @Override
    public AMD64AddressValue emitAddress(Value base, long displacement, Value index, int scale) {
        AllocatableValue baseRegister;
        long finalDisp = displacement;
        if (isConstant(base)) {
            if (asConstant(base).isNull()) {
                baseRegister = Value.ILLEGAL;
            } else if (asConstant(base).getKind() != Kind.Object && !getCodeCache().needsDataPatch(asConstant(base))) {
                finalDisp += asConstant(base).asLong();
                baseRegister = Value.ILLEGAL;
            } else {
                baseRegister = load(base);
            }
        } else {
            baseRegister = asAllocatable(base);
        }

        AllocatableValue indexRegister;
        Scale scaleEnum;
        if (!index.equals(Value.ILLEGAL) && scale != 0) {
            scaleEnum = Scale.fromInt(scale);
            if (isConstant(index)) {
                finalDisp += asConstant(index).asLong() * scale;
                indexRegister = Value.ILLEGAL;

            } else if (scaleEnum == null) {
                /* Scale value that architecture cannot handle, so scale manually. */
                Value longIndex = index.getKind() == Kind.Long ? index : emitSignExtend(index, 32, 64);
                if (CodeUtil.isPowerOf2(scale)) {
                    indexRegister = emitShl(longIndex, Constant.forLong(CodeUtil.log2(scale)));
                } else {
                    indexRegister = emitMul(longIndex, Constant.forLong(scale));
                }
                scaleEnum = Scale.Times1;

            } else {
                indexRegister = asAllocatable(index);
            }
        } else {
            indexRegister = Value.ILLEGAL;
            scaleEnum = Scale.Times1;
        }

        int displacementInt;
        if (NumUtil.isInt(finalDisp)) {
            displacementInt = (int) finalDisp;
        } else {
            displacementInt = 0;
            AllocatableValue displacementRegister = load(Constant.forLong(finalDisp));
            if (baseRegister.equals(Value.ILLEGAL)) {
                baseRegister = displacementRegister;
            } else if (indexRegister.equals(Value.ILLEGAL)) {
                indexRegister = displacementRegister;
                scaleEnum = Scale.Times1;
            } else {
                baseRegister = emitAdd(baseRegister, displacementRegister);
            }
        }

        return new AMD64AddressValue(target().wordKind, baseRegister, indexRegister, scaleEnum, displacementInt);
    }

    public AMD64AddressValue asAddressValue(Value address) {
        if (address instanceof AMD64AddressValue) {
            return (AMD64AddressValue) address;
        } else {
            return emitAddress(address, 0, Value.ILLEGAL, 0);
        }
    }

    @Override
    public Variable emitAddress(StackSlot address) {
        Variable result = newVariable(target().wordKind);
        append(new StackLeaOp(result, address));
        return result;
    }

    @Override
    public void emitJump(LabelRef label) {
        assert label != null;
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
        boolean mirrored = emitCompare(cmpKind, left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        switch (left.getKind().getStackKind()) {
            case Int:
            case Long:
            case Object:
                append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
                break;
            case Float:
            case Double:
                append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability) {
        append(new BranchOp(ConditionFlag.Overflow, overflow, noOverflow, overflowProbability));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        emitIntegerTest(left, right);
        append(new BranchOp(Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        boolean mirrored = emitCompare(cmpKind, left, right);
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
        assert a.getKind().isNumericInteger();
        if (LIRValueUtil.isVariable(b)) {
            append(new AMD64TestOp(load(b), loadNonConst(a)));
        } else {
            append(new AMD64TestOp(load(a), loadNonConst(b)));
        }
    }

    protected void emitCompareOp(PlatformKind cmpKind, Variable left, Value right) {
        switch ((Kind) cmpKind) {
            case Byte:
            case Boolean:
                append(new CompareOp(BCMP, left, right));
                break;
            case Short:
            case Char:
                append(new CompareOp(SCMP, left, right));
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
    }

    protected void emitCompareMemoryConOp(Kind kind, AMD64AddressValue address, Value value, LIRFrameState state) {
        assert kind.getStackKind() == value.getKind().getStackKind();
        switch (kind) {
            case Byte:
            case Boolean:
                append(new CompareMemoryOp(BCMP, kind, address, value, state));
                break;
            case Short:
            case Char:
                append(new CompareMemoryOp(SCMP, kind, address, value, state));
                break;
            case Int:
                append(new CompareMemoryOp(ICMP, kind, address, value, state));
                break;
            case Long:
                append(new CompareMemoryOp(LCMP, kind, address, value, state));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    protected void emitCompareRegMemoryOp(Kind kind, Value value, AMD64AddressValue address, LIRFrameState state) {
        AMD64Compare opcode = null;
        switch (kind) {
            case Byte:
            case Boolean:
                opcode = BCMP;
                break;
            case Short:
            case Char:
                opcode = SCMP;
                break;
            case Int:
                opcode = ICMP;
                break;
            case Long:
                opcode = LCMP;
                break;
            case Object:
                opcode = ACMP;
                break;
            case Float:
                opcode = FCMP;
                break;
            case Double:
                opcode = DCMP;
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        append(new CompareMemoryOp(opcode, kind, address, value, state));
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompare(PlatformKind cmpKind, Value a, Value b) {
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
        emitCompareOp(cmpKind, left, right);
        return mirrored;
    }

    @Override
    public Variable emitNegate(Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = newVariable(input.getKind());
        switch (input.getKind()) {
            case Int:
                append(new Unary1Op(INEG, result, input));
                break;
            case Long:
                append(new Unary1Op(LNEG, result, input));
                break;
            case Float:
                append(new BinaryRegConst(FXOR, result, input, Constant.forFloat(Float.intBitsToFloat(0x80000000))));
                break;
            case Double:
                append(new BinaryRegConst(DXOR, result, input, Constant.forDouble(Double.longBitsToDouble(0x8000000000000000L))));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitNot(Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = newVariable(input.getKind());
        switch (input.getKind()) {
            case Int:
                append(new Unary1Op(INOT, result, input));
                break;
            case Long:
                append(new Unary1Op(LNOT, result, input));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    private Variable emitBinary(AMD64Arithmetic op, boolean commutative, Value a, Value b) {
        if (isConstant(b)) {
            return emitBinaryConst(op, commutative, asAllocatable(a), asConstant(b));
        } else if (commutative && isConstant(a)) {
            return emitBinaryConst(op, commutative, asAllocatable(b), asConstant(a));
        } else {
            return emitBinaryVar(op, commutative, asAllocatable(a), asAllocatable(b));
        }
    }

    private Variable emitBinaryConst(AMD64Arithmetic op, boolean commutative, AllocatableValue a, Constant b) {
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
                if (NumUtil.isInt(b.asLong())) {
                    Variable result = newVariable(a.getKind());
                    append(new BinaryRegConst(op, result, a, b));
                    return result;
                }
                break;

            case IMUL:
            case LMUL:
                if (NumUtil.isInt(b.asLong())) {
                    Variable result = newVariable(a.getKind());
                    append(new BinaryRegStackConst(op, result, a, b));
                    return result;
                }
                break;
        }

        return emitBinaryVar(op, commutative, a, asAllocatable(b));
    }

    private Variable emitBinaryVar(AMD64Arithmetic op, boolean commutative, AllocatableValue a, AllocatableValue b) {
        Variable result = newVariable(a.getKind());
        if (commutative) {
            append(new BinaryCommutative(op, result, a, b));
        } else {
            append(new BinaryRegStack(op, result, a, b));
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
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(ISUB, false, a, b);
            case Long:
                return emitBinary(LSUB, false, a, b);
            case Float:
                return emitBinary(FSUB, false, a, b);
            case Double:
                return emitBinary(DSUB, false, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitMul(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(IMUL, true, a, b);
            case Long:
                return emitBinary(LMUL, true, a, b);
            case Float:
                return emitBinary(FMUL, true, a, b);
            case Double:
                return emitBinary(DMUL, true, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    protected Value emitBinaryMemory(AMD64Arithmetic op, Kind kind, AllocatableValue a, AMD64AddressValue location, LIRFrameState state) {
        Variable result = newVariable(a.getKind());
        append(new BinaryMemory(op, kind, result, a, location, state));
        return result;
    }

    protected Value emitConvert2MemoryOp(PlatformKind kind, AMD64Arithmetic op, AMD64AddressValue address, LIRFrameState state) {
        Variable result = newVariable(kind);
        append(new Unary2MemoryOp(op, result, (Kind) null, address, state));
        return result;
    }

    protected Value emitZeroExtendMemory(Kind memoryKind, int resultBits, AMD64AddressValue address, LIRFrameState state) {
        // Issue a zero extending load of the proper bit size and set the result to
        // the proper kind.
        Variable result = newVariable(resultBits == 32 ? Kind.Int : Kind.Long);
        append(new ZeroExtendLoadOp(memoryKind, result, address, state));
        return result;
    }

    private void emitDivRem(AMD64Arithmetic op, Value a, Value b, LIRFrameState state) {
        AllocatableValue rax = AMD64.rax.asValue(a.getPlatformKind());
        emitMove(rax, a);
        append(new DivRemOp(op, rax, asAllocatable(b), state));
    }

    public Value[] emitIntegerDivRem(Value a, Value b, DeoptimizingNode deopting) {
        LIRFrameState state = state(deopting);
        switch (a.getKind().getStackKind()) {
            case Int:
                emitDivRem(IDIVREM, a, b, state);
                return new Value[]{emitMove(RAX_I), emitMove(RDX_I)};
            case Long:
                emitDivRem(LDIVREM, a, b, state);
                return new Value[]{emitMove(RAX_L), emitMove(RDX_L)};
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitDiv(Value a, Value b, DeoptimizingNode deopting) {
        switch (a.getKind().getStackKind()) {
            case Int:
                emitDivRem(IDIV, a, b, state(deopting));
                return emitMove(RAX_I);
            case Long:
                emitDivRem(LDIV, a, b, state(deopting));
                return emitMove(RAX_L);
            case Float: {
                Variable result = newVariable(a.getPlatformKind());
                append(new BinaryRegStack(FDIV, result, asAllocatable(a), asAllocatable(b)));
                return result;
            }
            case Double: {
                Variable result = newVariable(a.getPlatformKind());
                append(new BinaryRegStack(DDIV, result, asAllocatable(a), asAllocatable(b)));
                return result;
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitRem(Value a, Value b, DeoptimizingNode deopting) {
        switch (a.getKind().getStackKind()) {
            case Int:
                emitDivRem(IREM, a, b, state(deopting));
                return emitMove(RDX_I);
            case Long:
                emitDivRem(LREM, a, b, state(deopting));
                return emitMove(RDX_L);
            case Float: {
                Variable result = newVariable(a.getPlatformKind());
                append(new FPDivRemOp(FREM, result, load(a), load(b)));
                return result;
            }
            case Double: {
                Variable result = newVariable(a.getPlatformKind());
                append(new FPDivRemOp(DREM, result, load(a), load(b)));
                return result;
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitUDiv(Value a, Value b, DeoptimizingNode deopting) {
        LIRFrameState state = state(deopting);
        switch (a.getKind().getStackKind()) {
            case Int:
                emitDivRem(IUDIV, a, b, state);
                return emitMove(RAX_I);
            case Long:
                emitDivRem(LUDIV, a, b, state);
                return emitMove(RAX_L);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitURem(Value a, Value b, DeoptimizingNode deopting) {
        LIRFrameState state = state(deopting);
        switch (a.getKind().getStackKind()) {
            case Int:
                emitDivRem(IUREM, a, b, state);
                return emitMove(RDX_I);
            case Long:
                emitDivRem(LUREM, a, b, state);
                return emitMove(RDX_L);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(IAND, true, a, b);
            case Long:
                return emitBinary(LAND, true, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(IOR, true, a, b);
            case Long:
                return emitBinary(LOR, true, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(IXOR, true, a, b);
            case Long:
                return emitBinary(LXOR, true, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private Variable emitShift(AMD64Arithmetic op, Value a, Value b) {
        Variable result = newVariable(a.getPlatformKind());
        AllocatableValue input = asAllocatable(a);
        if (isConstant(b)) {
            append(new BinaryRegConst(op, result, input, asConstant(b)));
        } else {
            emitMove(RCX_I, b);
            append(new BinaryRegReg(op, result, input, RCX_I));
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

    private AllocatableValue emitConvert1Op(PlatformKind kind, AMD64Arithmetic op, AllocatableValue input) {
        Variable result = newVariable(kind);
        append(new Unary1Op(op, result, input));
        return result;
    }

    private AllocatableValue emitConvert2Op(PlatformKind kind, AMD64Arithmetic op, AllocatableValue input) {
        Variable result = newVariable(kind);
        append(new Unary2Op(op, result, input));
        return result;
    }

    @Override
    public Value emitReinterpret(PlatformKind to, Value inputVal) {
        Kind from = inputVal.getKind();
        if (to == from) {
            return inputVal;
        }

        AllocatableValue input = asAllocatable(inputVal);
        /*
         * Conversions between integer to floating point types require moves between CPU and FPU
         * registers.
         */
        switch ((Kind) to) {
            case Int:
                switch (from) {
                    case Float:
                        return emitConvert2Op(to, MOV_F2I, input);
                }
                break;
            case Long:
                switch (from) {
                    case Double:
                        return emitConvert2Op(to, MOV_D2L, input);
                }
                break;
            case Float:
                switch (from) {
                    case Int:
                        return emitConvert2Op(to, MOV_I2F, input);
                }
                break;
            case Double:
                switch (from) {
                    case Long:
                        return emitConvert2Op(to, MOV_L2D, input);
                }
                break;
        }
        throw GraalInternalError.shouldNotReachHere();
    }

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
            // TODO make it possible to reinterpret Long as Int in LIR without move
            return emitConvert1Op(Kind.Int, L2I, asAllocatable(inputVal));
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
                    return emitConvert2Op(Kind.Long, B2L, asAllocatable(inputVal));
                case 16:
                    return emitConvert2Op(Kind.Long, S2L, asAllocatable(inputVal));
                case 32:
                    return emitConvert2Op(Kind.Long, I2L, asAllocatable(inputVal));
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    return emitConvert2Op(Kind.Int, B2I, asAllocatable(inputVal));
                case 16:
                    return emitConvert2Op(Kind.Int, S2I, asAllocatable(inputVal));
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
            append(new BinaryRegConst(AMD64Arithmetic.LAND, result, asAllocatable(inputVal), Constant.forLong(mask)));
            return result;
        } else {
            assert inputVal.getKind().getStackKind() == Kind.Int;
            Variable result = newVariable(Kind.Int);
            int mask = (int) IntegerStamp.defaultMask(fromBits);
            append(new BinaryRegConst(AMD64Arithmetic.IAND, result, asAllocatable(inputVal), Constant.forInt(mask)));
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
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    public abstract void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments);

    @Override
    protected void emitForeignCall(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        if (maxOffset != (int) maxOffset) {
            append(new AMD64Call.DirectFarForeignCallOp(linkage, result, arguments, temps, info));
        } else {
            append(new AMD64Call.DirectNearForeignCallOp(linkage, result, arguments, temps, info));
        }
    }

    @Override
    public void emitBitCount(Variable result, Value value) {
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new AMD64BitManipulationOp(IPOPCNT, result, asAllocatable(value)));
        } else {
            append(new AMD64BitManipulationOp(LPOPCNT, result, asAllocatable(value)));
        }
    }

    @Override
    public void emitBitScanForward(Variable result, Value value) {
        append(new AMD64BitManipulationOp(BSF, result, asAllocatable(value)));
    }

    @Override
    public void emitBitScanReverse(Variable result, Value value) {
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new AMD64BitManipulationOp(IBSR, result, asAllocatable(value)));
        } else {
            append(new AMD64BitManipulationOp(LBSR, result, asAllocatable(value)));
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
        append(new Unary2Op(SQRT, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        Variable result = newVariable(input.getPlatformKind());
        append(new AMD64MathIntrinsicOp(base10 ? LOG10 : LOG, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathCos(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new AMD64MathIntrinsicOp(COS, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathSin(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new AMD64MathIntrinsicOp(SIN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathTan(Value input) {
        Variable result = newVariable(input.getPlatformKind());
        append(new AMD64MathIntrinsicOp(TAN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public void emitByteSwap(Variable result, Value input) {
        append(new AMD64ByteSwapOp(result, input));
    }

    @Override
    public void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length) {
        append(new AMD64ArrayEqualsOp(this, kind, result, array1, array2, asAllocatable(length)));
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

    @Override
    protected void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        // a temp is needed for loading object constants
        boolean needsTemp = key.getKind() == Kind.Object;
        append(new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, needsTemp ? newVariable(key.getKind()) : Value.ILLEGAL));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        append(new TableSwitchOp(lowKey, defaultTarget, targets, key, newVariable(target().wordKind), newVariable(key.getPlatformKind())));
    }

}
