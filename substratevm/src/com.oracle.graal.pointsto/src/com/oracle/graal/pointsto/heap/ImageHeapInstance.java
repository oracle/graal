/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.heap;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This class implements an instance object snapshot. It stores the field values in an Object[],
 * indexed by {@link AnalysisField#getPosition()}. Each array entry is either
 * <li>a not-yet-executed {@link AnalysisFuture} of {@link JavaConstant} which captures the
 * original, hosted field value and contains logic to transform and replace this value</li>, or
 * <li>the result of executing the future, a replaced {@link JavaConstant}, i.e., the snapshot.</li>
 * <p>
 * The future task is executed when the field is marked as read. Moreover, the future is
 * self-replacing, i.e., when it is executed it also calls
 * {@link #setFieldValue(AnalysisField, JavaConstant)} and updates the corresponding entry.
 */
public final class ImageHeapInstance extends ImageHeapConstant {

    private static final VarHandle arrayHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    public static final VarHandle valuesHandle = ReflectionUtil.unreflectField(InstanceData.class, "fieldValues", MethodHandles.lookup());

    private static final class InstanceData extends ConstantData {

        /**
         * Stores the field values, indexed by {@link AnalysisField#getPosition()}. For normal
         * constants it is set via {@link #setFieldValues(Object[])} only when the constant is
         * actually used and the hosted values of its fields may be read. For simulated constants it
         * is set on creation.
         * <p>
         * Each value is either an {@link AnalysisFuture} of {@link JavaConstant} or its result, a
         * {@link JavaConstant}. Evaluating the {@link AnalysisFuture} runs
         * {@link ImageHeapScanner#createFieldValue(AnalysisField, ImageHeapInstance, ValueSupplier, ObjectScanner.ScanReason)}
         * which adds the result to the image heap.
         */
        private Object[] fieldValues;

        private InstanceData(AnalysisType type, JavaConstant hostedObject, Object[] fieldValues) {
            super(type, hostedObject);
            this.fieldValues = fieldValues;
            assert !type.isArray() : type;
        }
    }

    ImageHeapInstance(AnalysisType type, JavaConstant hostedObject) {
        super(new InstanceData(type, hostedObject, null), false);
    }

    public ImageHeapInstance(AnalysisType type) {
        super(new InstanceData(type, null, new Object[type.getInstanceFields(true).length]), false);
    }

    private ImageHeapInstance(ConstantData data, boolean compressed) {
        super(data, compressed);
    }

    @Override
    public InstanceData getConstantData() {
        return (InstanceData) super.getConstantData();
    }

    void setFieldValues(Object[] fieldValues) {
        boolean success = valuesHandle.compareAndSet(constantData, null, fieldValues);
        AnalysisError.guarantee(success, "Unexpected field values reference for constant %s", this);
    }

    /**
     * {@link InstanceData#fieldValues} are only set once, in {@link #setFieldValues(Object[])} and
     * shouldn't be accessed before set, i.e., read access is guarded by
     * {@link #isReaderInstalled()} which ensures that the future setting the field values was
     * executed, therefore we can read the field directly.
     */
    private Object[] getFieldValues() {
        AnalysisError.guarantee(isReaderInstalled());
        Object[] fieldValues = getConstantData().fieldValues;
        AnalysisError.guarantee(fieldValues != null);
        return fieldValues;
    }

    /**
     * Record the task computing the field value. It will be retrieved and executed when the field
     * is marked as read.
     */
    void setFieldTask(AnalysisField field, AnalysisFuture<JavaConstant> task) {
        arrayHandle.setVolatile(getFieldValues(), field.getPosition(), task);
    }

    /**
     * Record the field value produced by the task set in
     * {@link #setFieldTask(AnalysisField, AnalysisFuture)}, i.e., the snapshot, already transformed
     * and replaced.
     */
    public void setFieldValue(AnalysisField field, JavaConstant value) {
        arrayHandle.setVolatile(getFieldValues(), field.getPosition(), value);
    }

    /**
     * Return either a task for transforming the field value, effectively a future for
     * {@link ImageHeapScanner#createFieldValue(AnalysisField, ImageHeapInstance, ValueSupplier, ObjectScanner.ScanReason)},
     * or the result of executing the task, i.e., a {@link JavaConstant}.
     */
    public Object getFieldValue(AnalysisField field) {
        return arrayHandle.getVolatile(getFieldValues(), field.getPosition());
    }

    /**
     * Returns the field value, i.e., a {@link JavaConstant}. If the value is not yet materialized
     * then the future is executed on the current thread.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public JavaConstant readFieldValue(AnalysisField field) {
        Object value = getFieldValue(field);
        return value instanceof JavaConstant ? (JavaConstant) value : ((AnalysisFuture<ImageHeapConstant>) value).ensureDone();
    }

    @Override
    public JavaConstant compress() {
        assert !compressed : this;
        return new ImageHeapInstance(constantData, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed : this;
        return new ImageHeapInstance(constantData, false);
    }

    @Override
    public ImageHeapConstant forObjectClone() {
        if (!constantData.type.isCloneableWithAllocation()) {
            return null;
        }

        Object[] fieldValues = getFieldValues();
        Objects.requireNonNull(fieldValues, "Cannot clone an instance before the field values are set.");
        Object[] newFieldValues = Arrays.copyOf(fieldValues, fieldValues.length);
        /* The new constant is never backed by a hosted object, regardless of the input object. */
        return new ImageHeapInstance(new InstanceData(constantData.type, null, newFieldValues), compressed);
    }
}
