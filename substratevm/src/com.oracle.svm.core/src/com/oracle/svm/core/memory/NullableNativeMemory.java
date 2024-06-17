/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.core.memory;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.nmt.NmtMallocHeader;

/**
 * Internal API for managing native memory. This class supports native memory tracking (NMT) and is
 * therefore preferred over the public API class {@link UnmanagedMemory} and its
 * {@link UnmanagedMemorySupport implementations}.
 *
 * All methods that allocate native memory return {@code null} if the memory allocation fails.
 */
public class NullableNativeMemory {
    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is undefined.
     * <p>
     * This method returns a null pointer when allocation fails.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T malloc(UnsignedWord size, NmtCategory category) {
        T outerPointer = UntrackedNullableNativeMemory.malloc(getAllocationSize(size));
        return track(outerPointer, size, category);
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is undefined.
     * <p>
     * This method returns a null pointer when allocation fails.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T malloc(int size, NmtCategory category) {
        assert size >= 0;
        return malloc(WordFactory.unsigned(size), category);
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is set to 0.
     * <p>
     * This method returns a null pointer when allocation fails.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T calloc(UnsignedWord size, NmtCategory category) {
        T outerPointer = UntrackedNullableNativeMemory.calloc(getAllocationSize(size));
        return track(outerPointer, size, category);
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is set to 0.
     * <p>
     * This method returns a null pointer when allocation fails.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T calloc(int size, NmtCategory category) {
        assert size >= 0;
        return calloc(WordFactory.unsigned(size), category);
    }

    /**
     * Changes the size of the provided native memory to {@code size} bytes. If the new size is
     * larger than the old size, the content of the additional memory is undefined.
     * <p>
     * This method returns a null pointer when allocation fails. In that case, the old data is not
     * deallocated and remains unchanged.
     */
    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T realloc(T ptr, UnsignedWord size, NmtCategory category) {
        if (ptr.isNull()) {
            return malloc(size, category);
        } else if (!VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            return UntrackedNullableNativeMemory.realloc(ptr, getAllocationSize(size));
        }

        /* Query the NMT information for the old allocation. */
        NmtMallocHeader header = NativeMemoryTracking.getHeader(ptr);
        T oldOuterPointer = (T) header;
        int oldCategory = header.getCategory();
        UnsignedWord oldSize = header.getAllocationSize();

        /* Try to realloc. */
        T newOuterPointer = UntrackedNullableNativeMemory.realloc(oldOuterPointer, getAllocationSize(size));
        if (newOuterPointer.isNull()) {
            return WordFactory.nullPointer();
        }

        oldOuterPointer = WordFactory.nullPointer();

        /* Only untrack the old block if the allocation was successful. */
        NativeMemoryTracking.singleton().untrack(oldSize, oldCategory);
        return track(newOuterPointer, size, category);
    }

    /**
     * Changes the size of the provided native memory to {@code size} bytes. If the new size is
     * larger than the old size, the content of the additional memory is undefined.
     * <p>
     * This method returns a null pointer when allocation fails. In that case, the old data is not
     * deallocated and remains unchanged.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T realloc(T ptr, int size, NmtCategory category) {
        assert size >= 0;
        return realloc(ptr, WordFactory.unsigned(size), category);
    }

    /**
     * Frees native memory that was previously allocated using methods of this class. This method is
     * a no-op if the given pointer is {@code null}.
     * <p>
     * Note that this method must <b>NOT</b> be used to free memory that was allocated via other
     * classes (e.g., {@link UnmanagedMemorySupport}) or outside of Native Image code (e.g., in a C
     * library).
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void free(PointerBase ptr) {
        if (ptr.isNull()) {
            return;
        }

        PointerBase outerPtr = untrack(ptr);
        UntrackedNullableNativeMemory.free(outerPtr);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static UnsignedWord getAllocationSize(UnsignedWord size) {
        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            return size.add(NativeMemoryTracking.sizeOfNmtHeader());
        }
        return size;
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static <T extends PointerBase> T track(T outerPtr, UnsignedWord size, NmtCategory category) {
        if (VMInspectionOptions.hasNativeMemoryTrackingSupport() && outerPtr.isNonNull()) {
            T innerPtr = (T) NativeMemoryTracking.singleton().initializeHeader(outerPtr, size, category);
            NativeMemoryTracking.singleton().track(innerPtr);
            return innerPtr;
        }
        return outerPtr;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static PointerBase untrack(PointerBase innerPtr) {
        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            return NativeMemoryTracking.singleton().untrack(innerPtr);
        }
        return innerPtr;
    }
}
