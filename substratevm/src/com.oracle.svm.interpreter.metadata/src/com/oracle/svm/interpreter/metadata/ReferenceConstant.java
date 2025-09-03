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
package com.oracle.svm.interpreter.metadata;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.Objects;

public final class ReferenceConstant<T> implements JavaConstant {

    private final T ref; // can be null

    // Opaque, used to identify constant on the image heap.
    private final long imageHeapOffset;

    private ReferenceConstant(T ref) {
        this.imageHeapOffset = 0L;
        this.ref = ref;
    }

    private ReferenceConstant(long heapOffset) {
        this.imageHeapOffset = heapOffset;
        this.ref = null;
    }

    @VisibleForSerialization
    public static <T> ReferenceConstant<T> createFromNonNullReference(T ref) {
        return new ReferenceConstant<>(MetadataUtil.requireNonNull(ref));
    }

    @VisibleForSerialization
    public static <T> ReferenceConstant<T> createFromReference(T ref) {
        if (ref == null) {
            // Cannot return the nullReference() singleton here since this method is
            // used for de-serialization where reference "identity" must be preserved.
            return new ReferenceConstant<>(null);
        }
        return createFromNonNullReference(ref);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static ReferenceConstant<?> createFromImageHeapConstant(ImageHeapConstant imageHeapConstant) {
        assert !imageHeapConstant.isNull() : imageHeapConstant;
        return new ReferenceConstant<>(Objects.requireNonNull(imageHeapConstant));
    }

    @VisibleForSerialization
    public static <T> ReferenceConstant<T> createFromHeapOffset(long nativeHeapOffset) {
        assert nativeHeapOffset != 0L;
        return new ReferenceConstant<>(nativeHeapOffset);
    }

    public T getReferent() {
        if (isOpaque()) {
            throw new UnsupportedOperationException("Cannot extract opaque referent");
        }
        return ref;
    }

    @Override
    public String toString() {
        return "ReferenceConstant<" + ref + ">";
    }

    public boolean isOpaque() {
        return imageHeapOffset != 0L;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return ref == null && imageHeapOffset == 0L;
    }

    @Override
    public boolean isDefaultForKind() {
        return isNull();
    }

    @Override
    public Object asBoxedPrimitive() {
        throw new IllegalArgumentException();
    }

    @Override
    public int asInt() {
        throw new IllegalArgumentException();
    }

    @Override
    public boolean asBoolean() {
        throw new IllegalArgumentException();
    }

    @Override
    public long asLong() {
        throw new IllegalArgumentException();
    }

    @Override
    public float asFloat() {
        throw new IllegalArgumentException();
    }

    @Override
    public double asDouble() {
        throw new IllegalArgumentException();
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(ref) ^ (int) (imageHeapOffset ^ (imageHeapOffset >>> 32));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ReferenceConstant<?> that && this.imageHeapOffset == that.imageHeapOffset && this.ref == that.ref;
    }

    private static final ReferenceConstant<?> NULL = new ReferenceConstant<>(null);

    @SuppressWarnings("unchecked")
    public static <R> ReferenceConstant<R> nullReference() {
        return (ReferenceConstant<R>) NULL;
    }
}
