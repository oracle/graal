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
package com.sun.c1x.opt;

import static com.sun.cri.bytecode.Bytecodes.*;

import java.lang.reflect.*;
import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * The {@code Canonicalizer} reduces instructions to a canonical form by folding constants,
 * putting constants on the right side of commutative operators, simplifying conditionals,
 * and several other transformations.
 *
 * @author Ben L. Titzer
 */
public class Canonicalizer extends DefaultValueVisitor {

    final RiRuntime runtime;
    final RiMethod method;
    final CiTarget target;
    Value canonical;
    List<Instruction> extra;

    public Canonicalizer(RiRuntime runtime, RiMethod method, CiTarget target) {
        this.runtime = runtime;
        this.method = method;
        this.target = target;
    }

    public Value canonicalize(Instruction original) {
        this.canonical = original;
        this.extra = null;
        original.accept(this);
        return this.canonical;
    }

    public List<Instruction> extra() {
        return extra;
    }

    private <T extends Instruction> T addInstr(T x) {
        if (extra == null) {
            extra = new LinkedList<Instruction>();
        }
        extra.add(x);
        return x;
    }

    private Constant intInstr(int v) {
        return addInstr(Constant.forInt(v));
    }

    private Constant longInstr(long v) {
        return addInstr(Constant.forLong(v));
    }

    private Constant wordInstr(long v) {
        return addInstr(Constant.forWord(v));
    }

    private Value setCanonical(Value x) {
        return canonical = x;
    }

    private Value setIntConstant(int val) {
        return canonical = Constant.forInt(val);
    }

    private Value setConstant(CiConstant val) {
        return canonical = new Constant(val);
    }

    private Value setBooleanConstant(boolean val) {
        return canonical = Constant.forBoolean(val);
    }

    private Value setObjectConstant(Object val) {
        if (C1XOptions.SupportObjectConstants) {
            return canonical = Constant.forObject(val);
        }
        return canonical;
    }

    private Value setLongConstant(long val) {
        return canonical = Constant.forLong(val);
    }

    private Value setFloatConstant(float val) {
        return canonical = Constant.forFloat(val);
    }

    private Value setDoubleConstant(double val) {
        return canonical = Constant.forDouble(val);
    }

    private Value setWordConstant(long val) {
        return canonical = Constant.forDouble(val);
    }

    private void moveConstantToRight(Op2 x) {
        if (x.x().isConstant() && isCommutative(x.opcode)) {
            x.swapOperands();
        }
    }

    private void visitOp2(Op2 i) {
        final Value x = i.x();
        final Value y = i.y();

        if (x == y) {
            // the left and right operands are the same value, try reducing some operations
            switch (i.opcode) {
                case ISUB: setIntConstant(0); return;
                case LSUB: setLongConstant(0); return;
                case IAND: // fall through
                case LAND: // fall through
                case IOR:  // fall through
                case LOR: setCanonical(x); return;
                case IXOR: setIntConstant(0); return;
                case LXOR: setLongConstant(0); return;
            }
        }

        CiKind kind = x.kind;
        if (x.isConstant() && y.isConstant()) {
            // both operands are constants, try constant folding
            switch (kind) {
                case Int: {
                    Integer val = foldIntOp2(i.opcode, x.asConstant().asInt(), y.asConstant().asInt());
                    if (val != null) {
                        setIntConstant(val); // the operation was successfully folded to an int
                        return;
                    }
                    break;
                }
                case Long: {
                    Long val = foldLongOp2(i.opcode, x.asConstant().asLong(), y.asConstant().asLong());
                    if (val != null) {
                        setLongConstant(val); // the operation was successfully folded to a long
                        return;
                    }
                    break;
                }
                case Float: {
                    if (C1XOptions.CanonicalizeFloatingPoint) {
                        // try to fold a floating point operation
                        Float val = foldFloatOp2(i.opcode, x.asConstant().asFloat(), y.asConstant().asFloat());
                        if (val != null) {
                            setFloatConstant(val); // the operation was successfully folded to a float
                            return;
                        }
                    }
                    break;
                }
                case Double: {
                    if (C1XOptions.CanonicalizeFloatingPoint) {
                        // try to fold a floating point operation
                        Double val = foldDoubleOp2(i.opcode, x.asConstant().asDouble(), y.asConstant().asDouble());
                        if (val != null) {
                            setDoubleConstant(val); // the operation was successfully folded to a double
                            return;
                        }
                    }
                    break;
                }
                case Word: {
                    CiConstant val = runtime.foldWordOperation(i.opcode, new CiMethodInvokeArguments() {
                        int argIndex;
                        @Override
                        public CiConstant nextArg() {
                            if (argIndex == 0) {
                                return x.asConstant();
                            }
                            if (argIndex == 1) {
                                return y.asConstant();
                            }
                            argIndex++;
                            return null;
                        }
                    });

                    if (val != null) {
                        setConstant(val); // the operation was successfully folded to a word
                        return;
                    }
                    break;
                }
            }
        }

        // if there is a constant on the left and the operation is commutative, move it to the right
        moveConstantToRight(i);

        if (i.y().isConstant()) {
            // the right side is a constant, try strength reduction
            switch (kind) {
                case Int: {
                    if (reduceIntOp2(i, i.x(), i.y().asConstant().asInt()) != null) {
                        return;
                    }
                    break;
                }
                case Long: {
                    if (reduceLongOp2(i, i.x(), i.y().asConstant().asLong()) != null) {
                        return;
                    }
                    break;
                }
                case Word: {
                    if (reduceWordOp2(i, i.x(), i.y().asConstant().asLong()) != null) {
                        return;
                    }
                    break;
                }
                // XXX: note that other cases are possible, but harder
                // floating point operations need to be extra careful
            }
        }
        assert Util.archKindsEqual(i, canonical);
    }

