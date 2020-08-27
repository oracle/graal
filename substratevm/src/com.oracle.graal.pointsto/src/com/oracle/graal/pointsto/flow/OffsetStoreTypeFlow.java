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

import java.util.List;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.java.UnsafeCompareAndSwapNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.UnsafePartitionKind;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.nodes.AnalysisUnsafePartitionStoreNode;
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
    private final AnalysisType objectType;

    /** The flow of the input value. */
    private final TypeFlow<?> valueFlow;
    /** The flow of the receiver object. */
    protected TypeFlow<?> objectFlow;

    public OffsetStoreTypeFlow(ValueNode node, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
        super(node.getNodeSourcePosition(), componentType);
        this.objectType = objectType;
        this.valueFlow = valueFlow;
        this.objectFlow = objectFlow;
    }

    public OffsetStoreTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, OffsetStoreTypeFlow original) {
        super(original, methodFlows);
        this.objectType = original.objectType;
        this.valueFlow = methodFlows.lookupCloneOf(bb, original.valueFlow);
        this.objectFlow = methodFlows.lookupCloneOf(bb, original.objectFlow);
    }

    @Override
    public TypeFlow<?> receiver() {
        return objectFlow;
    }

    /** Return the state of the receiver object. */
    public TypeState getObjectState() {
        return objectFlow.getState();
    }

    @Override
    public abstract TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows);

    @Override
    public abstract boolean addState(BigBang bb, TypeState add);

    @Override
    public void setObserved(TypeFlow<?> newObjectFlow) {
        this.objectFlow = newObjectFlow;
    }

    @Override
    public abstract void onObservedUpdate(BigBang bb);

    @Override
    public void onObservedSaturated(BigBang bb, TypeFlow<?> observed) {
        assert this.isClone();
        /* When receiver object flow saturates start observing the flow of the the object type. */
        replaceObservedWith(bb, objectType);
    }

    /**
     * Implements the type flow of an indexed store operation. The type state of an indexed store
     * flow reflects the elements type state of the receiver array objects that triggered the store
     * operation, i.e., the stored values. The declared type of a store operation is the component
     * type of the receiver array, if known statically, null otherwise.
     */
    public static class StoreIndexedTypeFlow extends OffsetStoreTypeFlow {

        public StoreIndexedTypeFlow(ValueNode node, AnalysisType arrayType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(node, arrayType, arrayType.getComponentType(), objectFlow, valueFlow);
        }

        public StoreIndexedTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, StoreIndexedTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public StoreIndexedTypeFlow copy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new StoreIndexedTypeFlow(bb, methodFlows, this);
        }

        @Override
        public boolean addState(BigBang bb, TypeState add) {
            /* Only a clone should be updated */
            assert this.isClone();
            if (add.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Index storing UnknownTypeState into object array. Store: " + source);
                return false;
            }
            return super.addState(bb, add, true);
        }

        @Override
        public void onObservedUpdate(BigBang bb) {
            /* Only a clone should be updated */
            assert this.isClone();

            TypeState objectState = objectFlow.getState();

            if (objectState.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Index storing into UnknownTypeState objects. Store: " + this);
                return;
            }

            /* Iterate over the receiver objects. */
            for (AnalysisObject object : objectState.objects()) {
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
        public String toString() {
            return "StoreIndexedTypeFlow<" + getState() + ">";
        }
    }

    public abstract static class AbstractUnsafeStoreTypeFlow extends OffsetStoreTypeFlow {

        AbstractUnsafeStoreTypeFlow(ValueNode node, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(node, objectType, componentType, objectFlow, valueFlow);
        }

        AbstractUnsafeStoreTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, OffsetStoreTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public final AbstractUnsafeStoreTypeFlow copy(BigBang bb, MethodFlowsGraph methodFlows) {
            AbstractUnsafeStoreTypeFlow copy = makeCopy(bb, methodFlows);
            // Register the unsafe store. It will be force-updated when new unsafe fields are
            // registered. Only the clones are registered since the original flows are not updated.
            bb.registerUnsafeStore(copy);
            return copy;
        }

        protected abstract AbstractUnsafeStoreTypeFlow makeCopy(BigBang bb, MethodFlowsGraph methodFlows);

        @Override
        public void initClone(BigBang bb) {
            /*
             * Unsafe store type flow models unsafe writes to both instance and static fields. From
             * an analysis stand point for static fields the base doesn't matter. An unsafe store
             * can write to any of the static fields marked for unsafe access.
             */
            for (AnalysisField field : bb.getUniverse().getUnsafeAccessedStaticFields()) {
                this.addUse(bb, field.getStaticFieldFlow().filterFlow(bb));
            }
        }

        @Override
        public boolean addState(BigBang bb, TypeState add) {
            /* Only a clone should be updated */
            assert this.isClone();
            if (add.isUnknown()) {
                bb.getUnsupportedFeatures().addMessage(graphRef.getMethod().format("%H.%n(%p)"), graphRef.getMethod(), "Illegal: Store UnknownTypeState via unsafe. Store: " + this.getSource());
                return false;
            }
            return super.addState(bb, add, true);
        }

        void handleUnsafeAccessedFields(BigBang bb, List<AnalysisField> unsafeAccessedFields, AnalysisObject object) {
            for (AnalysisField field : unsafeAccessedFields) {
                /* Write through the field filter flow. */
                if (field.hasUnsafeFrozenTypeState()) {
                    UnsafeWriteSinkTypeFlow unsafeWriteSink = object.getUnsafeWriteSinkFrozenFilterFlow(bb, this.method(), field);
                    this.addUse(bb, unsafeWriteSink);
                } else {
                    FieldFilterTypeFlow fieldFilterFlow = object.getInstanceFieldFilterFlow(bb, this.method(), field);
                    this.addUse(bb, fieldFilterFlow);
                }

            }
        }

        @Override
        public void onObservedUpdate(BigBang bb) {
            /* Only a clone should be updated */
            assert this.isClone();

            TypeState objectState = objectFlow.getState();

            if (objectState.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Unsafe store into UnknownTypeState objects. Store: " + this);
                return;
            }

            /* Iterate over the receiver objects. */
            for (AnalysisObject object : objectState.objects()) {
                AnalysisType objectType = object.type();
                if (objectType.isArray()) {
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
                    handleUnsafeAccessedFields(bb, objectType.unsafeAccessedFields(), object);
                }
            }
        }
    }

    /**
     * Implements an unsafe store operation type flow.
     */
    public static class UnsafeStoreTypeFlow extends AbstractUnsafeStoreTypeFlow {

        public UnsafeStoreTypeFlow(RawStoreNode node, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(node, objectType, componentType, objectFlow, valueFlow);
        }

        public UnsafeStoreTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, UnsafeStoreTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public UnsafeStoreTypeFlow makeCopy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new UnsafeStoreTypeFlow(bb, methodFlows, this);
        }

        @Override
        public String toString() {
            return "UnsafeStoreTypeFlow<" + getState() + ">";
        }
    }

    /**
     * Implements an unsafe compare and swap operation type flow.
     */
    public static class CompareAndSwapTypeFlow extends AbstractUnsafeStoreTypeFlow {

        public CompareAndSwapTypeFlow(UnsafeCompareAndSwapNode node, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(node, objectType, componentType, objectFlow, valueFlow);
        }

        public CompareAndSwapTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, CompareAndSwapTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public CompareAndSwapTypeFlow makeCopy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new CompareAndSwapTypeFlow(bb, methodFlows, this);
        }

        @Override
        public String toString() {
            return "CompareAndSwapTypeFlow<" + getState() + ">";
        }
    }

    /**
     * Implements an atomic read and write operation type flow.
     */
    public static class AtomicWriteTypeFlow extends AbstractUnsafeStoreTypeFlow {

        public AtomicWriteTypeFlow(ValueNode node, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(node, objectType, componentType, objectFlow, valueFlow);
        }

        public AtomicWriteTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, AtomicWriteTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public AtomicWriteTypeFlow makeCopy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new AtomicWriteTypeFlow(bb, methodFlows, this);
        }

        @Override
        public String toString() {
            return "AtomicWriteTypeFlow<" + getState() + ">";
        }
    }

    public static class UnsafePartitionStoreTypeFlow extends AbstractUnsafeStoreTypeFlow {

        protected final UnsafePartitionKind partitionKind;
        protected final AnalysisType partitionType;

        public UnsafePartitionStoreTypeFlow(AnalysisUnsafePartitionStoreNode node, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow,
                        UnsafePartitionKind partitionKind, AnalysisType partitionType) {
            super(node, objectType, componentType, objectFlow, valueFlow);
            this.partitionKind = partitionKind;
            this.partitionType = partitionType;
        }

        public UnsafePartitionStoreTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, UnsafePartitionStoreTypeFlow original) {
            super(bb, methodFlows, original);
            this.partitionKind = original.partitionKind;
            this.partitionType = original.partitionType;
        }

        @Override
        public UnsafePartitionStoreTypeFlow makeCopy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new UnsafePartitionStoreTypeFlow(bb, methodFlows, this);
        }

        @Override
        public boolean addState(BigBang bb, TypeState add) {
            /* Only a clone should be updated */
            assert this.isClone();
            if (add.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Store UnknownTypeState via unsafe. Store: " + source);
                return false;
            }
            return super.addState(bb, add, true);
        }

        @Override
        public TypeState filter(BigBang bb, TypeState update) {
            if (partitionType.equals(bb.getObjectType())) {
                /* No need to filter. */
                return update;
            } else {
                /* Filter the incoming state with the partition type. */
                return TypeState.forIntersection(bb, update, partitionType.getTypeFlow(bb, true).getState());
            }
        }

        @Override
        public void onObservedUpdate(BigBang bb) {
            /* Only a clone should be updated */
            assert this.isClone();

            TypeState objectState = objectFlow.getState();

            if (objectState.isUnknown()) {
                bb.reportIllegalUnknownUse(graphRef.getMethod(), source, "Illegal: Unsafe store into UnknownTypeState objects. Store: " + source);
                return;
            }

            /* Iterate over the receiver objects. */
            for (AnalysisObject object : objectState.objects()) {
                AnalysisType objectType = object.type();
                assert !objectType.isArray();

                handleUnsafeAccessedFields(bb, objectType.unsafeAccessedFields(partitionKind), object);
            }
        }

        @Override
        public String toString() {
            return "UnsafePartitionStoreTypeFlow<" + getState() + "> : " + partitionKind;
        }
    }

    /**
     * Implements the raw memory store operation type flow.
     */
    public static class JavaWriteTypeFlow extends AbstractUnsafeStoreTypeFlow {

        public JavaWriteTypeFlow(JavaWriteNode node, AnalysisType objectType, AnalysisType componentType, TypeFlow<?> objectFlow, TypeFlow<?> valueFlow) {
            super(node, objectType, componentType, objectFlow, valueFlow);
        }

        public JavaWriteTypeFlow(BigBang bb, MethodFlowsGraph methodFlows, JavaWriteTypeFlow original) {
            super(bb, methodFlows, original);
        }

        @Override
        public JavaWriteTypeFlow makeCopy(BigBang bb, MethodFlowsGraph methodFlows) {
            return new JavaWriteTypeFlow(bb, methodFlows, this);
        }

        @Override
        public String toString() {
            return "JavaWriteTypeFlow<" + getState() + ">";
        }
    }

}
