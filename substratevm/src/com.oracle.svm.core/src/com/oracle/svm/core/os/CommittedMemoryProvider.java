/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import java.util.EnumSet;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;
import com.oracle.svm.core.heap.Heap;

/**
 * A provider of ranges of committed memory, which is virtual memory that is backed by physical
 * memory or swap space.
 */
public interface CommittedMemoryProvider {
    /**
     * Value for alignment parameters that indicates that no specific alignment is required (other
     * than the {@linkplain #getGranularity() granularity} usually).
     */
    UnsignedWord UNALIGNED = WordFactory.unsigned(1);

    @Fold
    static CommittedMemoryProvider get() {
        return ImageSingletons.lookup(CommittedMemoryProvider.class);
    }

    /**
     * Returns whether this provider will always guarantee a heap address space alignment of
     * {@link Heap#getPreferredAddressSpaceAlignment()} at image runtime, which may also depend on
     * {@link ImageHeapProvider#guaranteesHeapPreferredAddressSpaceAlignment()}.
     */
    @Fold
    boolean guaranteesHeapPreferredAddressSpaceAlignment();

    /**
     * Performs initializations <em>for the current isolate</em>, before any other methods of this
     * interface may be called.
     *
     * @return zero in case of success, non-zero in case of an error.
     */
    @Uninterruptible(reason = "Still being initialized.")
    int initialize(WordPointer isolatePointer, CEntryPointCreateIsolateParameters parameters);

    /**
     * Tear down <em>for the current isolate</em>. This must be the last method of this interface
     * that is called in an isolate.
     *
     * @return zero in case of success, non-zero in case of an error.
     */
    @Uninterruptible(reason = "Tear-down in progress.")
    int tearDown();

    /**
     * Returns the granularity of committed memory management, which is typically the same as that
     * of {@linkplain VirtualMemoryProvider#getGranularity() virtual memory management}.
     */
    @Uninterruptible(reason = "Still being initialized.", mayBeInlined = true)
    default UnsignedWord getGranularity() {
        return VirtualMemoryProvider.get().getGranularity();
    }

    Pointer allocateAlignedChunk(UnsignedWord nbytes, UnsignedWord alignment);

    Pointer allocateUnalignedChunk(UnsignedWord nbytes);

    Pointer allocateExecutableMemory(UnsignedWord nbytes, UnsignedWord alignment);

    /**
     * This method returns {@code true} if the memory returned by {@link #allocateUnalignedChunk} is
     * guaranteed to be zeroed.
     */
    boolean areUnalignedChunksZeroed();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void freeAlignedChunk(PointerBase start, UnsignedWord nbytes, UnsignedWord alignment);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void freeUnalignedChunk(PointerBase start, UnsignedWord nbytes);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void freeExecutableMemory(PointerBase start, UnsignedWord nbytes, UnsignedWord alignment);

    /**
     * Called by the garbage collector before a collection is started, as an opportunity to perform
     * lazy operations, sanity checks or clean-ups.
     */
    default void beforeGarbageCollection() {
    }

    /**
     * Called by the garbage collector after a collection has ended, as an opportunity to perform
     * lazy operations, sanity checks or clean-ups.
     */
    default void afterGarbageCollection() {
    }

    enum Access {
        READ,
        WRITE,
        EXECUTE
    }

    /**
     * Change access permissions for a block of committed memory that was allocated with one of the
     * allocation methods.
     *
     * @param start The start of the address range to be protected, which must be a multiple of the
     *            {@linkplain #getGranularity() granularity}.
     * @param nbytes The size in bytes of the address range to be protected, which will be rounded
     *            up to a multiple of the {@linkplain #getGranularity() granularity}.
     * @param access The modes in which the memory is permitted to be accessed, see {@link Access}.
     * @return 0 when successful, or a non-zero implementation-specific error code.
     */
    int protect(PointerBase start, UnsignedWord nbytes, EnumSet<Access> access);
}
