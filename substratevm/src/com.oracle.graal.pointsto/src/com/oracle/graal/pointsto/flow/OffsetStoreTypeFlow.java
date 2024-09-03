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

import java.util.Collection;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.PointsToAnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

/**
 * The abstract class for offset store flows (i.e. indexed stores, unsafe stores at offset, java
 * writes).
 *
 * The type state of the offset store reflects the that of the stored value. The offset store
 * observes the type state changes in the receiver object, it implements TypeFlowObserver.When the
 * state of the receiver object changes the offset store type flow is notified and it propagates its
 * state, i.e., the one of the stored value, to the corresponding array elements flows (in case of
 * indexed stores) or field flows (in case of unsafe stores).
 */
public abstract class OffsetStoreTypeFlow extends TypeFlow<BytecodePosition> {

    /*
     * The type of the receiver object of the offset store operation. Can be approximated by Object
     * or Object[] when it cannot be infered from stamps.
     */
    protected final AnalysisType objectType;

    /** The flow of the input value. */
    protected final TypeFlow<?> valueFlow;
    /** The flow of the receiver object. */
    protected TypeFlow<?> objectFlow;

    boolean isContextInsensitive;

    public OffsetStoreTypeFlow(BytecodePosition storeLocation, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
        super(storeLocation, componentType);
        this.objectType = objectType;
        this.valueFlow = valueFlow;
        this.objectFlow = objectFlow;
    }

