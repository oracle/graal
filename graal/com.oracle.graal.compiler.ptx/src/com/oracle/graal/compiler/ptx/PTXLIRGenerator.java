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
import static com.oracle.graal.lir.ptx.PTXCompare.*;
import static com.oracle.graal.lir.ptx.PTXBitManipulationOp.IntrinsicOpcode.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.ptx.PTXBitManipulationOp;
import com.oracle.graal.lir.ptx.PTXCompare.CompareOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.BranchOp;
import com.oracle.graal.lir.ptx.PTXControlFlow.ReturnOp;
import com.oracle.graal.lir.ptx.PTXMove.LoadOp;
import com.oracle.graal.lir.ptx.PTXMove.MoveFromRegOp;
import com.oracle.graal.lir.ptx.PTXMove.MoveToRegOp;
import com.oracle.graal.lir.ptx.PTXMove.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.Condition;
import com.oracle.graal.nodes.calc.ConvertNode;
import com.oracle.graal.nodes.extended.IndexedLocationNode;
import com.oracle.graal.nodes.extended.LocationNode;
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
            scale = indexedLoc.indexScaling();
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
        throw new InternalError("NYI");
    }

    @Override
    public void emitJump(LabelRef label, LIRFrameState info) {
        append(new JumpOp(label, info));
    }

    @Override
    public void emitCompareBranch(Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef label, LIRFrameState info) {
        switch (left.getKind().getStackKind()) {
            case Int:
                append(new CompareOp(ICMP, cond, left, right));
                append(new BranchOp(cond, label, info));
                break;
            case Object:
                append(new CompareOp(ACMP, cond, left, right));
                append(new BranchOp(cond, label, info));
                break;
            default:
                throw GraalInternalError.shouldNotReachHere("" + left.getKind());
        }
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, boolean negated, LabelRef label, LIRFrameState info) {
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
    public void emitDeoptimizeOnOverflow(DeoptimizationAction action, DeoptimizationReason reason, Object deoptInfo) {
        throw new InternalError("NYI");
    }

    @Override
    public void emitDeoptimize(DeoptimizationAction action, DeoptimizationReason reason, Object deoptInfo) {
        throw new InternalError("NYI");
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
    protected void emitCall(RuntimeCallTarget callTarget, Value result, Value[] arguments, Value[] temps, Value targetAddress, LIRFrameState info) {
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
    protected LabelRef createDeoptStub(DeoptimizationAction action, DeoptimizationReason reason, LIRFrameState info, Object deoptInfo) {
        assert info.topFrame.getBCI() >= 0 : "invalid bci for deopt framestate";
        PTXDeoptimizationStub stub = new PTXDeoptimizationStub(action, reason, info, deoptInfo);
        lir.stubs.add(stub);
        return LabelRef.forLabel(stub.label);
    }

    @Override
    protected void emitNullCheckGuard(ValueNode object) {
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
    public void visitExceptionObject(ExceptionObjectNode i) {
        throw new InternalError("NYI");
    }

    @Override
    public void visitSafepointNode(SafepointNode i) {
        throw new InternalError("NYI");
    }
}
