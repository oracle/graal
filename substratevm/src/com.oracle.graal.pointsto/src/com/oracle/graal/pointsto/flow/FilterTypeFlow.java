/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * The points-to analysis model of an {@code InstanceOfNode}, which represents an instanceof test.
 */
public class FilterTypeFlow extends TypeFlow<BytecodePosition> {

    /**
     * If the filter is exact we only compare with the {@link #filterType}, not including its
     * instantiated sub-types, otherwise we compare with the entire type hierarchy rooted at
     * {@link #filterType}.
     */
    private final boolean isExact;
    /** True if the filter allows types assignable from the test type, false otherwise. */
    private final boolean isAssignable;
    /** True if the filter allows null, false otherwise. */
    private final boolean includeNull;
    /**
     * Track the type to filter against. This is needed since declaredType == filterType only when
     * isAssignable is set.
     */
    private final AnalysisType filterType;

    public FilterTypeFlow(BytecodePosition position, AnalysisType filterType, boolean isExact, boolean isAssignable, boolean includeNull) {
        super(position, isAssignable ? filterType : null);
        this.isExact = isExact;
        this.isAssignable = isAssignable;
        this.includeNull = includeNull;
        this.filterType = filterType;
    }

    public FilterTypeFlow(MethodFlowsGraph methodFlows, FilterTypeFlow original) {
        super(original, methodFlows);
        this.isExact = original.isExact;
        this.isAssignable = original.isAssignable;
        this.includeNull = original.includeNull;
        this.filterType = original.filterType;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new FilterTypeFlow(methodFlows, this);
    }

    @Override
    public TypeState filter(PointsToAnalysis bb, TypeState update) {
        TypeState result;
        if (isExact) {
            /*
             * If the filter is exact we only check the update state against the exact type, and not
             * its entire hierarchy.
             */
            if (isAssignable) {
                result = TypeState.forIntersection(bb, update, TypeState.forExactType(bb, filterType, includeNull));
            } else {
                result = TypeState.forSubtraction(bb, update, TypeState.forExactType(bb, filterType, !includeNull));
            }
        } else {
            /*
             * If the filter is not exact we check the update state against the entire hierarchy,
             * not only the exact type (AnalysisType.getTypeFlow() returns the type plus all its
             * instantiated sub-types).
             */
            if (isAssignable) {
                result = TypeState.forIntersection(bb, update, filterType.getAssignableTypes(includeNull));
            } else {
                result = TypeState.forSubtraction(bb, update, filterType.getAssignableTypes(!includeNull));
            }
        }
        return result;
    }

    @Override
    protected void onInputSaturated(PointsToAnalysis bb, TypeFlow<?> input) {
        if (isAssignable) {
            /* Swap this flow out at its uses/observers with its filter type flow. */
            setSaturated();
            swapOut(bb, filterType.getTypeFlow(bb, includeNull));
        } else {
            super.onInputSaturated(bb, input);
        }
    }

    @Override
    protected void notifyUseOfSaturation(PointsToAnalysis bb, TypeFlow<?> use) {
        if (isAssignable) {
            swapAtUse(bb, filterType.getTypeFlow(bb, includeNull), use);
        } else {
            super.notifyUseOfSaturation(bb, use);
        }
    }

    @Override
    protected void notifyObserverOfSaturation(PointsToAnalysis bb, TypeFlow<?> observer) {
        if (isAssignable) {
            swapAtObserver(bb, filterType.getTypeFlow(bb, includeNull), observer);
        } else {
            super.notifyObserverOfSaturation(bb, observer);
        }
    }

    public boolean isExact() {
        return isExact;
    }

    public boolean isAssignable() {
        return isAssignable;
    }

    public boolean includeNull() {
        return includeNull;
    }

    @Override
    public String toString() {
        return "FilterTypeFlow<" + filterType + ", isAssignable: " + isAssignable + ", includeNull: " + includeNull + ">";
    }
}
