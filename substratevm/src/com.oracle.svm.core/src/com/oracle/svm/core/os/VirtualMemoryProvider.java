/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointCreateIsolateParameters;

public interface VirtualMemoryProvider {
    @Fold
    static VirtualMemoryProvider get() {
        return ImageSingletons.lookup(VirtualMemoryProvider.class);
    }

    /**
     * Performs initializations <em>for the current isolate</em>, before any other methods of this
     * interface may be called.
     *
     * @return initialization result code, non-zero in case of an error.
     */
    @Uninterruptible(reason = "Still being initialized.")
    int initialize(WordPointer isolatePointer, CEntryPointCreateIsolateParameters parameters);

    /**
     * Tear down <em>for the current isolate</em>. This must be the last method of this interface
     * that is called in an isolate.
     */
    @Uninterruptible(reason = "Tear-down in progress.")
    int tearDown();

    /**
     * Reserve a block of virtual address space.
     *
     * @param size The size of the requested reservation.
     * @param executable If true, the space is allocated with execute permissions.
     * @return A pointer to the reserved space if successful, or {@link WordFactory#nullPointer()}
     *         otherwise.
     */
    Pointer allocateVirtualMemory(UnsignedWord size, boolean executable);

    /**
     * Delete the mapping for the specified range of virtual addresses.
     *
     * @param start A pointer returned by
     *            {@link VirtualMemoryProvider#allocateVirtualMemory(UnsignedWord, boolean)}
     * @param size The size of the allocation.
     * @return true on success, false otherwise.
     */
    boolean freeVirtualMemory(PointerBase start, UnsignedWord size);

    /**
     * Reserve a block of virtual address space at a given alignment.
     *
     * @param size The size of the requested reservation.
     * @param alignment The requested alignment.
     * @return A pointer to the reserved space if successful, or {@link WordFactory#nullPointer()}
     *         otherwise.
     */
    Pointer allocateVirtualMemoryAligned(UnsignedWord size, UnsignedWord alignment);

    /**
     * Release a reservation for a block of virtual address space at a given alignment.
     *
     * @param start A pointer returned by
     *            {@link VirtualMemoryProvider#allocateVirtualMemoryAligned(UnsignedWord, UnsignedWord)}
     * @param size The size of the allocation.
     * @param alignment The alignment of the allocation.
     * @return true on success, or false otherwise.
     */
    boolean freeVirtualMemoryAligned(PointerBase start, UnsignedWord size, UnsignedWord alignment);
}
