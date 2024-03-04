/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;

public final class ImageHeapObjectArray extends ImageHeapArray {

    private static final VarHandle arrayHandle = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final VarHandle elementsHandle = ReflectionUtil.unreflectField(ObjectArrayData.class, "arrayElementValues", MethodHandles.lookup());

    private static final class ObjectArrayData extends ConstantData {

        /**
         * Stores the array element values, indexed by array index. For normal constants it is set
         * via {@link #setElementValues(Object[])} only when the constant is actually used and the
         * hosted values of its elements may be read. For simulated constants it is set on creation.
         * <p>
         * Each value is either an {@link AnalysisFuture} of {@link JavaConstant} or its result, a
         * {@link JavaConstant}. Evaluating the {@link AnalysisFuture} runs
         * {@link ImageHeapScanner#createImageHeapConstant(JavaConstant, ObjectScanner.ScanReason)}
         * which adds the result to the image heap.
         */
        private Object[] arrayElementValues;

        final int length;

        private ObjectArrayData(AnalysisType type, JavaConstant hostedObject, Object[] arrayElementValues, int length) {
            super(type, hostedObject);
            this.arrayElementValues = arrayElementValues;
            this.length = length;
            assert type.isArray() && !type.getComponentType().isPrimitive() : type;
        }
    }

    ImageHeapObjectArray(AnalysisType type, JavaConstant hostedObject, int length) {
        super(new ObjectArrayData(type, hostedObject, null, length), false);
    }

    ImageHeapObjectArray(AnalysisType type, int length) {
        super(new ObjectArrayData(type, null, new Object[length], length), false);
    }

    private ImageHeapObjectArray(ConstantData data, boolean compressed) {
        super(data, compressed);
    }

    @Override
    public ObjectArrayData getConstantData() {
        return (ObjectArrayData) super.getConstantData();
    }

    void setElementValues(Object[] elementValues) {
        boolean success = elementsHandle.compareAndSet(constantData, null, elementValues);
        AnalysisError.guarantee(success, "Unexpected field values reference for constant %s", this);
    }

    /**
     * {@link ObjectArrayData#arrayElementValues} are only set once, in
     * {@link #setElementValues(Object[])} and shouldn't be accessed before set, i.e., read access
     * is guarded by {@link #isReaderInstalled()} which ensures that the future setting the field
     * values was executed, therefore we can read the field directly.
     */
    private Object[] getElementValues() {
        AnalysisError.guarantee(isReaderInstalled());
        Object[] arrayElements = getConstantData().arrayElementValues;
        AnalysisError.guarantee(arrayElements != null);
        return arrayElements;
    }

    /**
     * Return the value of the element at the specified index as computed by
     * {@link ImageHeapScanner#onArrayElementReachable(ImageHeapArray, AnalysisType, JavaConstant, int, ObjectScanner.ScanReason, Consumer)}.
     */
    @Override
    public Object getElement(int idx) {
        return arrayHandle.getVolatile(getElementValues(), idx);
    }

    /**
     * Returns the element value, i.e., a {@link JavaConstant}. If the value is not yet materialized
     * then the future is executed on the current thread.
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public JavaConstant readElementValue(int index) {
        Object value = getElement(index);
        return value instanceof JavaConstant ? (JavaConstant) value : ((AnalysisFuture<ImageHeapConstant>) value).ensureDone();
    }

    @Override
    public void setElement(int idx, JavaConstant value) {
        arrayHandle.setVolatile(getElementValues(), idx, value);
    }

    void setElementTask(int idx, AnalysisFuture<JavaConstant> task) {
        arrayHandle.setVolatile(getElementValues(), idx, task);
    }

    @Override
    public int getLength() {
        return getConstantData().length;
    }

    @Override
    public JavaConstant compress() {
        assert !compressed : this;
        return new ImageHeapObjectArray(constantData, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed : this;
        return new ImageHeapObjectArray(constantData, false);
    }

    @Override
    public ImageHeapConstant forObjectClone() {
        assert constantData.type.isCloneableWithAllocation() : "all arrays implement Cloneable";

        Object[] arrayElements = getElementValues();
        Objects.requireNonNull(arrayElements, "Cannot clone an array before the element values are set.");
        Object[] newArrayElementValues = Arrays.copyOf(arrayElements, arrayElements.length);
        /* The new constant is never backed by a hosted object, regardless of the input object. */
        return new ImageHeapObjectArray(new ObjectArrayData(constantData.type, null, newArrayElementValues, arrayElements.length), compressed);
    }
}
