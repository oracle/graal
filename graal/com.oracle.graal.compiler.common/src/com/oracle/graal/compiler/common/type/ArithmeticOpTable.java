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

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Add;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.And;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Div;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Mul;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Or;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Rem;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Sub;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.BinaryOp.Xor;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.IntegerConvertOp.Narrow;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.IntegerConvertOp.SignExtend;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.IntegerConvertOp.ZeroExtend;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.UnaryOp.Neg;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable.UnaryOp.Not;

/**
 * Information about arithmetic operations.
 */
public final class ArithmeticOpTable {

    private final UnaryOp<Neg> neg;
    private final BinaryOp<Add> add;
    private final BinaryOp<Sub> sub;

    private final BinaryOp<Mul> mul;
    private final BinaryOp<Div> div;
    private final BinaryOp<Rem> rem;

    private final UnaryOp<Not> not;
    private final BinaryOp<And> and;
    private final BinaryOp<Or> or;
    private final BinaryOp<Xor> xor;

    private final IntegerConvertOp<ZeroExtend> zeroExtend;
    private final IntegerConvertOp<SignExtend> signExtend;
    private final IntegerConvertOp<Narrow> narrow;

    private final FloatConvertOp[] floatConvert;

    public static ArithmeticOpTable forStamp(Stamp s) {
        if (s instanceof ArithmeticStamp) {
            return ((ArithmeticStamp) s).getOps();
        } else {
            return EMPTY;
        }
    }

    public static final ArithmeticOpTable EMPTY = new ArithmeticOpTable(null, null, null, null, null, null, null, null, null, null, null, null, null);

    public ArithmeticOpTable(UnaryOp<Neg> neg, BinaryOp<Add> add, BinaryOp<Sub> sub, BinaryOp<Mul> mul, BinaryOp<Div> div, BinaryOp<Rem> rem, UnaryOp<Not> not, BinaryOp<And> and, BinaryOp<Or> or,
                    BinaryOp<Xor> xor, IntegerConvertOp<ZeroExtend> zeroExtend, IntegerConvertOp<SignExtend> signExtend, IntegerConvertOp<Narrow> narrow, FloatConvertOp... floatConvert) {
        this(neg, add, sub, mul, div, rem, not, and, or, xor, zeroExtend, signExtend, narrow, Stream.of(floatConvert));
    }

    public interface ArithmeticOpWrapper {

        <OP> UnaryOp<OP> wrapUnaryOp(UnaryOp<OP> op);

        <OP> BinaryOp<OP> wrapBinaryOp(BinaryOp<OP> op);

        <OP> IntegerConvertOp<OP> wrapIntegerConvertOp(IntegerConvertOp<OP> op);

        FloatConvertOp wrapFloatConvertOp(FloatConvertOp op);
    }

    private static <T> T wrapIfNonNull(Function<T, T> wrapper, T obj) {
        if (obj == null) {
            return null;
        } else {
            return wrapper.apply(obj);
        }
    }

    public static ArithmeticOpTable wrap(ArithmeticOpWrapper wrapper, ArithmeticOpTable inner) {
        UnaryOp<Neg> neg = wrapIfNonNull(wrapper::wrapUnaryOp, inner.getNeg());
        BinaryOp<Add> add = wrapIfNonNull(wrapper::wrapBinaryOp, inner.getAdd());
        BinaryOp<Sub> sub = wrapIfNonNull(wrapper::wrapBinaryOp, inner.getSub());

        BinaryOp<Mul> mul = wrapIfNonNull(wrapper::wrapBinaryOp, inner.getMul());
        BinaryOp<Div> div = wrapIfNonNull(wrapper::wrapBinaryOp, inner.getDiv());
        BinaryOp<Rem> rem = wrapIfNonNull(wrapper::wrapBinaryOp, inner.getRem());

        UnaryOp<Not> not = wrapIfNonNull(wrapper::wrapUnaryOp, inner.getNot());
        BinaryOp<And> and = wrapIfNonNull(wrapper::wrapBinaryOp, inner.getAnd());
        BinaryOp<Or> or = wrapIfNonNull(wrapper::wrapBinaryOp, inner.getOr());
        BinaryOp<Xor> xor = wrapIfNonNull(wrapper::wrapBinaryOp, inner.getXor());

        IntegerConvertOp<ZeroExtend> zeroExtend = wrapIfNonNull(wrapper::wrapIntegerConvertOp, inner.getZeroExtend());
        IntegerConvertOp<SignExtend> signExtend = wrapIfNonNull(wrapper::wrapIntegerConvertOp, inner.getSignExtend());
        IntegerConvertOp<Narrow> narrow = wrapIfNonNull(wrapper::wrapIntegerConvertOp, inner.getNarrow());

        Stream<FloatConvertOp> floatConvert = Stream.of(inner.floatConvert).filter(Objects::nonNull).map(wrapper::wrapFloatConvertOp);

        return new ArithmeticOpTable(neg, add, sub, mul, div, rem, not, and, or, xor, zeroExtend, signExtend, narrow, floatConvert);
    }

