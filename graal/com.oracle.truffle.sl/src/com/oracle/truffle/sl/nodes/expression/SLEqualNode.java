/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.expression;

import java.math.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.sl.nodes.*;
import com.oracle.truffle.sl.runtime.*;

/**
 * The {@code ==} operator of SL is defined on all types. Therefore, we need a
 * {@link #equal(Object, Object) generic implementation} that can handle all possible types. But
 * since {@code ==} can only return {@code true} when the type of the left and right operand are the
 * same, the specializations already cover all possible cases that can return {@code true} and the
 * generic case is trivial.
 * <p>
 * Note that we do not need the analogous {@code =!} operator, because we can just
 * {@link SLLogicalNotNode negate} the {@code ==} operator.
 */
@NodeInfo(shortName = "==")
public abstract class SLEqualNode extends SLBinaryNode {

    public SLEqualNode(SourceSection src) {
        super(src);
    }

    @Specialization
    protected boolean equal(long left, long right) {
        return left == right;
    }

    @Specialization
    protected boolean equal(BigInteger left, BigInteger right) {
        return left.equals(right);
    }

    @Specialization
    protected boolean equal(boolean left, boolean right) {
        return left == right;
    }

    @Specialization
    protected boolean equal(String left, String right) {
        return left.equals(right);
    }

    @Specialization
    protected boolean equal(SLFunction left, SLFunction right) {
        /*
         * Our function registry maintains one canonical SLFunction object per function name, so we
         * do not need equals().
         */
        return left == right;
    }

    @Specialization
    protected boolean equal(SLNull left, SLNull right) {
        /* There is only the singleton instance of SLNull, so we do not need equals(). */
        return left == right;
    }

    /**
     * The {@link Fallback} annotation informs the Truffle DSL that this method should be executed
     * when no {@link Specialization specialized method} matches. The operand types must be
     * {@link Object}.
     */
    @Fallback
    protected boolean equal(Object left, Object right) {
        /*
         * We covered all the cases that can return true in specializations. If we compare two
         * values with different types, no specialization matches and we end up here.
         */
        assert !left.equals(right);
        return false;
    }
}
