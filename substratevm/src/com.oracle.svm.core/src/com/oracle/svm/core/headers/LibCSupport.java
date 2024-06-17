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

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.memory.NativeMemory;

/** Platform-independent LibC support. Don't use this class directly, use {@link LibC} instead. */
public interface LibCSupport {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int errno();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void setErrno(int value);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T extends PointerBase> T memcpy(T dest, PointerBase src, UnsignedWord n);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T extends PointerBase> int memcmp(T s1, T s2, UnsignedWord n);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T extends PointerBase> T memmove(T dest, PointerBase src, UnsignedWord n);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T extends PointerBase> T memset(T s, SignedWord c, UnsignedWord n);

    /** Don't call this directly, see {@link NativeMemory} for more details. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T extends PointerBase> T malloc(UnsignedWord size);

    /** Don't call this directly, see {@link NativeMemory} for more details. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T extends PointerBase> T calloc(UnsignedWord nmemb, UnsignedWord size);

    /** Don't call this directly, see {@link NativeMemory} for more details. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    <T extends PointerBase> T realloc(PointerBase ptr, UnsignedWord size);

    /** Don't call this directly, see {@link NativeMemory} for more details. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void free(PointerBase ptr);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void exit(int status);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord strlen(CCharPointer str);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    CCharPointer strdup(CCharPointer str);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int strcmp(CCharPointer s1, CCharPointer s2);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int isdigit(int c);

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord strtoull(CCharPointer string, CCharPointerPointer endPtr, int base);
}
