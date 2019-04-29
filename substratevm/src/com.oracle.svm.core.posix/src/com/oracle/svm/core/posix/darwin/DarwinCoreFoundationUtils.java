/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.darwin;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.posix.headers.darwin.CoreFoundation;

@Platforms(InternalPlatform.DARWIN_AND_JNI.class)
public final class DarwinCoreFoundationUtils {

    private DarwinCoreFoundationUtils() {
    }

    public static CoreFoundation.CFMutableStringRef toCFStringRef(String str) {
        CoreFoundation.CFMutableStringRef stringRef = CoreFoundation.CFStringCreateMutable(WordFactory.nullPointer(), WordFactory.zero());
        if (stringRef.isNull()) {
            throw new OutOfMemoryError("native heap");
        }
        char[] charArray = str.toCharArray();
        try (PinnedObject pathPin = PinnedObject.create(charArray)) {
            PointerBase addressOfCharArray = pathPin.addressOfArrayElement(0);
            CoreFoundation.CFStringAppendCharacters(stringRef, addressOfCharArray, WordFactory.signed(charArray.length));
        }
        return stringRef;
    }

    public static String fromCFStringRef(CoreFoundation.CFStringRef cfstr) {
        int length = (int) CoreFoundation.CFStringGetLength(cfstr);
        char[] chars = new char[length];
        for (int i = 0; i < length; ++i) {
            chars[i] = CoreFoundation.CFStringGetCharacterAtIndex(cfstr, i);
        }
        return String.valueOf(chars);
    }

}
