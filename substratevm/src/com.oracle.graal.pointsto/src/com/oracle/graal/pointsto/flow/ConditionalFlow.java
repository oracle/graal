/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.pointsto.typestate.PrimitiveTypeState;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;

/**
 * This flow represents a ternary operator. The type state is computed based on the type state of
 * the condition and the left and right inputs.
 * <p>
 * The condition used by conditional flow should always produce a primitive or empty type state.
 * <p>
 * The conditional flow is connected via observer edge with the condition and via use edges with
 * true and false values, but this decision is quite arbitrary, its implementation can check the
 * values of all its three inputs anyways.
 */
public class ConditionalFlow extends TypeFlow<BytecodePosition> {

    private final TypeFlow<?> condition;
    private final TypeFlow<?> trueValue;
    private final TypeFlow<?> falseValue;

    public ConditionalFlow(BytecodePosition source, AnalysisType declaredType, TypeFlow<?> condition, TypeFlow<?> trueValue, TypeFlow<?> falseValue) {
        super(source, declaredType);
        assert condition.isPrimitiveFlow() : condition;
        this.condition = condition;
        this.trueValue = trueValue;
        this.falseValue = falseValue;
    }

    private ConditionalFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, ConditionalFlow original) {
        super(original, methodFlows);
        this.condition = methodFlows.lookupCloneOf(bb, original.condition);
        this.trueValue = methodFlows.lookupCloneOf(bb, original.trueValue);
        this.falseValue = methodFlows.lookupCloneOf(bb, original.falseValue);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new ConditionalFlow(bb, methodFlows, this);
    }

    @Override
    protected void onInputSaturated(PointsToAnalysis bb, TypeFlow<?> input) {
        /*
         * GR-58387: This could stop the propagation of saturation, so it can be problematic for
         * open-world analysis.
         */
        addState(bb, TypeState.forEmpty());
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        addState(bb, TypeState.forEmpty());
    }

    @Override
    public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
        addState(bb, TypeState.forEmpty());
    }

    /**
     * Depending on the state of the condition, return none, one, or both of the true/false inputs.
     */
    @Override
    protected TypeState processInputState(PointsToAnalysis bb, TypeState newState) {
        TypeState trueState = trueValue.getOutputState(bb);
        TypeState falseState = falseValue.getOutputState(bb);
        if (condition.isSaturated()) {
            /* If the condition is already saturated, merge both inputs. */
            return TypeState.forUnion(bb, trueState, falseState);
        }
        var conditionValue = condition.getOutputState(bb);
        if (conditionValue.isEmpty()) {
            /* If the condition is empty, do not produce any output yet. */
            return TypeState.forEmpty();
        }
        if (conditionValue instanceof PrimitiveTypeState prim) {
            var canBeTrue = prim.canBeTrue();
            var canBeFalse = prim.canBeFalse();
            if (canBeTrue && !canBeFalse) {
                return trueState;
            } else if (!canBeTrue && canBeFalse) {
                return falseState;
            }
            return TypeState.forUnion(bb, trueState, falseState);
        }
        throw AnalysisError.shouldNotReachHere("Unexpected non-primitive type state of the condition: " + conditionValue + ", at flow " + this);
    }
}
