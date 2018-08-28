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
package com.oracle.svm.core.windows.headers;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;

//Checkstyle: stop

/**
 * Basic functions from the standard Visual Studio C Run-Time library
 */
@CContext(WindowsDirectives.class)
@Platforms(Platform.WINDOWS.class)
public class LibC {

    /**
     * The memcpy() function copies n bytes from memory area src to memory area dest. The memory
     * areas must not overlap. Use memmove(3) if the memory areas do overlap.
     *
     * We re-wire `memcpy()` to `memmove()` in order to avoid backwards compatibility issues with
     * systems that run older versions of `glibc`. Without this change image construction would use
     * `glibc` from the machine that constructs the image. Then, the image would not link with older
     * `glibc` versions.
     *
     * @return The memcpy() function returns a pointer to dest.
     */
    @CFunction(value = "memmove", transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T memcpy(T dest, PointerBase src, UnsignedWord n);

    /**
     * The memmove() function copies n bytes from memory area src to memory area dest. The memory
     * areas may overlap: copying takes place as though the bytes in src are first copied into a
     * temporary array that does not overlap src or dest, and the bytes are then copied from the
     * temporary array to dest.
     *
     * @return The memmove() function returns a pointer to dest.
     */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T memmove(T dest, PointerBase src, UnsignedWord n);

    /**
     * The memset() function fills the first n bytes of the memory area pointed to by s with the
     * constant byte c.
     *
     * @return The memset() function returns a pointer to the memory area s.
     */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T memset(T s, SignedWord c, UnsignedWord n);

    /**
     * The bzero() function writes n zeroed bytes to the string s. If n is zero, bzero() does
     * nothing.
     */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native void bzero(PointerBase s, UnsignedWord n);

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
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T malloc(UnsignedWord size);

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
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T calloc(UnsignedWord nmemb, UnsignedWord size);

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
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T realloc(PointerBase ptr, UnsignedWord size);

    /**
     * The free() function frees the memory space pointed to by ptr, which must have been returned
     * by a previous call to malloc(), calloc() or realloc(). Otherwise, or if free(ptr) has already
     * been called before, undefined behavior occurs. If ptr is NULL, no operation is performed.
     */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native void free(PointerBase ptr);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native void exit(int status);

    public static final int EXIT_CODE_ABORT = 99;

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void abort() {
        exit(EXIT_CODE_ABORT);
    }

    /** Lexicographically compare null-terminated strings s1 and s2. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int strcmp(PointerBase s1, PointerBase s2);

    /**
     * The strcpy() and strncpy() functions return dst. The stpcpy() and stpncpy() functions return
     * a pointer to the terminating `\0' character of dst. If stpncpy() does not terminate dst with
     * a NUL character, it instead returns a pointer to dst[n] (which does not necessarily refer to
     * a valid memory location.)
     */

    /** Copy a zero-terminated string from source to destination. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strcpy(CCharPointer dst, CCharPointer src);

    /**
     * Copy a zero-terminated string from source to a zero-terminated destination of at most len
     * characters.
     */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strncpy(CCharPointer dst, CCharPointer src, UnsignedWord len);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native UnsignedWord strlcpy(CCharPointer dst, CCharPointer src, UnsignedWord len);

    /** Copy a zero-terminated string from source to a newly allocated destination. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strdup(CCharPointer src);

    /** Returns a pointer to the first occurrence of the character c in the string s. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strchr(CCharPointer s, int c);

    /** Calculate the length of a string. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native UnsignedWord strlen(CCharPointer s);

    /* Split a string into substrings at locations of delimiters, modifying the string in place. */
    @CFunction(value = "strtok_s", transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strtok_r(CCharPointer str, CCharPointer delim, CCharPointerPointer saveptr);

    /** Convert the of the string to an integer, according to the specified radix. */
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native long strtol(CCharPointer nptr, CCharPointerPointer endptr, int base);
}
