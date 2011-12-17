/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.max.graal.compiler.target.amd64;

import static com.oracle.max.graal.compiler.target.amd64.AMD64ArithmeticOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64CompareOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64CompareToIntOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64ConvertFIOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64ConvertFLOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64ConvertOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64DivOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64LogicFloatOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64MulOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64Op1Opcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64ShiftOpcode.*;
import static com.oracle.max.graal.compiler.target.amd64.AMD64StandardOpcode.*;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.FrameMap.StackBlock;
import com.oracle.max.graal.compiler.stub.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.nodes.DeoptimizeNode.DeoptAction;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.java.*;
import com.sun.cri.ci.*;
import com.sun.cri.xir.*;

/**
 * This class implements the X86-specific portion of the LIR generator.
 */
public class AMD64LIRGenerator extends LIRGenerator {

    private static final CiRegisterValue RAX_I = AMD64.rax.asValue(CiKind.Int);
    private static final CiRegisterValue RAX_L = AMD64.rax.asValue(CiKind.Long);
    private static final CiRegisterValue RAX_O = AMD64.rax.asValue(CiKind.Object);
    private static final CiRegisterValue RDX_I = AMD64.rdx.asValue(CiKind.Int);
    private static final CiRegisterValue RDX_L = AMD64.rdx.asValue(CiKind.Long);
    private static final CiRegisterValue RCX_I = AMD64.rcx.asValue(CiKind.Int);

    static {
        StandardOpcode.MOVE = AMD64StandardOpcode.MOVE;
        StandardOpcode.NULL_CHECK = AMD64StandardOpcode.NULL_CHECK;
        StandardOpcode.DIRECT_CALL = AMD64CallOpcode.DIRECT_CALL;
        StandardOpcode.INDIRECT_CALL = AMD64CallOpcode.INDIRECT_CALL;
        StandardOpcode.LABEL = AMD64StandardOpcode.LABEL;
        StandardOpcode.JUMP = AMD64StandardOpcode.JUMP;
        StandardOpcode.RETURN = AMD64StandardOpcode.RETURN;
        StandardOpcode.XIR = AMD64XirOpcode.XIR;
    }

    public AMD64LIRGenerator(GraalCompilation compilation, RiXirGenerator xir) {
        super(compilation, xir);
        lir.methodEndMarker = new AMD64MethodEndStub();
    }

    @Override
    protected void emitNode(ValueNode node) {
        if (node instanceof AMD64LIRLowerable) {
            ((AMD64LIRLowerable) node).generateAmd64(this);
        } else {
            super.emitNode(node);
        }
    }

    @Override
    public boolean canStoreConstant(CiConstant c) {
        // there is no immediate move of 64-bit constants on Intel
        switch (c.kind) {
            case Long:   return Util.isInt(c.asLong());
            case Double: return false;
            case Object: return c.isNull();
            default:     return true;
        }
    }

    @Override
    public boolean canInlineConstant(CiConstant c) {
        switch (c.kind) {
            case Long:   return NumUtil.isInt(c.asLong());
            case Object: return c.isNull();
            default:     return true;
        }
    }


    @Override
    public CiVariable emitMove(CiValue input) {
        CiVariable result = newVariable(input.kind);
        append(MOVE.create(result, input));
        return result;
    }

    @Override
    public void emitMove(CiValue src, CiValue dst) {
        append(MOVE.create(dst, src));
    }

    @Override
    public CiVariable emitLoad(CiAddress loadAddress, CiKind kind, boolean canTrap) {
        CiVariable result = newVariable(kind);
        append(LOAD.create(result, loadAddress.base, loadAddress.index, loadAddress.scale, loadAddress.displacement, kind, canTrap ? state() : null));
        return result;
    }

    @Override
    public void emitStore(CiAddress storeAddress, CiValue inputVal, CiKind kind, boolean canTrap) {
        CiValue input = loadForStore(inputVal, kind);
        append(STORE.create(storeAddress.base, storeAddress.index, storeAddress.scale, storeAddress.displacement, input, kind, canTrap ? state() : null));
    }

    @Override
    public CiVariable emitLea(CiAddress address) {
        CiVariable result = newVariable(target().wordKind);
        append(LEA.create(result, address.base, address.index, address.scale, address.displacement));
        return result;
    }

    @Override
    public CiVariable emitLea(StackBlock stackBlock) {
        CiVariable result = newVariable(target().wordKind);
        append(LEA_STACK_BLOCK.create(result, stackBlock));
        return result;
    }

