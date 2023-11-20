/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow.context.object;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.FieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.UnsafeWriteSinkTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.typestore.ArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Models a runtime object at analysis time.
 */
public class AnalysisObject implements Comparable<AnalysisObject> {

    public static final Comparator<AnalysisObject> objectsTypeComparator = Comparator.comparingInt(o -> o.getTypeId());
    public static final AnalysisObject[] EMPTY_ARRAY = new AnalysisObject[0];

    private static final AtomicInteger nextObjectId = new AtomicInteger();

    protected enum AnalysisObjectKind {
        /** The types of runtime objects that the analysis models. */
        ContextInsensitive("!S"),
        ConstantObject("C"),
        AllocationContextSensitive("AS"),
        ConstantContextSensitive("CS");

        protected final String prefix;

        AnalysisObjectKind(String prefix) {
            this.prefix = prefix;
        }
    }

    /**
     * The id of an analysis object. It contains the type ID in the 32 MSB and the actual object ID
     * in the 32 LSB.
     */
    protected final long id;

    /** The {@linkplain AnalysisType concrete type} of an analysis object. */
    protected final AnalysisType type;

    protected final AnalysisObjectKind kind;

    /**
     * The merging of analysis objects is used to erase the identity of more concrete analysis
     * objects (i.e., objects that wrap a constant, or retain information about their allocation
     * location) and to replace them with the per-type context-insensitive analysis object.
     * 
     * This distinction between merged and un-merged objects is essential to correctly track field
     * and array flows reads and writes. For example when a concrete analysis object is the receiver
     * of a write operation then only the field flow associated with that receiver will get the
     * state of the stored value, such that any read from the same receiver and field will return
     * the written values, but no others. However, if there is a union between the concrete receiver
     * and a context-insensitive object of the same type then the receiver needs to be marked as
     * `merged` to signal that all reads from a field of this object must include all the state
     * written to the corresponding field flow of the context-insensitive object and all writes must
     * also flow in the corresponding field flow of the context-insensitive object.
     */
    protected volatile boolean merged;

    @SuppressWarnings("rawtypes") //
    private static final AtomicReferenceFieldUpdater<AnalysisObject, AtomicReferenceArray> INSTANCE_FIELD_TYPE_STORE_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(AnalysisObject.class, AtomicReferenceArray.class, "instanceFieldsTypeStore");

    /** The array elements flow for this object. */
    protected final ArrayElementsTypeStore arrayElementsTypeStore;

    /** The instance field flows for this object. */
    protected volatile AtomicReferenceArray<FieldTypeStore> instanceFieldsTypeStore;

    /**
     * By default an analysis object is context insensitive.
     */
    public AnalysisObject(AnalysisUniverse universe, AnalysisType type) {
        this(universe, type, AnalysisObjectKind.ContextInsensitive);
    }

    /**
     * Constructor allowing the subclasses to specify the type of context sensitivity they
     * implement.
     */
    @SuppressWarnings("this-escape")
    protected AnalysisObject(AnalysisUniverse universe, AnalysisType type, AnalysisObjectKind kind) {
        this.id = createId(type.getId());
        this.type = type;
        this.kind = kind;
        this.merged = false;
        this.arrayElementsTypeStore = universe.analysisPolicy().createArrayElementsTypeStore(this, universe);
    }

    /**
     * The object IDs are manipulated to contain the type ID. We LSHIFT the type ID with 32 and XOR
     * with the object ID, thus the object ID is a long and has the type ID in it's most significant
     * 32 bits. 32 bits should be enough to generate IDs for all the objects.
     *
     * object id = |type id|(32) << 32 ^ |object id|(32) = |type id|object id|(64)
     *
     * @param typeId
     * @return object id
     */
    private static long createId(int typeId) {
        long resultId = typeId;
        long objectId = nextObjectId.incrementAndGet();
        resultId = resultId << 32 ^ objectId;
        return resultId;
    }

    public int getTypeId() {
        return (int) (id >> 32);
    }

    public AnalysisType type() {
        return this.type;
    }

    public long getId() {
        return this.id;
    }

    /**
     * Note that this object has been merged. If it is context sensitive then its context
     * sensitivity is invalidated. If it is context insensitive then it means that it has been
     * merged with some context sensitive objects, i.e., it now represents those objects too and
     * their corresponding data, like field and array elements flows.
     */
    public void noteMerge(@SuppressWarnings("unused") PointsToAnalysis bb) {
        this.merged = true;
    }

    public String prefix() {
        return kind.prefix;
    }

    public final boolean isContextInsensitiveObject() {
        return this.kind == AnalysisObjectKind.ContextInsensitive;
    }

    public final boolean isAllocationContextSensitiveObject() {
        return this.kind == AnalysisObjectKind.AllocationContextSensitive;
    }

    public JavaConstant asConstant() {
        return null;
    }

    public ArrayElementsTypeStore getArrayElementsTypeStore() {
        return arrayElementsTypeStore;
    }

    /** Returns the array elements type flow corresponding to an analysis object of array type. */
    public ArrayElementsTypeFlow getArrayElementsFlow(PointsToAnalysis bb, boolean isStore) {
        assert this.isObjectArray() : this;

        // ensure initialization
        arrayElementsTypeStore.init(bb);

        return isStore ? arrayElementsTypeStore.writeFlow() : arrayElementsTypeStore.readFlow();
    }

