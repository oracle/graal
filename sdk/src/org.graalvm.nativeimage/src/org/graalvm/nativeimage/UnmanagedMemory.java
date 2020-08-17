/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
 * @since 19.0
 */
public final class UnmanagedMemory {

    private UnmanagedMemory() {
    }

    /**
     * Allocates {@code size} bytes of unmanaged memory. The content of the memory is undefined.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer. This
     * method never returns a the null pointer, but instead throws a {@link OutOfMemoryError} when
     * allocation fails.
     *
     * @since 19.0
     */
    public static <T extends PointerBase> T malloc(UnsignedWord size) {
        T result = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(size);
        if (result.isNull()) {
            throw new OutOfMemoryError("malloc of unmanaged memory");
        }
        return result;
    }

    /**
     * Allocates {@code size} bytes of unmanaged memory. The content of the memory is undefined.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer. This
     * method never returns a the null pointer, but instead throws a {@link OutOfMemoryError} when
     * allocation fails.
     *
     * @since 19.0
     */
    public static <T extends PointerBase> T malloc(int size) {
        return malloc(WordFactory.unsigned(size));
    }

    /**
     * Allocates {@code size} bytes of unmanaged memory. The content of the memory is set to 0.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer. This
     * method never returns a the null pointer, but instead throws a {@link OutOfMemoryError} when
     * allocation fails.
     *
     * @since 19.0
     */
    public static <T extends PointerBase> T calloc(UnsignedWord size) {
        T result = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(size);
        if (result.isNull()) {
            throw new OutOfMemoryError("calloc of unmanaged memory");
        }
        return result;
    }

    /**
     * Allocates {@code size} bytes of unmanaged memory. The content of the memory is set to 0.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer. This
     * method never returns a the null pointer, but instead throws a {@link OutOfMemoryError} when
     * allocation fails.
     *
     * @since 19.0
     */
    public static <T extends PointerBase> T calloc(int size) {
        return calloc(WordFactory.unsigned(size));
    }

    /**
     * Changes the size of the provided unmanaged memory to {@code size} bytes of unmanaged memory.
     * If the new size is larger than the old size, the content of the additional memory is
     * undefined.
     * <p>
     * If {@code size} is 0, the method is allowed but not required to return the null pointer. This
     * method never returns a the null pointer, but instead throws a {@link OutOfMemoryError} when
     * allocation fails.
     *
     * @since 19.0
     */
    public static <T extends PointerBase> T realloc(T ptr, UnsignedWord size) {
        T result = ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(ptr, size);
        if (result.isNull()) {
            throw new OutOfMemoryError("realloc of unmanaged memory");
        }
        return result;
    }

    /**
     * Frees unmanaged memory that was previously allocated using methods of this class.
     *
     * @since 19.0
     */
    public static void free(PointerBase ptr) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(ptr);
    }
}
