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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

/**
 * Primitive operations for low-level virtual memory management.
 */
public interface VirtualMemoryProvider {
    /**
     * Bitmask with the modes of protection for {@linkplain #commit committed} or
     * {@linkplain #mapFile mapped} memory.
     */
    interface Access {
        /**
         * Accessing the memory range is not permitted, but physical memory or swap memory might be
         * provisioned for it.
         */
        int NONE = 0;
        /** The memory range may be read. */
        int READ = (1 << 0);
        /** The memory range may be written. */
        int WRITE = (1 << 1);
        /** Instructions in the memory range may be executed. */
        int EXECUTE = (1 << 2);
    }

    @Fold
    static VirtualMemoryProvider get() {
        return ImageSingletons.lookup(VirtualMemoryProvider.class);
    }

    /**
     * Returns the granularity of virtual memory management, which is generally the operating
     * system's page size.
     */
    UnsignedWord getGranularity();

    /**
     * Returns the alignment used by virtual memory management, which is generally equal to the
     * {@linkplain #getGranularity granularity} or a multiple thereof.
     */
    default UnsignedWord getAlignment() {
        return getGranularity();
    }

    /**
     * Reserve an address range that fits the specified number of bytes. The reserved address range
     * is not intended to be accessed until {@link #commit} is called on the range (or on a part of
     * the range). Even then, the call to {@link #commit} is not guaranteed to succeed because no
     * physical memory or swap memory is guaranteed to be provisioned for the reserved range.
     *
     * @param nbytes The size in bytes of the address range to be reserved, which will be rounded up
     *            to a multiple of the {@linkplain #getGranularity() granularity}.
     * @param alignment The alignment in bytes of the start of the address range to be reserved.
     * @return An {@linkplain #getAlignment aligned} pointer to the beginning of the reserved
     *         address range, or {@link WordFactory#nullPointer()} in case of an error.
     */
    Pointer reserve(UnsignedWord nbytes, UnsignedWord alignment);

    /**
     * Map a region of an open file to the specified address range. When {@linkplain Access#WRITE
     * write access} is requested and mapped memory is written, that memory is copied before
     * writing, and no modifications are ever written to the underlying file (copy on write). If the
     * file's contents change after it has been mapped, it is undefined whether these changes become
     * visible through the mapping.
     *
     * @param start The start of the address range to contain the mapping, which must be a multiple
     *            of the {@linkplain #getGranularity() granularity}, or
     *            {@link WordFactory#nullPointer() null} to select an available (unreserved,
     *            uncommitted) address range in an arbitrary location.
     * @param nbytes The size in bytes of the file region to be mapped, which need not be a multiple
     *            of the {@linkplain #getGranularity() granularity}.
     * @param fileHandle A platform-specific open file handle.
     * @param offset The offset in bytes of the region within the file to be mapped, which must be a
     *            multiple of the {@linkplain #getGranularity() granularity}.
     * @param access The modes in which the memory is permitted to be accessed, see {@link Access}.
     * @return The start of the mapped address range, or {@link WordFactory#nullPointer()} in case
     *         of an error.
     */
    Pointer mapFile(PointerBase start, UnsignedWord nbytes, WordBase fileHandle, UnsignedWord offset, int access);

    /**
     * Commit an address range so that physical memory or swap memory can be provisioned for it, and
     * the memory can be accessed in the specified {@linkplain Access access modes}. No guarantees
     * are made about the memory contents.
     * <p>
     * This method may be called for a specific range that was previously reserved with
     * {@link #reserve}, or for a range committed with {@link #commit}, or for a subrange of such
     * ranges. If the provided range covers addresses outside of such ranges, or from multiple
     * independently reserved ranges, undefined effects can occur.
     * <p>
     * Alternatively, {@link WordFactory#nullPointer() NULL} can be passed for the start address, in
     * which case an available (unreserved, uncommitted) address range in an arbitrary but
     * {@linkplain #getAlignment aligned} location will be selected, reserved and committed in one
     * step.
     *
     * @param start The start of the address range to be committed, which must be a multiple of the
     *            {@linkplain #getGranularity() granularity}, or {@link WordFactory#nullPointer()
     *            NULL} to select an available (unreserved, uncommitted) address range in an
     *            arbitrary but {@linkplain #getAlignment aligned} location.
     * @param nbytes The size in bytes of the address range to be committed, which will be rounded
     *            up to a multiple of the {@linkplain #getGranularity() granularity}.
     * @param access The modes in which the memory is permitted to be accessed, see {@link Access}.
     * @return The start of the committed address range, or {@link WordFactory#nullPointer()} in
     *         case of an error, such as inadequate physical memory.
     */
    Pointer commit(PointerBase start, UnsignedWord nbytes, int access);

    /**
     * Change the protection of a committed address range, or of a subrange of a committed address
     * range, so that the memory can be accessed in the specified {@linkplain Access access modes}.
     *
     * @param start The start of the address range to be protected, which must be a multiple of the
     *            {@linkplain #getGranularity() granularity}.
     * @param nbytes The size in bytes of the address range to be protected, which will be rounded
     *            up to a multiple of the {@linkplain #getGranularity() granularity}.
     * @param access The modes in which the memory is permitted to be accessed, see {@link Access}.
     * @return 0 when successful, or a non-zero implementation-specific error code.
     */
    int protect(PointerBase start, UnsignedWord nbytes, int access);

    /**
     * Uncommit a committed address range, or a subrange of a committed address range, so that it
     * returns to {@linkplain #reserve reserved state} in which the memory is not intended to be
     * accessed, and no physical memory or swap memory is guaranteed to be provisioned for it.
     * Calling this method for an already uncommitted (reserved) or only partially committed address
     * range is not an error.
     *
     * @param start The start of the address range to be uncommitted, which must be a multiple of
     *            the {@linkplain #getGranularity() granularity}.
     * @param nbytes The size in bytes of the address range to be uncommitted, which will be rounded
     *            up to a multiple of the {@linkplain #getGranularity() granularity}.
     * @return 0 when successful, or a non-zero implementation-specific error code.
     */
    int uncommit(PointerBase start, UnsignedWord nbytes);

    /**
     * Free an entire reserved address range (which may be committed or partially committed). No
     * subrange of a reserved range and no non-reserved range must be specified, or undefined
     * effects can occur. After the address range has been successfully freed, it becomes available
     * for reuse by the system and might be returned from future calls to {@link #reserve} and
     * {@link #commit commit(NULL, ..)}.
     *
     * @param start The start of the address range to be freed, which must be the exact address that
     *            was originally returned by {@link #reserve} or {@link #commit commit(NULL, ..)}
     * @param nbytes The size in bytes of the address range to be freed, which must be the exact
     *            size that was originally passed to {@link #reserve} or {@link #commit commit(NULL,
     *            ..)}
     * @return 0 when successful, or a non-zero implementation-specific error code.
     */
    int free(PointerBase start, UnsignedWord nbytes);
}
