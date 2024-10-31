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

/**
 * Implements a clone operation. This flow observes the state changes of the input flow, clones its
 * objects, then it updates its state. When the state is updated it also copies the corresponding
 * elements, i.e., array elements if the type is array or field values if the type is non-array,
 * into the clones.
 */
public class CloneTypeFlow extends TypeFlow<BytecodePosition> {

    private TypeFlow<?> input;
    /** The allocation context for the generated clone object. Null if this is not a clone. */
    protected final AnalysisContext allocationContext;

    public CloneTypeFlow(BytecodePosition cloneLocation, AnalysisType inputType, TypeFlow<?> input) {
        super(cloneLocation, inputType);
        this.allocationContext = null;
        this.input = input;
    }

    public CloneTypeFlow(PointsToAnalysis bb, CloneTypeFlow original, MethodFlowsGraph methodFlows, AnalysisContext allocationContext) {
        super(original, methodFlows);
        this.allocationContext = allocationContext;
        this.input = methodFlows.lookupCloneOf(bb, original.input);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        AnalysisContext allocContext = bb.analysisPolicy().allocationContext(bb, methodFlows);
        return new CloneTypeFlow(bb, this, methodFlows, allocContext);
    }

    @Override
    protected void onFlowEnabled(PointsToAnalysis bb) {
        if (input.isFlowEnabled()) {
            bb.postTask(() -> onObservedUpdate(bb));
        }
    }

    @Override
    public void onObservedUpdate(PointsToAnalysis bb) {
        if (!isFlowEnabled()) {
            return;
        }
        /* The input state has changed, clone its objects. */
        TypeState inputState = input.getState();

        /*
         * The clone type flow creates a clone of it's input state. It intercepts the input state
         * and it creates a new state with the same types. The object abstractions can be the same
         * or different depending on the analysis policy. If new heap objects are created they
         * encapsulate the location of the cloning. From the point of view of the analysis a clone
         * flow is a source.
         */
        TypeState resultState = bb.analysisPolicy().cloneState(bb, getState(), inputState, source, allocationContext);

        /* Update the clone flow state. */
        addState(bb, resultState);
    }

    @Override
    public void update(PointsToAnalysis bb) {
        /* Link the elements of the cloned objects to the elements of the source objects. */
        bb.analysisPolicy().linkClonedObjects(bb, input, this, source);

        /* Element flows of array clones (if any) have been updated, update the uses. */
        super.update(bb);
    }

    @Override
    public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
        if (bb.isClosed(declaredType)) {
            if (!isSaturated()) {
                /*
                 * When the input flow saturates start observing the flow of the declared type,
                 * unless the clone is already saturated.
                 */
                replaceObservedWith(bb, declaredType);
            }
        } else {
            /* Propagate the saturation stamp through the clone flow. */
            onSaturated(bb);
        }
    }

    @Override
    protected void onSaturated() {
        /* Deregister the clone as an observer of the input. */
        input.removeObserver(this);
    }

    @Override
    public void setObserved(TypeFlow<?> newInputFlow) {
        this.input = newInputFlow;
    }

    @Override
    public String toString() {
        return "Clone<" + super.toString() + ">";
    }

}
