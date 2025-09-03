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

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.nmt.NmtCategory;

/**
 * Internal API for managing native memory. This class supports native memory tracking (NMT) and is
 * therefore preferred over the public API class {@link UnmanagedMemory} and its
 * {@link UnmanagedMemorySupport implementations}.
 *
 * All methods that allocate native memory throw an {@link OutOfMemoryError} if the memory
 * allocation fails. If native memory needs to be allocated from uninterruptible code, use
 * {@link NullableNativeMemory} instead.
 *
 * Note that NMT may cause segfaults in certain scenarios:
 * <ul>
 * <li>Native memory that was allocated outside of Java (e.g., in a C library) or via some API that
 * does not support NMT (e.g., {@link UnmanagedMemory}) may only be freed with
 * {@link UntrackedNullableNativeMemory#free}. This is necessary because NMT assumes that each
 * allocation has a custom header, which isn't true for such memory.</li>
 * <li>NMT accesses the image heap. If native memory needs to be allocated before the image heap is
 * mapped or after it was unmapped, {@link UntrackedNullableNativeMemory} must be used.</li>
 * </ul>
 */
public class NativeMemory {
    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is undefined.
     * <p>
     * This method never returns a null pointer but instead throws an {@link OutOfMemoryError} when
     * allocation fails.
     */
    public static <T extends PointerBase> T malloc(UnsignedWord size, NmtCategory category) {
        T result = NullableNativeMemory.malloc(size, category);
        if (result.isNull()) {
            throw new OutOfMemoryError("Memory allocation failed: malloc returned null.");
        }
        return result;
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is undefined.
     * <p>
     * This method never returns a null pointer but instead throws an {@link OutOfMemoryError} when
     * allocation fails.
     */
    public static <T extends PointerBase> T malloc(int size, NmtCategory category) {
        assert size >= 0;
        return malloc(Word.unsigned(size), category);
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is set to 0.
     * <p>
     * This method never returns a null pointer, but instead throws an {@link OutOfMemoryError} when
     * allocation fails.
     */
    public static <T extends PointerBase> T calloc(UnsignedWord size, NmtCategory category) {
        T result = NullableNativeMemory.calloc(size, category);
        if (result.isNull()) {
            throw new OutOfMemoryError("Memory allocation failed: calloc returned null.");
        }
        return result;
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is set to 0.
     * <p>
     * This method never returns a null pointer, but instead throws an {@link OutOfMemoryError} when
     * allocation fails.
     */
    public static <T extends PointerBase> T calloc(int size, NmtCategory category) {
        assert size >= 0;
        return calloc(Word.unsigned(size), category);
    }

    /**
     * Changes the size of the provided native memory to {@code size} bytes. If the new size is
     * larger than the old size, the content of the additional memory is undefined.
     * <p>
     * This method never returns a null pointer, but instead throws an {@link OutOfMemoryError} when
     * allocation fails. In that case, the old data is not deallocated and remains unchanged.
     */
    public static <T extends PointerBase> T realloc(T ptr, UnsignedWord size, NmtCategory category) {
        T result = NullableNativeMemory.realloc(ptr, size, category);
        if (result.isNull()) {
            throw new OutOfMemoryError("Memory allocation failed: realloc returned null.");
        }
        return result;
    }

    /**
     * Changes the size of the provided native memory to {@code size} bytes. If the new size is
     * larger than the old size, the content of the additional memory is undefined.
     * <p>
     * This method never returns a null pointer, but instead throws an {@link OutOfMemoryError} when
     * allocation fails. In that case, the old data is not deallocated and remains unchanged.
     */
    public static <T extends PointerBase> T realloc(T ptr, int size, NmtCategory category) {
        assert size >= 0;
        return realloc(ptr, Word.unsigned(size), category);
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
        NullableNativeMemory.free(ptr);
    }
}
