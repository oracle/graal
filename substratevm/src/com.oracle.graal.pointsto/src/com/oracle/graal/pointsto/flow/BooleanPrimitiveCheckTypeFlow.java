/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This flow represents a primitive comparison using one of the {@link PrimitiveComparison}
 * operators. This flow is used by {@link ConditionalFlow}.
 */
public class BooleanPrimitiveCheckTypeFlow extends BooleanCheckTypeFlow {

    private final TypeFlow<?> left;
    private final TypeFlow<?> right;
    private final PrimitiveComparison comparison;
    private final boolean isUnsigned;

    public BooleanPrimitiveCheckTypeFlow(BytecodePosition position, AnalysisType declaredType, TypeFlow<?> left, TypeFlow<?> right, PrimitiveComparison comparison, boolean isUnsigned) {
        super(position, declaredType);
        this.left = left;
        this.right = right;
        this.comparison = comparison;
        this.isUnsigned = isUnsigned;
    }

    private BooleanPrimitiveCheckTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, BooleanPrimitiveCheckTypeFlow original) {
        super(original, methodFlows);
        this.left = methodFlows.lookupCloneOf(bb, original.left);
        this.right = methodFlows.lookupCloneOf(bb, original.right);
        this.comparison = original.comparison;
        this.isUnsigned = original.isUnsigned;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new BooleanPrimitiveCheckTypeFlow(bb, methodFlows, this);
    }

    @Override
    protected void onInputSaturated(PointsToAnalysis bb, TypeFlow<?> input) {
        /*
         * If an input saturated, it does not mean that the condition has to always saturate as
         * well, e.g. Any == {5} will return {5}.
         */
        addState(bb, TypeState.forEmpty());
    }

    /**
     * Computes new type state of this flow by comparing the type states of left and right.
     *
     * @return can be either empty, true, false, or any.
     */
    @Override
    protected TypeState processInputState(PointsToAnalysis bb, TypeState newState) {
        var leftState = left.getOutputState(bb);
        var rightState = right.getOutputState(bb);
        if (leftState.isEmpty() || rightState.isEmpty()) {
            return TypeState.forEmpty();
        }
        assert leftState.isPrimitive() : left;
        assert rightState.isPrimitive() : right;
        return convertToBoolean(bb, TypeState.filter(leftState, comparison, rightState, isUnsigned), TypeState.filter(leftState, comparison.negate(), rightState, isUnsigned));
    }
}