    private Value reduceIntOp2(Op2 original, Value x, int y) {
        // attempt to reduce a binary operation with a constant on the right
        int opcode = original.opcode;
        switch (opcode) {
            case IADD: return y == 0 ? setCanonical(x) : null;
            case ISUB: return y == 0 ? setCanonical(x) : null;
            case IMUL: {
                if (y == 1) {
                    return setCanonical(x);
                }
                if (y > 0 && (y & y - 1) == 0 && C1XOptions.CanonicalizeMultipliesToShifts) {
                    // strength reduce multiply by power of 2 to shift operation
                    return setCanonical(new ShiftOp(ISHL, x, intInstr(CiUtil.log2(y))));
                }
                return y == 0 ? setIntConstant(0) : null;
            }
            case IDIV: return y == 1 ? setCanonical(x) : null;
            case IREM: return y == 1 ? setCanonical(x) : null;
            case IAND: {
                if (y == -1) {
                    return setCanonical(x);
                }
                return y == 0 ? setIntConstant(0) : null;
            }
            case IOR: {
                if (y == -1) {
                    return setIntConstant(-1);
                }
                return y == 0 ? setCanonical(x) : null;
            }
            case IXOR: return y == 0 ? setCanonical(x) : null;
            case ISHL: return reduceShift(false, opcode, IUSHR, x, y);
            case ISHR: return reduceShift(false, opcode, 0, x, y);
            case IUSHR: return reduceShift(false, opcode, ISHL, x, y);
        }
        return null;
    }

    private Value reduceShift(boolean islong, int opcode, int reverse, Value x, long y) {
        int mod = islong ? 0x3f : 0x1f;
        long shift = y & mod;
        if (shift == 0) {
            return setCanonical(x);
        }
        if (x instanceof ShiftOp) {
            // this is a chained shift operation ((e shift e) shift K)
            ShiftOp s = (ShiftOp) x;
            if (s.y().isConstant()) {
                long z = s.y().asConstant().asLong();
                if (s.opcode == opcode) {
                    // this is a chained shift operation (e >> C >> K)
                    y = y + z;
                    shift = y & mod;
                    if (shift == 0) {
                        return setCanonical(s.x());
                    }
                    // reduce to (e >> (C + K))
                    return setCanonical(new ShiftOp(opcode, s.x(), intInstr((int) shift)));
                }
                if (s.opcode == reverse && y == z) {
                    // this is a chained shift of the form (e >> K << K)
                    if (islong) {
                        long mask = -1;
                        if (opcode == LUSHR) {
                            mask = mask >>> y;
                        } else {
                            mask = mask << y;
                        }
                        // reduce to (e & mask)
                        return setCanonical(new LogicOp(LAND, s.x(), longInstr(mask)));
                    } else {
                        int mask = -1;
                        if (opcode == IUSHR) {
                            mask = mask >>> y;
                        } else {
                            mask = mask << y;
                        }
                        return setCanonical(new LogicOp(IAND, s.x(), intInstr(mask)));
                    }
                }
            }
        }
        if (y != shift) {
            // (y & mod) != y
            return setCanonical(new ShiftOp(opcode, x, intInstr((int) shift)));
        }
        return null;
    }

    private Value reduceLongOp2(Op2 original, Value x, long y) {
        // attempt to reduce a binary operation with a constant on the right
        int opcode = original.opcode;
        switch (opcode) {
            case LADD: return y == 0 ? setCanonical(x) : null;
            case LSUB: return y == 0 ? setCanonical(x) : null;
            case LMUL: {
                if (y == 1) {
                    return setCanonical(x);
                }
                if (y > 0 && (y & y - 1) == 0 && C1XOptions.CanonicalizeMultipliesToShifts) {
                    // strength reduce multiply by power of 2 to shift operation
                    return setCanonical(new ShiftOp(LSHL, x, intInstr(CiUtil.log2(y))));
                }
                return y == 0 ? setLongConstant(0) : null;
            }
            case LDIV: return y == 1 ? setCanonical(x) : null;
            case LREM: return y == 1 ? setCanonical(x) : null;
            case LAND: {
                if (y == -1) {
                    return setCanonical(x);
                }
                return y == 0 ? setLongConstant(0) : null;
            }
            case LOR: {
                if (y == -1) {
                    return setLongConstant(-1);
                }
                return y == 0 ? setCanonical(x) : null;
            }
            case LXOR: return y == 0 ? setCanonical(x) : null;
            case LSHL: return reduceShift(true, opcode, LUSHR, x, y);
            case LSHR: return reduceShift(true, opcode, 0, x, y);
            case LUSHR: return reduceShift(true, opcode, LSHL, x, y);
        }
        return null;
    }

