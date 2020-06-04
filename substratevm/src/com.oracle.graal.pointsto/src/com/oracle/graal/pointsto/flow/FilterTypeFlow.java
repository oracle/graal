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

import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

/**
 * The points-to analysis model of an {@code InstanceOfNode}, which represents an instanceof test.
 */
public class FilterTypeFlow extends TypeFlow<BytecodePosition> {

    /**
     * If the filter is exact we only compare with the {@link #declaredType}, not including its
     * instantiated sub-types, otherwise we compare with the entire type hierarchy rooted at
     * {@link #declaredType}.
     */
    private final boolean isExact;
    /** True if the filter allows types assignable from the test type, false otherwise. */
    private final boolean isAssignable;
    /** True if the filter allows null, false otherwise. */
    private final boolean includeNull;

    public FilterTypeFlow(ValueNode node, AnalysisType filterType, boolean isAssignable, boolean includeNull) {
        this(node, filterType, false, isAssignable, includeNull);
    }

    public FilterTypeFlow(ValueNode node, AnalysisType filterType, boolean isExact, boolean isAssignable, boolean includeNull) {
        super(node.getNodeSourcePosition(), filterType);
        this.isExact = isExact;
        this.isAssignable = isAssignable;
        this.includeNull = includeNull;
    }

    public FilterTypeFlow(MethodFlowsGraph methodFlows, FilterTypeFlow original) {
        super(original, methodFlows);
        this.isExact = original.isExact;
        this.isAssignable = original.isAssignable;
        this.includeNull = original.includeNull;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return new FilterTypeFlow(methodFlows, this);
    }

    @Override
    public TypeState filter(BigBang bb, TypeState update) {
        if (update.isUnknown()) {
            // Filtering UnknownTypeState would otherwise return EmptyTypeState.
            bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Filter of UnknownTypeState objects.");
            return TypeState.forEmpty();
        }

        TypeState result;
        if (isExact) {
            /*
             * If the filter is exact we only check the update state against the exact type, and not
             * its entire hierarchy.
             */
            if (isAssignable) {
                result = TypeState.forIntersection(bb, update, TypeState.forExactType(bb, declaredType, includeNull));
            } else {
                result = TypeState.forSubtraction(bb, update, TypeState.forExactType(bb, declaredType, !includeNull));
            }
        } else {
            /*
             * If the filter is not exact we check the update state against the entire hierarchy,
             * not only the exact type (AnalysisType.getTypeFlow() returns the type plus all its
             * instantiated sub-types).
             */
            if (isAssignable) {
                result = TypeState.forIntersection(bb, update, declaredType.getTypeFlow(bb, includeNull).getState());
            } else {
                result = TypeState.forSubtraction(bb, update, declaredType.getTypeFlow(bb, !includeNull).getState());
            }
        }
        return result;
    }

    @Override
    protected void onInputSaturated(BigBang bb, TypeFlow<?> input) {
        if (isAssignable) {
            TypeFlow<?> sourceFlow = declaredType.getTypeFlow(bb, includeNull);

            /*
             * First mark this flow as saturated, then swap it out at its uses/observers with its
             * declared type flow. Marking this flow as saturated first is important: if there are
             * any uses or observers *in-flight*, i.e., not yet registered at this point, trying to
             * swap-out will have no effect on those. However, if this flow is already marked as
             * saturated when the use or observer *lands*, even if that happens while/after
             * swapping-out, then the corresponding use or observer will be notified of its input
             * saturation. Otherwise it may neighter get the saturation signal OR get swapped-out.
             * 
             * The downside in the later case is that the input/observer will lose the more precise
             * type information that swapping-out would have provided and will just use the more
             * conservative approximation, e.g., the target method declared type for invokes.
             */
            setSaturated();
            swapOut(bb, sourceFlow);
        } else {
            super.onInputSaturated(bb, input);
        }
    }

    @Override
    public boolean addState(BigBang bb, TypeState add) {
        assert this.isClone();
        return super.addState(bb, add);
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
        return "FilterTypeFlow<" + declaredType + ", isAssignable: " + isAssignable + ", includeNull: " + includeNull + ">";
    }
}
