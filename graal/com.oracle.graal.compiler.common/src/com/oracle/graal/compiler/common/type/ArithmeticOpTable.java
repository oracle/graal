/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.type;

import static com.oracle.graal.compiler.common.type.ArithmeticOpTable.IntegerConvertOp.*;

import java.util.stream.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;

/**
 * Information about arithmetic operations.
 */
public final class ArithmeticOpTable {

    private final UnaryOp neg;
    private final BinaryOp add;
    private final BinaryOp sub;

    private final BinaryOp mul;
    private final BinaryOp div;
    private final BinaryOp rem;

    private final UnaryOp not;
    private final BinaryOp and;
    private final BinaryOp or;
    private final BinaryOp xor;

    private final IntegerConvertOp zeroExtend;
    private final IntegerConvertOp signExtend;
    private final IntegerConvertOp narrow;

    private final FloatConvertOp[] floatConvert;

    public static ArithmeticOpTable forStamp(Stamp s) {
        if (s instanceof ArithmeticStamp) {
            return ((ArithmeticStamp) s).getOps();
        } else {
            return EMPTY;
        }
    }

    public static final ArithmeticOpTable EMPTY = create();

    public static ArithmeticOpTable create(Op... ops) {
        UnaryOp neg = null;
        BinaryOp add = null;
        BinaryOp sub = null;

        BinaryOp mul = null;
        BinaryOp div = null;
        BinaryOp rem = null;

        UnaryOp not = null;
        BinaryOp and = null;
        BinaryOp or = null;
        BinaryOp xor = null;

        IntegerConvertOp zeroExtend = null;
        IntegerConvertOp signExtend = null;
        IntegerConvertOp narrow = null;

        FloatConvertOp[] floatConvert = new FloatConvertOp[FloatConvert.values().length];

        for (Op op : ops) {
            if (op instanceof BinaryOp) {
                BinaryOp binary = (BinaryOp) op;
                switch (binary.getOperator()) {
                    case '+':
                        assert add == null;
                        add = binary;
                        break;
                    case '-':
                        assert sub == null;
                        sub = binary;
                        break;
                    case '*':
                        assert mul == null;
                        mul = binary;
                        break;
                    case '/':
                        assert div == null;
                        div = binary;
                        break;
                    case '%':
                        assert rem == null;
                        rem = binary;
                        break;
                    case '&':
                        assert and == null;
                        and = binary;
                        break;
                    case '|':
                        assert or == null;
                        or = binary;
                        break;
                    case '^':
                        assert xor == null;
                        xor = binary;
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere("unknown binary operator " + binary.getOperator());
                }
            } else if (op instanceof IntegerConvertOp) {
                IntegerConvertOp convert = (IntegerConvertOp) op;
                switch (convert.getOperator()) {
                    case IntegerConvertOp.ZERO_EXTEND:
                        assert zeroExtend == null;
                        zeroExtend = convert;
                        break;
                    case IntegerConvertOp.SIGN_EXTEND:
                        assert signExtend == null;
                        signExtend = convert;
                        break;
                    case IntegerConvertOp.NARROW:
                        assert narrow == null;
                        narrow = convert;
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere("unknown integer conversion operator " + convert.getOperator());
                }
            } else if (op instanceof FloatConvertOp) {
                FloatConvertOp convert = (FloatConvertOp) op;
                int idx = convert.getFloatConvert().ordinal();
                assert floatConvert[idx] == null;
                floatConvert[idx] = convert;
            } else if (op instanceof UnaryOp) {
                UnaryOp unary = (UnaryOp) op;
                switch (unary.getOperator()) {
                    case '-':
                        assert neg == null;
                        neg = unary;
                        break;
                    case '~':
                        assert not == null;
                        not = unary;
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere("unknown unary operator " + unary.getOperator());
                }
            } else {
                throw GraalInternalError.shouldNotReachHere("unknown Op subclass " + op);
            }
        }

        return new ArithmeticOpTable(neg, add, sub, mul, div, rem, not, and, or, xor, zeroExtend, signExtend, narrow, floatConvert);
    }

