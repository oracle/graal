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

package com.oracle.graal.compiler.ptx;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.api.meta.Value.*;
import static com.oracle.graal.lir.ptx.PTXArithmetic.*;
import static com.oracle.graal.lir.ptx.PTXBitManipulationOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.ptx.PTXCompare.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.ptx.*;
import com.oracle.graal.lir.ptx.PTXArithmetic.ConvertOp;
import com.oracle.graal.lir.ptx.PTXArithmetic.Op1Stack;
import com.oracle.graal.lir.ptx.PTXArithmetic.Op2Reg;
import com.oracle.graal.lir.ptx.PTXArithmetic.Op2Stack;
import com.oracle.graal.lir.ptx.PTXArithmetic.ShiftOp;
import com.oracle.graal.lir.ptx.PTXCompare.CompareOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.BranchOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.CondMoveOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.ReturnNoValOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.ReturnOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.TableSwitchOp;
import com.oracle.graal.lir.ptx.PTXMemOp.LoadOp;
import com.oracle.graal.lir.ptx.PTXMemOp.LoadParamOp;
import com.oracle.graal.lir.ptx.PTXMemOp.LoadReturnAddrOp;
import com.oracle.graal.lir.ptx.PTXMemOp.StoreOp;
import com.oracle.graal.lir.ptx.PTXMemOp.StoreReturnValOp;
import com.oracle.graal.lir.ptx.PTXMove.MoveFromRegOp;
import com.oracle.graal.lir.ptx.PTXMove.MoveToRegOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.util.*;

/**
 * This class implements the PTX specific portion of the LIR generator.
 */
public class PTXLIRGenerator extends LIRGenerator {

    // Number of the predicate register that can be used when needed.
    // This value will be recorded and incremented in the LIR instruction
    // that sets a predicate register. (e.g., CompareOp)
    private int nextPredRegNum;

    public static final ForeignCallDescriptor ARITHMETIC_FREM = new ForeignCallDescriptor("arithmeticFrem", float.class, float.class, float.class);
    public static final ForeignCallDescriptor ARITHMETIC_DREM = new ForeignCallDescriptor("arithmeticDrem", double.class, double.class, double.class);

