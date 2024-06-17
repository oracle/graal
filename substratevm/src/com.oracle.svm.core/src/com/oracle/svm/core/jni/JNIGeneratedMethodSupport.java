/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni;

import org.graalvm.word.PointerBase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;

/**
 * Helper code that is used in generated JNI code via {@code JNIGraphKit}.
 */
public final class JNIGeneratedMethodSupport {
    // Careful around here -- these methods are invoked by generated methods.

    static PointerBase nativeCallAddress(JNINativeLinkage linkage) {
        return linkage.getOrFindEntryPoint();
    }

    static int nativeCallPrologue() {
        return JNIObjectHandles.pushLocalFrame(JNIObjectHandles.NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY);
    }

    @Uninterruptible(reason = "Must not throw any exceptions - otherwise, we might leak memory.")
    static void nativeCallEpilogue(int handleFrame) {
        JNIObjectHandles.popLocalFramesIncluding(handleFrame);
    }

    static JNIEnvironment environment() {
        return JNIThreadLocalEnvironment.getAddress();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static JNIObjectHandle boxObjectInLocalHandle(Object obj) {
        return JNIObjectHandles.createLocal(obj);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static Object unboxHandle(JNIObjectHandle handle) {
        return JNIObjectHandles.getObject(handle);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    static void setPendingException(Throwable t) {
        JNIThreadLocalPendingException.set(t);
    }

    @Uninterruptible(reason = "Must not throw any exceptions.")
    static Throwable getAndClearPendingException() {
        Throwable t = JNIThreadLocalPendingException.get();
        JNIThreadLocalPendingException.clear();
        return t;
    }

    @Uninterruptible(reason = "Must not throw any exceptions, except for the pending exception.")
    static void rethrowPendingException() throws Throwable {
        Throwable t = getAndClearPendingException();
        if (t != null) {
            throw t;
        }
    }
}
