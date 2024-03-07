/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public final class ImageHeapPrimitiveArray extends ImageHeapArray {

    private static final class PrimitiveArrayData extends ConstantData {

        private final Object array;
        private final int length;

        private PrimitiveArrayData(AnalysisType type, JavaConstant hostedObject, Object array, int length) {
            super(type, hostedObject);
            this.array = array;
            this.length = length;
            assert type.isArray() && type.getComponentType().isPrimitive() : type;
        }
    }

    ImageHeapPrimitiveArray(AnalysisType type, int length) {
        super(new PrimitiveArrayData(type, null,
                        /* Without a hosted object, we need to create a backing primitive array. */
                        Array.newInstance(type.getComponentType().getStorageKind().toJavaClass(), length),
                        length), false);
    }

    ImageHeapPrimitiveArray(AnalysisType type, JavaConstant hostedObject, Object array, int length) {
        super(new PrimitiveArrayData(type, hostedObject,
                        /* We need a clone of the hosted array so that we have a stable snapshot. */
                        getClone(type.getComponentType().getJavaKind(), array),
                        length), false);
    }

    private ImageHeapPrimitiveArray(ConstantData constantData, boolean compressed) {
        super(constantData, compressed);
    }

    @Override
    public PrimitiveArrayData getConstantData() {
        return (PrimitiveArrayData) super.getConstantData();
    }

    private static Object getClone(JavaKind kind, Object arrayObject) {
        return switch (kind) {
            case Boolean -> ((boolean[]) arrayObject).clone();
            case Byte -> ((byte[]) arrayObject).clone();
            case Short -> ((short[]) arrayObject).clone();
            case Char -> ((char[]) arrayObject).clone();
            case Int -> ((int[]) arrayObject).clone();
            case Long -> ((long[]) arrayObject).clone();
            case Float -> ((float[]) arrayObject).clone();
            case Double -> ((double[]) arrayObject).clone();
            default -> throw new IllegalArgumentException("Unsupported kind: " + kind);
        };
    }

    public Object getArray() {
        return getConstantData().array;
    }

    /**
     * Return the value of the element at the specified index as computed by
     * {@link ImageHeapScanner#onArrayElementReachable(ImageHeapArray, AnalysisType, JavaConstant, int, ObjectScanner.ScanReason, Consumer)}.
     */
    @Override
    public Object getElement(int idx) {
        return readElementValue(idx);
    }

    @Override
    public JavaConstant readElementValue(int idx) {
        return JavaConstant.forBoxedPrimitive(Array.get(getArray(), idx));
    }

    @Override
    public void setElement(int idx, JavaConstant value) {
        if (value.getJavaKind() != constantData.type.getComponentType().getJavaKind()) {
            throw AnalysisError.shouldNotReachHere("Cannot store value of kind " + value.getJavaKind() + " into primitive array of type " + getConstantData().type);
        }
        Array.set(getArray(), idx, value.asBoxedPrimitive());
    }

    @Override
    public int getLength() {
        return getConstantData().length;
    }

    @Override
    public JavaConstant compress() {
        assert !compressed : this;
        return new ImageHeapPrimitiveArray(constantData, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed : this;
        return new ImageHeapPrimitiveArray(constantData, false);
    }

    @Override
    public ImageHeapConstant forObjectClone() {
        assert constantData.type.isCloneableWithAllocation() : "all arrays implement Cloneable";

        PrimitiveArrayData data = getConstantData();
        Object newArray = getClone(data.type.getComponentType().getJavaKind(), data.array);
        /* The new constant is never backed by a hosted object, regardless of the input object. */
        return new ImageHeapPrimitiveArray(new PrimitiveArrayData(data.type, null, newArray, data.length), compressed);
    }
}
