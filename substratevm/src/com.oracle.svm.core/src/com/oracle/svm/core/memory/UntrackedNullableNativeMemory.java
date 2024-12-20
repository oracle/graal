/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Manages native memory. This class explicitly does <b>NOT</b> support native memory tracking
 * (NMT). It can therefore be used for the following use cases:
 * <ul>
 * <li>Allocate native memory that is later freed outside of Native Image code (e.g., in a C
 * library).</li>
 * <li>Free native memory that was allocated outside of Native Image code (e.g., in a C
 * library).</li>
 * </ul>
 */
public class UntrackedNullableNativeMemory {
    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is undefined.
     * <p>
     * This method returns a null pointer when allocation fails.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T malloc(UnsignedWord size) {
        return memory().malloc(size);
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is undefined.
     * <p>
     * This method returns a null pointer when allocation fails.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T malloc(int size) {
        assert size >= 0;
        return malloc(Word.unsigned(size));
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is set to 0.
     * <p>
     * This method returns a null pointer when allocation fails.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T calloc(UnsignedWord size) {
        return memory().calloc(size);
    }

    /**
     * Allocates {@code size} bytes of native memory. The content of the memory is set to 0.
     * <p>
     * This method returns a null pointer when allocation fails.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T calloc(int size) {
        assert size >= 0;
        return calloc(Word.unsigned(size));
    }

    /**
     * Changes the size of the provided native memory to {@code size} bytes. If the new size is
     * larger than the old size, the content of the additional memory is undefined.
     * <p>
     * This method returns a null pointer when allocation fails. In that case, the old data is not
     * deallocated and remains unchanged.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T realloc(T ptr, UnsignedWord size) {
        return memory().realloc(ptr, size);
    }

    /**
     * Changes the size of the provided native memory to {@code size} bytes. If the new size is
     * larger than the old size, the content of the additional memory is undefined.
     * <p>
     * This method returns a null pointer when allocation fails. In that case, the old data is not
     * deallocated and remains unchanged.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static <T extends PointerBase> T realloc(T ptr, int size) {
        assert size >= 0;
        return realloc(ptr, Word.unsigned(size));
    }

    /**
     * Frees native memory that was previously allocated using methods of this class or that was
     * allocated outside of Java (e.g., in a C library). This method is a no-op if the given pointer
     * is {@code null}.
     * <p>
     * Note that this method must <b>NOT</b> be used to free memory that was allocated via classes
     * that support native memory tracking (e.g., {@link NativeMemory} or
     * {@link NullableNativeMemory}).
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void free(PointerBase ptr) {
        memory().free(ptr);
    }

    @Fold
    static UnmanagedMemorySupport memory() {
        return ImageSingletons.lookup(UnmanagedMemorySupport.class);
    }
}
