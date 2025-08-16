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
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

public final class DynamicNewInstanceTypeFlow extends TypeFlow<BytecodePosition> {
    /** The new type provider. */
    private TypeFlow<?> newTypeFlow;

    /**
     * The allocation context for the generated dynamic object. Null if this is not a clone.
     */
    private final AnalysisContext allocationContext;

    public DynamicNewInstanceTypeFlow(BytecodePosition location, TypeFlow<?> newTypeFlow, AnalysisType type) {
        super(location, type);
        this.allocationContext = null;
        this.newTypeFlow = newTypeFlow;

        /*
         * The original dynamic new instance cannot be linked to the type, even using the
         * non-state-transfering method, because whenever the type is updated,which happens whenever
         * a subtype of it is discovered, would also update the dynamic new instance. We only want
         * that update in the clone.
         */
    }

    private DynamicNewInstanceTypeFlow(PointsToAnalysis bb, DynamicNewInstanceTypeFlow original, MethodFlowsGraph methodFlows, AnalysisContext allocationContext) {
        super(original, methodFlows);
        this.allocationContext = allocationContext;
        this.newTypeFlow = methodFlows.lookupCloneOf(bb, original.newTypeFlow);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        AnalysisContext allocContext = bb.analysisPolicy().allocationContext(bb, methodFlows);
        return new DynamicNewInstanceTypeFlow(bb, this, methodFlows, allocContext);
    }

    @Override
    public void initFlow(PointsToAnalysis bb) {
        assert !bb.usePredicates() || newTypeFlow.getPredicate() != null || MethodFlowsGraph.nonMethodFlow(newTypeFlow) || newTypeFlow.isFlowEnabled() : "Missing predicate for the flow " +
                        newTypeFlow + ", which is input for " + this;
        newTypeFlow.addObserver(bb, this);
    }

    @Override
    public boolean needsInitialization() {
        return true;
    }

    @Override
    protected void onFlowEnabled(PointsToAnalysis bb) {
        if (newTypeFlow.isFlowEnabled()) {
            bb.postTask(() -> onObservedUpdate(bb));
        }
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        if (!isFlowEnabled()) {
            return;
        }
        /* The state of the new type provider has changed. */
        TypeState newTypeState = newTypeFlow.getState();
        TypeState updateState = bb.analysisPolicy().dynamicNewInstanceState(bb, getState(), newTypeState, source, allocationContext);
        addState(bb, updateState);
    }

    public AnalysisContext allocationContext() {
        return allocationContext;
    }

    @Override
    public void setObserved(TypeFlow<?> declaredTypeFlow) {
        this.newTypeFlow = declaredTypeFlow;
    }

    @Override
    public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
        if (bb.isClosed(declaredType)) {
            /* When the new-type flow saturates start observing the flow of the declared type. */
            replaceObservedWith(bb, declaredType);
        } else {
            /* Propagate the saturation stamp through the dynamic new instance flow. */
            onSaturated(bb);
        }
    }

    @Override
    public boolean canSaturate(PointsToAnalysis bb) {
        /* Dynamic new instance of closed types doesn't saturate, it tracks all input types. */
        return !bb.isClosed(declaredType);
    }

    @Override
    public String toString() {
        return "DynamicNewInstanceFlow<" + getStateDescription() + ">";
    }
}
