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
package com.oracle.svm.jni;

// Checkstyle: allow reflection

import java.lang.reflect.Array;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.jni.access.JNIAccessibleField;
import com.oracle.svm.jni.access.JNINativeLinkage;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

import jdk.vm.ci.meta.JavaKind;
import sun.misc.Unsafe;

/**
 * Helper code that is used in generated JNI code via {@code JNIGraphKit}.
 */
public final class JNIGeneratedMethodSupport {
    // Careful around here -- these methods are invoked by generated methods.

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    static PointerBase nativeCallAddress(JNINativeLinkage linkage) {
        return linkage.getOrFindEntryPoint();
    }

    static int nativeCallPrologue() {
        return JNIObjectHandles.pushLocalFrame(JNIObjectHandles.NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY);
    }

    static void nativeCallEpilogue(int handleFrame) {
        JNIObjectHandles.popLocalFramesIncluding(handleFrame);
    }

    static JNIEnvironment environment() {
        return JNIThreadLocalEnvironment.getAddress();
    }

    static JNIObjectHandle boxObjectInLocalHandle(Object obj) {
        return JNIObjectHandles.createLocal(obj);
    }

    static Object unboxHandle(JNIObjectHandle handle) {
        return JNIObjectHandles.getObject(handle);
    }

    static WordBase getFieldOffsetFromId(JNIFieldId fieldId) {
        return JNIAccessibleField.getOffsetFromId(fieldId);
    }

    static byte[] getStaticPrimitiveFieldsArray() {
        return StaticFieldsSupport.getStaticPrimitiveFields();
    }

    static Object[] getStaticObjectFieldsArray() {
        return StaticFieldsSupport.getStaticObjectFields();
    }

    static void setPendingException(Throwable t) {
        JNIThreadLocalPendingException.set(t);
    }

    static Throwable getAndClearPendingException() {
        Throwable t = JNIThreadLocalPendingException.get();
        JNIThreadLocalPendingException.clear();
        return t;
    }

    static void rethrowPendingException() throws Throwable {
        Throwable t = getAndClearPendingException();
        if (t != null) {
            throw t;
        }
    }

    static PointerBase pinArrayAndGetAddress(Object array, CCharPointer isCopy) throws Throwable {
        if (array.getClass().isArray()) {
            if (isCopy.isNonNull()) {
                isCopy.write((byte) 0);
            }
            return JNIThreadLocalPinnedObjects.pinArrayAndGetAddress(array);
        }
        return WordFactory.nullPointer();
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
            UNSAFE.copyMemory(array, offset, null, buffer.rawValue(), count * elementSize);
        }
    }

    static void setPrimitiveArrayRegion(JavaKind elementKind, Object array, int start, int count, PointerBase buffer) {
        if (start < 0 || count < 0 || start + count > Array.getLength(array)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (count > 0) {
            long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
            int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
            UNSAFE.copyMemory(null, buffer.rawValue(), array, offset, count * elementSize);
        }
    }
}
