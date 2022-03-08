/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisFuture;

import jdk.vm.ci.meta.JavaConstant;

/**
 * It represents an object snapshot. It stores the replaced object, i.e., the result of applying
 * object replacers on the original object, and the instance field values or array elements of this
 * object. The field values are stored as JavaConstant to also encode primitive values.
 * ImageHeapObject are created only after an object is processed through the object replacers.
 */
public class ImageHeapObject {
    /**
     * Store the object, already processed by the object transformers.
     */
    private final JavaConstant object;

    ImageHeapObject(JavaConstant object) {
        this.object = object;
    }

    public JavaConstant getObject() {
        return object;
    }

    /* Equals and hashCode just compare the replaced constant. */

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageHeapObject) {
            ImageHeapObject other = (ImageHeapObject) o;
            return this.object.equals(other.object);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return object.hashCode();
    }
}

/**
 * This class implements an instance object snapshot. It stores the field values in an Object[],
 * indexed by {@link AnalysisField#getPosition()}. Each array entry is either
 * <li>a not-yet-executed {@link AnalysisFuture} of {@link JavaConstant} which captures the
 * original, hosted field value and contains logic to transform and replace this value</li>, or
 * <li>the result of executing the future, a replaced {@link JavaConstant}, i.e., the snapshot.</li>
 *
 * The future task is executed when the field is marked as read. Moreover, the future is
 * self-replacing, i.e., when it is executed it also calls
 * {@link #setFieldValue(AnalysisField, JavaConstant)} and updates the corresponding entry.
 */
final class ImageHeapInstance extends ImageHeapObject {

    private static final VarHandle arrayHandle = MethodHandles.arrayElementVarHandle(Object[].class);

    /**
     * Stores either an {@link AnalysisFuture} of {@link JavaConstant} or its result, a
     * {@link JavaConstant}.
     */
    private final Object[] values;

    ImageHeapInstance(JavaConstant object, int length) {
        super(object);
        this.values = new Object[length];
    }

    /**
     * Record the task computing the field value. It will be retrieved and executed when the field
     * is marked as read.
     */
    public void setFieldTask(AnalysisField field, AnalysisFuture<JavaConstant> task) {
        arrayHandle.setVolatile(this.values, field.getPosition(), task);
    }

    /**
     * Record the field value produced by the task set in
     * {@link #setFieldTask(AnalysisField, AnalysisFuture)}, i.e., the snapshot, already transformed
     * and replaced.
     */
    public void setFieldValue(AnalysisField field, JavaConstant value) {
        arrayHandle.setVolatile(this.values, field.getPosition(), value);
    }

    /**
     * Return either a task for transforming the field value, effectively a future for
     * {@link ImageHeapScanner#onFieldValueReachable(AnalysisField, JavaConstant, JavaConstant, ObjectScanner.ScanReason, Consumer)},
     * or the result of executing the task, i.e., a {@link JavaConstant}.
     */
    public Object getFieldValue(AnalysisField field) {
        return arrayHandle.getVolatile(this.values, field.getPosition());
    }
}

final class ImageHeapArray extends ImageHeapObject {

    /**
     * Contains the already scanned array elements.
     */
    private final JavaConstant[] arrayElementValues;

    ImageHeapArray(JavaConstant object, JavaConstant[] arrayElementValues) {
        super(object);
        this.arrayElementValues = arrayElementValues;
    }

    /**
     * Return the value of the element at the specified index as computed by
     * {@link ImageHeapScanner#onArrayElementReachable(JavaConstant, AnalysisType, JavaConstant, int, ObjectScanner.ScanReason)}.
     */
    public JavaConstant getElement(int idx) {
        return arrayElementValues[idx];
    }

    public void setElement(int idx, JavaConstant value) {
        arrayElementValues[idx] = value;
    }

    public int getLength() {
        return arrayElementValues.length;
    }
}
