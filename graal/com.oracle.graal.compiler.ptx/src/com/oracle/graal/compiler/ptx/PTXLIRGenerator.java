/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.lir.ptx.PTXArithmetic.*;
import static com.oracle.graal.lir.ptx.PTXBitManipulationOp.IntrinsicOpcode.*;
import static com.oracle.graal.lir.ptx.PTXCompare.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.ptx.*;
import com.oracle.graal.lir.ptx.PTXArithmetic.Op1Stack;
import com.oracle.graal.lir.ptx.PTXArithmetic.Op2Reg;
import com.oracle.graal.lir.ptx.PTXArithmetic.Op2Stack;
import com.oracle.graal.lir.ptx.PTXArithmetic.ShiftOp;
import com.oracle.graal.lir.ptx.PTXCompare.CompareOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.BranchOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.ReturnOp;
import com.oracle.graal.lir.ptx.PTXMove.LoadOp;
import com.oracle.graal.lir.ptx.PTXMove.MoveFromRegOp;
import com.oracle.graal.lir.ptx.PTXMove.MoveToRegOp;
import com.oracle.graal.lir.ptx.PTXMove.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;

/**
 * This class implements the PTX specific portion of the LIR generator.
 */
public class PTXLIRGenerator extends LIRGenerator {

    public static class PTXSpillMoveFactory implements LIR.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(Value result, Value input) {
            throw new InternalError("NYI");
        }
    }

    public PTXLIRGenerator(StructuredGraph graph, CodeCacheProvider runtime, TargetDescription target, FrameMap frameMap, ResolvedJavaMethod method, LIR lir) {
        super(graph, runtime, target, frameMap, method, lir);
        lir.spillMoveFactory = new PTXSpillMoveFactory();
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
        // Operand b must be in the .reg state space.
        return false;
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
    public Variable emitMove(Value input) {
        Variable result = newVariable(input.getKind());
        emitMove(result, input);
        return result;
    }

    @Override
    public void emitMove(Value dst, Value src) {
        if (isRegister(src) || isStackSlot(dst)) {
            append(new MoveFromRegOp(dst, src));
        } else {
            append(new MoveToRegOp(dst, src));
        }
    }

    private PTXAddressValue prepareAddress(Kind kind, Value base, int displacement, Value index, int scale) {
        AllocatableValue baseRegister;
        long finalDisp = displacement;
        if (isConstant(base)) {
            if (asConstant(base).isNull()) {
                baseRegister = AllocatableValue.UNUSED;
            } else if (asConstant(base).getKind() != Kind.Object) {
                finalDisp += asConstant(base).asLong();
                baseRegister = AllocatableValue.UNUSED;
            } else {
                baseRegister = load(base);
            }
        } else if (base == Value.ILLEGAL) {
            baseRegister = AllocatableValue.UNUSED;
        } else {
            baseRegister = asAllocatable(base);
        }

        if (index != Value.ILLEGAL) {
            if (isConstant(index)) {
                finalDisp += asConstant(index).asLong() * scale;
            } else {
                Value indexRegister;
                if (scale != 1) {
                    indexRegister = emitMul(index, Constant.forInt(scale));
                } else {
                    indexRegister = index;
                }

                if (baseRegister == AllocatableValue.UNUSED) {
                    baseRegister = asAllocatable(indexRegister);
                } else {
                    baseRegister = emitAdd(baseRegister, indexRegister);
                }
            }
        }

        return new PTXAddressValue(kind, baseRegister, finalDisp);
    }

    @Override
    public Variable emitLoad(Kind kind, Value base, int displacement, Value index, int scale, boolean canTrap) {
        PTXAddressValue loadAddress = prepareAddress(kind, base, displacement, index, scale);
        Variable result = newVariable(loadAddress.getKind());
        append(new LoadOp(result, loadAddress, canTrap ? state() : null));
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value base, int displacement, Value index, int scale, Value inputVal, boolean canTrap) {
        PTXAddressValue storeAddress = prepareAddress(kind, base, displacement, index, scale);
        Variable input = load(inputVal);
        append(new StoreOp(storeAddress, input, canTrap ? state() : null));
    }

    @Override
    public Variable emitLea(Value base, int displacement, Value index, int scale) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitLea(StackSlot address) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitJump(LabelRef label) {
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef label) {
        switch (left.getKind().getStackKind()) {
            case Int:
                append(new CompareOp(ICMP, cond, left, right));
                append(new BranchOp(cond, label));
                break;
            case Object:
                append(new CompareOp(ACMP, cond, left, right));
                append(new BranchOp(cond, label));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef label, boolean negated) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, boolean negated, LabelRef label) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitConditionalMove(Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitNegate(Value input) {
        Variable result = newVariable(input.getKind());
        switch (input.getKind()) {
            case Int:
                append(new Op1Stack(INEG, result, input));
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
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        // No peephole optimizations for now
        return false;
    }

    @Override
    public Value emitDiv(Value a, Value b) {
        throw new InternalError("NYI");
    }

    @Override
    public Value emitRem(Value a, Value b) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitUDiv(Value a, Value b) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitURem(Value a, Value b) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new Op2Stack(IAND, result, a, loadNonConst(b)));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        throw new InternalError("NYI");
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        Variable result = newVariable(a.getKind());
        switch (a.getKind()) {
            case Int:
                append(new ShiftOp(IUSHR, result, a, b));
                break;
            default:
                GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitConvert(ConvertNode.Op opcode, Value inputVal) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitDeoptimize(DeoptimizationAction action, DeoptimizationReason reason) {
        append(new ReturnOp(Value.ILLEGAL));
    }

    @Override
    public void emitMembar(int barriers) {
        throw new InternalError("NYI");
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
    protected void emitCall(RuntimeCallTarget callTarget, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        throw new InternalError("NYI");
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
        throw new InternalError("NYI");
    }

    @Override
    public void emitBitScanReverse(Variable result, Value value) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitMathAbs(Variable result, Variable input) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitMathSqrt(Variable result, Variable input) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitMathLog(Variable result, Variable input, boolean base10) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitMathCos(Variable result, Variable input) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitMathSin(Variable result, Variable input) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitMathTan(Variable result, Variable input) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitByteSwap(Variable result, Value input) {
        throw new InternalError("NYI");
    }

    @Override
    protected void emitReturn(Value input) {
        append(new ReturnOp(input));
    }

    @Override
    protected void emitSequentialSwitch(Constant[] keyConstants, LabelRef[] keyTargets, LabelRef defaultTarget, Value key) {
        throw new InternalError("NYI");
    }

    @Override
    protected void emitSwitchRanges(int[] lowKeys, int[] highKeys, LabelRef[] targets, LabelRef defaultTarget, Value key) {
        throw new InternalError("NYI");
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        throw new InternalError("NYI");
    }

    @Override
    public void visitCompareAndSwap(CompareAndSwapNode node) {
        throw new InternalError("NYI");
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        throw new InternalError("NYI");
    }

    @Override
    public void visitLoadException(LoadExceptionObjectNode i) {
        throw new InternalError("NYI");
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitUnwind(Value operand) {
        // TODO Auto-generated method stub

    }

    @Override
    public void emitNullCheck(ValueNode v) {
        throw new InternalError("NYI");
    }
}