    public OffsetStoreTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, OffsetStoreTypeFlow original) {
        super(original, methodFlows);
        this.objectType = original.objectType;
        this.valueFlow = original.valueFlow != null ? methodFlows.lookupCloneOf(bb, original.valueFlow) : null;
        this.objectFlow = methodFlows.lookupCloneOf(bb, original.objectFlow);
    }

    @Override
    public TypeFlow<?> receiver() {
        return objectFlow;
    }

    @Override
    public abstract TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows);

    @Override
    public void setObserved(TypeFlow<?> newObjectFlow) {
        this.objectFlow = newObjectFlow;
    }

    @Override
    public abstract void onObservedUpdate(PointsToAnalysis bb);

    @Override
    public abstract void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed);

    public void markAsContextInsensitive() {
        isContextInsensitive = true;
    }

    @Override
    public boolean isContextInsensitive() {
        return isContextInsensitive;
    }

    /**
     * Implements the type flow of an indexed store operation. The type state of an indexed store
     * flow reflects the elements type state of the receiver array objects that triggered the store
     * operation, i.e., the stored values. The declared type of a store operation is the component
     * type of the receiver array, if known statically, null otherwise.
     */
    public static class StoreIndexedTypeFlow extends OffsetStoreTypeFlow {

        public StoreIndexedTypeFlow(BytecodePosition storeLocation, AnalysisType arrayType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(storeLocation, arrayType, arrayType.getComponentType(), objectFlow, valueFlow);
        }

        public StoreIndexedTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, StoreIndexedTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public StoreIndexedTypeFlow copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
            return new StoreIndexedTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void onObservedUpdate(PointsToAnalysis bb) {
            TypeState objectState = objectFlow.getState();
            /* Iterate over the receiver objects. */
            for (AnalysisObject object : objectState.objects(bb)) {
                if (bb.analysisPolicy().relaxTypeFlowConstraints() && !object.type().isArray()) {
                    /* Ignore non-array types when type flow constraints are relaxed. */
                    continue;
                }
                if (object.isPrimitiveArray() || object.isEmptyObjectArrayConstant(bb)) {
                    /* Cannot write to a primitive array or an empty array constant. */
                    continue;
                }

                /* Add the elements flow as a use to the value flow. */
                TypeFlow<?> elementsFlow = object.getArrayElementsFlow(bb, true);
                this.addUse(bb, elementsFlow);
            }
        }

        @Override
        public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
            /*
             * When receiver flow saturates swap in the saturated indexed store type flow. When the
             * store itself saturates it propagates the saturation state to the uses/observers and
             * unlinks them, but it still observes the receiver state to notify no-yet-reachable
             * field flows of saturation.
             */

            /* Deregister the store as an observer of the receiver. */
            objectFlow.removeObserver(this);

            /* Deregister the store as a use of the value flow. */
            valueFlow.removeUse(this);

            /* Link the saturated store. */
            StoreIndexedTypeFlow contextInsensitiveStore = ((PointsToAnalysisType) objectType).initAndGetContextInsensitiveIndexedStore(bb, source);
            /*
             * Link the value flow to the saturated store. The receiver is already set in the
             * saturated store.
             */
            valueFlow.addUse(bb, contextInsensitiveStore);
        }

        @Override
        public String toString() {
            return "StoreIndexedTypeFlow<" + getState() + ">";
        }
    }

    public abstract static class AbstractUnsafeStoreTypeFlow extends OffsetStoreTypeFlow {

        AbstractUnsafeStoreTypeFlow(BytecodePosition storeLocation, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(storeLocation, objectType, filterUncheckedInterface(componentType), objectFlow, valueFlow);
        }

        AbstractUnsafeStoreTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, OffsetStoreTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public final AbstractUnsafeStoreTypeFlow copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
            return makeCopy(bb, methodFlows);
        }

        protected abstract AbstractUnsafeStoreTypeFlow makeCopy(PointsToAnalysis bb, MethodFlowsGraph methodFlows);

        @Override
        public void initFlow(PointsToAnalysis bb) {
            assert !bb.analysisPolicy().isContextSensitiveAnalysis() || this.isClone() : this;
            /*
             * Register the unsafe store. It will be force-updated when new unsafe fields are
             * registered.
             */
            bb.registerUnsafeStore(this);
            forceUpdate(bb);
        }

        @Override
        public boolean needsInitialization() {
            return true;
        }

        public void forceUpdate(PointsToAnalysis bb) {
            /*
             * Unsafe store type flow models unsafe writes to both instance and static fields. From
             * an analysis stand point for static fields the base doesn't matter. An unsafe store
             * can write to any of the static fields marked for unsafe access.
             */
            for (AnalysisField field : bb.getUniverse().getUnsafeAccessedStaticFields()) {
                addUse(bb, field.getStaticFieldFlow().filterFlow(bb));
            }
        }

        void handleUnsafeAccessedFields(PointsToAnalysis bb, Collection<AnalysisField> unsafeAccessedFields, AnalysisObject object) {
            for (AnalysisField field : unsafeAccessedFields) {
                /* Write through the field filter flow. */
                addUse(bb, object.getInstanceFieldFilterFlow(bb, objectFlow, source, field));
            }
        }
    }

    /**
     * Implements an unsafe store operation type flow.
     */
    public static class UnsafeStoreTypeFlow extends AbstractUnsafeStoreTypeFlow {

        public UnsafeStoreTypeFlow(BytecodePosition storeLocation, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(storeLocation, objectType, componentType, objectFlow, valueFlow);
        }

        public UnsafeStoreTypeFlow(PointsToAnalysis bb, MethodFlowsGraph methodFlows, UnsafeStoreTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public UnsafeStoreTypeFlow makeCopy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
            return new UnsafeStoreTypeFlow(bb, methodFlows, this);
        }

        @Override
        public void onObservedUpdate(PointsToAnalysis bb) {
            TypeState objectState = objectFlow.getState();
            /* Iterate over the receiver objects. */
            for (AnalysisObject object : objectState.objects(bb)) {
                AnalysisType type = object.type();
                if (type.isArray()) {
                    if (object.isPrimitiveArray() || object.isEmptyObjectArrayConstant(bb)) {
                        /* Cannot write to a primitive array or an empty array constant. */
                        continue;
                    }

                    /*
                     * Add the elements flow as a use to the store value flow. Unsafe store to an
                     * array is essentially an index store. We intercept unsafe store to arrays in
                     * MethodTypeFlow but some arrays have only Object type at that point.
                     */
                    TypeFlow<?> elementsFlow = object.getArrayElementsFlow(bb, true);
                    this.addUse(bb, elementsFlow);
                } else {
                    handleUnsafeAccessedFields(bb, type.unsafeAccessedFields(), object);
                }
            }
        }

        @Override
        public void onObservedSaturated(PointsToAnalysis bb, TypeFlow<?> observed) {
            /*
             * When receiver flow saturates swap in the saturated unsafe store type flow. When the
             * store itself saturates it propagates the saturation state to the uses/observers and
             * unlinks them, but it still observes the receiver state to notify no-yet-reachable
             * unsafe fields of saturation.
             */

            /* Deregister the store as an observer of the receiver. */
            objectFlow.removeObserver(this);

            /* Deregister the store as a use of the value flow. */
            valueFlow.removeUse(this);

            /* Link the saturated store. */
            AbstractUnsafeStoreTypeFlow contextInsensitiveStore = ((PointsToAnalysisType) objectType).initAndGetContextInsensitiveUnsafeStore(bb, source);
            /*
             * Link the value flow to the saturated store. The receiver is already set in the
             * saturated store.
             */
            valueFlow.addUse(bb, contextInsensitiveStore);
        }

        @Override
        public String toString() {
            return "UnsafeStoreTypeFlow<" + getState() + ">";
        }
    }
}
