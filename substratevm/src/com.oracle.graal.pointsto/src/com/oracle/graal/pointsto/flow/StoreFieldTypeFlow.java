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
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Implements a field store operation type flow.
 */
public abstract class StoreFieldTypeFlow extends AccessFieldTypeFlow {

    protected StoreFieldTypeFlow(BytecodePosition storeLocation, AnalysisField field) {
        super(storeLocation, field);
    }

    protected StoreFieldTypeFlow(StoreFieldTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows);
    }

    @Override
    public TypeState filter(PointsToAnalysis bb, TypeState newState) {
        /*
         * If the type flow constraints are relaxed filter the stored value using the field's
         * declared type.
         */
        return declaredTypeFilter(bb, newState);
    }

    public static class StoreStaticFieldTypeFlow extends StoreFieldTypeFlow {

        /** The flow of the static field. */
        private final FieldTypeFlow fieldFlow;

        /** The flow of the input value. */
        private final TypeFlow<?> valueFlow;

        StoreStaticFieldTypeFlow(BytecodePosition storeLocation, AnalysisField field, TypeFlow<?> valueFlow, FieldTypeFlow fieldFlow) {
            super(storeLocation, field);
            this.valueFlow = valueFlow;
            this.fieldFlow = fieldFlow;
        }

        StoreStaticFieldTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, StoreStaticFieldTypeFlow original) {
            super(original, methodFlows);
            this.valueFlow = methodFlows.lookupCloneOf(bb, original.valueFlow);
            this.fieldFlow = original.fieldFlow;
        }

        @Override
        public StoreFieldTypeFlow copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
            /* A store to a static field type flow is not context dependent, but it's value is. */
            return new StoreStaticFieldTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void initClone(PointsToAnalysis bb) {
            this.addUse(bb, fieldFlow);
        }

        @Override
        public String toString() {
            return "StoreStaticFieldTypeFlow<" + getState() + ">";
        }

    }

    /**
     * The state of the StoreFieldTypeFlow reflects the state of the stored value. The
     * StoreFieldTypeFlow is an observer of the receiver flow, i.e. flow modeling the receiver
     * object of the store operation..
     *
     * Every time the state of the receiver flow changes the corresponding field flows are added as
     * uses to the store field flow. Thus the stored value is propagated to the store field flow
     * into the field flows.
     */
    public static class StoreInstanceFieldTypeFlow extends StoreFieldTypeFlow {

        /** The flow of the stored value. */
        private final TypeFlow<?> valueFlow;

        /** The flow of the store operation receiver object. */
        private TypeFlow<?> objectFlow;

        private boolean isContextInsensitive;

        public StoreInstanceFieldTypeFlow(BytecodePosition storeLocation, AnalysisField field, TypeFlow<?> objectFlow) {
            this(storeLocation, field, null, objectFlow);
        }

        public StoreInstanceFieldTypeFlow(BytecodePosition storeLocation, AnalysisField field, TypeFlow<?> valueFlow, TypeFlow<?> objectFlow) {
            super(storeLocation, field);
            this.valueFlow = valueFlow;
            this.objectFlow = objectFlow;
        }

        StoreInstanceFieldTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, StoreInstanceFieldTypeFlow original) {
            super(original, methodFlows);
            this.valueFlow = original.valueFlow != null ? methodFlows.lookupCloneOf(bb, original.valueFlow) : null;
            this.objectFlow = methodFlows.lookupCloneOf(bb, original.objectFlow);
        }

        public void markAsContextInsensitive() {
            isContextInsensitive = true;
        }

        @Override
        public boolean isContextInsensitive() {
            return isContextInsensitive;
        }

        @Override
        public StoreInstanceFieldTypeFlow copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
            return new StoreInstanceFieldTypeFlow(bb, methodFlows, this);
        }

        @Override
        public TypeFlow<?> receiver() {
            return objectFlow;
        }

        @Override
        public void setObserved(TypeFlow<?> newObjectFlow) {
            this.objectFlow = newObjectFlow;
        }

        @Override
        public void onObservedUpdate(PointsToAnalysis bb) {
            /* Only a clone or a context insensitive flow should be updated */
            assert this.isClone() || this.isContextInsensitive();

            /*
             * The state of the receiver object has changed. Add an use link between the value flow
             * and the field flows of the new objects.
             */
            TypeState objectState = objectFlow.getState();
            objectState = filterObjectState(bb, objectState);
            /* Iterate over the receiver objects. */
            for (AnalysisObject receiver : objectState.objects()) {
                /* Get the field flow corresponding to the receiver object. */
                FieldTypeFlow fieldFlow = receiver.getInstanceFieldFlow(bb, objectFlow, source, field, true);
                /* Register the field flow as a use, if not already registered. */
                this.addUse(bb, fieldFlow);
            }
        }

        @Override
        public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
            assert this.isClone() && !this.isContextInsensitive();
            /*
             * When receiver flow saturates swap in the saturated store type flow. When the store
             * itself saturates it propagates the saturation state to the uses/observers and unlinks
             * them, but it still observes the receiver state to notify no-yet-reachable fields of
             * saturation.
             */

            /* Deregister the store as an observer of the receiver. */
            objectFlow.removeObserver(this);

            /* Deregister the store as a use of the value flow. */
            valueFlow.removeUse(this);

            /* Link the saturated store. */
            StoreFieldTypeFlow contextInsensitiveStore = ((PointsToAnalysisField) field).initAndGetContextInsensitiveStore(bb, source);
            /*
             * Link the value flow to the saturated store. The receiver is already set in the
             * saturated store.
             */
            valueFlow.addUse(bb, contextInsensitiveStore);
        }

        @Override
        public String toString() {
            return "StoreInstanceFieldTypeFlow<" + getState() + ">";
        }
    }
}
