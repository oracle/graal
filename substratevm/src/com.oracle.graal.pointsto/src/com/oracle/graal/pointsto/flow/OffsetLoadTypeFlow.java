/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.svm.util.UnsafePartitionKind;

import jdk.vm.ci.code.BytecodePosition;

/**
 * The abstract class for offset load flows (i.e. indexed loads, unsafe loads at offset, java read
 * loads).
 */
public abstract class OffsetLoadTypeFlow extends TypeFlow<BytecodePosition> {

    /*
     * The type of the receiver object of the offset load operation. Can be approximated by Object
     * or Object[] when it cannot be inferred from stamps.
     */
    private final AnalysisType objectType;

    /** The type flow of the receiver object of the load operation. */
    protected TypeFlow<?> objectFlow;

    public OffsetLoadTypeFlow(BytecodePosition loadLocation, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow) {
        super(loadLocation, componentType);
        this.objectType = objectType;
        this.objectFlow = objectFlow;
    }

    public OffsetLoadTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, OffsetLoadTypeFlow original) {
        super(original, methodFlows);
        this.objectType = original.objectType;
        this.objectFlow = methodFlows.lookupCloneOf(bb, original.objectFlow);
    }

    @Override
    public void setObserved(TypeFlow<?> newObjectFlow) {
        this.objectFlow = newObjectFlow;
    }

    @Override
    public abstract void onObservedUpdate(PointsToAnalysis bb);

    @Override
    public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
        if (!isSaturated()) {
            /*
             * When the receiver flow saturates start observing the flow of the object type, unless
             * the load is already saturated.
             */
            replaceObservedWith(bb, objectType);
        }
    }

    @Override
    protected void onSaturated() {
        /* Deregister the load as an observer of the receiver. */
        objectFlow.removeObserver(this);
    }

    @Override
    public TypeFlow<?> receiver() {
        return objectFlow;
    }

    public TypeState getObjectState() {
        return objectFlow.getState();
    }

    /**
     * Implements the type flow of an indexed load operation. The type state of an indexed load flow
     * reflects the elements type state of the receiver array objects that triggered the load
     * operation, i.e., the loaded values. The declared type of a load operation is the component
     * type of the receiver array, if known statically, null otherwise.
     */
    public static class LoadIndexedTypeFlow extends OffsetLoadTypeFlow {

        public LoadIndexedTypeFlow(BytecodePosition loadLocation, AnalysisType arrayType, TypeFlow<?> arrayFlow) {
            super(loadLocation, arrayType, arrayType.getComponentType(), arrayFlow);
        }

        public LoadIndexedTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, LoadIndexedTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public LoadIndexedTypeFlow copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
            return new LoadIndexedTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void onObservedUpdate(PointsToAnalysis bb) {
            TypeState arrayState = getObjectState();
            for (AnalysisObject object : arrayState.objects(bb)) {
                if (bb.analysisPolicy().relaxTypeFlowConstraints() && !object.type().isArray()) {
                    /* Ignore non-array types when type flow constraints are relaxed. */
                    continue;
                }
                if (object.isPrimitiveArray() || object.isEmptyObjectArrayConstant(bb)) {
                    /* Nothing to read from a primitive array or an empty array constant. */
                    continue;
                }

                /* Add the indexed load flow as a use to the elements flow. */
                TypeFlow<?> elementsFlow = object.getArrayElementsFlow(bb, false);
                elementsFlow.addUse(bb, this);
            }
        }

        @Override
        public TypeState filter(PointsToAnalysis bb, TypeState newState) {
            /*
             * If the type flow constraints are relaxed filter the loaded value using the array's
             * declared type.
             */
            return declaredTypeFilter(bb, newState);
        }

        @Override
        public String toString() {
            return "LoadIndexedTypeFlow<" + getState() + ">";
        }

    }

    public abstract static class AbstractUnsafeLoadTypeFlow extends OffsetLoadTypeFlow {

        AbstractUnsafeLoadTypeFlow(BytecodePosition loadLocation, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow) {
            super(loadLocation, objectType, filterUncheckedInterface(componentType), objectFlow);
        }

        AbstractUnsafeLoadTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, AbstractUnsafeLoadTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public void initFlow(PointsToAnalysis bb) {
            assert !bb.analysisPolicy().isContextSensitiveAnalysis() || this.isClone() : this;
            /*
             * Register the unsafe load. It will be force-updated when new unsafe fields are
             * registered.
             */
            bb.registerUnsafeLoad(this);
            forceUpdate(bb);
        }

        @Override
        public boolean needsInitialization() {
            return true;
        }

        public void forceUpdate(PointsToAnalysis bb) {
            /*
             * Unsafe load type flow models unsafe reads from both instance and static fields. From
             * an analysis stand point for static fields the base doesn't matter. An unsafe load can
             * read from any of the static fields marked for unsafe access.
             */
            for (AnalysisField field : bb.getUniverse().getUnsafeAccessedStaticFields()) {
                /*
                 * Primitive type states are not propagated through unsafe loads/stores. Instead,
                 * both primitive fields that are unsafe written and all unsafe loads for primitives
                 * are pre-saturated.
                 */
                if (field.getStorageKind().isObject()) {
                    field.getStaticFieldFlow().addUse(bb, this);
                }
            }
        }

        protected void processField(PointsToAnalysis bb, AnalysisObject object, AnalysisField field) {
            if (field.getStorageKind().isObject()) {
                TypeFlow<?> fieldFlow = object.getInstanceFieldFlow(bb, objectFlow, source, field, false);
                fieldFlow.addUse(bb, this);
            }
        }
    }

    public static class UnsafeLoadTypeFlow extends AbstractUnsafeLoadTypeFlow {

        public UnsafeLoadTypeFlow(BytecodePosition loadLocation, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> arrayFlow) {
            super(loadLocation, objectType, componentType, arrayFlow);
        }

        private UnsafeLoadTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, UnsafeLoadTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public AbstractUnsafeLoadTypeFlow copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
            return new UnsafeLoadTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void onObservedUpdate(PointsToAnalysis bb) {
            TypeState objectState = getObjectState();
            for (AnalysisObject object : objectState.objects(bb)) {
                AnalysisType objectType = object.type();
                if (objectType.isArray()) {
                    if (object.isPrimitiveArray() || object.isEmptyObjectArrayConstant(bb)) {
                        /* Nothing to read from a primitive array or an empty array constant. */
                        continue;
                    }

                    /*
                     * Add the array elements value flow as a use to the unsafe load flow. Unsafe
                     * write to an array is essentially an index store. We intercept unsafe loads
                     * from arrays in MethodTypeFlow but some arrays have only Object type at that
                     * point.
                     */
                    TypeFlow<?> elementsFlow = object.getArrayElementsFlow(bb, false);
                    elementsFlow.addUse(bb, this);
                } else {
                    for (AnalysisField field : objectType.unsafeAccessedFields()) {
                        assert field != null;
                        processField(bb, object, field);
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "UnsafeLoadTypeFlow<" + getState() + ">";
        }
    }

    public static class UnsafePartitionLoadTypeFlow extends AbstractUnsafeLoadTypeFlow {

        protected final UnsafePartitionKind partitionKind;
        protected final AnalysisType partitionType;

        public UnsafePartitionLoadTypeFlow(BytecodePosition loadLocation, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> arrayFlow,
                        UnsafePartitionKind partitionKind, AnalysisType partitionType) {
            super(loadLocation, objectType, componentType, arrayFlow);
            this.partitionKind = partitionKind;
            this.partitionType = partitionType;
        }

        private UnsafePartitionLoadTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, UnsafePartitionLoadTypeFlow original) {
            super(bb, methodFlows, original);
            this.partitionKind = original.partitionKind;
            this.partitionType = original.partitionType;
        }

        @Override
        public AbstractUnsafeLoadTypeFlow copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
            return new UnsafePartitionLoadTypeFlow(bb, methodFlows, this);
        }

        @Override
        public TypeState filter(PointsToAnalysis bb, TypeState update) {
            if (partitionType.equals(bb.getObjectType())) {
                /* No need to filter. */
                return update;
            } else {
                /* Filter the incoming state with the partition type. */
                return TypeState.forIntersection(bb, update, partitionType.getAssignableTypes(true));
            }
        }

        @Override
        public void onObservedUpdate(PointsToAnalysis bb) {
            TypeState objectState = getObjectState();

            for (AnalysisObject object : objectState.objects(bb)) {
                AnalysisType objectType = object.type();
                assert !objectType.isArray() : objectType;

                for (AnalysisField field : objectType.unsafeAccessedFields(partitionKind)) {
                    processField(bb, object, field);
                }
            }
        }

        @Override
        public String toString() {
            return "UnsafePartitionLoadTypeFlow<" + getState() + "> : " + partitionKind;
        }
    }
}