    private Value reduceWordOp2(Op2 original, Value x, long y) {
        if (y == 0) {
            // Defer to arithmetic exception at runtime
            return null;
        }
        // attempt to reduce a binary operation with a constant on the right
        int opcode = original.opcode;
        switch (opcode) {
            case WDIVI:
            case WDIV: {
                if (y == 1) {
                    return setCanonical(x);
                }
                if (CiUtil.isPowerOf2(y)) {
                    return setCanonical(new ShiftOp(target.arch.is64bit() ? LUSHR : IUSHR, x, intInstr(CiUtil.log2(y))));
                }
                break;
            }
            case WREMI: {
                if (y == 1) {
                    return setCanonical(intInstr(0));
                }
                if (CiUtil.isPowerOf2(y)) {
                    int mask = (int) y - 1;
                    if (target.arch.is64bit()) {
                        Convert l2i = new Convert(L2I, x, CiKind.Int);
                        addInstr(l2i);
                        return setCanonical(new LogicOp(IAND, l2i, intInstr(mask)));
                    }
                    return setCanonical(new LogicOp(CiKind.Int, IAND, x, intInstr(mask)));
                }
                break;
            }
            case WREM: {
                if (y == 1) {
                    return setCanonical(wordInstr(0));
                }
                if (CiUtil.isPowerOf2(y)) {
                    if (target.arch.is64bit()) {
                        long mask = y - 1L;
                        return setCanonical(new LogicOp(LAND, x, longInstr(mask)));
                    }
                    int mask = (int) y - 1;
                    return setCanonical(new LogicOp(IAND, x, intInstr(mask)));
                }
                break;
            }
        }
        return null;
    }

    private boolean inCurrentBlock(Value x) {
        if (x instanceof Instruction) {
            Instruction i = (Instruction) x;
            int max = 4; // XXX: anything special about 4? seems like a tunable heuristic
            while (max > 0 && i != null && !(i instanceof BlockEnd)) {
                i = i.next();
                max--;
            }
            return i == null;
        }
        return true;
    }

    private Value eliminateNarrowing(CiKind kind, Convert c) {
        Value nv = null;
        switch (c.opcode) {
            case I2B:
                if (kind == CiKind.Byte) {
                    nv = c.value();
                }
                break;
            case I2S:
                if (kind == CiKind.Short || kind == CiKind.Byte) {
                    nv = c.value();
                }
                break;
            case I2C:
                if (kind == CiKind.Char || kind == CiKind.Byte) {
                    nv = c.value();
                }
                break;
        }
        return nv;
    }

    @Override
    public void visitLoadField(LoadField i) {
        if (!i.isLoaded() || !C1XOptions.CanonicalizeConstantFields) {
            return;
        }
        if (i.isStatic()) {
            RiField field = i.field();
            CiConstant value = field.constantValue(null);
            if (value != null) {
                if (method.isClassInitializer()) {
                    // don't do canonicalization in the <clinit> method
                    return;
                }
                setConstant(value);
            }
        } else {
            RiField field = i.field();
            if (i.object().isConstant()) {
                CiConstant value = field.constantValue(i.object().asConstant());
                if (value != null) {
                    setConstant(value);
                }
            }
        }
    }

    @Override
    public void visitStoreField(StoreField i) {
        if (C1XOptions.CanonicalizeNarrowingInStores) {
            // Eliminate narrowing conversions emitted by javac which are unnecessary when
            // writing the value to a field that is packed
            Value v = i.value();
            if (v instanceof Convert) {
                Value nv = eliminateNarrowing(i.field().kind(), (Convert) v);
                // limit this optimization to the current basic block
                // XXX: why is this limited to the current block?
                if (nv != null && inCurrentBlock(v)) {
                    setCanonical(new StoreField(i.object(), i.field(), nv, i.isStatic(),
                                                i.stateBefore(), i.isLoaded()));
                }
            }
        }
    }

    @Override
    public void visitArrayLength(ArrayLength i) {
        // we can compute the length of the array statically if the object
        // is a NewArray of a constant, or if the object is a constant reference
        // (either by itself or loaded from a constant value field)
        Value array = i.array();
        if (array instanceof NewArray) {
            // the array is a NewArray; check if it has a constant length
            NewArray newArray = (NewArray) array;
            Value length = newArray.length();
            if (length instanceof Constant) {
                // note that we don't use the Constant instruction itself
                // as that would cause problems with liveness later
                int actualLength = length.asConstant().asInt();
                setIntConstant(actualLength);
            }
        } else if (array instanceof LoadField) {
            // the array is a load of a field; check if it is a constant
            LoadField load = (LoadField) array;
            CiConstant cons = load.constantValue();
            if (cons != null && cons.isNonNull()) {
                setIntConstant(runtime.getArrayLength(cons));
            }
        } else if (array.isConstant()) {
            // the array itself is a constant object reference
            CiConstant obj = array.asConstant();
            if (obj.isNonNull()) {
                setIntConstant(runtime.getArrayLength(obj));
            }
        }
    }

    @Override
    public void visitStoreIndexed(StoreIndexed i) {
        Value array = i.array();
        Value value = i.value();
        if (C1XOptions.CanonicalizeNarrowingInStores) {
            // Eliminate narrowing conversions emitted by javac which are unnecessary when
            // writing the value to an array (which is packed)
            Value v = value;
            if (v instanceof Convert) {
                Value nv = eliminateNarrowing(i.elementKind(), (Convert) v);
                if (nv != null && inCurrentBlock(v)) {
                    setCanonical(new StoreIndexed(array, i.index(), i.length(), i.elementKind(), nv, i.stateBefore()));
                }
            }
        }
        if (C1XOptions.CanonicalizeArrayStoreChecks && i.elementKind() == CiKind.Object) {
            if (value.isNullConstant()) {
                i.eliminateStoreCheck();
            } else {
                RiType exactType = array.exactType();
                if (exactType != null && exactType.isResolved()) {
                    if (exactType.componentType().superType() == null) {
                        // the exact type of the array is Object[] => no check is necessary
                        i.eliminateStoreCheck();
                    } else {
                        RiType declaredType = value.declaredType();
                        if (declaredType != null && declaredType.isResolved() && declaredType.isSubtypeOf(exactType.componentType())) {
                            // the value being stored has a known type
                            i.eliminateStoreCheck();
                        }
                    }
                }
            }
        }
        if (i.index().isConstant() && i.length() != null && i.length().isConstant()) {
            int index = i.index().asConstant().asInt();
            if (index >= 0 && index < i.length().asConstant().asInt()) {
                i.eliminateBoundsCheck();
            }
        }
    }

