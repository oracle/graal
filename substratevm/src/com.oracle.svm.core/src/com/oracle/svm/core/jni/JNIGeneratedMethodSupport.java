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

import java.lang.reflect.Array;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.jni.access.JNIAccessibleField;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;

import jdk.internal.misc.Unsafe;
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
        return JNIObjectHandles.pushLocalFrame(JNIObjectHandles.NATIVE_CALL_MIN_LOCAL_HANDLE_CAPACITY);
    }

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static WordBase getFieldOffsetFromId(JNIFieldId fieldId) {
        return JNIAccessibleField.getOffsetFromId(fieldId);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static Object getStaticPrimitiveFieldsArray() {
        return StaticFieldsSupport.getStaticPrimitiveFields();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static Object getStaticObjectFieldsArray() {
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

    static PointerBase createArrayViewAndGetAddress(Object array, CCharPointer isCopy) throws Throwable {
        if (array.getClass().isArray()) {
            PrimitiveArrayView ref = JNIThreadLocalPrimitiveArrayViews.createArrayView(array);
            if (isCopy.isNonNull()) {
                isCopy.write(ref.isCopy() ? (byte) 1 : (byte) 0);
            }
            return ref.addressOfArrayElement(0);
        }
        return WordFactory.nullPointer();
    }

    static void destroyNewestArrayViewByAddress(PointerBase address, int mode) throws Throwable {
        JNIThreadLocalPrimitiveArrayViews.destroyNewestArrayViewByAddress(address, mode);
    }

    static void getPrimitiveArrayRegion(JavaKind elementKind, Object array, int start, int count, PointerBase buffer) {
        if (start < 0 || count < 0 || start + count > Array.getLength(array)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (count > 0) {
            long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
            int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
            Unsafe.getUnsafe().copyMemory(array, offset, null, buffer.rawValue(), count * elementSize);
        }
    }

    static void setPrimitiveArrayRegion(JavaKind elementKind, Object array, int start, int count, PointerBase buffer) {
        if (start < 0 || count < 0 || start + count > Array.getLength(array)) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (count > 0) {
            long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
            int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
            Unsafe.getUnsafe().copyMemory(null, buffer.rawValue(), array, offset, count * elementSize);
        }
    }
}
