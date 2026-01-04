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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.image.ImageHeapLayouter;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * Provides new instances of the image heap for creating isolates. The same image heap provider
 * implementation can be shared by different garbage collectors.
 *
 * If {@link SubstrateOptions#SpawnIsolates} is disabled, the image heap is loaded and mapped by the
 * operating system instead of an image heap provider (see {@link OSCommittedMemoryProvider}). Note
 * that this mode is deprecated and not covered by the documentation below.
 *
 * If {@link SubstrateOptions#SpawnIsolates} is enabled, a heap base is used and the image heap is
 * explicitly mapped into a contiguous address space. Here is the typical memory layout of the
 * address space at run-time:
 *
 * <pre>
 * |----------------------------------------------------------------------------------|
 * | protected memory | metaspace  |         read-only        | writable | read-only  |
 * |   null regions   | (optional) |                          |          | (optional) |
 * |----------------------------------------------------------------------------------|
 * |                               | normal data | relocatable data |   normal data   |
 * |----------------------------------------------------------------------------------|
 * ^                               ^                                                  ^
 * heapBase                 image heap begin                                  image heap end
 * </pre>
 *
 * The memory at the heap base is explicitly marked as inaccessible. Accesses to it will result in a
 * segfault.
 *
 * Note that the relocatable data may overlap with both the read-only and writable part of the image
 * heap. Besides that, parts of the read-only relocatable data may be writable at run-time.
 *
 * For more information on the structure of the image heap, see the classes that implement
 * {@link ImageHeapLayouter}.
 */
public interface ImageHeapProvider {
    @Fold
    static ImageHeapProvider get() {
        return ImageSingletons.lookup(ImageHeapProvider.class);
    }

    /**
     * Creates a new instance of the image heap.
     *
     * @param reservedAddressSpace If non-null, this specifies the address of a contiguous block of
     *            memory in which the image heap and the Java heap should be placed. If null, the
     *            {@link ImageHeapProvider} is responsible for allocating sufficient memory for the
     *            image heap at an arbitrary address.
     * @param reservedSize If {@code reservedAddressSpace} is non-null, the number of reserved bytes
     *            at that address.
     * @param heapBaseOut An address where a pointer to the heap base will be written to. Must not
     *            be null.
     * @param imageHeapEndOut An address where a pointer to the end of the image heap will be
     *            written to. Must not be null.
     * @return a result code from {@link CEntryPointErrors}.
     */
    @Uninterruptible(reason = "Still being initialized.")
    int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer heapBaseOut, WordPointer imageHeapEndOut);

    /**
     * Disposes an instance of the image heap that was created with this provider. This method must
     * only be called if the image heap memory was allocated by the {@link ImageHeapProvider}.
     */
    @Uninterruptible(reason = "Called during isolate tear-down.")
    int freeImageHeap(PointerBase heapBase);

    /**
     * Returns the end offset of the image heap, relative to the heap base (excluding any auxiliary
     * images). This value is a multiple of the build-time page size.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    UnsignedWord getImageHeapEndOffsetInAddressSpace();

    /**
     * Number of reserved image heap bytes (excluding any auxiliary images). This value is a
     * multiple of the build-time page size.
     */
    UnsignedWord getImageHeapReservedBytes();

    /**
     * Number of mapped image heap bytes (excluding any auxiliary images). This value is a multiple
     * of the build-time page size.
     */
    UnsignedWord getImageHeapMappedBytes();
}