    /** Returns the filter field flow corresponding to an unsafe accessed filed. */
    public FieldFilterTypeFlow getInstanceFieldFilterFlow(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field) {
        assert !Modifier.isStatic(field.getModifiers()) && field.isUnsafeAccessed() : field;

        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, objectFlow, context, field);
        return fieldTypeStore.writeFlow().filterFlow(bb);
    }

    public UnsafeWriteSinkTypeFlow getUnsafeWriteSinkFrozenFilterFlow(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field) {
        assert !Modifier.isStatic(field.getModifiers()) && field.hasUnsafeFrozenTypeState() : field;
        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, objectFlow, context, field);
        return fieldTypeStore.unsafeWriteSinkFlow(bb);
    }

    /** Returns the instance field flow corresponding to a filed of the object's type. */
    public FieldTypeFlow getInstanceFieldFlow(PointsToAnalysis bb, AnalysisField field, boolean isStore) {
        return getInstanceFieldFlow(bb, null, null, field, isStore);
    }

    public FieldTypeFlow getInstanceFieldFlow(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field, boolean isStore) {
        assert !Modifier.isStatic(field.getModifiers()) : field;

        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, objectFlow, context, field);

        return isStore ? fieldTypeStore.writeFlow() : fieldTypeStore.readFlow();
    }

    final FieldTypeStore getInstanceFieldTypeStore(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field) {
        assert !Modifier.isStatic(field.getModifiers()) : field;
        assert bb != null && !bb.getUniverse().sealed() : "universe is sealed";

        checkField(bb, objectFlow, context, field);

        if (instanceFieldsTypeStore == null) {
            INSTANCE_FIELD_TYPE_STORE_UPDATER.compareAndSet(this, null, new AtomicReferenceArray<>(type.getInstanceFields(true).length));
        }

        AnalysisError.guarantee(field.getPosition() >= 0 && field.getPosition() < instanceFieldsTypeStore.length(), "Field %s.%s has invalid position %d.", field.getDeclaringClass().toJavaName(),
                        field.getName(), field.getPosition());

        FieldTypeStore fieldStore = instanceFieldsTypeStore.get(field.getPosition());
        if (fieldStore == null) {
            fieldStore = bb.analysisPolicy().createFieldTypeStore(bb, this, field, bb.getUniverse());
            boolean result = instanceFieldsTypeStore.compareAndSet(field.getPosition(), null, fieldStore);
            if (result) {
                fieldStore.init(bb);
                linkFieldFlows(bb, field, fieldStore);
            } else {
                fieldStore = instanceFieldsTypeStore.get(field.getPosition());
            }
        }

        return fieldStore;
    }

    private void checkField(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field) {
        /*
         * Assignable types are assigned on AnalysisType creation, before the type is published, so
         * if other is assignable to this then other would have been added to
         * this.assignableTypesState and there is no risk of calling this.isAssignableFrom(other)
         * too early. Using the type state based check is cheaper than getting the assignable
         * information from the host vm every time.
         */
        if (!field.getDeclaringClass().getAssignableTypes(false).containsType(type)) {
            throw AnalysisError.fieldNotPresentError(bb, objectFlow, context, field, type);
        }
    }

    @SuppressWarnings("unused")
    protected void linkFieldFlows(PointsToAnalysis bb, AnalysisField field, FieldTypeStore fieldStore) {
        // link the initial instance field flow to the field write flow
        field.getInitialInstanceFieldFlow().addUse(bb, fieldStore.writeFlow());
        // link the field read flow to the context insensitive instance field flow
        fieldStore.readFlow().addUse(bb, field.getInstanceFieldFlow());
    }

    /**
     * Check if a constant object wraps the empty array constant, e.g. <code> static final Object[]
     * EMPTY_ELEMENTDATA = {}</code> for ArrayList;
     */
    public boolean isEmptyObjectArrayConstant(@SuppressWarnings("unused") PointsToAnalysis bb) {
        return false;
    }

    /** Check if an analysis object is a primitive array. */
    public boolean isPrimitiveArray() {
        return this.type().isArray() && this.type().getComponentType().getJavaKind() != JavaKind.Object;
    }

    /** Check if an analysis object is an object array. */
    public boolean isObjectArray() {
        return this.type().isArray() && this.type().getComponentType().getJavaKind() == JavaKind.Object;
    }

    /**
     * Check if a constant object wraps the empty array constant, i.e. <code> static final Object[]
     * EMPTY_ELEMENTDATA = {}</code>;
     */
    public static boolean isEmptyObjectArrayConstant(PointsToAnalysis bb, JavaConstant constant) {
        assert constant.getJavaKind() == JavaKind.Object : constant;
        Integer length = bb.getConstantReflectionProvider().readArrayLength(constant);
        return length != null && length == 0;
    }

    @Override
    public String toString() {
        return String.format("0x%016X", id) + ":" + kind.prefix + ":" + (merged ? "M" : "") + ":" + (type != null ? type.toJavaName(false) : "");
    }

    @Override
    public int compareTo(AnalysisObject other) {
        return Long.compare(this.getId(), other.getId());
    }

    @Override
    public final boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        /* Just get the lower 32 bits, i.e., the object id without the type id. */
        return (int) (id & 0xFFFFFFFF);
    }
}