    @Override
    public void visitLoadIndexed(LoadIndexed i) {
        if (i.index().isConstant() && i.length() != null && i.length().isConstant()) {
            int index = i.index().asConstant().asInt();
            if (index >= 0 && index < i.length().asConstant().asInt()) {
                i.eliminateBoundsCheck();
            }
        }
    }

    @Override
    public void visitNegateOp(NegateOp i) {
        CiKind vt = i.x().kind;
        Value v = i.x();
        if (i.x().isConstant()) {
            switch (vt) {
                case Int: setIntConstant(-v.asConstant().asInt()); break;
                case Long: setLongConstant(-v.asConstant().asLong()); break;
                case Float: setFloatConstant(-v.asConstant().asFloat()); break;
                case Double: setDoubleConstant(-v.asConstant().asDouble()); break;
            }
        }
        assert vt == canonical.kind;
    }

    @Override
    public void visitArithmeticOp(ArithmeticOp i) {
        visitOp2(i);
    }

    @Override
    public void visitShiftOp(ShiftOp i) {
        visitOp2(i);
    }

    @Override
    public void visitLogicOp(LogicOp i) {
        visitOp2(i);
    }

    @Override
    public void visitCompareOp(CompareOp i) {
        if (i.kind.isVoid()) {
            return;
        }
        // we can reduce a compare op if the two inputs are the same,
        // or if both are constants
        Value x = i.x();
        Value y = i.y();
        CiKind xt = x.kind;
        if (x == y) {
            // x and y are generated by the same instruction
            switch (xt) {
                case Long: setIntConstant(0); return;
                case Float:
                    if (x.isConstant()) {
                        float xval = x.asConstant().asFloat(); // get the actual value of x (and y since x == y)
                        Integer val = foldFloatCompare(i.opcode, xval, xval);
                        assert val != null : "invalid opcode in float compare op";
                        setIntConstant(val);
                        return;
                    }
                    break;
                case Double:
                    if (x.isConstant()) {
                        double xval = x.asConstant().asDouble(); // get the actual value of x (and y since x == y)
                        Integer val = foldDoubleCompare(i.opcode, xval, xval);
                        assert val != null : "invalid opcode in double compare op";
                        setIntConstant(val);
                        return;
                    }
                    break;
                // note that there are no integer CompareOps
            }
        }
        if (x.isConstant() && y.isConstant()) {
            // both x and y are constants
            switch (xt) {
                case Long:
                    setIntConstant(foldLongCompare(x.asConstant().asLong(), y.asConstant().asLong()));
                    break;
                case Float: {
                    Integer val = foldFloatCompare(i.opcode, x.asConstant().asFloat(), y.asConstant().asFloat());
                    assert val != null : "invalid opcode in float compare op";
                    setIntConstant(val);
                    break;
                }
                case Double: {
                    Integer val = foldDoubleCompare(i.opcode, x.asConstant().asDouble(), y.asConstant().asDouble());
                    assert val != null : "invalid opcode in float compare op";
                    setIntConstant(val);
                    break;
                }
            }
        }
        assert Util.archKindsEqual(i, canonical);
    }

    @Override
    public void visitIfOp(IfOp i) {
        moveConstantToRight(i);
    }

    @Override
    public void visitConvert(Convert i) {
        Value v = i.value();
        if (v.isConstant()) {
            // fold conversions between primitive types
            // Checkstyle: stop
            switch (i.opcode) {
                case I2B: setIntConstant   ((byte)   v.asConstant().asInt()); return;
                case I2S: setIntConstant   ((short)  v.asConstant().asInt()); return;
                case I2C: setIntConstant   ((char)   v.asConstant().asInt()); return;
                case I2L: setLongConstant  (         v.asConstant().asInt()); return;
                case I2F: setFloatConstant (         v.asConstant().asInt()); return;
                case L2I: setIntConstant   ((int)    v.asConstant().asLong()); return;
                case L2F: setFloatConstant (         v.asConstant().asLong()); return;
                case L2D: setDoubleConstant(         v.asConstant().asLong()); return;
                case F2D: setDoubleConstant(         v.asConstant().asFloat()); return;
                case F2I: setIntConstant   ((int)    v.asConstant().asFloat()); return;
                case F2L: setLongConstant  ((long)   v.asConstant().asFloat()); return;
                case D2F: setFloatConstant ((float)  v.asConstant().asDouble()); return;
                case D2I: setIntConstant   ((int)    v.asConstant().asDouble()); return;
                case D2L: setLongConstant  ((long)   v.asConstant().asDouble()); return;
            }
            // Checkstyle: resume
        }

        CiKind kind = CiKind.Illegal;
        if (v instanceof LoadField) {
            // remove redundant conversions from field loads of the correct type
            kind = ((LoadField) v).field().kind();
        } else if (v instanceof LoadIndexed) {
            // remove redundant conversions from array loads of the correct type
            kind = ((LoadIndexed) v).elementKind();
        } else if (v instanceof Convert) {
            // remove chained redundant conversions
            Convert c = (Convert) v;
            switch (c.opcode) {
                case I2B: kind = CiKind.Byte; break;
                case I2S: kind = CiKind.Short; break;
                case I2C: kind = CiKind.Char; break;
            }
        }

        if (kind != CiKind.Illegal) {
            // if any of the above matched
            switch (i.opcode) {
                case I2B:
                    if (kind == CiKind.Byte) {
                        setCanonical(v);
                    }
                    break;
                case I2S:
                    if (kind == CiKind.Byte || kind == CiKind.Short) {
                        setCanonical(v);
                    }
                    break;
                case I2C:
                    if (kind == CiKind.Char) {
                        setCanonical(v);
                    }
                    break;
            }
        }

        if (v instanceof Op2) {
            // check if the operation was IAND with a constant; it may have narrowed the value already
            Op2 op = (Op2) v;
            // constant should be on right hand side if there is one
            if (op.opcode == IAND && op.y().isConstant()) {
                int safebits = 0;
                int mask = op.y().asConstant().asInt();
                switch (i.opcode) {
                    case I2B: safebits = 0x7f; break;
                    case I2S: safebits = 0x7fff; break;
                    case I2C: safebits = 0xffff; break;
                }
                if (safebits != 0 && (mask & ~safebits) == 0) {
                    // the mask already cleared all the upper bits necessary.
                    setCanonical(v);
                }
            }
        }
    }