    private ArithmeticOpTable(UnaryOp<Neg> neg, BinaryOp<Add> add, BinaryOp<Sub> sub, BinaryOp<Mul> mul, BinaryOp<Div> div, BinaryOp<Rem> rem, UnaryOp<Not> not, BinaryOp<And> and, BinaryOp<Or> or,
                    BinaryOp<Xor> xor, IntegerConvertOp<ZeroExtend> zeroExtend, IntegerConvertOp<SignExtend> signExtend, IntegerConvertOp<Narrow> narrow, Stream<FloatConvertOp> floatConvert) {
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
        this.floatConvert = new FloatConvertOp[FloatConvert.values().length];
        floatConvert.forEach(op -> this.floatConvert[op.getFloatConvert().ordinal()] = op);
    }

    /**
     * Describes the unary negation operation.
     */
    public final UnaryOp<Neg> getNeg() {
        return neg;
    }

    /**
     * Describes the addition operation.
     */
    public final BinaryOp<Add> getAdd() {
        return add;
    }

    /**
     * Describes the subtraction operation.
     */
    public final BinaryOp<Sub> getSub() {
        return sub;
    }

    /**
     * Describes the multiplication operation.
     */
    public final BinaryOp<Mul> getMul() {
        return mul;
    }

    /**
     * Describes the division operation.
     */
    public final BinaryOp<Div> getDiv() {
        return div;
    }

    /**
     * Describes the remainder operation.
     */
    public final BinaryOp<Rem> getRem() {
        return rem;
    }

    /**
     * Describes the bitwise not operation.
     */
    public final UnaryOp<Not> getNot() {
        return not;
    }

    /**
     * Describes the bitwise and operation.
     */
    public final BinaryOp<And> getAnd() {
        return and;
    }

    /**
     * Describes the bitwise or operation.
     */
    public final BinaryOp<Or> getOr() {
        return or;
    }

    /**
     * Describes the bitwise xor operation.
     */
    public final BinaryOp<Xor> getXor() {
        return xor;
    }

    /**
     * Describes the zero extend conversion.
     */
    public IntegerConvertOp<ZeroExtend> getZeroExtend() {
        return zeroExtend;
    }

    /**
     * Describes the sign extend conversion.
     */
    public IntegerConvertOp<SignExtend> getSignExtend() {
        return signExtend;
    }

    /**
     * Describes the narrowing conversion.
     */
    public IntegerConvertOp<Narrow> getNarrow() {
        return narrow;
    }

    /**
     * Describes integer/float/double conversions.
     */
    public FloatConvertOp getFloatConvert(FloatConvert op) {
        return floatConvert[op.ordinal()];
    }

    public abstract static class Op {

        private final String operator;

        protected Op(String operator) {
            this.operator = operator;
        }

        @Override
        public String toString() {
            return operator;
        }
    }

    /**
     * Describes a unary arithmetic operation.
     */
    public abstract static class UnaryOp<T> extends Op {

        public abstract static class Neg extends UnaryOp<Neg> {

            protected Neg() {
                super("-");
            }
        }

        public abstract static class Not extends UnaryOp<Not> {

            protected Not() {
                super("~");
            }
        }

        protected UnaryOp(String operation) {
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
    public abstract static class BinaryOp<T> extends Op {

        public abstract static class Add extends BinaryOp<Add> {

            protected Add(boolean associative, boolean commutative) {
                super("+", associative, commutative);
            }
        }

        public abstract static class Sub extends BinaryOp<Sub> {

            protected Sub(boolean associative, boolean commutative) {
                super("-", associative, commutative);
            }
        }

        public abstract static class Mul extends BinaryOp<Mul> {

            protected Mul(boolean associative, boolean commutative) {
                super("*", associative, commutative);
            }
        }

        public abstract static class Div extends BinaryOp<Div> {

            protected Div(boolean associative, boolean commutative) {
                super("/", associative, commutative);
            }
        }

        public abstract static class Rem extends BinaryOp<Rem> {

            protected Rem(boolean associative, boolean commutative) {
                super("%", associative, commutative);
            }
        }

        public abstract static class And extends BinaryOp<And> {

            protected And(boolean associative, boolean commutative) {
                super("&", associative, commutative);
            }
        }

        public abstract static class Or extends BinaryOp<Or> {

            protected Or(boolean associative, boolean commutative) {
                super("|", associative, commutative);
            }
        }

        public abstract static class Xor extends BinaryOp<Xor> {

            protected Xor(boolean associative, boolean commutative) {
                super("^", associative, commutative);
            }
        }

        private final boolean associative;
        private final boolean commutative;

        protected BinaryOp(String operation, boolean associative, boolean commutative) {
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

    public abstract static class FloatConvertOp extends UnaryOp<FloatConvertOp> {

        private final FloatConvert op;

        protected FloatConvertOp(FloatConvert op) {
            super(op.name());
            this.op = op;
        }

        public FloatConvert getFloatConvert() {
            return op;
        }
    }

    public abstract static class IntegerConvertOp<T> extends Op {

        public abstract static class ZeroExtend extends IntegerConvertOp<ZeroExtend> {

            protected ZeroExtend() {
                super("ZeroExtend");
            }
        }

        public abstract static class SignExtend extends IntegerConvertOp<SignExtend> {

            protected SignExtend() {
                super("SignExtend");
            }
        }

        public abstract static class Narrow extends IntegerConvertOp<Narrow> {

            protected Narrow() {
                super("Narrow");
            }
        }

        protected IntegerConvertOp(String op) {
            super(op);
        }

        public abstract Constant foldConstant(int inputBits, int resultBits, Constant value);

        public abstract Stamp foldStamp(int inputBits, int resultBits, Stamp stamp);
    }
}
