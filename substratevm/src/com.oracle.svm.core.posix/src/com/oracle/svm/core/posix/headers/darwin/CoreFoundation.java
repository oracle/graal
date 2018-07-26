/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.posix.headers.PosixDirectives;

//Checkstyle: stop

/**
 * Contains the definitions from CoreFoundation/CoreFoundation.h that we actually needed.
 */
@CContext(PosixDirectives.class)
@CLibrary("-framework CoreFoundation")
@Platforms(Platform.DARWIN.class)
public class CoreFoundation {

    public interface CFStringRef extends PointerBase {
    }

    public interface CFMutableStringRef extends CFStringRef {

    }

    /**
     * Functions to create mutable strings. "maxLength", if not 0, is a hard bound on the length of
     * the string. If 0, there is no limit on the length.
     */
    @CFunction
    public static native CFMutableStringRef CFStringCreateMutable(PointerBase alloc, SignedWord maxLength);

    @CFunction
    public static native void CFStringAppendCharacters(CFMutableStringRef theString, PointerBase chars, SignedWord numChars);

    /**
     * Normalizes the string into the specified form as described in Unicode Technical Report #15.
     * 
     * @param theString The string which is to be normalized. If this parameter is not a valid
     *            mutable CFString, the behavior is undefined.
     * @param theForm The form into which the string is to be normalized. If this parameter is not a
     *            valid CFStringNormalizationForm value, the behavior is undefined.
     */
    @CFunction
    public static native void CFStringNormalize(CFMutableStringRef theString, SignedWord theForm);

    /** Number of 16-bit Unicode characters in the string. */
    @CFunction
    public static native long CFStringGetLength(CFStringRef theString);

    @CFunction
    public static native void CFRelease(PointerBase cf);

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
