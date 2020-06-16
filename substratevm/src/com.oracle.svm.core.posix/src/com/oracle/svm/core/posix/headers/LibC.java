/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;

// Checkstyle: stop

/**
 * Basic functions from the standard C library that we require to be present on all Posix platforms.
 */
public class LibC {

    /**
     * We re-wire `memcpy()` to `memmove()` in order to avoid backwards compatibility issues with
     * systems that run older versions of `glibc`. Without this change image construction would use
     * `glibc` from the machine that constructs the image. Then, the image would not link with older
     * `glibc` versions.
     */
    @CFunction(value = "memmove", transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T memcpy(T dest, PointerBase src, UnsignedWord n);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T memmove(T dest, PointerBase src, UnsignedWord n);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T memset(T s, SignedWord c, UnsignedWord n);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T malloc(UnsignedWord size);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T calloc(UnsignedWord nmemb, UnsignedWord size);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T realloc(PointerBase ptr, UnsignedWord size);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native void free(PointerBase ptr);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native void exit(int status);

    public static final int EXIT_CODE_ABORT = 99;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void abort() {
        /*
         * Using the abort system call has unexpected performance implications on Oracle Enterprise
         * Linux: Storing the crash dump information takes minutes even for tiny images. Therefore,
         * we just exit with an otherwise unused exit code.
         */
        exit(EXIT_CODE_ABORT);
    }

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int strcmp(PointerBase s1, PointerBase s2);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strcpy(CCharPointer dst, CCharPointer src);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strncpy(CCharPointer dst, CCharPointer src, UnsignedWord len);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native UnsignedWord strlcpy(CCharPointer dst, CCharPointer src, UnsignedWord len);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strdup(CCharPointer src);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strtok_r(CCharPointer str, CCharPointer delim, CCharPointerPointer saveptr);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native long strtol(CCharPointer nptr, CCharPointerPointer endptr, int base);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strstr(CCharPointer str, CCharPointer substr);
}
