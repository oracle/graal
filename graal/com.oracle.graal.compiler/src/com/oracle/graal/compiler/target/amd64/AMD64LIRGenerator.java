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

package com.oracle.graal.compiler.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;
import static com.oracle.graal.lir.amd64.AMD64Arithmetic.*;
import static com.oracle.graal.lir.amd64.AMD64Compare.*;

import java.util.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ci.CiTargetMethod.Mark;
import com.oracle.max.cri.ri.*;
import com.oracle.max.cri.xir.CiXirAssembler.XirMark;
import com.oracle.max.cri.xir.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.compiler.util.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.ValueUtil;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.DivOp;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Op1Reg;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Op1Stack;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Op2Reg;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.Op2Stack;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.ShiftOp;
import com.oracle.graal.lir.amd64.AMD64Call.DirectCallOp;
import com.oracle.graal.lir.amd64.AMD64Call.IndirectCallOp;
import com.oracle.graal.lir.amd64.AMD64Compare.CompareOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.CondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.ReturnOp;
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

/**
 * This class implements the X86-specific portion of the LIR generator.
 */
public abstract class AMD64LIRGenerator extends LIRGenerator {

    private static final CiRegisterValue RAX_I = AMD64.rax.asValue(RiKind.Int);
    private static final CiRegisterValue RAX_L = AMD64.rax.asValue(RiKind.Long);
    private static final CiRegisterValue RDX_I = AMD64.rdx.asValue(RiKind.Int);
    private static final CiRegisterValue RDX_L = AMD64.rdx.asValue(RiKind.Long);
    private static final CiRegisterValue RCX_I = AMD64.rcx.asValue(RiKind.Int);

    public static class AMD64SpillMoveFactory implements LIR.SpillMoveFactory {
        @Override
        public LIRInstruction createMove(CiValue result, CiValue input) {
            return new SpillMoveOp(result, input);
        }

        @Override
        public LIRInstruction createExchange(CiValue input1, CiValue input2) {
            // TODO (cwimmer) implement XCHG operation for LIR
            return null;
        }
    }

