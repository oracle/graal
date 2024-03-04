/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti.utils;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.jvmti.headers.JvmtiError;

public class JvmtiUninterruptibleUtils {

    private JvmtiUninterruptibleUtils() {
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "jvmti")
    public static JvmtiError writeStringToCCharArray(char[] buffer, int endIndex, CCharPointerPointer charPtr, ReplaceDotWithSlash replacer) {
        if (endIndex <= 0) {
            return JvmtiError.JVMTI_ERROR_INTERNAL;
        }
        UnsignedWord bufferSize = WordFactory.unsigned(com.oracle.svm.core.jdk.UninterruptibleUtils.String.modifiedUTF8LengthCharArray(buffer, endIndex, true, replacer));
        CCharPointer cStringBuffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(bufferSize);
        if (cStringBuffer.isNull()) {
            charPtr.write(WordFactory.nullPointer());
            return JvmtiError.JVMTI_ERROR_OUT_OF_MEMORY;
        }

        com.oracle.svm.core.jdk.UninterruptibleUtils.String.toModifiedUTF8FromCharArray(buffer, endIndex, (Pointer) cStringBuffer, ((Pointer) cStringBuffer).add(bufferSize), true, replacer);
        charPtr.write(cStringBuffer);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static class ReplaceDotWithSlash implements com.oracle.svm.core.jdk.UninterruptibleUtils.CharReplacer {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public char replace(char ch) {
            if (ch == '.') {
                return '/';
            }
            return ch;
        }
    }
}
