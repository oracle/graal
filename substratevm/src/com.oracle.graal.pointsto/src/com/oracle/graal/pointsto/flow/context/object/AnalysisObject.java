/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.FieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.UnsafeWriteSinkTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.typestore.ArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;
import com.oracle.graal.pointsto.util.AnalysisError;

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
     * Is this a context sensitive object that was merged with a context insensitive object, or a
     * context insensitive object that has merged some context sensitive objects?
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
    public void noteMerge(@SuppressWarnings("unused") BigBang bb) {
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

    public final boolean isConstantContextSensitiveObject() {
        return this.kind == AnalysisObjectKind.ConstantContextSensitive;
    }

    public final boolean isContextSensitiveObject() {
        return this.isAllocationContextSensitiveObject() || this.isConstantContextSensitiveObject();
    }

    public ArrayElementsTypeStore getArrayElementsTypeStore() {
        return arrayElementsTypeStore;
    }

    /** Returns the array elements type flow corresponding to an analysis object of array type. */
    public ArrayElementsTypeFlow getArrayElementsFlow(BigBang bb, boolean isStore) {
        assert this.isObjectArray();

        // ensure initialization
        arrayElementsTypeStore.init(bb);

        return isStore ? arrayElementsTypeStore.writeFlow() : arrayElementsTypeStore.readFlow();
    }

    /** Returns the filter field flow corresponding to an unsafe accessed filed. */
    public FieldFilterTypeFlow getInstanceFieldFilterFlow(BigBang bb, AnalysisMethod context, AnalysisField field) {
        assert !Modifier.isStatic(field.getModifiers()) && field.isUnsafeAccessed();

        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, context, field);
        return fieldTypeStore.writeFlow().filterFlow(bb);
    }

    public UnsafeWriteSinkTypeFlow getUnsafeWriteSinkFrozenFilterFlow(BigBang bb, AnalysisMethod context, AnalysisField field) {
        assert !Modifier.isStatic(field.getModifiers()) && field.hasUnsafeFrozenTypeState();
        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, context, field);
        return fieldTypeStore.unsafeWriteSinkFlow(bb);
    }

    /** Returns the instance field flow corresponding to a filed of the object's type. */
    public FieldTypeFlow getInstanceFieldFlow(BigBang bb, AnalysisField field, boolean isStore) {
        return getInstanceFieldFlow(bb, null, field, isStore);
    }

    public FieldTypeFlow getInstanceFieldFlow(BigBang bb, AnalysisMethod context, AnalysisField field, boolean isStore) {
        assert !Modifier.isStatic(field.getModifiers());

        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, context, field);

        return isStore ? fieldTypeStore.writeFlow() : fieldTypeStore.readFlow();
    }

    final FieldTypeStore getInstanceFieldTypeStore(BigBang bb, AnalysisMethod context, AnalysisField field) {
        assert !Modifier.isStatic(field.getModifiers());
        assert bb != null && !bb.getUniverse().sealed();

        if (instanceFieldsTypeStore == null) {
            AnalysisField[] fields = type.getInstanceFields(true);
            INSTANCE_FIELD_TYPE_STORE_UPDATER.compareAndSet(this, null, new AtomicReferenceArray<>(fields.length));
        }

        if (field.getPosition() < 0 || field.getPosition() >= instanceFieldsTypeStore.length()) {
            throw AnalysisError.fieldNotPresentError(context, field, type);
        }

        FieldTypeStore fieldStore = instanceFieldsTypeStore.get(field.getPosition());
        if (fieldStore == null) {
            fieldStore = bb.analysisPolicy().createFieldTypeStore(this, field, bb.getUniverse());
            boolean result = instanceFieldsTypeStore.compareAndSet(field.getPosition(), null, fieldStore);
            if (result) {
                fieldStore.init(bb);
                // link the initial instance field flow to the field write flow
                field.getInitialInstanceFieldFlow().addUse(bb, fieldStore.writeFlow());
                // link the field read flow to the context insensitive instance field flow
                fieldStore.readFlow().addUse(bb, field.getInstanceFieldFlow());
            } else {
                fieldStore = instanceFieldsTypeStore.get(field.getPosition());
            }
        }

        return fieldStore;
    }

    /**
     * Check if a constant object wraps the empty array constant, e.g. <code> static final Object[]
     * EMPTY_ELEMENTDATA = {}</code> for ArrayList;
     */
    public boolean isEmptyObjectArrayConstant(@SuppressWarnings("unused") BigBang bb) {
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
    public static boolean isEmptyObjectArrayConstant(BigBang bb, JavaConstant constant) {
        assert constant.getJavaKind() == JavaKind.Object;
        Object valueObj = bb.getProviders().getSnippetReflection().asObject(Object.class, constant);
        if (valueObj instanceof Object[]) {
            Object[] arrayValueObj = (Object[]) valueObj;
            if (arrayValueObj.length == 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("0x%016X", id) + ":" + kind.prefix + ":" + (type != null ? type.toJavaName(false) : "");
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
