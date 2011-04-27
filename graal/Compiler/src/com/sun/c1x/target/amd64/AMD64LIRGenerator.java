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

package com.sun.c1x.target.amd64;

import static com.sun.cri.bytecode.Bytecodes.UnsignedComparisons.*;

import com.sun.c1x.*;
import com.sun.c1x.alloc.OperandPool.VariableFlag;
import com.sun.c1x.gen.*;
import com.sun.c1x.globalstub.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.util.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * This class implements the X86-specific portion of the LIR generator.
 *
 * @author Thomas Wuerthinger
 * @author Ben L. Titzer
 */
public class AMD64LIRGenerator extends LIRGenerator {

    private static final CiRegisterValue RAX_I = AMD64.rax.asValue(CiKind.Int);
    private static final CiRegisterValue RAX_L = AMD64.rax.asValue(CiKind.Long);
    private static final CiRegisterValue RAX_W = AMD64.rax.asValue(CiKind.Word);
    private static final CiRegisterValue RDX_I = AMD64.rdx.asValue(CiKind.Int);
    private static final CiRegisterValue RDX_L = AMD64.rdx.asValue(CiKind.Long);

    private static final CiRegisterValue LDIV_TMP = RDX_L;


    /**
     * The register in which MUL puts the result for 64-bit multiplication.
     */
    private static final CiRegisterValue LMUL_OUT = RAX_L;

    private static final CiRegisterValue SHIFT_COUNT_IN = AMD64.rcx.asValue(CiKind.Int);

    protected static final CiValue ILLEGAL = CiValue.IllegalValue;

    public AMD64LIRGenerator(C1XCompilation compilation) {
        super(compilation);
    }

    @Override
    protected CiValue exceptionPcOpr() {
        return ILLEGAL;
    }

    @Override
    protected boolean canStoreAsConstant(Value v, CiKind kind) {
        if (kind == CiKind.Short || kind == CiKind.Char) {
            // there is no immediate move of word values in asemblerI486.?pp
            return false;
        }
        return v instanceof Constant;
    }

    @Override
    protected boolean canInlineAsConstant(Value v) {
        if (v.kind == CiKind.Long) {
            if (v.isConstant() && Util.isInt(v.asConstant().asLong())) {
                return true;
            }
            return false;
        }
        return v.kind != CiKind.Object || v.isNullConstant();
    }

    @Override
    protected CiAddress genAddress(CiValue base, CiValue index, int shift, int disp, CiKind kind) {
        assert base.isVariableOrRegister();
        if (index.isConstant()) {
            return new CiAddress(kind, base, (((CiConstant) index).asInt() << shift) + disp);
        } else {
            assert index.isVariableOrRegister();
            return new CiAddress(kind, base, (index), CiAddress.Scale.fromShift(shift), disp);
        }
    }

    @Override
    protected void genCmpMemInt(Condition condition, CiValue base, int disp, int c, LIRDebugInfo info) {
        lir.cmpMemInt(condition, base, disp, c, info);
    }

    @Override
    protected void genCmpRegMem(Condition condition, CiValue reg, CiValue base, int disp, CiKind kind, LIRDebugInfo info) {
        lir.cmpRegMem(condition, reg, new CiAddress(kind, base, disp), info);
    }