    public Stream<Op> getAllOps() {
        Stream<Op> ops = Stream.of(neg, add, sub, mul, div, rem, not, and, or, xor, zeroExtend, signExtend, narrow);
        Stream<Op> floatOps = Stream.of(floatConvert);
        return Stream.concat(ops, floatOps).filter(op -> op != null);
    }

    private ArithmeticOpTable(UnaryOp neg, BinaryOp add, BinaryOp sub, BinaryOp mul, BinaryOp div, BinaryOp rem, UnaryOp not, BinaryOp and, BinaryOp or, BinaryOp xor, IntegerConvertOp zeroExtend,
                    IntegerConvertOp signExtend, IntegerConvertOp narrow, FloatConvertOp[] floatConvert) {
        this.neg = neg;
        this.add = add;
        this.sub = sub;
        this.mul = mul;
        this.div = div;
        this.rem = rem;
        this.not = not;
        this.and = and;
        this.or = or;
        this.xor = xor;
        this.zeroExtend = zeroExtend;
        this.signExtend = signExtend;
        this.narrow = narrow;
        this.floatConvert = floatConvert;
    }

    public UnaryOp getUnaryOp(UnaryOp op) {
        switch (op.getOperator()) {
            case '-':
                return getNeg();
            case '~':
                return getNot();
            default:
                return getFloatConvertOp((FloatConvertOp) op);
        }
    }

    public BinaryOp getBinaryOp(BinaryOp op) {
        switch (op.getOperator()) {
            case '+':
                return getAdd();
            case '-':
                return getSub();
            case '*':
                return getMul();
            case '/':
                return getDiv();
            case '%':
                return getRem();
            case '&':
                return getAnd();
            case '|':
                return getOr();
            case '^':
                return getXor();
            default:
                throw GraalInternalError.shouldNotReachHere("unknown binary operator " + op);
        }
    }

    public IntegerConvertOp getIntegerConvertOp(IntegerConvertOp op) {
        switch (op.getOperator()) {
            case ZERO_EXTEND:
                return getZeroExtend();
            case SIGN_EXTEND:
                return getSignExtend();
            case NARROW:
                return getNarrow();
            default:
                throw GraalInternalError.shouldNotReachHere("unknown integer convert operator " + op);
        }
    }

    public FloatConvertOp getFloatConvertOp(FloatConvertOp op) {
        return getFloatConvert(op.getFloatConvert());
    }

    /**
     * Describes the unary negation operation.
     */
    public final UnaryOp getNeg() {
        return neg;
    }

    /**
     * Describes the addition operation.
     */
    public final BinaryOp getAdd() {
        return add;
    }

    /**
     * Describes the subtraction operation.
     */
    public final BinaryOp getSub() {
        return sub;
    }

    /**
     * Describes the multiplication operation.
     */
    public final BinaryOp getMul() {
        return mul;
    }

    /**
     * Describes the division operation.
     */
    public final BinaryOp getDiv() {
        return div;
    }

    /**
     * Describes the remainder operation.
     */
    public final BinaryOp getRem() {
        return rem;
    }

    /**
     * Describes the bitwise not operation.
     */
    public final UnaryOp getNot() {
        return not;
    }

    /**
     * Describes the bitwise and operation.
     */
    public final BinaryOp getAnd() {
        return and;
    }

    /**
     * Describes the bitwise or operation.
     */
    public final BinaryOp getOr() {
        return or;
    }

    /**
     * Describes the bitwise xor operation.
     */
    public final BinaryOp getXor() {
        return xor;
    }

    public IntegerConvertOp getZeroExtend() {
        return zeroExtend;
    }

    public IntegerConvertOp getSignExtend() {
        return signExtend;
    }

