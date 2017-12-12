/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

// Checkstyle: allow reflection

import java.lang.reflect.Array;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.UnsafeAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.jni.access.JNINativeLinkage;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.meta.JavaKind;

/**
 * Helper code that is used in generated JNI code via {@code JNIGraphKit}.
 */
public final class JNIGeneratedMethodSupport {
    // Careful around here -- these methods are invoked by generated methods.

    static PointerBase nativeCallAddress(JNINativeLinkage linkage) {
        return linkage.getOrFindEntryPoint();
    }

    static int nativeCallPrologue() {
        return JNIThreadLocalHandles.get().pushFrame(JNIThreadLocalHandles.NATIVE_CALL_MINIMUM_HANDLE_CAPACITY);
    }

    static void nativeCallEpilogue(int handleFrame) {
        JNIThreadLocalHandles.get().popFramesIncluding(handleFrame);
    }

    static JNIEnvironment environment() {
        if (!JNIThreadLocalEnvironment.isInitialized()) {
            JNIThreadLocalEnvironment.initialize();
        }
        return JNIThreadLocalEnvironment.getAddress();
    }

    static JNIObjectHandle boxObjectInLocalHandle(Object obj) {
        return JNIThreadLocalHandles.get().create(obj);
    }

    static Object unboxHandle(JNIObjectHandle handle) {
        return JNIObjectHandles.getObject(handle);
    }

    static byte[] getStaticPrimitiveFieldsArray() {
        return StaticFieldsSupport.getStaticPrimitiveFields();
    }

    static Object[] getStaticObjectFieldsArray() {
        return StaticFieldsSupport.getStaticObjectFields();
    }

    static void retainPendingException(Throwable t) {
        JNIThreadLocalPendingException.set(t);
    }

    static void rethrowPendingException() throws Throwable {
        Throwable t = JNIThreadLocalPendingException.get();
        if (t != null) {
            JNIThreadLocalPendingException.clear();
            throw t;
        }
    }

    static PointerBase pinArrayAndGetAddress(Object array, CIntPointer isCopy) throws Throwable {
        if (array.getClass().isArray()) {
            if (isCopy.isNonNull()) {
                isCopy.write(0);
            }
            return JNIThreadLocalPinnedObjects.pinArrayAndGetAddress(array);
        }
        return Word.nullPointer();
    }

    static boolean unpinArrayByAddress(PointerBase address) throws Throwable {
        return JNIThreadLocalPinnedObjects.unpinArrayByAddress(address);
    }

    static void getPrimitiveArrayRegion(JavaKind elementKind, Object array, int start, int count, PointerBase buffer) {
        if (start < 0 || count < 0 || start + count > Array.getLength(array)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (count > 0) {
            long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
            int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
            UnsafeAccess.UNSAFE.copyMemory(array, offset, null, buffer.rawValue(), count * elementSize);
        }
    }

    static void setPrimitiveArrayRegion(JavaKind elementKind, Object array, int start, int count, PointerBase buffer) {
        if (start < 0 || count < 0 || start + count > Array.getLength(array)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (count > 0) {
            long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
            int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
            UnsafeAccess.UNSAFE.copyMemory(null, buffer.rawValue(), array, offset, count * elementSize);
        }
    }
}
