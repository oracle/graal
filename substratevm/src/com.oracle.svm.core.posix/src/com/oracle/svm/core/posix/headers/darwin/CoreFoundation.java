/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers.darwin;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.posix.headers.PosixDirectives;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file CoreFoundation/CoreFoundation.h.
 */
@CContext(PosixDirectives.class)
@CLibrary("-framework CoreFoundation")
public class CoreFoundation {

    public interface CFStringRef extends PointerBase {
    }

    public interface CFMutableStringRef extends CFStringRef {

    }

    @CFunction
    public static native CFMutableStringRef CFStringCreateMutable(PointerBase alloc, SignedWord maxLength);

    @CFunction
    public static native void CFStringAppendCharacters(CFMutableStringRef theString, PointerBase chars, SignedWord numChars);

    @CFunction
    public static native void CFStringNormalize(CFMutableStringRef theString, SignedWord theForm);

    @CFunction
    public static native long CFStringGetLength(CFStringRef theString);

    @CFunction
    public static native void CFRelease(PointerBase cf);

    @CFunction
    public static native PointerBase CFRetain(PointerBase cf);

    public interface CFDictionaryRef extends PointerBase {
    }

    @CFunction
    public static native CFDictionaryRef _CFCopyServerVersionDictionary();

    @CFunction
    public static native CFDictionaryRef _CFCopySystemVersionDictionary();

    @CFunction
    public static native CFStringRef CFDictionaryGetValue(CFDictionaryRef theDict, CFStringRef key);

    @CFunction
    public static native char CFStringGetCharacterAtIndex(CFStringRef theString, long idx);
}
