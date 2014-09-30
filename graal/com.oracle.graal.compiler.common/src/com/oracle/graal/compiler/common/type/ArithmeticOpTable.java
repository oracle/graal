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

import com.oracle.graal.api.meta.*;

/**
 * Information about arithmetic operations.
 */
public final class ArithmeticOpTable {

    protected UnaryOp neg;
    protected BinaryOp add;
    protected BinaryOp sub;

    protected BinaryOp mul;
    protected BinaryOp div;
    protected BinaryOp rem;

    protected UnaryOp not;
    protected BinaryOp and;
    protected BinaryOp or;
    protected BinaryOp xor;

    public static ArithmeticOpTable forStamp(Stamp s) {
        if (s instanceof ArithmeticStamp) {
            return ((ArithmeticStamp) s).getOps();
        } else {
            return EMPTY;
        }
    }

    public static final ArithmeticOpTable EMPTY = new ArithmeticOpTable();

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

    /**
     * Describes a unary arithmetic operation.
     */
    public abstract static class UnaryOp {

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
    public abstract static class BinaryOp {

        private final boolean associative;
        private final boolean commutative;

        protected BinaryOp(boolean associative, boolean commutative) {
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
}