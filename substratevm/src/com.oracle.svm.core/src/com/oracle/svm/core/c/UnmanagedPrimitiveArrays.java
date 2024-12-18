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
package com.oracle.svm.core.c;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.word.Word;

/**
 * Support for allocating and accessing primitive element arrays created in unmanaged memory. They
 * can be passed between isolates if their lifecycle is managed correctly. These arrays must
 * eventually be freed by either calling {@linkplain #releaseUnmanagedArray} or by calling free on
 * the array pointer.
 */
public final class UnmanagedPrimitiveArrays {

    private static final UninterruptibleUtils.AtomicLong runtimeArraysInExistence = new UninterruptibleUtils.AtomicLong(0);
    private static final OutOfMemoryError OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate native array");

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends UnmanagedPrimitiveArray<?>> T createArray(int length, Class<?> arrayType, NmtCategory nmtCategory) {
        DynamicHub hub = SubstrateUtil.cast(arrayType, DynamicHub.class);
        VMError.guarantee(LayoutEncoding.isPrimitiveArray(hub.getLayoutEncoding()));
        UnsignedWord size = Word.unsigned(length).shiftLeft(LayoutEncoding.getArrayIndexShift(hub.getLayoutEncoding()));
        Pointer array = NullableNativeMemory.calloc(size, nmtCategory);
        if (array.isNull()) {
            throw OUT_OF_MEMORY_ERROR;
        }

        // already zero-initialized thanks to calloc()
        trackUnmanagedArray((UnmanagedPrimitiveArray<?>) array);
        return (T) array;
    }

    /** Begins tracking an array, e.g. when it is handed over from a different isolate. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void trackUnmanagedArray(@SuppressWarnings("unused") UnmanagedPrimitiveArray<?> array) {
        assert !Heap.getHeap().isInImageHeap((Pointer) array) : "must not track image heap objects";
        assert array.isNull() || runtimeArraysInExistence.incrementAndGet() > 0 : "overflow";
    }

    /** Untracks an array created at runtime, e.g. before it is handed over to another isolate. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void untrackUnmanagedArray(UnmanagedPrimitiveArray<?> array) {
        assert array.isNull() || runtimeArraysInExistence.getAndDecrement() > 0;
    }

    /** Releases an array created at runtime. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void releaseUnmanagedArray(UnmanagedPrimitiveArray<?> array) {
        untrackUnmanagedArray(array);
        NullableNativeMemory.free(array);
    }

    /** Returns a pointer to the address of the given index of an array. */
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T getAddressOf(UnmanagedPrimitiveArray<?> array, int layoutEncoding, int index) {
        assert index >= 0;
        return (T) ((Pointer) array).add(index << LayoutEncoding.getArrayIndexShift(layoutEncoding));
    }

    @Uninterruptible(reason = "Destination array must not move.")
    public static <T> T copyToHeap(UnmanagedPrimitiveArray<?> src, int srcPos, T dest, int destPos, int length) {
        DynamicHub destHub = KnownIntrinsics.readHub(dest);
        VMError.guarantee(LayoutEncoding.isPrimitiveArray(destHub.getLayoutEncoding()), "Copying is only supported for primitive arrays");
        VMError.guarantee(srcPos >= 0 && destPos >= 0 && length >= 0 && destPos + length <= ArrayLengthNode.arrayLength(dest));
        Pointer destAddressAtPos = Word.objectToUntrackedPointer(dest).add(LayoutEncoding.getArrayElementOffset(destHub.getLayoutEncoding(), destPos));
        Pointer srcAddressAtPos = getAddressOf(src, destHub.getLayoutEncoding(), srcPos);
        JavaMemoryUtil.copyPrimitiveArrayForward(srcAddressAtPos, destAddressAtPos, Word.unsigned(length).shiftLeft(LayoutEncoding.getArrayIndexShift(destHub.getLayoutEncoding())));
        return dest;
    }

    @Uninterruptible(reason = "Destination array must not move.")
    public static <T> T compareOrCopyToHeap(UnmanagedPrimitiveArray<?> src, int srcPos, T dest, int destPos, int length) {
        DynamicHub destHub = KnownIntrinsics.readHub(dest);
        VMError.guarantee(LayoutEncoding.isPrimitiveArray(destHub.getLayoutEncoding()), "Copying is only supported for primitive arrays");
        VMError.guarantee(srcPos >= 0 && destPos >= 0 && length >= 0 && destPos + length <= ArrayLengthNode.arrayLength(dest));
        Pointer destAddressAtPos = Word.objectToUntrackedPointer(dest).add(LayoutEncoding.getArrayElementOffset(destHub.getLayoutEncoding(), destPos));
        Pointer srcAddressAtPos = getAddressOf(src, destHub.getLayoutEncoding(), srcPos);

        UnsignedWord size = Word.unsigned(length).shiftLeft(LayoutEncoding.getArrayIndexShift(destHub.getLayoutEncoding()));

        // First compare until a difference is found
        UnsignedWord equalBytes = UnmanagedMemoryUtil.compare(srcAddressAtPos, destAddressAtPos, size);
        if (equalBytes.belowThan(size)) {
            // If the two arrays are not equal, copy over the remaining bytes
            UnsignedWord alignBits = Word.unsigned(0x7);
            UnsignedWord offset = equalBytes.and(alignBits.not());
            JavaMemoryUtil.copyPrimitiveArrayForward(srcAddressAtPos.add(offset), destAddressAtPos.add(offset), size.subtract(offset));
        }
        return dest;
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Destination array must not move.")
    public static <T> UnmanagedPrimitiveArray<?> copyFromHeap(T src, int srcPos, UnmanagedPrimitiveArray<?> dest, int destPos, int length) {
        DynamicHub srcHub = KnownIntrinsics.readHub(src);
        VMError.guarantee(LayoutEncoding.isPrimitiveArray(srcHub.getLayoutEncoding()), "Copying is only supported for primitive arrays");
        VMError.guarantee(srcPos >= 0 && destPos >= 0 && length >= 0 && srcPos + length <= ArrayLengthNode.arrayLength(src));
        Pointer srcAddressAtPos = Word.objectToUntrackedPointer(src).add(LayoutEncoding.getArrayElementOffset(srcHub.getLayoutEncoding(), srcPos));
        Pointer destAddressAtPos = getAddressOf(dest, srcHub.getLayoutEncoding(), destPos);
        JavaMemoryUtil.copyPrimitiveArrayForward(srcAddressAtPos, destAddressAtPos, Word.unsigned(length).shiftLeft(LayoutEncoding.getArrayIndexShift(srcHub.getLayoutEncoding())));
        return dest;
    }

}