    @Override
    protected boolean strengthReduceMultiply(CiValue left, int c, CiValue result, CiValue tmp) {
        if (tmp.isLegal()) {
            if (CiUtil.isPowerOf2(c + 1)) {
                lir.move(left, tmp);
                lir.shiftLeft(left, CiUtil.log2(c + 1), left);
                lir.sub(left, tmp, result);
                return true;
            } else if (CiUtil.isPowerOf2(c - 1)) {
                lir.move(left, tmp);
                lir.shiftLeft(left, CiUtil.log2(c - 1), left);
                lir.add(left, tmp, result);
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitNegateOp(NegateOp x) {
        LIRItem value = new LIRItem(x.x(), this);
        value.setDestroysRegister();
        value.loadItem();
        CiVariable reg = newVariable(x.kind);
        GlobalStub globalStub = null;
        if (x.kind == CiKind.Float) {
            globalStub = stubFor(GlobalStub.Id.fneg);
        } else if (x.kind == CiKind.Double) {
            globalStub = stubFor(GlobalStub.Id.dneg);
        }
        lir.negate(value.result(), reg, globalStub);
        setResult(x, reg);
    }

    @Override
    public void visitSignificantBit(SignificantBitOp x) {
        LIRItem value = new LIRItem(x.value(), this);
        value.setDestroysRegister();
        value.loadItem();
        CiValue reg = createResultVariable(x);
        if (x.op == Bytecodes.LSB) {
            lir.lsb(value.result(), reg);
        } else {
            lir.msb(value.result(), reg);
        }
    }

    public boolean livesLonger(Value x, Value y) {
        BlockBegin bx = x.block();
        BlockBegin by = y.block();
        if (bx == null || by == null) {
            return false;
        }
        return bx.loopDepth() < by.loopDepth();
    }

    public void visitArithmeticOpFloat(ArithmeticOp x) {
        LIRItem left = new LIRItem(x.x(), this);
        LIRItem right = new LIRItem(x.y(), this);
        assert !left.isStack() || !right.isStack() : "can't both be memory operands";
        boolean mustLoadBoth = (x.opcode == Bytecodes.FREM || x.opcode == Bytecodes.DREM);

        // Both are in register, swap operands such that the short-living one is on the left side.
        if (x.isCommutative() && left.isRegisterOrVariable() && right.isRegisterOrVariable()) {
            if (livesLonger(x.x(), x.y())) {
                LIRItem tmp = left;
                left = right;
                right = tmp;
            }
        }

        if (left.isRegisterOrVariable() || x.x().isConstant() || mustLoadBoth) {
            left.loadItem();
        }

        if (mustLoadBoth) {
            // frem and drem destroy also right operand, so move it to a new register
            right.setDestroysRegister();
            right.loadItem();
        } else if (right.isRegisterOrVariable()) {
            right.loadItem();
        }

        CiVariable reg;

        if (x.opcode == Bytecodes.FREM) {
            reg = callRuntimeWithResult(CiRuntimeCall.ArithmeticFrem, null, left.result(), right.result());
        } else if (x.opcode == Bytecodes.DREM) {
            reg = callRuntimeWithResult(CiRuntimeCall.ArithmeticDrem, null, left.result(), right.result());
        } else {
            reg = newVariable(x.kind);
            arithmeticOpFpu(x.opcode, reg, left.result(), right.result(), ILLEGAL);
        }

        setResult(x, reg);
    }

    public void visitArithmeticOpLong(ArithmeticOp x) {
        int opcode = x.opcode;
        if (opcode == Bytecodes.LDIV || opcode == Bytecodes.LREM) {
            // emit inline 64-bit code
            LIRDebugInfo info = x.needsZeroCheck() ? stateFor(x) : null;
            CiValue dividend = force(x.x(), RAX_L); // dividend must be in RAX
            CiValue divisor = load(x.y());            // divisor can be in any (other) register

            CiValue result = createResultVariable(x);
            CiValue resultReg;
            if (opcode == Bytecodes.LREM) {
                resultReg = RDX_L; // remainder result is produced in rdx
                lir.lrem(dividend, divisor, resultReg, LDIV_TMP, info);
            } else {
                resultReg = RAX_L; // division result is produced in rax
                lir.ldiv(dividend, divisor, resultReg, LDIV_TMP, info);
            }

            lir.move(resultReg, result);
        } else if (opcode == Bytecodes.LMUL) {
            LIRItem right = new LIRItem(x.y(), this);

            // right register is destroyed by the long mul, so it must be
            // copied to a new register.
            right.setDestroysRegister();

            CiValue left = load(x.x());
            right.loadItem();

            arithmeticOpLong(opcode, LMUL_OUT, left, right.result(), null);
            CiValue result = createResultVariable(x);
            lir.move(LMUL_OUT, result);
        } else {
            LIRItem right = new LIRItem(x.y(), this);

            CiValue left = load(x.x());
            // don't load constants to save register
            right.loadNonconstant();
            createResultVariable(x);
            arithmeticOpLong(opcode, x.operand(), left, right.result(), null);
        }
    }

    public void visitArithmeticOpInt(ArithmeticOp x) {
        int opcode = x.opcode;
        if (opcode == Bytecodes.IDIV || opcode == Bytecodes.IREM) {
            // emit code for integer division or modulus

            // Call 'stateFor' before 'force()' because 'stateFor()' may
            // force the evaluation of other instructions that are needed for
            // correct debug info.  Otherwise the live range of the fixed
            // register might be too long.
            LIRDebugInfo info = x.needsZeroCheck() ? stateFor(x) : null;

            CiValue dividend = force(x.x(), RAX_I); // dividend must be in RAX
            CiValue divisor = load(x.y());          // divisor can be in any (other) register

            // idiv and irem use rdx in their implementation so the
            // register allocator must not assign it to an interval that overlaps
            // this division instruction.
            CiRegisterValue tmp = RDX_I;

            CiValue result = createResultVariable(x);
            CiValue resultReg;
            if (opcode == Bytecodes.IREM) {
                resultReg = tmp; // remainder result is produced in rdx
                lir.irem(dividend, divisor, resultReg, tmp, info);
            } else {
                resultReg = RAX_I; // division result is produced in rax
                lir.idiv(dividend, divisor, resultReg, tmp, info);
            }

            lir.move(resultReg, result);
        } else {
            // emit code for other integer operations
            LIRItem left = new LIRItem(x.x(), this);
            LIRItem right = new LIRItem(x.y(), this);
            LIRItem leftArg = left;
            LIRItem rightArg = right;
            if (x.isCommutative() && left.isStack() && right.isRegisterOrVariable()) {
                // swap them if left is real stack (or cached) and right is real register(not cached)
                leftArg = right;
                rightArg = left;
            }

            leftArg.loadItem();

            // do not need to load right, as we can handle stack and constants
            if (opcode == Bytecodes.IMUL) {
                // check if we can use shift instead
                boolean useConstant = false;
                boolean useTmp = false;
                if (rightArg.result().isConstant()) {
                    int iconst = rightArg.instruction.asConstant().asInt();
                    if (iconst > 0) {
                        if (CiUtil.isPowerOf2(iconst)) {
                            useConstant = true;
                        } else if (CiUtil.isPowerOf2(iconst - 1) || CiUtil.isPowerOf2(iconst + 1)) {
                            useConstant = true;
                            useTmp = true;
                        }
                    }
                }
                if (!useConstant) {
                    rightArg.loadItem();
                }
                CiValue tmp = ILLEGAL;
                if (useTmp) {
                    tmp = newVariable(CiKind.Int);
                }
                createResultVariable(x);

                arithmeticOpInt(opcode, x.operand(), leftArg.result(), rightArg.result(), tmp);
            } else {
                createResultVariable(x);
                CiValue tmp = ILLEGAL;
                arithmeticOpInt(opcode, x.operand(), leftArg.result(), rightArg.result(), tmp);
            }
        }
    }

    public void visitArithmeticOpWord(ArithmeticOp x) {
        int opcode = x.opcode;
        if (opcode == Bytecodes.WDIV || opcode == Bytecodes.WREM || opcode == Bytecodes.WDIVI || opcode == Bytecodes.WREMI) {
            // emit code for long division or modulus
                // emit inline 64-bit code
                LIRDebugInfo info = x.needsZeroCheck() ? stateFor(x) : null;
                CiValue dividend = force(x.x(), RAX_L); // dividend must be in RAX
                CiValue divisor = load(x.y());            // divisor can be in any (other) register

                CiValue result = createResultVariable(x);
                CiValue resultReg;
                if (opcode == Bytecodes.WREM) {
                    resultReg = RDX_L; // remainder result is produced in rdx
                    lir.wrem(dividend, divisor, resultReg, LDIV_TMP, info);
                } else if (opcode == Bytecodes.WREMI) {
                    resultReg = RDX_L; // remainder result is produced in rdx
                    lir.wremi(dividend, divisor, resultReg, LDIV_TMP, info);
                } else if (opcode == Bytecodes.WDIV) {
                    resultReg = RAX_L; // division result is produced in rax
                    lir.wdiv(dividend, divisor, resultReg, LDIV_TMP, info);
                } else {
                    assert opcode == Bytecodes.WDIVI;
                    resultReg = RAX_L; // division result is produced in rax
                    lir.wdivi(dividend, divisor, resultReg, LDIV_TMP, info);
                }

                lir.move(resultReg, result);
        } else if (opcode == Bytecodes.LMUL) {
            LIRItem right = new LIRItem(x.y(), this);

            // right register is destroyed by the long mul, so it must be
            // copied to a new register.
            right.setDestroysRegister();

            CiValue left = load(x.x());
            right.loadItem();

            CiValue reg = LMUL_OUT;
            arithmeticOpLong(opcode, reg, left, right.result(), null);
            CiValue result = createResultVariable(x);
            lir.move(reg, result);
        } else {
            LIRItem right = new LIRItem(x.y(), this);

            CiValue left = load(x.x());
            // don't load constants to save register
            right.loadNonconstant();
            createResultVariable(x);
            arithmeticOpLong(opcode, x.operand(), left, right.result(), null);
        }
    }

    @Override
    public void visitArithmeticOp(ArithmeticOp x) {
        trySwap(x);

        if (x.kind.isWord() || x.opcode == Bytecodes.WREMI) {
            visitArithmeticOpWord(x);
            return;
        }

        assert Util.archKindsEqual(x.x().kind, x.kind) && Util.archKindsEqual(x.y().kind, x.kind) : "wrong parameter types: " + Bytecodes.nameOf(x.opcode);
        switch (x.kind) {
            case Float:
            case Double:
                visitArithmeticOpFloat(x);
                return;
            case Long:
                visitArithmeticOpLong(x);
                return;
            case Int:
                visitArithmeticOpInt(x);
                return;
        }
        throw Util.shouldNotReachHere();
    }

    @Override
    public void visitShiftOp(ShiftOp x) {
        // count must always be in rcx
        CiValue count = makeOperand(x.y());
        boolean mustLoadCount = !count.isConstant() || x.kind == CiKind.Long;
        if (mustLoadCount) {
            // count for long must be in register
            count = force(x.y(), SHIFT_COUNT_IN);
        }

        CiValue value = load(x.x());
        CiValue reg = createResultVariable(x);

        shiftOp(x.opcode, reg, value, count, ILLEGAL);
    }

    @Override
    public void visitLogicOp(LogicOp x) {
        trySwap(x);

        LIRItem right = new LIRItem(x.y(), this);

        CiValue left = load(x.x());
        right.loadNonconstant();
        CiValue reg = createResultVariable(x);

        logicOp(x.opcode, reg, left, right.result());
    }

    private void trySwap(Op2 x) {
        // (tw) TODO: Check what this is for?
    }

    @Override
    public void visitCompareOp(CompareOp x) {
        LIRItem left = new LIRItem(x.x(), this);
        LIRItem right = new LIRItem(x.y(), this);
        if (!x.kind.isVoid() && x.x().kind.isLong()) {
            left.setDestroysRegister();
        }
        left.loadItem();
        right.loadItem();

        if (x.kind.isVoid()) {
            lir.cmp(Condition.TRUE, left.result(), right.result());
        } else if (x.x().kind.isFloat() || x.x().kind.isDouble()) {
            CiValue reg = createResultVariable(x);
            int code = x.opcode;
            lir.fcmp2int(left.result(), right.result(), reg, (code == Bytecodes.FCMPL || code == Bytecodes.DCMPL));
        } else if (x.x().kind.isLong() || x.x().kind.isWord()) {
            CiValue reg = createResultVariable(x);
            lir.lcmp2int(left.result(), right.result(), reg);
        } else {
            Util.unimplemented();
        }
    }

    @Override
    public void visitUnsignedCompareOp(UnsignedCompareOp x) {
        LIRItem left = new LIRItem(x.x(), this);
        LIRItem right = new LIRItem(x.y(), this);
        left.loadItem();
        right.loadItem();
        Condition condition = null;
        switch (x.op) {
            case BELOW_THAN  : condition = Condition.BT; break;
            case ABOVE_THAN  : condition = Condition.AT; break;
            case BELOW_EQUAL : condition = Condition.BE; break;
            case ABOVE_EQUAL : condition = Condition.AE; break;
            default:
                Util.unimplemented();
        }
        CiValue result = createResultVariable(x);
        lir.cmp(condition, left.result(), right.result());
        lir.cmove(condition, CiConstant.INT_1, CiConstant.INT_0, result);
    }

    @Override
    public void visitCompareAndSwap(CompareAndSwap x) {

        // (tw) TODO: Factor out common code with genCompareAndSwap.

        CiKind dataKind = x.dataKind;
        CiValue tempPointer = load(x.pointer());
        CiAddress addr = getAddressForPointerOp(x, dataKind, tempPointer);

        CiValue expectedValue = force(x.expectedValue(), AMD64.rax.asValue(dataKind));
        CiValue newValue = load(x.newValue());
        assert Util.archKindsEqual(newValue.kind, dataKind) : "invalid type";

        if (dataKind.isObject()) { // Write-barrier needed for Object fields.
            // Do the pre-write barrier : if any.
            preGCWriteBarrier(addr, false, null);
        }

        CiValue pointer = newVariable(CiKind.Word);
        lir.lea(addr, pointer);
        CiValue result = createResultVariable(x);
        CiValue resultReg = AMD64.rax.asValue(dataKind);
        if (dataKind.isObject()) {
            lir.casObj(pointer, expectedValue, newValue);
        } else if (dataKind.isInt()) {
            lir.casInt(pointer, expectedValue, newValue);
        } else {
            assert dataKind.isLong() || dataKind.isWord();
            lir.casLong(pointer, expectedValue, newValue);
        }

        lir.move(resultReg, result);

        if (dataKind.isObject()) { // Write-barrier needed for Object fields.
            // Seems to be precise
            postGCWriteBarrier(pointer, newValue);
        }
    }

    @Override
    protected void genCompareAndSwap(Intrinsic x, CiKind kind) {
        assert x.numberOfArguments() == 5 : "wrong number of arguments: " + x.numberOfArguments();
        // Argument 0 is the receiver.
        LIRItem obj = new LIRItem(x.argumentAt(1), this); // object
        LIRItem offset = new LIRItem(x.argumentAt(2), this); // offset of field
        LIRItem val = new LIRItem(x.argumentAt(4), this); // replace field with val if matches cmp

        assert obj.instruction.kind.isObject() : "invalid type";

        assert val.instruction.kind == kind : "invalid type";

        // get address of field
        obj.loadItem();
        offset.loadNonconstant();
        CiAddress addr;
        if (offset.result().isConstant()) {
            addr = new CiAddress(kind, obj.result(), (int) ((CiConstant) offset.result()).asLong());
        } else {
            addr = new CiAddress(kind, obj.result(), offset.result());
        }

        // Compare operand needs to be in RAX.
        CiValue cmp = force(x.argumentAt(3), AMD64.rax.asValue(kind));
        val.loadItem();

        CiValue pointer = newVariable(CiKind.Word);
        lir.lea(addr, pointer);

        if (kind.isObject()) { // Write-barrier needed for Object fields.
            // Do the pre-write barrier : if any.
            preGCWriteBarrier(pointer, false, null);
        }

        if (kind.isObject()) {
            lir.casObj(pointer, cmp, val.result());
        } else if (kind.isInt()) {
            lir.casInt(pointer, cmp, val.result());
        } else if (kind.isLong()) {
            lir.casLong(pointer, cmp, val.result());
        } else {
            Util.shouldNotReachHere();
        }

        // generate conditional move of boolean result
        CiValue result = createResultVariable(x);
        lir.cmove(Condition.EQ, CiConstant.INT_1, CiConstant.INT_0, result);
        if (kind.isObject()) { // Write-barrier needed for Object fields.
            // Seems to be precise
            postGCWriteBarrier(pointer, val.result());
        }
    }

    @Override
    protected void genMathIntrinsic(Intrinsic x) {
        assert x.numberOfArguments() == 1 : "wrong type";

        CiValue calcInput = load(x.argumentAt(0));

        switch (x.intrinsic()) {
            case java_lang_Math$abs:
                lir.abs(calcInput, createResultVariable(x), ILLEGAL);
                break;
            case java_lang_Math$sqrt:
                lir.sqrt(calcInput, createResultVariable(x), ILLEGAL);
                break;
            case java_lang_Math$sin:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticSin, null, calcInput));
                break;
            case java_lang_Math$cos:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticCos, null, calcInput));
                break;
            case java_lang_Math$tan:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticTan, null, calcInput));
                break;
            case java_lang_Math$log:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticLog, null, calcInput));
                break;
            case java_lang_Math$log10:
                setResult(x, callRuntimeWithResult(CiRuntimeCall.ArithmeticLog10, null, calcInput));
                break;
            default:
                Util.shouldNotReachHere("Unknown math intrinsic");
        }
    }

    @Override
    public void visitConvert(Convert x) {
        CiValue input = load(x.value());
        CiVariable result = newVariable(x.kind);
        // arguments of lirConvert
        GlobalStub globalStub = null;
        switch (x.opcode) {
            case Bytecodes.F2I: globalStub = stubFor(GlobalStub.Id.f2i); break;
            case Bytecodes.F2L: globalStub = stubFor(GlobalStub.Id.f2l); break;
            case Bytecodes.D2I: globalStub = stubFor(GlobalStub.Id.d2i); break;
            case Bytecodes.D2L: globalStub = stubFor(GlobalStub.Id.d2l); break;
        }
        if (globalStub != null) {
            // Force result to be rax to match global stubs expectation.
            CiValue stubResult = x.kind == CiKind.Int ? RAX_I : RAX_L;
            lir.convert(x.opcode, input, stubResult, globalStub);
            lir.move(stubResult, result);
        } else {
            lir.convert(x.opcode, input, result, globalStub);
        }
        setResult(x, result);
    }

    @Override
    public void visitBlockBegin(BlockBegin x) {
        // nothing to do for now
    }

    @Override
    public void visitIf(If x) {
        CiKind kind = x.x().kind;

        Condition cond = x.condition();

        LIRItem xitem = new LIRItem(x.x(), this);
        LIRItem yitem = new LIRItem(x.y(), this);
        LIRItem xin = xitem;
        LIRItem yin = yitem;

        if (kind.isLong()) {
            // for longs, only conditions "eql", "neq", "lss", "geq" are valid;
            // mirror for other conditions
            if (cond == Condition.GT || cond == Condition.LE) {
                cond = cond.mirror();
                xin = yitem;
                yin = xitem;
            }
            xin.setDestroysRegister();
        }
        xin.loadItem();
        if (kind.isLong() && yin.result().isConstant() && yin.instruction.asConstant().asLong() == 0 && (cond == Condition.EQ || cond == Condition.NE)) {
            // dont load item
        } else if (kind.isLong() || kind.isFloat() || kind.isDouble()) {
            // longs cannot handle constants at right side
            yin.loadItem();
        }

        // add safepoint before generating condition code so it can be recomputed
        if (x.isSafepoint()) {
            emitXir(xir.genSafepoint(site(x)), x, stateFor(x, x.stateAfter()), null, false);
        }
        setNoResult(x);

        CiValue left = xin.result();
        CiValue right = yin.result();
        lir.cmp(cond, left, right);
        moveToPhi(x.stateAfter());
        if (x.x().kind.isFloat() || x.x().kind.isDouble()) {
            lir.branch(cond, right.kind, x.trueSuccessor(), x.unorderedSuccessor());
        } else {
            lir.branch(cond, right.kind, x.trueSuccessor());
        }
        assert x.defaultSuccessor() == x.falseSuccessor() : "wrong destination above";
        lir.jump(x.defaultSuccessor());
    }

    @Override
    protected void genGetObjectUnsafe(CiValue dst, CiValue src, CiValue offset, CiKind kind, boolean isVolatile) {
        if (isVolatile && kind == CiKind.Long) {
            CiAddress addr = new CiAddress(CiKind.Double, src, offset);
            CiValue tmp = newVariable(CiKind.Double);
            lir.load(addr, tmp, null);
            CiValue spill = operands.newVariable(CiKind.Long, VariableFlag.MustStartInMemory);
            lir.move(tmp, spill);
            lir.move(spill, dst);
        } else {
            CiAddress addr = new CiAddress(kind, src, offset);
            lir.load(addr, dst, null);
        }
    }

    @Override
    protected void genPutObjectUnsafe(CiValue src, CiValue offset, CiValue data, CiKind kind, boolean isVolatile) {
        if (isVolatile && kind == CiKind.Long) {
            CiAddress addr = new CiAddress(CiKind.Double, src, offset);
            CiValue tmp = newVariable(CiKind.Double);
            CiValue spill = operands.newVariable(CiKind.Double, VariableFlag.MustStartInMemory);
            lir.move(data, spill);
            lir.move(spill, tmp);
            lir.move(tmp, addr);
        } else {
            CiAddress addr = new CiAddress(kind, src, offset);
            boolean isObj = (kind == CiKind.Jsr || kind == CiKind.Object);
            if (isObj) {
                // Do the pre-write barrier, if any.
                preGCWriteBarrier(addr, false, null);
                lir.move(data, addr);
                assert src.isVariableOrRegister() : "must be register";
                // Seems to be a precise address
                postGCWriteBarrier(addr, data);
            } else {
                lir.move(data, addr);
            }
        }
    }

    @Override
    protected CiValue osrBufferPointer() {
        return Util.nonFatalUnimplemented(null);
    }

    @Override
    public void visitBoundsCheck(BoundsCheck boundsCheck) {
        Value x = boundsCheck.index();
        Value y = boundsCheck.length();
        CiValue left = load(x);
        CiValue right = null;
        if (y.isConstant()) {
            right = makeOperand(y);
        } else {
            right = load(y);
        }
        lir.cmp(boundsCheck.condition.negate(), left, right);
        emitGuard(boundsCheck);
    }
}