    @Override
    public void emitLabel(Label label, boolean align) {
        append(LABEL.create(label, align));
    }

    @Override
    public void emitJump(LabelRef label, LIRDebugInfo info) {
        append(JUMP.create(label, info));
    }

    @Override
    public void emitBranch(CiValue left, CiValue right, Condition cond, boolean unorderedIsTrue, LabelRef label, LIRDebugInfo info) {
        emitCompare(left, right);
        switch (left.kind) {
            case Boolean:
            case Int:
            case Long:
            case Object: append(BRANCH.create(cond, label, info)); break;
            case Float:
            case Double: append(FLOAT_BRANCH.create(cond, unorderedIsTrue, label, info)); break;
            default: throw Util.shouldNotReachHere("" + left.kind);
        }
    }

    @Override
    public CiVariable emitCMove(CiValue left, CiValue right, Condition cond, boolean unorderedIsTrue, CiValue trueValue, CiValue falseValue) {
        emitCompare(left, right);

        CiVariable result = newVariable(trueValue.kind);
        switch (left.kind) {
            case Boolean:
            case Int:
            case Long:
            case Object: append(CMOVE.create(result, cond, load(trueValue), loadNonConst(falseValue))); break;
            case Float:
            case Double: append(FLOAT_CMOVE.create(result, cond, unorderedIsTrue, load(trueValue), load(falseValue))); break;

        }
        return result;
    }

    private void emitCompare(CiValue a, CiValue b) {
        CiVariable left = load(a);
        CiValue right = loadNonConst(b);
        switch (left.kind) {
            case Jsr:
            case Int: append(ICMP.create(left, right)); break;
            case Long: append(LCMP.create(left, right)); break;
            case Object: append(ACMP.create(left, right)); break;
            case Float: append(FCMP.create(left, right)); break;
            case Double: append(DCMP.create(left, right)); break;
            default: throw Util.shouldNotReachHere();
        }
    }

