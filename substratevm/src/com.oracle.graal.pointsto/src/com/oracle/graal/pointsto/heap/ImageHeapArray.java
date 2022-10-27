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

import java.util.function.Consumer;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ImageHeapArray extends ImageHeapConstant {

    /**
     * Contains the already scanned array elements.
     */
    private final JavaConstant[] arrayElementValues;

    public ImageHeapArray(ResolvedJavaType type, int length) {
        this(type, null, new JavaConstant[length]);
    }

    public ImageHeapArray(ResolvedJavaType type, JavaConstant object, int length) {
        this(type, object, new JavaConstant[length]);
    }

    ImageHeapArray(ResolvedJavaType type, JavaConstant object, JavaConstant[] arrayElementValues) {
        this(type, object, arrayElementValues, createIdentityHashCode(object), false);
    }

    private ImageHeapArray(ResolvedJavaType type, JavaConstant object, JavaConstant[] arrayElementValues, int identityHashCode, boolean compressed) {
        super(type, object, identityHashCode, compressed);
        assert type.isArray();
        this.arrayElementValues = arrayElementValues;
    }

    /**
     * Return the value of the element at the specified index as computed by
     * {@link ImageHeapScanner#onArrayElementReachable(ImageHeapArray, AnalysisType, JavaConstant, int, ObjectScanner.ScanReason, Consumer)}.
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

    @Override
    public JavaConstant compress() {
        assert !compressed;
        return new ImageHeapArray(type, hostedObject, arrayElementValues, identityHashCode, true);
    }

    @Override
    public JavaConstant uncompress() {
        assert compressed;
        return new ImageHeapArray(type, hostedObject, arrayElementValues, identityHashCode, false);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ImageHeapArray) {
            return super.equals(o) && this.arrayElementValues == ((ImageHeapArray) o).arrayElementValues;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + System.identityHashCode(arrayElementValues);
        return result;
    }
}
