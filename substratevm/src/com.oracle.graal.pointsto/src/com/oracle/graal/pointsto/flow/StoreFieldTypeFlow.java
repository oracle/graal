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

import org.graalvm.compiler.nodes.java.StoreFieldNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.typestate.TypeState;

/**
 * Implements a field store operation type flow.
 */
public abstract class StoreFieldTypeFlow extends AccessFieldTypeFlow {

    protected StoreFieldTypeFlow(StoreFieldNode node) {
        super(node);
    }

    protected StoreFieldTypeFlow(StoreFieldTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows);
    }

    @Override
    public TypeState filter(BigBang bb, TypeState newState) {
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

        StoreStaticFieldTypeFlow(StoreFieldNode node, TypeFlow<?> valueFlow, FieldTypeFlow fieldFlow) {
            super(node);
            this.valueFlow = valueFlow;
            this.fieldFlow = fieldFlow;
        }

        StoreStaticFieldTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, StoreStaticFieldTypeFlow original) {
            super(original, methodFlows);
            this.valueFlow = methodFlows.lookupCloneOf(bb, original.valueFlow);
            this.fieldFlow = original.fieldFlow;
        }

        @Override
        public StoreFieldTypeFlow copy(BigBang bb, MethodFlowsGraph methodFlows) {
            /* A store to a static field type flow is not context dependent, but it's value is. */
            return new StoreStaticFieldTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void initClone(BigBang bb) {
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

        StoreInstanceFieldTypeFlow(StoreFieldNode node, TypeFlow<?> valueFlow, TypeFlow<?> objectFlow) {
            super(node);
            this.valueFlow = valueFlow;
            this.objectFlow = objectFlow;
        }

        StoreInstanceFieldTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, StoreInstanceFieldTypeFlow original) {
            super(original, methodFlows);
            this.valueFlow = methodFlows.lookupCloneOf(bb, original.valueFlow);
            this.objectFlow = methodFlows.lookupCloneOf(bb, original.objectFlow);
        }

        @Override
        public StoreInstanceFieldTypeFlow copy(BigBang bb, MethodFlowsGraph methodFlows) {
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
        public void onObservedUpdate(BigBang bb) {
            /* Only a clone should be updated */
            assert this.isClone();

            /*
             * The state of the receiver object has changed. Add an use link between the value flow
             * and the field flows of the new objects.
             */
            TypeState objectState = objectFlow.getState();

            if (objectState.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Storing into UnknownTypeState objects. Field: " + field);
                return;
            }
            objectState = filterObjectState(bb, objectState);
            /* Iterate over the receiver objects. */
            for (AnalysisObject receiver : objectState.objects()) {
                /* Get the field flow corresponding to the receiver object. */
                FieldTypeFlow fieldFlow = receiver.getInstanceFieldFlow(bb, this.method(), field, true);
                /* Register the field flow as a use, if not already registered. */
                this.addUse(bb, fieldFlow);
            }
        }

        @Override
        public void onObservedSaturated(BigBang bb, TypeFlow<?> observed) {
            assert this.isClone();
            /* When receiver flow saturates start observing the flow of the field declaring type. */
            replaceObservedWith(bb, field.getDeclaringClass());
        }

        @Override
        public String toString() {
            return "StoreInstanceFieldTypeFlow<" + getState() + ">";
        }
    }
}