    @Override
    public CiVariable emitNegate(CiValue input) {
        CiVariable result = newVariable(input.kind);
        switch (input.kind) {
            case Int:    append(INEG.create(result, input)); break;
            case Long:   append(LNEG.create(result, input)); break;
            case Float:  append(FXOR.create(result, input, CiConstant.forFloat(Float.intBitsToFloat(0x80000000)))); break;
            case Double: append(DXOR.create(result, input, CiConstant.forDouble(Double.longBitsToDouble(0x8000000000000000L)))); break;
            default: throw Util.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public CiVariable emitAdd(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(IADD.create(result, a, loadNonConst(b))); break;
            case Long:   append(LADD.create(result, a, loadNonConst(b))); break;
            case Float:  append(FADD.create(result, a, loadNonConst(b))); break;
            case Double: append(DADD.create(result, a, loadNonConst(b))); break;
            default:     throw Util.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public CiVariable emitSub(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(ISUB.create(result, a, loadNonConst(b))); break;
            case Long:   append(LSUB.create(result, a, loadNonConst(b))); break;
            case Float:  append(FSUB.create(result, a, loadNonConst(b))); break;
            case Double: append(DSUB.create(result, a, loadNonConst(b))); break;
            default:     throw Util.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public CiVariable emitMul(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(IMUL.create(result, a, loadNonConst(b))); break;
            case Long:   append(LMUL.create(result, a, loadNonConst(b))); break;
            case Float:  append(FMUL.create(result, a, loadNonConst(b))); break;
            case Double: append(DMUL.create(result, a, loadNonConst(b))); break;
            default:     throw Util.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public CiVariable emitDiv(CiValue a, CiValue b) {
        switch(a.kind) {
            case Int:
                append(MOVE.create(RAX_I, a));
                append(IDIV.create(RAX_I, state(), RAX_I, load(b)));
                return emitMove(RAX_I);
            case Long:
                append(MOVE.create(RAX_L, a));
                append(LDIV.create(RAX_L, state(), RAX_L, load(b)));
                return emitMove(RAX_L);
            case Float: {
                CiVariable result = newVariable(a.kind);
                append(FDIV.create(result, a, loadNonConst(b)));
                return result;
            }
            case Double: {
                CiVariable result = newVariable(a.kind);
                append(DDIV.create(result, a, loadNonConst(b)));
                return result;
            }
            default:
                throw Util.shouldNotReachHere();
        }
    }

    @Override
    public CiVariable emitRem(CiValue a, CiValue b) {
        switch(a.kind) {
            case Int:
                append(MOVE.create(RAX_I, a));
                append(IREM.create(RDX_I, state(), RAX_I, load(b)));
                return emitMove(RDX_I);
            case Long:
                append(MOVE.create(RAX_L, a));
                append(LREM.create(RDX_L, state(), RAX_L, load(b)));
                return emitMove(RDX_L);
            case Float:
                return emitCallToRuntime(CiRuntimeCall.ArithmeticFrem, false, a, b);
            case Double:
                return emitCallToRuntime(CiRuntimeCall.ArithmeticDrem, false, a, b);
            default:
                throw Util.shouldNotReachHere();
        }
    }

    @Override
    public CiVariable emitUDiv(CiValue a, CiValue b) {
        switch(a.kind) {
            case Int:
                append(MOVE.create(RAX_I, load(a)));
                append(UIDIV.create(RAX_I, state(), RAX_I, load(b)));
                return emitMove(RAX_I);
            case Long:
                append(MOVE.create(RAX_L, load(a)));
                append(ULDIV.create(RAX_L, state(), RAX_L, load(b)));
                return emitMove(RAX_L);
            default:
                throw Util.shouldNotReachHere();
        }
    }

    @Override
    public CiVariable emitURem(CiValue a, CiValue b) {
        switch(a.kind) {
            case Int:
                append(MOVE.create(RAX_I, load(a)));
                append(UIREM.create(RDX_I, state(), RAX_I, load(b)));
                return emitMove(RDX_I);
            case Long:
                append(MOVE.create(RAX_L, load(a)));
                append(ULREM.create(RDX_L, state(), RAX_L, load(b)));
                return emitMove(RDX_L);
            default:
                throw Util.shouldNotReachHere();
        }
    }


    @Override
    public CiVariable emitAnd(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(IAND.create(result, a, loadNonConst(b))); break;
            case Long:   append(LAND.create(result, a, loadNonConst(b))); break;
            default:     throw Util.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public CiVariable emitOr(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(IOR.create(result, a, loadNonConst(b))); break;
            case Long:   append(LOR.create(result, a, loadNonConst(b))); break;
            default:     throw Util.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public CiVariable emitXor(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch(a.kind) {
            case Int:    append(IXOR.create(result, a, loadNonConst(b))); break;
            case Long:   append(LXOR.create(result, a, loadNonConst(b))); break;
            default:     throw Util.shouldNotReachHere();
        }
        return result;
    }


    @Override
    public CiVariable emitShl(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch (a.kind) {
            case Int:    append(ISHL.create(result, a, loadShiftCount(b))); break;
            case Long:   append(LSHL.create(result, a, loadShiftCount(b))); break;
            default: Util.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public CiVariable emitShr(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch (a.kind) {
            case Int:    append(ISHR.create(result, a, loadShiftCount(b))); break;
            case Long:   append(LSHR.create(result, a, loadShiftCount(b))); break;
            default: Util.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public CiVariable emitUShr(CiValue a, CiValue b) {
        CiVariable result = newVariable(a.kind);
        switch (a.kind) {
            case Int:    append(UISHR.create(result, a, loadShiftCount(b))); break;
            case Long:   append(ULSHR.create(result, a, loadShiftCount(b))); break;
            default: Util.shouldNotReachHere();
        }
        return result;
    }

    private CiValue loadShiftCount(CiValue value) {
        if (value.isConstant()) {
            return value;
        }
        // Non-constant shift count must be in RCX
        append(MOVE.create(RCX_I, value));
        return RCX_I;
    }


    @Override
    public CiVariable emitConvert(ConvertNode.Op opcode, CiValue inputVal) {
        CiVariable input = load(inputVal);
        CiVariable result = newVariable(opcode.to);
        switch (opcode) {
            case I2L: append(I2L.create(result, input)); break;
            case L2I: append(L2I.create(result, input)); break;
            case I2B: append(I2B.create(result, input)); break;
            case I2C: append(I2C.create(result, input)); break;
            case I2S: append(I2S.create(result, input)); break;
            case F2D: append(F2D.create(result, input)); break;
            case D2F: append(D2F.create(result, input)); break;
            case I2F: append(I2F.create(result, input)); break;
            case I2D: append(I2D.create(result, input)); break;
            case F2I: append(F2I.create(result, stubFor(CompilerStub.Id.f2i), input)); break;
            case D2I: append(D2I.create(result, stubFor(CompilerStub.Id.d2i), input)); break;
            case L2F: append(L2F.create(result, input)); break;
            case L2D: append(L2D.create(result, input)); break;
            case F2L: append(F2L.create(result, stubFor(CompilerStub.Id.f2l), input, newVariable(CiKind.Long))); break;
            case D2L: append(D2L.create(result, stubFor(CompilerStub.Id.d2l), input, newVariable(CiKind.Long))); break;
            case MOV_I2F: append(MOV_I2F.create(result, input)); break;
            case MOV_L2D: append(MOV_L2D.create(result, input)); break;
            case MOV_F2I: append(MOV_F2I.create(result, input)); break;
            case MOV_D2L: append(MOV_D2L.create(result, input)); break;
            default: throw Util.shouldNotReachHere();
        }
        return result;
    }


    @Override
    public void emitDeoptimizeOn(Condition cond, DeoptAction action, Object deoptInfo) {
        LIRDebugInfo info = state();
        LabelRef stubEntry = createDeoptStub(action, info, deoptInfo);
        if (cond != null) {
            append(BRANCH.create(cond, stubEntry, info));
        } else {
            append(JUMP.create(stubEntry, info));
        }
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = compilation.compiler.target.arch.requiredBarriers(barriers);
        if (compilation.compiler.target.isMP && necessaryBarriers != 0) {
            append(MEMBAR.create(necessaryBarriers));
        }
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, CiValue index) {
        // Making a copy of the switch value is necessary because jump table destroys the input value
        CiVariable tmp = emitMove(index);
        append(TABLE_SWITCH.create(lowKey, defaultTarget, targets, tmp, newVariable(compilation.compiler.target.wordKind)));
    }

    @Override
    protected LabelRef createDeoptStub(DeoptAction action, LIRDebugInfo info, Object deoptInfo) {
        assert info.topFrame.bci >= 0 : "invalid bci for deopt framestate";
        AMD64DeoptimizationStub stub = new AMD64DeoptimizationStub(action, info, deoptInfo);
        lir.deoptimizationStubs.add(stub);
        return LabelRef.forLabel(stub.label);
    }

    // TODO The CompareAndSwapNode in its current form needs to be lowered to several Nodes before code generation to separate three parts:
    // * The write barriers (and possibly read barriers) when accessing an object field
    // * The distinction of returning a boolean value (semantic similar to a BooleanNode to be used as a condition?) or the old value being read
    // * The actual compare-and-swap
    @Override
    public void visitCompareAndSwap(CompareAndSwapNode node) {
        CiKind kind = node.newValue().kind();
        assert kind == node.expected().kind();

        CiValue expected = loadNonConst(operand(node.expected()));
        CiVariable newValue = load(operand(node.newValue()));
        CiVariable addrBase = load(operand(node.object()));
        CiValue addrIndex = loadNonConst(operand(node.offset()));

        if (kind == CiKind.Object) {
            CiVariable loadedAddress = newVariable(compilation.compiler.target.wordKind);
            append(LEA.create(loadedAddress, addrBase, addrIndex, CiAddress.Scale.Times1, 0));
            addrBase = loadedAddress;
            addrIndex = CiVariable.IllegalValue;

            preGCWriteBarrier(addrBase, false, null);
        }

        CiRegisterValue rax = AMD64.rax.asValue(kind);
        append(MOVE.create(rax, expected));
        append(CAS.create(rax, addrBase, addrIndex, CiAddress.Scale.Times1, 0, rax, newValue));

        CiVariable result = newVariable(node.kind());
        if (node.directResult()) {
            append(MOVE.create(result, rax));
        } else {
            append(CMOVE.create(result, Condition.EQ, load(CiConstant.TRUE), CiConstant.FALSE));
        }
        setResult(node, result);

        if (kind == CiKind.Object) {
            postGCWriteBarrier(addrBase, newValue);
        }
    }

    // TODO The class NormalizeCompareNode should be lowered away in the front end, since the code generated is long and uses branches anyway.
    @Override
    public void visitNormalizeCompare(NormalizeCompareNode x) {
        emitCompare(operand(x.x()), operand(x.y()));
        CiVariable result = newVariable(x.kind());
        switch (x.x().kind()){
            case Float:
            case Double:
                if (x.isUnorderedLess) {
                    append(CMP2INT_UL.create(result));
                } else {
                    append(CMP2INT_UG.create(result));
                }
                break;
            case Long:
                append(CMP2INT.create(result));
                break;
            default:
                throw Util.shouldNotReachHere();
        }
        setResult(x, result);
    }
}
