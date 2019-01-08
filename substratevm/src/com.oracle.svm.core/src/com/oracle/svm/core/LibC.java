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
package com.oracle.svm.core;

import com.oracle.svm.core.annotate.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

/**
 * Platform independent access to common LibC functions.
 */
public class LibC {

    @Platforms(Platform.HOSTED_ONLY.class)
    protected LibC() {
    }

    /**
     * The malloc() function allocates size bytes and returns a pointer to the allocated memory. The
     * memory is not initialized. If size is 0, then malloc() returns either NULL, or a unique
     * pointer value that can later be successfully passed to free().
     *
     * @return The malloc() and calloc() functions return a pointer to the allocated memory that is
     *         suitably aligned for any kind of variable. On error, these functions return NULL.
     *         NULL may also be returned by a successful call to malloc() with a size of zero, or by
     *         a successful call to calloc() with nmemb or size equal to zero.
     *
     */
    @Uninterruptible(reason = "called from Uninterruptible function")
    public static <T extends PointerBase> T malloc(UnsignedWord size) {
        return PlatformLibC.singleton().malloc(size);
    }

    /**
     * The calloc() function allocates memory for an array of nmemb elements of size bytes each and
     * returns a pointer to the allocated memory. The memory is set to zero. If nmemb or size is 0,
     * then calloc() returns either NULL, or a unique pointer value that can later be successfully
     * passed to free().
     *
     * @return The malloc() and calloc() functions return a pointer to the allocated memory that is
     *         suitably aligned for any kind of variable. On error, these functions return NULL.
     *         NULL may also be returned by a successful call to malloc() with a size of zero, or by
     *         a successful call to calloc() with nmemb or size equal to zero.
     */
    @Uninterruptible(reason = "called from Uninterruptible function")
    public static <T extends PointerBase> T calloc(UnsignedWord nmemb, UnsignedWord size) {
        return PlatformLibC.singleton().calloc(nmemb, size);
    }

    /**
     * The realloc() function changes the size of the memory block pointed to by ptr to size bytes.
     * The contents will be unchanged in the range from the start of the region up to the minimum of
     * the old and new sizes. If the new size is larger than the old size, the added memory will not
     * be initialized. If ptr is NULL, then the call is equivalent to malloc(size), for all values
     * of size; if size is equal to zero, and ptr is not NULL, then the call is equivalent to
     * free(ptr). Unless ptr is NULL, it must have been returned by an earlier call to malloc(),
     * calloc() or realloc(). If the area pointed to was moved, a free(ptr) is done.
     *
     * @return The realloc() function returns a pointer to the newly allocated memory, which is
     *         suitably aligned for any kind of variable and may be different from ptr, or NULL if
     *         the request fails. If size was equal to 0, either NULL or a pointer suitable to be
     *         passed to free() is returned. If realloc() fails the original block is left
     *         untouched; it is not freed or moved.
     */
    @Uninterruptible(reason = "called from Uninterruptible function")
    public static <T extends PointerBase> T realloc(PointerBase ptr, UnsignedWord size) {
        return PlatformLibC.singleton().realloc(ptr, size);
    }

    /**
     * The free() function frees the memory space pointed to by ptr, which must have been returned
     * by a previous call to malloc(), calloc() or realloc(). Otherwise, or if free(ptr) has already
     * been called before, undefined behavior occurs. If ptr is NULL, no operation is performed.
     */
    @Uninterruptible(reason = "called from Uninterruptible function")
    public static void free(PointerBase ptr) {
        PlatformLibC.singleton().free(ptr);
    }
}