    @Override
    public void visitNullCheck(NullCheck i) {
        Value o = i.object();
        if (o.isNonNull()) {
            // if the instruction producing the object was a new, no check is necessary
            setCanonical(o);
        } else if (o.isConstant()) {
            // if the object is a constant, check if it is nonnull
            CiConstant c = o.asConstant();
            if (c.kind.isObject() && !c.isNull()) {
                setCanonical(o);
            }
        }
    }

    @Override
    public void visitInvoke(Invoke i) {
        if (C1XOptions.CanonicalizeFoldableMethods) {
            RiMethod method = i.target();
            if (method.isResolved()) {
                // only try to fold resolved method invocations
                CiConstant result = foldInvocation(runtime, i.target(), i.arguments());
                if (result != null) {
                    // folding was successful
                    setCanonical(new Constant(result));
                }
            }
        }
    }

    @Override
    public void visitCheckCast(CheckCast i) {
        // we can remove a redundant check cast if it is an object constant or the exact type is known
        if (i.targetClass().isResolved()) {
            Value o = i.object();
            RiType type = o.exactType();
            if (type == null) {
                type = o.declaredType();
            }
            if (type != null && type.isResolved() && type.isSubtypeOf(i.targetClass())) {
                // cast is redundant if exact type or declared type is already a subtype of the target type
                setCanonical(o);
            }
            if (o.isConstant()) {
                final CiConstant obj = o.asConstant();
                if (obj.isNull()) {
                    // checkcast of null is null
                    setCanonical(o);
                } else if (C1XOptions.CanonicalizeObjectCheckCast) {
                    if (i.targetClass().isInstance(obj)) {
                        // fold the cast if it will succeed
                        setCanonical(o);
                    }
                }
            }
        }
    }

    @Override
    public void visitInstanceOf(InstanceOf i) {
        // we can fold an instanceof if it is an object constant or the exact type is known
        if (i.targetClass().isResolved()) {
            Value o = i.object();
            RiType exact = o.exactType();
            if (exact != null && exact.isResolved() && o.isNonNull()) {
                setIntConstant(exact.isSubtypeOf(i.targetClass()) ? 1 : 0);
            } else if (o.isConstant()) {
                final CiConstant obj = o.asConstant();
                if (obj.isNull()) {
                    // instanceof of null is false
                    setIntConstant(0);
                } else if (C1XOptions.CanonicalizeObjectInstanceOf) {
                    // fold the instanceof test
                    setIntConstant(i.targetClass().isInstance(obj) ? 1 : 0);
                }
            }
        }
    }

    @Override
    public void visitIntrinsic(Intrinsic i) {
        if (!C1XOptions.CanonicalizeIntrinsics) {
            return;
        }
        if (!foldIntrinsic(i)) {
            // folding did not work, try recognizing special intrinsics
            reduceIntrinsic(i);
        }
        assert Util.archKindsEqual(i, canonical);
    }

    private void reduceIntrinsic(Intrinsic i) {
        Value[] args = i.arguments();
        C1XIntrinsic intrinsic = i.intrinsic();
        if (intrinsic == C1XIntrinsic.java_lang_Class$isInstance) {
            // try to convert a call to Class.isInstance() into an InstanceOf
            RiType type = getTypeOf(args[0]);
            if (type != null) {
                setCanonical(new InstanceOf(type, Constant.forObject(type.getEncoding(RiType.Representation.TypeInfo)), args[1], i.stateBefore()));
                return;
            }
        }
        if (intrinsic == C1XIntrinsic.java_lang_reflect_Array$newArray) {
            // try to convert a call to Array.newInstance() into a NewObjectArray or NewTypeArray
            RiType type = getTypeOf(args[0]);
            if (type != null) {
                if (type.kind() == CiKind.Object) {
                    setCanonical(new NewObjectArray(type, args[1], i.stateBefore()));
                } else {
                    RiType elementType = runtime.asRiType(type.kind());
                    setCanonical(new NewTypeArray(args[1], elementType, i.stateBefore()));
                }
                return;
            }
        }
        assert Util.archKindsEqual(i, canonical);
    }

