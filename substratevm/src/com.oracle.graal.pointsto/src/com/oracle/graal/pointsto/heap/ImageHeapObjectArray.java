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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AnalysisFuture;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ImageHeapObjectArray extends ImageHeapArray {

    private static final VarHandle arrayHandle = MethodHandles.arrayElementVarHandle(Object[].class);

    final int length;
    /**
     * A reference to the element values array. It is only set when the constant is initialized.
     * Each value is either an {@link AnalysisFuture} of {@link JavaConstant} or its result, a
     * {@link JavaConstant}, indexed by array index.
     */
    private final AtomicReference<Object[]> arrayElementValuesRef;

    ImageHeapObjectArray(ResolvedJavaType type, int length) {
        this(type, null, new Object[length], length);
    }

    ImageHeapObjectArray(ResolvedJavaType type, JavaConstant object, int length) {
        this(type, object, null, length);
    }

    ImageHeapObjectArray(ResolvedJavaType type, JavaConstant object, Object[] arrayElementValues, int length) {
        this(type, object, arrayElementValues, length, createIdentityHashCode(object), false);
    }

    private ImageHeapObjectArray(ResolvedJavaType type, JavaConstant object, Object[] arrayElementValues, int length, int identityHashCode, boolean compressed) {
        super(type, object, identityHashCode, compressed);
        this.arrayElementValuesRef = new AtomicReference<>(arrayElementValues);
        this.length = length;
    }

    private ImageHeapObjectArray(ResolvedJavaType type, JavaConstant object, AtomicReference<Object[]> arrayElementValuesRef, int length, int identityHashCode, boolean compressed) {
        super(type, object, identityHashCode, compressed);
        assert type.isArray() : type;
        this.arrayElementValuesRef = arrayElementValuesRef;
        this.length = length;
    }

    void setElementValues(Object[] elementValues) {
        boolean success = this.arrayElementValuesRef.compareAndSet(null, elementValues);
        AnalysisError.guarantee(success, "Unexpected field values reference for constant %s", this);
    }

    /**
     * Return the value of the element at the specified index as computed by
     * {@link ImageHeapScanner#onArrayElementReachable(ImageHeapArray, AnalysisType, JavaConstant, int, ObjectScanner.ScanReason, Consumer)}.
     */
    @Override
    public Object getElement(int idx) {
        AnalysisError.guarantee(isReaderInstalled());
        return arrayHandle.getVolatile(this.arrayElementValuesRef.get(), idx);
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
        AnalysisError.guarantee(isReaderInstalled());
        arrayHandle.setVolatile(this.arrayElementValuesRef.get(), idx, value);
    }

    void setElementTask(int idx, AnalysisFuture<JavaConstant> task) {
        AnalysisError.guarantee(isReaderInstalled());
        arrayHandle.setVolatile(this.arrayElementValuesRef.get(), idx, task);
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public JavaConstant compress() {
        assert !compressed : this;
        return new ImageHeapObjectArray(type, hostedObject, arrayElementValuesRef, length, identityHashCode, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed : this;
        return new ImageHeapObjectArray(type, hostedObject, arrayElementValuesRef, length, identityHashCode, false);
    }

    @Override
    public ImageHeapConstant forObjectClone() {
        assert type.isCloneableWithAllocation() : "all arrays implement Cloneable";

        Object[] newArrayElementValues = Arrays.copyOf(arrayElementValuesRef.get(), arrayElementValuesRef.get().length);
        /* The new constant is never backed by a hosted object, regardless of the input object. */
        JavaConstant newObject = null;
        return new ImageHeapObjectArray(type, newObject, newArrayElementValues, length, createIdentityHashCode(newObject), compressed);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageHeapObjectArray) {
            return super.equals(o) && this.arrayElementValuesRef == ((ImageHeapObjectArray) o).arrayElementValuesRef;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + System.identityHashCode(arrayElementValuesRef);
        return result;
    }
}
