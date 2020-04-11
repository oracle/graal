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
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
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

    private BytecodeLocation cloneSite;
    private TypeFlow<?> input;

    /** The allocation context for the generated clone object. Null if this is not a clone. */
    protected final AnalysisContext allocationContext;

    public CloneTypeFlow(ValueNode node, AnalysisType inputType, BytecodeLocation cloneLabel, TypeFlow<?> input) {
        super(node.getNodeSourcePosition(), inputType);
        this.cloneSite = cloneLabel;
        this.allocationContext = null;
        this.input = input;
    }

    public CloneTypeFlow(BigBang bb, CloneTypeFlow original, MethodFlowsGraph methodFlows, AnalysisContext allocationContext) {
        super(original, methodFlows);
        this.cloneSite = original.cloneSite;
        this.allocationContext = allocationContext;
        this.input = methodFlows.lookupCloneOf(bb, original.input);
    }

    @Override
    public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        AnalysisContext enclosingContext = methodFlows.context();
        AnalysisContext allocContext = bb.contextPolicy().allocationContext(enclosingContext, PointstoOptions.MaxHeapContextDepth.getValue(bb.getOptions()));

        return new CloneTypeFlow(bb, this, methodFlows, allocContext);
    }

    @Override
    public void onObservedUpdate(BigBang bb) {
        /* Only a clone should be updated */
        assert this.isClone() && context != null;

        /* The input state has changed, clone its objects. */
        TypeState inputState = input.getState();
        TypeState currentState = getState();
        TypeState resultState;

        /*
         * The clone type flow creates a clone of it's input state. It intercepts the input state
         * and it creates a new state with the same types. The object abstractions can be the same
         * or different depending on the analysis policy. IF new heap objects are created they
         * encapsulate the location of the cloning. From the point of view of the analysis a clone
         * flow is a source.
         */

        if (inputState.isEmpty() || inputState.isNull()) {
            /* Nothing to be cloned if the input state is not a concrete type state. */
            resultState = inputState.forNonNull(bb);
        } else {
            resultState = inputState.typesStream()
                            .filter(t -> !currentState.containsType(t))
                            .map(type -> TypeState.forClone(bb, cloneSite, type, allocationContext))
                            .reduce(TypeState.forEmpty(), (s1, s2) -> TypeState.forUnion(bb, s1, s2));

            assert !resultState.canBeNull();
        }

        /* Update the clone flow state. */
        addState(bb, resultState);
    }

    @Override
    public void update(BigBang bb) {

        assert this.isClone();

        TypeState inputState = input.getState();
        TypeState cloneState = this.getState();

        for (AnalysisType type : inputState.types()) {
            if (type.isArray()) {
                if (bb.analysisPolicy().aliasArrayTypeFlows()) {
                    /* All arrays are aliased, no need to model the array clone operation. */
                    continue;
                }

                /* The object array clones must also get the elements flows of the originals. */
                for (AnalysisObject originalObject : inputState.objects(type)) {
                    if (originalObject.isPrimitiveArray() || originalObject.isEmptyObjectArrayConstant(bb)) {
                        /* Nothing to read from a primitive array or an empty array constant. */
                        continue;
                    }
                    ArrayElementsTypeFlow originalObjectElementsFlow = originalObject.getArrayElementsFlow(bb, false);

                    for (AnalysisObject cloneObject : cloneState.objects(type)) {
                        if (cloneObject.isPrimitiveArray() || cloneObject.isEmptyObjectArrayConstant(bb)) {
                            /* Cannot write to a primitive array or an empty array constant. */
                            continue;
                        }
                        ArrayElementsTypeFlow cloneObjectElementsFlow = cloneObject.getArrayElementsFlow(bb, true);
                        originalObjectElementsFlow.addUse(bb, cloneObjectElementsFlow);
                    }
                }
            } else {

                /* The object clones must get field flows of the originals. */
                for (AnalysisObject originalObject : inputState.objects(type)) {
                    /* Link all the field flows of the original to the clone. */
                    for (AnalysisField field : type.getInstanceFields(true)) {
                        FieldTypeFlow originalObjectFieldFlow = originalObject.getInstanceFieldFlow(bb, this.method(), field, false);

                        for (AnalysisObject cloneObject : cloneState.objects(type)) {
                            FieldTypeFlow cloneObjectFieldFlow = cloneObject.getInstanceFieldFlow(bb, this.method(), field, true);
                            originalObjectFieldFlow.addUse(bb, cloneObjectFieldFlow);
                        }
                    }
                }
            }
        }

        /* Element flows of array clones (if any) have been updated, update the uses. */
        super.update(bb);
    }

    @Override
    public void onObservedSaturated(BigBang bb, TypeFlow<?> observed) {
        assert this.isClone();
        /* When the input flow saturates start observing the flow of the declared type. */
        replaceObservedWith(bb, declaredType);
    }

    @Override
    public void setObserved(TypeFlow<?> newInputFlow) {
        this.input = newInputFlow;
    }

    public BytecodeLocation getCloneSite() {
        return cloneSite;
    }

    @Override
    public String toString() {
        return "Clone<" + super.toString() + ">";
    }

}
