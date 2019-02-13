/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni;

import org.graalvm.word.SignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jni.nativeapi.JNIObjectRefType;

/**
 * Manages accesses to both {@link JNIThreadLocalHandles local} and {@link JNIGlobalHandles global}
 * JNI object handles.
 */
public final class JNIObjectHandles {

    public static <T extends SignedWord> T nullHandle() {
        return WordFactory.signed(0);
    }

    public static <T> T getObject(JNIObjectHandle handle) {
        if (handle.equal(nullHandle())) {
            return null;
        }
        if (JNIThreadLocalHandles.isInRange(handle)) {
            return JNIThreadLocalHandles.get().getObject(handle);
        }
        if (JNIGlobalHandles.singleton().isInRange(handle)) {
            return JNIGlobalHandles.singleton().get(handle);
        }
        throw new RuntimeException("Invalid object handle");
    }

    public static JNIObjectRefType getHandleType(JNIObjectHandle handle) {
        if (JNIThreadLocalHandles.isInRange(handle)) {
            return JNIObjectRefType.Local;
        }
        if (JNIGlobalHandles.singleton().isInRange(handle)) {
            if (JNIGlobalHandles.singleton().isWeak(handle)) {
                return JNIObjectRefType.WeakGlobal;
            }
            return JNIObjectRefType.Global;
        }
        return JNIObjectRefType.Invalid; // intentionally includes the null handle
    }
}
