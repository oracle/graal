/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.graal.pointsto.flow;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

/**
 * This flow represents a binary comparison of two values, <code>left</code> and <code>right</code>.
 * It filters the value of <code>left</code> using the <code>condition</code> and
 * <code>right</code>. Typically, two instances PrimitiveFilterTypeFlow are created for each
 * condition <code>x cmp y</code>. The first one filters <code>x</code> with respect to
 * <code>op</code> and <code>y</code>, and the second filters <code>y</code> with respect to
 * <code>op</code> and <code>x</code>.
 */
public abstract class PrimitiveFilterTypeFlow extends TypeFlow<BytecodePosition> {
    protected final TypeFlow<?> left;
    protected final PrimitiveComparison comparison;
    protected final boolean isUnsigned;

    private PrimitiveFilterTypeFlow(BytecodePosition position, AnalysisType declaredType, TypeFlow<?> left, PrimitiveComparison comparison, boolean isUnsigned) {
        super(position, declaredType);
        this.left = left;
        this.comparison = comparison;
        this.isUnsigned = isUnsigned;
    }

    @Override
    protected void onInputSaturated(PointsToAnalysis bb, TypeFlow<?> input) {
        /*
         * If an input saturated, it does not mean that the condition has to always saturate as
         * well, e.g. Any == 5 still returns 5.
         */
        addState(bb, TypeState.forEmpty());
    }

    public abstract TypeState getRightState(PointsToAnalysis bb);

    /**
     * Filters the type state of left using condition and right.
     */
    @Override
    protected TypeState processInputState(PointsToAnalysis bb, TypeState newState) {
        var leftState = left.getOutputState(bb);
        var rightState = getRightState(bb);
        assert leftState.isPrimitive() || leftState.isEmpty() : left;
        assert rightState.isPrimitive() || rightState.isEmpty() : this;
        return TypeState.filter(leftState, comparison, rightState, isUnsigned);
    }

    public PrimitiveComparison getComparison() {
        return comparison;
    }

    public TypeFlow<?> getLeft() {
        return left;
    }

    public static class ConstantFilter extends PrimitiveFilterTypeFlow {
        private final TypeState rightState;

        public ConstantFilter(BytecodePosition position, AnalysisType declaredType, TypeFlow<?> left, TypeState rightState, PrimitiveComparison comparison, boolean isUnsigned) {
            super(position, declaredType, left, comparison, isUnsigned);
            this.rightState = rightState;
        }

        @Override
        public TypeState getRightState(PointsToAnalysis bb) {
            return rightState;
        }
    }

    public static class VariableFilter extends PrimitiveFilterTypeFlow {
        private final TypeFlow<?> right;

        public VariableFilter(BytecodePosition position, AnalysisType declaredType, TypeFlow<?> left, TypeFlow<?> right, PrimitiveComparison comparison, boolean isUnsigned) {
            super(position, declaredType, left, comparison, isUnsigned);
            this.right = right;
        }

        @Override
        public TypeState getRightState(PointsToAnalysis bb) {
            return right.getOutputState(bb);
        }

    }
}
