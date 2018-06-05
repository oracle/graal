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

import org.graalvm.compiler.nodes.java.LoadFieldNode;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.typestate.TypeState;

/**
 * Implements a field load operation type flow.
 */
public abstract class LoadFieldTypeFlow extends TypeFlow<LoadFieldNode> {

    /** The field that this flow loads from. */
    protected final AnalysisField field;

    public LoadFieldTypeFlow(LoadFieldNode node) {
        super(node, null);
        this.field = (AnalysisField) node.field();
    }

    public LoadFieldTypeFlow(MethodFlowsGraph methodFlows, LoadFieldTypeFlow original) {
        super(original, methodFlows);
        this.field = original.field;
    }

    public AnalysisField field() {
        return field;
    }

    public static class LoadStaticFieldTypeFlow extends LoadFieldTypeFlow {

        private final FieldTypeFlow fieldFlow;

        LoadStaticFieldTypeFlow(LoadFieldNode node, FieldTypeFlow fieldFlow) {
            super(node);
            this.fieldFlow = fieldFlow;

            /*
             * The original static load cannot be added as a use to the static field, even using the
             * non-state-transfering method, because whenever the field is updated would also update
             * the load. We only want that update in the clone.
             */
        }

        LoadStaticFieldTypeFlow(MethodFlowsGraph methodFlows, LoadStaticFieldTypeFlow original) {
            super(methodFlows, original);
            fieldFlow = original.fieldFlow;
        }

        @Override
        public TypeFlow<LoadFieldNode> copy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new LoadStaticFieldTypeFlow(methodFlows, this);
        }

        @Override
        public void initClone(BigBang bb) {
            fieldFlow.addUse(bb, this);
        }

        @Override
        public boolean addState(BigBang bb, TypeState add) {
            assert this.isClone();
            return super.addState(bb, add);
        }

        @Override
        public String toString() {
            return "LoadStaticFieldTypeFlow<" + getState() + ">";
        }

    }

    /**
     * The type state of the load instance field flow reflects the type state of the field on the
     * receiver objects that triggered this load operation.
     */
    public static class LoadInstanceFieldTypeFlow extends LoadFieldTypeFlow {

        /** The flow of the receiver object. */
        private final TypeFlow<?> objectFlow;

        LoadInstanceFieldTypeFlow(LoadFieldNode node, TypeFlow<?> objectFlow) {
            super(node);
            this.objectFlow = objectFlow;
        }

        LoadInstanceFieldTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, LoadInstanceFieldTypeFlow original) {
            super(methodFlows, original);
            this.objectFlow = methodFlows.lookupCloneOf(bb, original.objectFlow);
        }

        @Override
        public LoadFieldTypeFlow copy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new LoadInstanceFieldTypeFlow(bb, methodFlows, this);
        }

        /** Return the receiver object flow. */
        @Override
        public TypeFlow<?> receiver() {
            return objectFlow;
        }

        /** Return the state of the receiver object. */
        public TypeState getObjectState() {
            return objectFlow.getState();
        }

        @Override
        public boolean addState(BigBang bb, TypeState add) {
            /* Only a clone should be updated */
            assert this.isClone();
            return super.addState(bb, add);
        }

        @Override
        public void onObservedUpdate(BigBang bb) {
            /* Only a clone should be updated */
            assert this.isClone();

            /*
             * The state of the receiver object of the load operation has changed. Link the new heap
             * sensitive field flows.
             */

            TypeState objectState = objectFlow.getState();
            if (objectState.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Field loading from UnknownTypeState objects. Field: " + field);
                return;
            }

            /* Iterate over the receiver objects. */
            for (AnalysisObject object : objectState.objects()) {
                /* Get the field flow corresponding to the receiver object. */
                FieldTypeFlow fieldFlow = object.getInstanceFieldFlow(bb, field, false);

                /* Add the load field flow as a use to the heap sensitive field flow. */
                fieldFlow.addUse(bb, this);
            }
        }

        @Override
        public String toString() {
            return "LoadInstanceFieldTypeFlow<" + getState() + ">";
        }
    }
}
