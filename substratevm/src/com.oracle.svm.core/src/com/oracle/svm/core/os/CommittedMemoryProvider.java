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

import com.oracle.svm.core.annotate.Uninterruptible;
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

    /**
     * Allocate a block of committed memory.
     *
     * @param nbytes The number of bytes to allocate, which is rounded up to the next multiple of
     *            the {@linkplain #getGranularity() granularity} if required.
     * @param alignment The required alignment of the block start, or {@link #UNALIGNED}.
     * @param executable Whether the block must be executable.
     * @return The start of the allocated block, or {@link WordFactory#nullPointer()} in case of an
     *         error.
     */
    Pointer allocate(UnsignedWord nbytes, UnsignedWord alignment, boolean executable);

    /**
     * Release a block of committed memory that was allocated with {@link #allocate}, requiring the
     * exact same parameter values that were originally passed to {@link #allocate}.
     *
     * @param start The start of the memory block, as returned by {@link #allocate}.
     * @param nbytes The originally requested size in bytes.
     * @param alignment The originally requested alignment.
     * @param executable Whether the block was requested to be executable.
     * @return true on success, or false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean free(PointerBase start, UnsignedWord nbytes, UnsignedWord alignment, boolean executable);

    /**
     * Called by the garbage collector before a collection is started, as an opportunity to perform
     * lazy operations, sanity checks or clean-ups.
     */
    default void beforeGarbageCollection() {
    }

    /**
     * Called by the garbage collector after a collection has ended, as an opportunity to perform
     * lazy operations, sanity checks or clean-ups.
     *
     * @param completeCollection Whether the garbage collector has performed a full collection.
     */
    default void afterGarbageCollection(boolean completeCollection) {
    }

    enum Access {
        READ,
        WRITE,
        EXECUTE
    }

    /**
     * Change access permissions for a block of committed memory that was allocated with
     * {@link #allocate}.
     *
     * @param start The start of the memory block
     * @param nbytes Length of the memory block
     * @param access protection setting
     * @return true on success, false otherwise
     */
    boolean protect(PointerBase start, UnsignedWord nbytes, EnumSet<Access> access);
}