    public AMD64LIRGenerator(Graph graph, RiRuntime runtime, CiTarget target, FrameMap frameMap, RiResolvedMethod method, LIR lir, RiXirGenerator xir, CiAssumptions assumptions) {
        super(graph, runtime, target, frameMap, method, lir, xir, assumptions);
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
    public boolean canStoreConstant(RiConstant c) {
        // there is no immediate move of 64-bit constants on Intel
        switch (c.kind) {
            case Long:   return Util.isInt(c.asLong());
            case Double: return false;
            case Object: return c.isNull();
            default:     return true;
        }
    }

    @Override
    public boolean canInlineConstant(RiConstant c) {
        switch (c.kind) {
            case Long:   return NumUtil.isInt(c.asLong());
            case Object: return c.isNull();
            default:     return true;
        }
    }

    @Override
    public CiAddress makeAddress(LocationNode location, ValueNode object) {
        CiValue base = operand(object);
        CiValue index = CiValue.IllegalValue;
        int scale = 1;
        long displacement = location.displacement();

        if (isConstant(base)) {
            if (!asConstant(base).isNull()) {
                displacement += asConstant(base).asLong();
            }
            base = CiValue.IllegalValue;
        }

        if (location instanceof IndexedLocationNode) {
            IndexedLocationNode indexedLoc = (IndexedLocationNode) location;

            index = operand(indexedLoc.index());
            if (indexedLoc.indexScalingEnabled()) {
                scale = target().sizeInBytes(location.getValueKind());
            }
            if (isConstant(index)) {
                long newDisplacement = displacement + asConstant(index).asLong() * scale;
                // only use the constant index if the resulting displacement fits into a 32 bit offset
                if (NumUtil.isInt(newDisplacement)) {
                    displacement = newDisplacement;
                    index = CiValue.IllegalValue;
                } else {
                    // create a temporary variable for the index, the pointer load cannot handle a constant index
                    CiValue newIndex = newVariable(RiKind.Long);
                    emitMove(index, newIndex);
                    index = newIndex;
                }
            }
        }

        return new CiAddress(location.getValueKind(), base, index, CiAddress.Scale.fromInt(scale), (int) displacement);
    }

    @Override
    public Variable emitMove(CiValue input) {
        Variable result = newVariable(input.kind);
        emitMove(input, result);
        return result;
    }

    @Override
    public void emitMove(CiValue src, CiValue dst) {
        if (isRegister(src) || isStackSlot(dst)) {
            append(new MoveFromRegOp(dst, src));
        } else {
            append(new MoveToRegOp(dst, src));
        }
    }

    @Override
    public Variable emitLoad(CiValue loadAddress, boolean canTrap) {
        Variable result = newVariable(loadAddress.kind);
        append(new LoadOp(result, loadAddress, canTrap ? state() : null));
        return result;
    }

    @Override
    public void emitStore(CiValue storeAddress, CiValue inputVal, boolean canTrap) {
        CiValue input = loadForStore(inputVal, storeAddress.kind);
        append(new StoreOp(storeAddress, input, canTrap ? state() : null));
    }

    @Override
    public Variable emitLea(CiValue address) {
        Variable result = newVariable(target().wordKind);
        append(new LeaOp(result, address));
        return result;
    }

    @Override
    public void emitLabel(Label label, boolean align) {
        append(new LabelOp(label, align));
    }

    @Override
    public void emitJump(LabelRef label, LIRDebugInfo info) {
        append(new JumpOp(label, info));
    }

    @Override
    public void emitBranch(CiValue left, CiValue right, Condition cond, boolean unorderedIsTrue, LabelRef label, LIRDebugInfo info) {
        boolean mirrored = emitCompare(left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        switch (left.kind.stackKind()) {
            case Int:
            case Long:
            case Object: append(new BranchOp(finalCondition, label, info)); break;
            case Float:
            case Double: append(new FloatBranchOp(finalCondition, unorderedIsTrue, label, info)); break;
            default: throw GraalInternalError.shouldNotReachHere("" + left.kind);
        }
    }

    @Override
    public Variable emitCMove(CiValue left, CiValue right, Condition cond, boolean unorderedIsTrue, CiValue trueValue, CiValue falseValue) {
        boolean mirrored = emitCompare(left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;

        Variable result = newVariable(trueValue.kind);
        switch (left.kind.stackKind()) {
            case Int:
            case Long:
            case Object: append(new CondMoveOp(result, finalCondition, load(trueValue), loadNonConst(falseValue))); break;
            case Float:
            case Double: append(new FloatCondMoveOp(result, finalCondition, unorderedIsTrue, load(trueValue), load(falseValue))); break;

        }
        return result;
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if it did so.
     *
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompare(CiValue a, CiValue b) {
        Variable left;
        CiValue right;
        boolean mirrored;
        if (ValueUtil.isVariable(b)) {
            left = load(b);
            right = loadNonConst(a);
            mirrored = true;
        } else {
            left = load(a);
            right = loadNonConst(b);
            mirrored = false;
        }
        switch (left.kind.stackKind()) {
            case Jsr:
            case Int: append(new CompareOp(ICMP, left, right)); break;
            case Long: append(new CompareOp(LCMP, left, right)); break;
            case Object: append(new CompareOp(ACMP, left, right)); break;
            case Float: append(new CompareOp(FCMP, left, right)); break;
            case Double: append(new CompareOp(DCMP, left, right)); break;
            default: throw GraalInternalError.shouldNotReachHere();
        }
        return mirrored;
    }

    @Override
    public Variable emitNegate(CiValue input) {
        Variable result = newVariable(input.kind);
        switch (input.kind) {
            case Int:    append(new Op1Stack(INEG, result, input)); break;
            case Long:   append(new Op1Stack(LNEG, result, input)); break;
            case Float:  append(new Op2Reg(FXOR, result, input, RiConstant.forFloat(Float.intBitsToFloat(0x80000000)))); break;
            case Double: append(new Op2Reg(DXOR, result, input, RiConstant.forDouble(Double.longBitsToDouble(0x8000000000000000L)))); break;
            default: throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitAdd(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(new Op2Stack(IADD, result, a, loadNonConst(b))); break;
            case Long:   append(new Op2Stack(LADD, result, a, loadNonConst(b))); break;
            case Float:  append(new Op2Stack(FADD, result, a, loadNonConst(b))); break;
            case Double: append(new Op2Stack(DADD, result, a, loadNonConst(b))); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitSub(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(new Op2Stack(ISUB, result, a, loadNonConst(b))); break;
            case Long:   append(new Op2Stack(LSUB, result, a, loadNonConst(b))); break;
            case Float:  append(new Op2Stack(FSUB, result, a, loadNonConst(b))); break;
            case Double: append(new Op2Stack(DSUB, result, a, loadNonConst(b))); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitMul(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(new Op2Reg(IMUL, result, a, loadNonConst(b))); break;
            case Long:   append(new Op2Reg(LMUL, result, a, loadNonConst(b))); break;
            case Float:  append(new Op2Stack(FMUL, result, a, loadNonConst(b))); break;
            case Double: append(new Op2Stack(DMUL, result, a, loadNonConst(b))); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitDiv(CiValue a, CiValue b) {
        switch(a.kind) {
            case Int:
                emitMove(a, RAX_I);
                append(new DivOp(IDIV, RAX_I, RAX_I, load(b), state()));
                return emitMove(RAX_I);
            case Long:
                emitMove(a, RAX_L);
                append(new DivOp(LDIV, RAX_L, RAX_L, load(b), state()));
                return emitMove(RAX_L);
            case Float: {
                Variable result = newVariable(a.kind);
                append(new Op2Stack(FDIV, result, a, loadNonConst(b)));
                return result;
            }
            case Double: {
                Variable result = newVariable(a.kind);
                append(new Op2Stack(DDIV, result, a, loadNonConst(b)));
                return result;
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public CiValue emitRem(CiValue a, CiValue b) {
        switch(a.kind) {
            case Int:
                emitMove(a, RAX_I);
                append(new DivOp(IREM, RDX_I, RAX_I, load(b), state()));
                return emitMove(RDX_I);
            case Long:
                emitMove(a, RAX_L);
                append(new DivOp(LREM, RDX_L, RAX_L, load(b), state()));
                return emitMove(RDX_L);
            case Float:
                return emitCall(CiRuntimeCall.ArithmeticFrem, false, a, b);
            case Double:
                return emitCall(CiRuntimeCall.ArithmeticDrem, false, a, b);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitUDiv(CiValue a, CiValue b) {
        switch(a.kind) {
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
    public Variable emitURem(CiValue a, CiValue b) {
        switch(a.kind) {
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
    public Variable emitAnd(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(new Op2Stack(IAND, result, a, loadNonConst(b))); break;
            case Long:   append(new Op2Stack(LAND, result, a, loadNonConst(b))); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitOr(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(new Op2Stack(IOR, result, a, loadNonConst(b))); break;
            case Long:   append(new Op2Stack(LOR, result, a, loadNonConst(b))); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitXor(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(new Op2Stack(IXOR, result, a, loadNonConst(b))); break;
            case Long:   append(new Op2Stack(LXOR, result, a, loadNonConst(b))); break;
            default:     throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }


    @Override
    public Variable emitShl(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch (a.kind) {
            case Int:    append(new ShiftOp(ISHL, result, a, loadShiftCount(b))); break;
            case Long:   append(new ShiftOp(LSHL, result, a, loadShiftCount(b))); break;
            default: GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitShr(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch (a.kind) {
            case Int:    append(new ShiftOp(ISHR, result, a, loadShiftCount(b))); break;
            case Long:   append(new ShiftOp(LSHR, result, a, loadShiftCount(b))); break;
            default: GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitUShr(CiValue a, CiValue b) {
        Variable result = newVariable(a.kind);
        switch (a.kind) {
            case Int:    append(new ShiftOp(IUSHR, result, a, loadShiftCount(b))); break;
            case Long:   append(new ShiftOp(LUSHR, result, a, loadShiftCount(b))); break;
            default: GraalInternalError.shouldNotReachHere();
        }
        return result;
    }

    private CiValue loadShiftCount(CiValue value) {
        if (isConstant(value)) {
            return value;
        }
        // Non-constant shift count must be in RCX
        emitMove(value, RCX_I);
        return RCX_I;
    }


    @Override
    public Variable emitConvert(ConvertNode.Op opcode, CiValue inputVal) {
        Variable input = load(inputVal);
        Variable result = newVariable(opcode.to);
        switch (opcode) {
            case I2L: append(new Op1Reg(I2L, result, input)); break;
            case L2I: append(new Op1Stack(L2I, result, input)); break;
            case I2B: append(new Op1Stack(I2B, result, input)); break;
            case I2C: append(new Op1Stack(I2C, result, input)); break;
            case I2S: append(new Op1Stack(I2S, result, input)); break;
            case F2D: append(new Op1Reg(F2D, result, input)); break;
            case D2F: append(new Op1Reg(D2F, result, input)); break;
            case I2F: append(new Op1Reg(I2F, result, input)); break;
            case I2D: append(new Op1Reg(I2D, result, input)); break;
            case F2I: append(new Op1Reg(F2I, result, input)); break;
            case D2I: append(new Op1Reg(D2I, result, input)); break;
            case L2F: append(new Op1Reg(L2F, result, input)); break;
            case L2D: append(new Op1Reg(L2D, result, input)); break;
            case F2L: append(new Op1Reg(F2L, result, input)); break;
            case D2L: append(new Op1Reg(D2L, result, input)); break;
            case MOV_I2F: append(new Op1Reg(MOV_I2F, result, input)); break;
            case MOV_L2D: append(new Op1Reg(MOV_L2D, result, input)); break;
            case MOV_F2I: append(new Op1Reg(MOV_F2I, result, input)); break;
            case MOV_D2L: append(new Op1Reg(MOV_D2L, result, input)); break;
            default: throw GraalInternalError.shouldNotReachHere();
        }
        return result;
    }


    @Override
    public void emitDeoptimizeOnOverflow(RiDeoptAction action, RiDeoptReason reason, Object deoptInfo) {
        LIRDebugInfo info = state();
        LabelRef stubEntry = createDeoptStub(action, reason, info, deoptInfo);
        append(new BranchOp(ConditionFlag.overflow, stubEntry, info));
    }


    @Override
    public void emitDeoptimize(RiDeoptAction action, RiDeoptReason reason, Object deoptInfo, long leafGraphId) {
        LIRDebugInfo info = state(leafGraphId);
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
    protected void emitCall(Object targetMethod, CiValue result, List<CiValue> arguments, CiValue targetAddress, LIRDebugInfo info, Map<XirMark, Mark> marks) {
        if (isConstant(targetAddress)) {
            assert asConstant(targetAddress).isDefaultValue() : "destination address should be zero";
            append(new DirectCallOp(targetMethod, result, arguments.toArray(new CiValue[arguments.size()]), info, marks));
        } else {
            append(new IndirectCallOp(targetMethod, result, arguments.toArray(new CiValue[arguments.size()]), targetAddress, info, marks));
        }
    }

    @Override
    protected void emitReturn(CiValue input) {
        append(new ReturnOp(input));
    }

    @Override
    protected void emitXir(XirSnippet snippet, CiValue[] operands, CiValue outputOperand, CiValue[] inputs, CiValue[] temps, int[] inputOperandIndices, int[] tempOperandIndices, int outputOperandIndex,
                    LIRDebugInfo info, LIRDebugInfo infoAfter, LabelRef trueSuccessor, LabelRef falseSuccessor) {
        append(new AMD64XirOp(snippet, operands, outputOperand, inputs, temps, inputOperandIndices, tempOperandIndices, outputOperandIndex, info, infoAfter, trueSuccessor, falseSuccessor));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, CiValue index) {
        // Making a copy of the switch value is necessary because jump table destroys the input value
        Variable tmp = emitMove(index);
        append(new TableSwitchOp(lowKey, defaultTarget, targets, tmp, newVariable(target.wordKind)));
    }

    @Override
    protected LabelRef createDeoptStub(RiDeoptAction action, RiDeoptReason reason, LIRDebugInfo info, Object deoptInfo) {
        assert info.topFrame.bci >= 0 : "invalid bci for deopt framestate";
        AMD64DeoptimizationStub stub = new AMD64DeoptimizationStub(action, reason, info, deoptInfo);
        lir.stubs.add(stub);
        return LabelRef.forLabel(stub.label);
    }

    @Override
    protected void emitNullCheckGuard(ValueNode object, long leafGraphId) {
        Variable value = load(operand(object));
        LIRDebugInfo info = state(leafGraphId);
        append(new NullCheckOp(value, info));
    }

    @Override
    public void visitCompareAndSwap(CompareAndSwapNode node) {
        RiKind kind = node.newValue().kind();
        assert kind == node.expected().kind();

        CiValue expected = loadNonConst(operand(node.expected()));
        Variable newValue = load(operand(node.newValue()));

        CiAddress address;
        int displacement = node.displacement();
        CiValue index = operand(node.offset());
        if (isConstant(index) && NumUtil.isInt(asConstant(index).asLong())) {
            displacement += (int) asConstant(index).asLong();
            address = new CiAddress(kind, load(operand(node.object())), displacement);
        } else {
            address = new CiAddress(kind, load(operand(node.object())), load(index), CiAddress.Scale.Times1, displacement);
        }

        CiRegisterValue rax = AMD64.rax.asValue(kind);
        emitMove(expected, rax);
        append(new CompareAndSwapOp(rax, address, rax, newValue));

        Variable result = newVariable(node.kind());
        append(new CondMoveOp(result, Condition.EQ, load(RiConstant.TRUE), RiConstant.FALSE));
        setResult(node, result);
    }
}
