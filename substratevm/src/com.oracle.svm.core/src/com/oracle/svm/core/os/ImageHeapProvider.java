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
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.heap.Heap;

/**
 * Provides new instances of the image heap for creating isolates. The same image heap provider
 * implementation can be shared by different garbage collectors.
 */
public interface ImageHeapProvider {
    @Fold
    static ImageHeapProvider get() {
        return ImageSingletons.lookup(ImageHeapProvider.class);
    }

    /**
     * Returns whether this provider, when not already supplied a reserved address space by
     * {@link CommittedMemoryProvider}, will always ensure a heap address space alignment of
     * {@link Heap#getPreferredAddressSpaceAlignment()} at image runtime.
     */
    @Fold
    boolean guaranteesHeapPreferredAddressSpaceAlignment();

    /**
     * Creates a new instance of the image heap.
     *
     * @param reservedAddressSpace If non-null, this specifies the address of a contiguous block of
     *            memory in which the image heap and the Java heap should be placed. If null, the
     *            {@link ImageHeapProvider} is responsible for allocating sufficient memory for the
     *            image heap at an arbitrary address.
     * @param reservedSize If {@code reservedAddressSpace} is non-null, the number of reserved bytes
     *            at that address.
     * @param basePointer An address where a pointer to the start address of the image heap instance
     *            will be written. Must not be null.
     * @param endPointer An address where a pointer to the end of the image heap instance will be
     *            written. May be null if this value is not required.
     * @return a result code from {@link CEntryPointErrors}.
     */
    int initialize(Pointer reservedAddressSpace, UnsignedWord reservedSize, WordPointer basePointer, WordPointer endPointer);

    /**
     * Disposes an instance of the image heap that was created with this provider. This method must
     * only be called if the image heap memory was allocated by the {@link ImageHeapProvider}.
     */
    int freeImageHeap(PointerBase heapBase);
}