    private boolean foldIntrinsic(Intrinsic i) {
        Value[] args = i.arguments();
        for (Value arg : args) {
            if (arg != null && !arg.isConstant()) {
                // one input is not constant, give up
                return true;
            }
        }
        switch (i.intrinsic()) {
            // do not use reflection here due to efficiency and potential bootstrap problems
            case java_lang_Object$hashCode: {
                Object object = argAsObject(args, 0);
                if (object != null) {
                    setIntConstant(System.identityHashCode(object));
                }
                return true;
            }
            case java_lang_Object$getClass: {
                Object object = argAsObject(args, 0);
                if (object != null) {
                    setObjectConstant(object.getClass());
                }
                return true;
            }

            // java.lang.Class
            case java_lang_Class$isAssignableFrom: {
                Class<?> javaClass = argAsClass(args, 0);
                Class<?> otherClass = argAsClass(args, 1);
                if (javaClass != null && otherClass != null) {
                    setBooleanConstant(javaClass.isAssignableFrom(otherClass));
                }
                return true;
            }
            case java_lang_Class$isInstance: {
                Class<?> javaClass = argAsClass(args, 0);
                Object object = argAsObject(args, 1);
                if (javaClass != null && object != null) {
                    setBooleanConstant(javaClass.isInstance(object));
                }
                return true;
            }
            case java_lang_Class$getModifiers: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setIntConstant(javaClass.getModifiers());
                }
                return true;
            }
            case java_lang_Class$isInterface: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setBooleanConstant(javaClass.isInterface());
                }
                return true;
            }
            case java_lang_Class$isArray: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setBooleanConstant(javaClass.isArray());
                }
                return true;
            }
            case java_lang_Class$isPrimitive: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setBooleanConstant(javaClass.isPrimitive());
                }
                return true;
            }
            case java_lang_Class$getSuperclass: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setObjectConstant(javaClass.getSuperclass());
                }
                return true;
            }
            case java_lang_Class$getComponentType: {
                Class<?> javaClass = argAsClass(args, 0);
                if (javaClass != null) {
                    setObjectConstant(javaClass.getComponentType());
                }
                return true;
            }

            // java.lang.Math
            case java_lang_Math$abs:   setDoubleConstant(Math.abs(argAsDouble(args, 0))); return true;
            case java_lang_Math$sin:   setDoubleConstant(Math.sin(argAsDouble(args, 0))); return true;
            case java_lang_Math$cos:   setDoubleConstant(Math.cos(argAsDouble(args, 0))); return true;
            case java_lang_Math$tan:   setDoubleConstant(Math.tan(argAsDouble(args, 0))); return true;
            case java_lang_Math$atan2: setDoubleConstant(Math.atan2(argAsDouble(args, 0), argAsDouble(args, 2))); return true;
            case java_lang_Math$sqrt:  setDoubleConstant(Math.sqrt(argAsDouble(args, 0))); return true;
            case java_lang_Math$log:   setDoubleConstant(Math.log(argAsDouble(args, 0))); return true;
            case java_lang_Math$log10: setDoubleConstant(Math.log10(argAsDouble(args, 0))); return true;
            case java_lang_Math$pow:   setDoubleConstant(Math.pow(argAsDouble(args, 0), argAsDouble(args, 2))); return true;
            case java_lang_Math$exp:   setDoubleConstant(Math.exp(argAsDouble(args, 0))); return true;
            case java_lang_Math$min:   setIntConstant(Math.min(argAsInt(args, 0), argAsInt(args, 1))); return true;
            case java_lang_Math$max:   setIntConstant(Math.max(argAsInt(args, 0), argAsInt(args, 1))); return true;

            // java.lang.Float
            case java_lang_Float$floatToRawIntBits: setIntConstant(Float.floatToRawIntBits(argAsFloat(args, 0))); return true;
            case java_lang_Float$floatToIntBits: setIntConstant(Float.floatToIntBits(argAsFloat(args, 0))); return true;
            case java_lang_Float$intBitsToFloat: setFloatConstant(Float.intBitsToFloat(argAsInt(args, 0))); return true;

            // java.lang.Double
            case java_lang_Double$doubleToRawLongBits: setLongConstant(Double.doubleToRawLongBits(argAsDouble(args, 0))); return true;
            case java_lang_Double$doubleToLongBits: setLongConstant(Double.doubleToLongBits(argAsDouble(args, 0))); return true;
            case java_lang_Double$longBitsToDouble: setDoubleConstant(Double.longBitsToDouble(argAsLong(args, 0))); return true;

            // java.lang.Integer
            case java_lang_Integer$bitCount: setIntConstant(Integer.bitCount(argAsInt(args, 0))); return true;
            case java_lang_Integer$reverseBytes: setIntConstant(Integer.reverseBytes(argAsInt(args, 0))); return true;

            // java.lang.Long
            case java_lang_Long$bitCount: setIntConstant(Long.bitCount(argAsLong(args, 0))); return true;
            case java_lang_Long$reverseBytes: setLongConstant(Long.reverseBytes(argAsLong(args, 0))); return true;

            // java.lang.System
            case java_lang_System$identityHashCode: {
                Object object = argAsObject(args, 0);
                if (object != null) {
                    setIntConstant(System.identityHashCode(object));
                }
                return true;
            }

            // java.lang.reflect.Array
            case java_lang_reflect_Array$getLength: {
                Object object = argAsObject(args, 0);
                if (object != null && object.getClass().isArray()) {
                    setIntConstant(Array.getLength(object));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitIf(If i) {
        if (i.x().isConstant()) {
            // move constant to the right
            i.swapOperands();
        }
        Value l = i.x();
        Value r = i.y();

        if (l == r && !l.kind.isFloatOrDouble()) {
            // this is a comparison of x op x
            // No opt for float/double due to NaN case
            reduceReflexiveIf(i);
            return;
        }

        CiKind rt = r.kind;

        Condition ifcond = i.condition();
        if (l.isConstant() && r.isConstant()) {
            // fold comparisons between constants and convert to Goto
            Boolean result = ifcond.foldCondition(l.asConstant(), r.asConstant(), runtime);
            if (result != null) {
                setCanonical(new Goto(i.successor(result), i.stateAfter(), i.isSafepoint()));
                return;
            }
        }

        if (r.isConstant() && rt.isInt()) {
            // attempt to reduce comparisons with constant on right side
            if (l instanceof CompareOp) {
                // attempt to reduce If ((a cmp b) op const)
                reduceIfCompareOpConstant(i, r.asConstant());
            }
        }

        if (isNullConstant(r) && l.isNonNull()) {
            // this is a comparison of null against something that is not null
            if (ifcond == Condition.EQ) {
                // new() == null is always false
                setCanonical(new Goto(i.falseSuccessor(), i.stateAfter(), i.isSafepoint()));
            } else if (ifcond == Condition.NE) {
                // new() != null is always true
                setCanonical(new Goto(i.trueSuccessor(), i.stateAfter(), i.isSafepoint()));
            }
        }
    }

    private boolean isNullConstant(Value r) {
        return r.isConstant() && r.asConstant().isNull();
    }

    private void reduceIfCompareOpConstant(If i, CiConstant rtc) {
        Condition ifcond = i.condition();
        Value l = i.x();
        CompareOp cmp = (CompareOp) l;
        boolean unorderedIsLess = cmp.opcode == FCMPL || cmp.opcode == DCMPL;
        BlockBegin lssSucc = i.successor(ifcond.foldCondition(CiConstant.forInt(-1), rtc, runtime));
        BlockBegin eqlSucc = i.successor(ifcond.foldCondition(CiConstant.forInt(0), rtc, runtime));
        BlockBegin gtrSucc = i.successor(ifcond.foldCondition(CiConstant.forInt(1), rtc, runtime));
        BlockBegin nanSucc = unorderedIsLess ? lssSucc : gtrSucc;
        // Note: At this point all successors (lssSucc, eqlSucc, gtrSucc, nanSucc) are
        //       equal to x->tsux() or x->fsux(). Furthermore, nanSucc equals either
        //       lssSucc or gtrSucc.
        if (lssSucc == eqlSucc && eqlSucc == gtrSucc) {
            // all successors identical => simplify to: Goto
            setCanonical(new Goto(lssSucc, i.stateAfter(), i.isSafepoint()));
        } else {
            // two successors differ and two successors are the same => simplify to: If (x cmp y)
            // determine new condition & successors
            Condition cond;
            BlockBegin tsux;
            BlockBegin fsux;
            if (lssSucc == eqlSucc) {
                cond = Condition.LE;
                tsux = lssSucc;
                fsux = gtrSucc;
            } else if (lssSucc == gtrSucc) {
                cond = Condition.NE;
                tsux = lssSucc;
                fsux = eqlSucc;
            } else if (eqlSucc == gtrSucc) {
                cond = Condition.GE;
                tsux = eqlSucc;
                fsux = lssSucc;
            } else {
                throw Util.shouldNotReachHere();
            }
            // TODO: the state after is incorrect here: should it be preserved from the original if?
            If canon = new If(cmp.x(), cond, nanSucc == tsux, cmp.y(), tsux, fsux, cmp.stateBefore(), i.isSafepoint());
            if (cmp.x() == cmp.y()) {
                // re-canonicalize the new if
                visitIf(canon);
            } else {
                setCanonical(canon);
            }
        }
    }

    private void reduceReflexiveIf(If i) {
        // simplify reflexive comparisons If (x op x) to Goto
        BlockBegin succ;
        switch (i.condition()) {
            case EQ: succ = i.successor(true); break;
            case NE: succ = i.successor(false); break;
            case LT: succ = i.successor(false); break;
            case LE: succ = i.successor(true); break;
            case GT: succ = i.successor(false); break;
            case GE: succ = i.successor(true); break;
            default:
                throw Util.shouldNotReachHere();
        }
        setCanonical(new Goto(succ, i.stateAfter(), i.isSafepoint()));
    }

    @Override
    public void visitTableSwitch(TableSwitch i) {
        Value v = i.value();
        if (v.isConstant()) {
            // fold a table switch over a constant by replacing it with a goto
            int val = v.asConstant().asInt();
            BlockBegin succ = i.defaultSuccessor();
            if (val >= i.lowKey() && val <= i.highKey()) {
                succ = i.successors().get(val - i.lowKey());
            }
            setCanonical(new Goto(succ, i.stateAfter(), i.isSafepoint()));
            return;
        }
        int max = i.numberOfCases();
        if (max == 0) {
            // replace switch with Goto
            if (v instanceof Instruction) {
                // TODO: is it necessary to add the instruction explicitly?
                addInstr((Instruction) v);
            }
            setCanonical(new Goto(i.defaultSuccessor(), i.stateAfter(), i.isSafepoint()));
            return;
        }
        if (max == 1) {
            // replace switch with If
            Constant key = intInstr(i.lowKey());
            If newIf = new If(v, Condition.EQ, false, key, i.successors().get(0), i.defaultSuccessor(), null, i.isSafepoint());
            newIf.setStateAfter(i.stateAfter());
            setCanonical(newIf);
        }
    }

    @Override
    public void visitLookupSwitch(LookupSwitch i) {
        Value v = i.value();
        if (v.isConstant()) {
            // fold a lookup switch over a constant by replacing it with a goto
            int val = v.asConstant().asInt();
            BlockBegin succ = i.defaultSuccessor();
            for (int j = 0; j < i.numberOfCases(); j++) {
                if (val == i.keyAt(j)) {
                    succ = i.successors().get(j);
                    break;
                }
            }
            setCanonical(new Goto(succ, i.stateAfter(), i.isSafepoint()));
            return;
        }
        int max = i.numberOfCases();
        if (max == 0) {
            // replace switch with Goto
            if (v instanceof Instruction) {
                addInstr((Instruction) v); // the value expression may produce side effects
            }
            setCanonical(new Goto(i.defaultSuccessor(), i.stateAfter(), i.isSafepoint()));
            return;
        }
        if (max == 1) {
            // replace switch with If
            Constant key = intInstr(i.keyAt(0));
            If newIf = new If(v, Condition.EQ, false, key, i.successors().get(0), i.defaultSuccessor(), null, i.isSafepoint());
            newIf.setStateAfter(i.stateAfter());
            setCanonical(newIf);
        }
    }

    private void visitUnsafeRawOp(UnsafeRawOp i) {
        if (i.base() instanceof ArithmeticOp) {
            // if the base is an arithmetic op, try reducing
            ArithmeticOp root = (ArithmeticOp) i.base();
            if (!root.isLive() && root.opcode == LADD) {
                // match unsafe(x + y) if the x + y is not pinned
                // try reducing (x + y) and (y + x)
                Value y = root.y();
                Value x = root.x();
                if (reduceRawOp(i, x, y) || reduceRawOp(i, y, x)) {
                    // the operation was reduced
                    return;
                }
                if (y instanceof Convert) {
                    // match unsafe(x + (long) y)
                    Convert convert = (Convert) y;
                    if (convert.opcode == I2L && convert.value().kind.isInt()) {
                        // the conversion is redundant
                        setUnsafeRawOp(i, x, convert.value(), 0);
                    }
                }
            }
        }
    }

    private boolean reduceRawOp(UnsafeRawOp i, Value base, Value index) {
        if (index instanceof Convert) {
            // skip any conversion operations
            index = ((Convert) index).value();
        }
        if (index instanceof ShiftOp) {
            // try to match the index as a shift by a constant
            ShiftOp shift = (ShiftOp) index;
            CiKind st = shift.y().kind;
            if (shift.y().isConstant() && st.isInt()) {
                int val = shift.y().asConstant().asInt();
                switch (val) {
                    case 0: // fall through
                    case 1: // fall through
                    case 2: // fall through
                    case 3: return setUnsafeRawOp(i, base, shift.x(), val);
                }
            }
        }
        if (index instanceof ArithmeticOp) {
            // try to match the index as a multiply by a constant
            // note that this case will not happen if C1XOptions.CanonicalizeMultipliesToShifts is true
            ArithmeticOp arith = (ArithmeticOp) index;
            CiKind st = arith.y().kind;
            if (arith.opcode == IMUL && arith.y().isConstant() && st.isInt()) {
                int val = arith.y().asConstant().asInt();
                switch (val) {
                    case 1: return setUnsafeRawOp(i, base, arith.x(), 0);
                    case 2: return setUnsafeRawOp(i, base, arith.x(), 1);
                    case 4: return setUnsafeRawOp(i, base, arith.x(), 2);
                    case 8: return setUnsafeRawOp(i, base, arith.x(), 3);
                }
            }
        }

        return false;
    }

    private boolean setUnsafeRawOp(UnsafeRawOp i, Value base, Value index, int log2scale) {
        i.setBase(base);
        i.setIndex(index);
        i.setLog2Scale(log2scale);
        return true;
    }

    @Override
    public void visitUnsafeGetRaw(UnsafeGetRaw i) {
        if (C1XOptions.CanonicalizeUnsafes) {
            visitUnsafeRawOp(i);
        }
    }

    @Override
    public void visitUnsafePutRaw(UnsafePutRaw i) {
        if (C1XOptions.CanonicalizeUnsafes) {
            visitUnsafeRawOp(i);
        }
    }

    private Object argAsObject(Value[] args, int index) {
        CiConstant c = args[index].asConstant();
        if (c != null) {
            return runtime.asJavaObject(c);
        }
        return null;
    }

    private Class<?> argAsClass(Value[] args, int index) {
        CiConstant c = args[index].asConstant();
        if (c != null) {
            return runtime.asJavaClass(c);
        }
        return null;
    }

    private double argAsDouble(Value[] args, int index) {
        return args[index].asConstant().asDouble();
    }

    private float argAsFloat(Value[] args, int index) {
        return args[index].asConstant().asFloat();
    }

    private int argAsInt(Value[] args, int index) {
        return args[index].asConstant().asInt();
    }

    private long argAsLong(Value[] args, int index) {
        return args[index].asConstant().asLong();
    }

    public static CiConstant foldInvocation(RiRuntime runtime, RiMethod method, final Value[] args) {
        CiConstant result = runtime.invoke(method, new CiMethodInvokeArguments() {
            int i;
            @Override
            public CiConstant nextArg() {
                if (i >= args.length) {
                    return null;
                }
                Value arg = args[i++];
                if (arg == null) {
                    if (i >= args.length) {
                        return null;
                    }
                    arg = args[i++];
                    assert arg != null;
                }
                return arg.isConstant() ? arg.asConstant() : null;
            }
        });
        if (result != null) {
            C1XMetrics.MethodsFolded++;
        }
        return result;
    }

    @Override
    public void visitTypeEqualityCheck(TypeEqualityCheck i) {
        if (i.condition == Condition.EQ && i.left() == i.right()) {
            setCanonical(null);
        }
    }

    @Override
    public void visitBoundsCheck(BoundsCheck b) {
        Value index = b.index();
        Value length = b.length();

        if (index.isConstant() && length.isConstant()) {
            int i = index.asConstant().asInt();
            int l = index.asConstant().asInt();
            Condition c = b.condition;
            if (c.check(i, l)) {
                setCanonical(null);
            }
        }
    }

    private RiType getTypeOf(Value x) {
        if (x.isConstant()) {
            return runtime.getTypeOf(x.asConstant());
        }
        return null;
    }
}
