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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.handles.PrimitiveArrayView;
import com.oracle.svm.core.jni.access.JNIAccessibleField;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;

/**
 * Helper code that is used in generated JNI code via {@code JNIGraphKit}.
 */
public class JNIGeneratedMethodSupport {
    @Fold
    public static JNIGeneratedMethodSupport singleton() {
        return ImageSingletons.lookup(JNIGeneratedMethodSupport.class);
    }

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

    public JNIObjectHandle getObjectField(JNIObjectHandle obj, JNIFieldId fieldId) {
        Object o = JNIObjectHandles.getObject(obj);
        long offset = JNIAccessibleField.getOffsetFromId(fieldId).rawValue();
        Object result = getObjectField0(o, offset);
        return JNIObjectHandles.createLocal(result);
    }

    protected Object getObjectField0(Object obj, long offset) {
        return Unsafe.getUnsafe().getReference(obj, offset);
    }

    public PointerBase createArrayViewAndGetAddress(JNIObjectHandle handle, CCharPointer isCopy) {
        Object obj = JNIObjectHandles.getObject(handle);
        if (!obj.getClass().isArray()) {
            throw new IllegalArgumentException("Argument is not an array");
        }

        /* Create a view for the non-null array object. */
        PrimitiveArrayView ref = JNIThreadLocalPrimitiveArrayViews.createArrayView(obj);
        if (isCopy.isNonNull()) {
            isCopy.write(ref.isCopy() ? (byte) 1 : (byte) 0);
        }
        return ref.addressOfArrayElement(0);
    }

    public void destroyNewestArrayViewByAddress(PointerBase address, int mode) {
        JNIThreadLocalPrimitiveArrayViews.destroyNewestArrayViewByAddress(address, mode);
    }

    public void getPrimitiveArrayRegion(JavaKind elementKind, JNIObjectHandle handle, int start, int count, PointerBase buffer) {
        Object obj = JNIObjectHandles.getObject(handle);
        /* Check if we have a non-null array object and if start/count are valid. */
        if (start < 0 || count < 0 || start > Array.getLength(obj) - count) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (count > 0) {
            long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
            int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
            UnsignedWord bytes = WordFactory.unsigned(count).multiply(elementSize);
            JavaMemoryUtil.copyOnHeap(obj, WordFactory.unsigned(offset), null, WordFactory.unsigned(buffer.rawValue()), bytes);
        }
    }

    public void setPrimitiveArrayRegion(JavaKind elementKind, JNIObjectHandle handle, int start, int count, PointerBase buffer) {
        Object obj = JNIObjectHandles.getObject(handle);
        if (start < 0 || count < 0 || start > Array.getLength(obj) - count) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (count > 0) {
            long offset = ConfigurationValues.getObjectLayout().getArrayElementOffset(elementKind, start);
            int elementSize = ConfigurationValues.getObjectLayout().sizeInBytes(elementKind);
            UnsignedWord bytes = WordFactory.unsigned(count).multiply(elementSize);
            JavaMemoryUtil.copyOnHeap(null, WordFactory.unsigned(buffer.rawValue()), obj, WordFactory.unsigned(offset), bytes);
        }
    }
}