    public IntegerConvertOp getNarrow() {
        return narrow;
    }

    public FloatConvertOp getFloatConvert(FloatConvert op) {
        return floatConvert[op.ordinal()];
    }

    public abstract static class Op {

        private final char operator;

        protected Op(char operator) {
            this.operator = operator;
        }

        public char getOperator() {
            return operator;
        }

        @Override
        public String toString() {
            return Character.toString(operator);
        }
    }

    /**
     * Describes a unary arithmetic operation.
     */
    public abstract static class UnaryOp extends Op {

        protected UnaryOp(char operation) {
            super(operation);
        }

        /**
         * Apply the operation to a {@link Constant}.
         */
        public abstract Constant foldConstant(Constant value);

        /**
         * Apply the operation to a {@link Stamp}.
         */
        public abstract Stamp foldStamp(Stamp stamp);
    }

    /**
     * Describes a binary arithmetic operation.
     */
    public abstract static class BinaryOp extends Op {

        private final boolean associative;
        private final boolean commutative;

        protected BinaryOp(char operation, boolean associative, boolean commutative) {
            super(operation);
            this.associative = associative;
            this.commutative = commutative;
        }

        /**
         * Apply the operation to two {@linkplain Constant Constants}.
         */
        public abstract Constant foldConstant(Constant a, Constant b);

        /**
         * Apply the operation to two {@linkplain Stamp Stamps}.
         */
        public abstract Stamp foldStamp(Stamp a, Stamp b);

        /**
         * Checks whether this operation is associative. An operation is associative when
         * {@code (a . b) . c == a . (b . c)} for all a, b, c. Note that you still have to be
         * careful with inverses. For example the integer subtraction operation will report
         * {@code true} here, since you can still reassociate as long as the correct negations are
         * inserted.
         */
        public final boolean isAssociative() {
            return associative;
        }

        /**
         * Checks whether this operation is commutative. An operation is commutative when
         * {@code a . b == b . a} for all a, b.
         */
        public final boolean isCommutative() {
            return commutative;
        }

        /**
         * Check whether a {@link Constant} is a neutral element for this operation. A neutral
         * element is any element {@code n} where {@code a . n == a} for all a.
         *
         * @param n the {@link Constant} that should be tested
         * @return true iff for all {@code a}: {@code a . n == a}
         */
        public boolean isNeutral(Constant n) {
            return false;
        }

        /**
         * Check whether this operation has a zero {@code z == a . a} for each a. Examples of
         * operations having such an element are subtraction and exclusive-or. Note that this may be
         * different from the numbers tested by {@link #isNeutral}.
         *
         * @param stamp a {@link Stamp}
         * @return a unique {@code z} such that {@code z == a . a} for each {@code a} in
         *         {@code stamp} if it exists, otherwise {@code null}
         */
        public Constant getZero(Stamp stamp) {
            return null;
        }
    }

    public abstract static class FloatConvertOp extends UnaryOp {

        private final FloatConvert op;

        protected FloatConvertOp(FloatConvert op) {
            super('\0');
            this.op = op;
        }

        public FloatConvert getFloatConvert() {
            return op;
        }

        @Override
        public String toString() {
            return op.name();
        }
    }

    public abstract static class IntegerConvertOp extends Op {

        public static final char ZERO_EXTEND = 'z';
        public static final char SIGN_EXTEND = 's';
        public static final char NARROW = 'n';

        protected IntegerConvertOp(char op) {
            super(op);
        }

        public abstract Constant foldConstant(int inputBits, int resultBits, Constant value);

        public abstract Stamp foldStamp(int resultBits, Stamp stamp);

        @Override
        public String toString() {
            switch (getOperator()) {
                case ZERO_EXTEND:
                    return "ZeroExtend";
                case SIGN_EXTEND:
                    return "SignExtend";
                case NARROW:
                    return "Narrow";
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        }
    }
}