    public static class PTXSpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(AllocatableValue result, Value input) {
            throw GraalInternalError.unimplemented("PTXSpillMoveFactory.createMove()");
        }
    }

    public PTXLIRGenerator(Providers providers, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(providers, cc, lirGenRes);
        lirGenRes.getLIR().setSpillMoveFactory(new PTXSpillMoveFactory());
        int callVariables = cc.getArgumentCount() + (cc.getReturn().equals(Value.ILLEGAL) ? 0 : 1);
        lirGenRes.getLIR().setFirstVariableNumber(callVariables);
        nextPredRegNum = 0;
    }

    public int getNextPredRegNumber() {
        return nextPredRegNum;
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

    protected static AllocatableValue toParamKind(AllocatableValue value) {
        if (value.getKind().getStackKind() != value.getKind()) {
            // We only have stack-kinds in the LIR, so convert the operand kind for values from the
            // calling convention.
            if (isRegister(value)) {
                return asRegister(value).asValue(value.getKind().getStackKind());
            } else if (isStackSlot(value)) {
                return StackSlot.get(value.getKind().getStackKind(), asStackSlot(value).getRawOffset(), asStackSlot(value).getRawAddFrameSize());
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
        return value;
    }

    public Variable emitWarpParam(Kind kind, Warp annotation) {
        Variable result = newVariable(kind);
        Variable tid = newVariable(Kind.Char);

        switch (annotation.dimension()) {
            case X:
                tid.setName("%tid.x");
                break;
            case Y:
                tid.setName("%tid.y");
                break;
            case Z:
                tid.setName("%tid.y");
                break;
        }
        emitMove(result, tid);

        return result;
    }

    @Override
    public Variable emitMove(Value input) {
        Variable result = newVariable(input.getKind());
        emitMove(result, input);
        return result;
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        if (isRegister(src) || isStackSlot(dst)) {
            append(new MoveFromRegOp(dst, src));
        } else {
            append(new MoveToRegOp(dst, src));
        }
    }

    @Override
    public void emitData(AllocatableValue dst, byte[] data) {
        throw GraalInternalError.unimplemented();
    }

    @Override
    public PTXAddressValue emitAddress(Value base, long displacement, Value index, int scale) {
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
        } else if (base.equals(Value.ILLEGAL)) {
            baseRegister = Value.ILLEGAL;
        } else {
            baseRegister = asAllocatable(base);
        }

        if (!index.equals(Value.ILLEGAL)) {
            if (isConstant(index)) {
                finalDisp += asConstant(index).asLong() * scale;
            } else {
                Value convertedIndex;
                Value indexRegister;

                convertedIndex = emitSignExtend(index, 32, 64);
                if (scale != 1) {
                    if (CodeUtil.isPowerOf2(scale)) {
                        indexRegister = emitShl(convertedIndex, Constant.forInt(CodeUtil.log2(scale)));
                    } else {
                        indexRegister = emitMul(convertedIndex, Constant.forInt(scale));
                    }
                } else {
                    indexRegister = convertedIndex;
                }
                if (baseRegister.equals(Value.ILLEGAL)) {
                    baseRegister = asAllocatable(indexRegister);
                } else {
                    Variable longBaseRegister = newVariable(Kind.Long);
                    emitMove(longBaseRegister, baseRegister);
                    baseRegister = emitAdd(longBaseRegister, indexRegister);
                }
            }
        }

        return new PTXAddressValue(target().wordKind, baseRegister, finalDisp);
    }

    private PTXAddressValue asAddress(Value address) {
        assert address != null;

        if (address instanceof PTXAddressValue) {
            return (PTXAddressValue) address;
        } else {
            return emitAddress(address, 0, Value.ILLEGAL, 0);
        }
    }

    @Override
    public Variable emitLoad(PlatformKind kind, Value address, Access access) {
        PTXAddressValue loadAddress = asAddress(address);
        Variable result = newVariable(kind);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        append(new LoadOp((Kind) kind, result, loadAddress, state));
        return result;
    }

    @Override
    public void emitStore(PlatformKind kind, Value address, Value inputVal, Access access) {
        PTXAddressValue storeAddress = asAddress(address);
        Variable input = load(inputVal);
        LIRFrameState state = null;
        if (access instanceof DeoptimizingNode) {
            state = state((DeoptimizingNode) access);
        }
        append(new StoreOp((Kind) kind, storeAddress, input, state));
    }

    @Override
    public Variable emitAddress(StackSlot address) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitAddress()");
    }

    @Override
    public void emitJump(LabelRef label) {
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        switch (left.getKind().getStackKind()) {
            case Int:
                append(new CompareOp(ICMP, cond, left, right, nextPredRegNum));
                append(new BranchOp(cond, trueDestination, falseDestination, nextPredRegNum++));
                break;
            case Long:
                append(new CompareOp(LCMP, cond, left, right, nextPredRegNum));
                append(new BranchOp(cond, trueDestination, falseDestination, nextPredRegNum++));
                break;
            case Float:
                append(new CompareOp(FCMP, cond, left, right, nextPredRegNum));
                append(new BranchOp(cond, trueDestination, falseDestination, nextPredRegNum++));
                break;
            case Double:
                append(new CompareOp(DCMP, cond, left, right, nextPredRegNum));
                append(new BranchOp(cond, trueDestination, falseDestination, nextPredRegNum++));
                break;
            case Object:
                append(new CompareOp(ACMP, cond, left, right, nextPredRegNum));
                append(new BranchOp(cond, trueDestination, falseDestination, nextPredRegNum++));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, double overflowProbability) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitOverflowCheckBranch()");
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        // / emitIntegerTest(left, right);
        // append(new BranchOp(negated ? Condition.NE : Condition.EQ, label));
        throw GraalInternalError.unimplemented("emitIntegerTestBranch()");
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {

        Condition finalCondition = LIRValueUtil.isVariable(right) ? cond.mirror() : cond;

        emitCompare(finalCondition, left, right);

        Variable result = newVariable(trueValue.getKind());
        switch (left.getKind().getStackKind()) {
            case Int:
            case Long:
            case Object:
                append(new CondMoveOp(result, finalCondition, load(trueValue), loadNonConst(falseValue), nextPredRegNum));
                nextPredRegNum++;
                break;
            case Float:
            case Double:
                append(new FloatCondMoveOp(result, finalCondition, unorderedIsTrue, load(trueValue), load(falseValue), nextPredRegNum));
                nextPredRegNum++;
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
    private boolean emitCompare(Condition cond, Value a, Value b) {
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
                append(new CompareOp(ICMP, cond, left, right, nextPredRegNum));
                break;
            case Long:
                append(new CompareOp(LCMP, cond, left, right, nextPredRegNum));
                break;
            case Object:
                append(new CompareOp(ACMP, cond, left, right, nextPredRegNum));
                break;
            case Float:
                append(new CompareOp(FCMP, cond, left, right, nextPredRegNum));
                break;
            case Double:
                append(new CompareOp(DCMP, cond, left, right, nextPredRegNum));
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
        append(new CondMoveOp(result, Condition.EQ, load(trueValue), loadNonConst(falseValue), nextPredRegNum));
        nextPredRegNum++;

        return result;
    }

    private void emitIntegerTest(Value a, Value b) {
        assert a.getKind().isNumericInteger();

        if (LIRValueUtil.isVariable(b)) {
            append(new PTXTestOp(load(b), loadNonConst(a), nextPredRegNum));
        } else {
            append(new PTXTestOp(load(a), loadNonConst(b), nextPredRegNum));
        }
    }

    @Override
    public Variable emitNegate(Value input) {
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
    public Variable emitNot(Value input) {
        Variable result = newVariable(input.getKind());
        switch (input.getKind()) {
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
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
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
    public Variable emitUDiv(Value a, Value b, DeoptimizingNode deopting) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitUDiv()");
    }

    @Override
    public Variable emitURem(Value a, Value b, DeoptimizingNode deopting) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitURem()");
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
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
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
                throw GraalInternalError.shouldNotReachHere("missing: " + a.getKind());
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
                append(new Op2Stack(ISHL, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LSHL, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Stack(ISHR, result, a, loadNonConst(b)));
                break;
            case Long:
                append(new Op2Stack(LSHR, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

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
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    public Variable emitConvertOp(Kind from, Kind to, Value inputVal) {
        Variable input = load(inputVal);
        Variable result = newVariable(to);
        append(new ConvertOp(result, input, to, from));
        return result;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        switch (op) {
            case D2F:
                return emitConvertOp(Kind.Double, Kind.Float, inputVal);
            case D2I:
                return emitConvertOp(Kind.Double, Kind.Int, inputVal);
            case D2L:
                return emitConvertOp(Kind.Double, Kind.Long, inputVal);
            case F2D:
                return emitConvertOp(Kind.Float, Kind.Double, inputVal);
            case F2I:
                return emitConvertOp(Kind.Float, Kind.Int, inputVal);
            case F2L:
                return emitConvertOp(Kind.Float, Kind.Long, inputVal);
            case I2D:
                return emitConvertOp(Kind.Int, Kind.Double, inputVal);
            case I2F:
                return emitConvertOp(Kind.Int, Kind.Float, inputVal);
            case L2D:
                return emitConvertOp(Kind.Long, Kind.Double, inputVal);
            case L2F:
                return emitConvertOp(Kind.Long, Kind.Float, inputVal);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getKind() == Kind.Long && bits <= 32) {
            return emitConvertOp(Kind.Long, Kind.Int, inputVal);
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
                    return emitConvertOp(Kind.Byte, Kind.Long, inputVal);
                case 16:
                    return emitConvertOp(Kind.Short, Kind.Long, inputVal);
                case 32:
                    return emitConvertOp(Kind.Int, Kind.Long, inputVal);
                case 64:
                    return inputVal;
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    return emitConvertOp(Kind.Byte, Kind.Int, inputVal);
                case 16:
                    return emitConvertOp(Kind.Short, Kind.Int, inputVal);
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
            append(new Op2Stack(LAND, result, inputVal, Constant.forLong(mask)));
            return result;
        } else {
            assert inputVal.getKind() == Kind.Int;
            Variable result = newVariable(Kind.Int);
            int mask = (int) IntegerStamp.defaultMask(fromBits);
            append(new Op2Stack(IAND, result, inputVal, Constant.forInt(mask)));
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
    public Value emitReinterpret(PlatformKind to, Value inputVal) {
        Variable result = newVariable(to);
        emitMove(result, inputVal);
        return result;
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, DeoptimizingNode deopting) {
        append(new ReturnOp(Value.ILLEGAL));
    }

    @Override
    public void emitMembar(int barriers) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitMembar()");
    }

    @Override
    protected void emitForeignCall(ForeignCallLinkage callTarget, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitForeignCall()");
    }

    @Override
    public void emitBitCount(Variable result, Value value) {
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new PTXBitManipulationOp(IPOPCNT, result, value));
        } else {
            append(new PTXBitManipulationOp(LPOPCNT, result, value));
        }
    }

    @Override
    public void emitBitScanForward(Variable result, Value value) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitBitScanForward()");
    }

    @Override
    public void emitBitScanReverse(Variable result, Value value) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitBitScanReverse()");
    }

    @Override
    public Value emitMathAbs(Value input) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitMathAbs()");
    }

    @Override
    public Value emitMathSqrt(Value input) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitMathSqrt()");
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitMathLog()");
    }

    @Override
    public Value emitMathCos(Value input) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitMathCos()");
    }

    @Override
    public Value emitMathSin(Value input) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitMathSin()");
    }

    @Override
    public Value emitMathTan(Value input) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitMathTan()");
    }

    @Override
    public void emitByteSwap(Variable result, Value input) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitByteSwap()");
    }

    @Override
    public void emitArrayEquals(Kind kind, Variable result, Value array1, Value array2, Value length) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented();
    }

    @Override
    public void emitReturn(Value input) {
        if (input != null) {
            AllocatableValue operand = resultOperandFor(input.getKind());
            // Load the global memory address from return parameter
            Variable loadVar = emitLoadReturnAddress(operand.getKind(), operand, null);
            // Store input in global memory whose location is loadVar
            emitStoreReturnValue(operand.getKind(), loadVar, input, null);
        }
        emitReturnNoVal();
    }

    void emitReturnNoVal() {
        append(new ReturnNoValOp());
    }

    @Override
    protected void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        boolean needsTemp = key.getKind() == Kind.Object;
        append(new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, needsTemp ? newVariable(key.getKind()) : Value.ILLEGAL, nextPredRegNum++));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        // Making a copy of the switch value is necessary because jump table destroys the input
        // value
        Variable tmp = emitMove(key);
        append(new TableSwitchOp(lowKey, defaultTarget, targets, tmp, newVariable(target().wordKind), nextPredRegNum++));
    }

    @Override
    public void emitUnwind(Value operand) {
        throw GraalInternalError.unimplemented("PTXLIRGenerator.emitUnwind()");
    }

    public Variable emitLoadParam(Kind kind, Value address, DeoptimizingNode deopting) {

        PTXAddressValue loadAddress = asAddress(address);
        Variable result = newVariable(kind);
        append(new LoadParamOp(kind, result, loadAddress, deopting != null ? state(deopting) : null));

        return result;
    }

    public Variable emitLoadReturnAddress(Kind kind, Value address, DeoptimizingNode deopting) {
        PTXAddressValue loadAddress = asAddress(address);
        Variable result;
        switch (kind) {
            case Float:
                result = newVariable(Kind.Int);
                break;
            case Double:
                result = newVariable(Kind.Long);
                break;
            default:
                result = newVariable(kind);
        }
        append(new LoadReturnAddrOp(kind, result, loadAddress, deopting != null ? state(deopting) : null));

        return result;
    }

    public void emitStoreReturnValue(Kind kind, Value address, Value inputVal, DeoptimizingNode deopting) {
        PTXAddressValue storeAddress = asAddress(address);
        Variable input = load(inputVal);
        append(new StoreReturnValOp(kind, storeAddress, input, deopting != null ? state(deopting) : null));
    }

    @Override
    public AllocatableValue resultOperandFor(Kind kind) {
        if (kind == Kind.Void) {
            return ILLEGAL;
        }
        return (new Variable(kind, 0));
    }

    public Value emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        throw GraalInternalError.unimplemented();
    }

    public Value emitAtomicReadAndAdd(Value address, Value delta) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented();
    }

    public Value emitAtomicReadAndWrite(Value address, Value newValue) {
        // TODO Auto-generated method stub
        throw GraalInternalError.unimplemented();
    }
}
