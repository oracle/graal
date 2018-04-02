/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage;

import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

/**
 * Contains static methods that allow allocate/free of unmanaged memory, i.e., memory that is not
 * under the control of the garbage collector. In a typical C environment, these are the malloc/free
 * functions of the standard C library, however this class makes no assumptions or guarantees about
 * how the memory is managed. In particular, it is not allowed to free memory returned by these
 * allocation function directly using the standard C library (or vice versa).
 *
 * @since 1.0
 */
public final class UnmanagedMemory {

    private UnmanagedMemory() {
    }

    /**
     * Allocates {@code size} bytes of unmanaged memory. The content of the memory is undefined.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer.
     *
     * @since 1.0
     */
    public static <T extends PointerBase> T malloc(UnsignedWord size) {
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(size);
    }

    /**
     * Allocates {@code size} bytes of unmanaged memory. The content of the memory is undefined.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer.
     *
     * @since 1.0
     */
    public static <T extends PointerBase> T malloc(int size) {
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(size));
    }

    /**
     * Allocates {@code size} bytes of unmanaged memory. The content of the memory is set to 0.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer.
     *
     * @since 1.0
     */
    public static <T extends PointerBase> T calloc(UnsignedWord size) {
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(size);
    }

    /**
     * Allocates {@code size} bytes of unmanaged memory. The content of the memory is set to 0.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer.
     *
     * @since 1.0
     */
    public static <T extends PointerBase> T calloc(int size) {
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(WordFactory.unsigned(size));
    }

    /**
     * Changes the size of the provided unmanaged memory to {@code size} bytes of unmanaged memory.
     * If the new size is larger than the old size, the content of the additional memory is
     * undefined.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer.
     *
     * @since 1.0
     */
    public static <T extends PointerBase> T realloc(T ptr, UnsignedWord size) {
        return ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(ptr, size);
    }

    /**
     * Frees unmanaged memory that was previously allocated using methods of this class.
     *
     * @since 1.0
     */
    public static void free(PointerBase ptr) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(ptr);
    }
}
