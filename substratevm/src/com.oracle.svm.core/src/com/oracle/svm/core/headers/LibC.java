/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.headers;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.Uninterruptible;

public class LibC {
    public static final int EXIT_CODE_ABORT = 99;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int errno() {
        return libc().errno();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setErrno(int value) {
        libc().setErrno(value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T memcpy(T dest, PointerBase src, UnsignedWord n) {
        return libc().memcpy(dest, src, n);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T memmove(T dest, PointerBase src, UnsignedWord n) {
        return libc().memmove(dest, src, n);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T memset(T s, SignedWord c, UnsignedWord n) {
        return libc().memset(s, c, n);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T malloc(UnsignedWord size) {
        return libc().malloc(size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T calloc(UnsignedWord nmemb, UnsignedWord size) {
        return libc().calloc(nmemb, size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static <T extends PointerBase> T realloc(PointerBase ptr, UnsignedWord size) {
        return libc().realloc(ptr, size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void free(PointerBase ptr) {
        libc().free(ptr);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void exit(int status) {
        libc().exit(status);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void abort() {
        /*
         * Using the abort system call has unexpected performance implications on Oracle Enterprise
         * Linux: Storing the crash dump information takes minutes even for tiny images. Therefore,
         * we just exit with an otherwise unused exit code.
         */
        exit(EXIT_CODE_ABORT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord strlen(CCharPointer str) {
        return libc().strlen(str);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int isdigit(int c) {
        return libc().isdigit(c);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord strtoull(CCharPointer string, CCharPointerPointer endPtr, int base) {
        return libc().strtoull(string, endPtr, base);
    }

    @Fold
    public static boolean isSupported() {
        return ImageSingletons.contains(LibCSupport.class);
    }

    @Fold
    static LibCSupport libc() {
        return ImageSingletons.lookup(LibCSupport.class);
    }
}
