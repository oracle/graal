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
public class PrimitiveFilterTypeFlow extends TypeFlow<BytecodePosition> {

    private final TypeFlow<?> left;
    private final TypeFlow<?> right;
    private final PrimitiveComparison comparison;
    private final boolean isUnsigned;

    public PrimitiveFilterTypeFlow(BytecodePosition position, AnalysisType declaredType, TypeFlow<?> left, TypeFlow<?> right, PrimitiveComparison comparison, boolean isUnsigned) {
        super(position, declaredType);
        this.left = left;
        this.right = right;
        this.comparison = comparison;
        this.isUnsigned = isUnsigned;
    }

    private PrimitiveFilterTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, PrimitiveFilterTypeFlow original) {
        super(original, methodFlows);
        this.left = methodFlows.lookupCloneOf(bb, original.left);
        this.right = methodFlows.lookupCloneOf(bb, original.right);
        this.comparison = original.comparison;
        this.isUnsigned = original.isUnsigned;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new PrimitiveFilterTypeFlow(bb, methodFlows, this);
    }

    @Override
    public boolean addState(PointsToAnalysis bb, TypeState add) {
        return super.addState(bb, eval(bb));
    }

    @Override
    protected void onInputSaturated(PointsToAnalysis bb, TypeFlow<?> input) {
        /*
         * If an input saturated, it does not mean that the condition has to always saturate as
         * well, e.g. Any == 5 still returns 5.
         */
        super.addState(bb, eval(bb));
    }

    /**
     * Filters the type state of left using condition and right.
     */
    private TypeState eval(PointsToAnalysis bb) {
        var leftState = left.getOutputState(bb);
        var rightState = right.getOutputState(bb);
        assert leftState.isPrimitive() || leftState.isEmpty() : left;
        assert rightState.isPrimitive() || rightState.isEmpty() : right;
        return TypeState.filter(leftState, comparison, rightState, isUnsigned);
    }

    public PrimitiveComparison getComparison() {
        return comparison;
    }

    public TypeFlow<?> getLeft() {
        return left;
    }
}
