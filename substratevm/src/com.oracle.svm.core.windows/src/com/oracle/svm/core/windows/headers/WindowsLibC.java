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

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

// Checkstyle: stop

/**
 * Basic functions from the standard Visual Studio C Run-Time library
 */
@CContext(WindowsDirectives.class)
public class WindowsLibC {
    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CIntPointer _errno();

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> T memcpy(T dest, PointerBase src, UnsignedWord n);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native <T extends PointerBase> int memcmp(T s1, T s2, UnsignedWord n);

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

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native UnsignedWord strlen(CCharPointer str);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int strcmp(PointerBase s1, PointerBase s2);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strcpy(CCharPointer dst, CCharPointer src);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strncpy(CCharPointer dst, CCharPointer src, UnsignedWord len);

    @CFunction(value = "_strdup", transition = CFunction.Transition.NO_TRANSITION)
    public static native CCharPointer strdup(CCharPointer src);

    @CPointerTo(nameOfCType = "wchar_t")
    public interface WCharPointer extends PointerBase {
    }

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native WCharPointer _wgetenv(WCharPointer varname);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native UnsignedWord wcslen(WCharPointer varname);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native WCharPointer wcsncat(WCharPointer strDest, WCharPointer strSource, UnsignedWord count);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native int isdigit(int c);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native UnsignedWord strtoull(CCharPointer string, CCharPointerPointer endPtr, int base);

    @CFunction(transition = CFunction.Transition.NO_TRANSITION)
    public static native double log(double value);
}
