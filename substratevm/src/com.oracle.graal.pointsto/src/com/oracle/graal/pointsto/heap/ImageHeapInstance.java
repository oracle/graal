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
import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.util.AnalysisFuture;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

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

    /**
     * Stores either an {@link AnalysisFuture} of {@link JavaConstant} or its result, a
     * {@link JavaConstant}, indexed by {@link AnalysisField#getPosition()}.
     */
    private final Object[] fieldValues;

    public ImageHeapInstance(ResolvedJavaType type) {
        this(type, null, type.getInstanceFields(true).length);
    }

    ImageHeapInstance(ResolvedJavaType type, JavaConstant object, int length) {
        this(type, object, new Object[length], createIdentityHashCode(object), false);
    }

    private ImageHeapInstance(ResolvedJavaType type, JavaConstant object, Object[] fieldValues, int identityHashCode, boolean compressed) {
        super(type, object, identityHashCode, compressed);
        this.fieldValues = fieldValues;
    }

    /**
     * Record the task computing the field value. It will be retrieved and executed when the field
     * is marked as read.
     */
    public void setFieldTask(AnalysisField field, AnalysisFuture<JavaConstant> task) {
        arrayHandle.setVolatile(this.fieldValues, field.getPosition(), task);
    }

    /**
     * Record the field value produced by the task set in
     * {@link #setFieldTask(AnalysisField, AnalysisFuture)}, i.e., the snapshot, already transformed
     * and replaced.
     */
    public void setFieldValue(AnalysisField field, JavaConstant value) {
        arrayHandle.setVolatile(this.fieldValues, field.getPosition(), value);
    }

    /**
     * Return either a task for transforming the field value, effectively a future for
     * {@link ImageHeapScanner#onFieldValueReachable(AnalysisField, ImageHeapInstance, JavaConstant, ObjectScanner.ScanReason, Consumer)},
     * or the result of executing the task, i.e., a {@link JavaConstant}.
     */
    public Object getFieldValue(AnalysisField field) {
        return arrayHandle.getVolatile(this.fieldValues, field.getPosition());
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
        assert !compressed;
        return new ImageHeapInstance(type, hostedObject, fieldValues, identityHashCode, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed;
        return new ImageHeapInstance(type, hostedObject, fieldValues, identityHashCode, false);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageHeapInstance) {
            return super.equals(o) && this.fieldValues == ((ImageHeapInstance) o).fieldValues;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + System.identityHashCode(fieldValues);
        return result;
    }
}
