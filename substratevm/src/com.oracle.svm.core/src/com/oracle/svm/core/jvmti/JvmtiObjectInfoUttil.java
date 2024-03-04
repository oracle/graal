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
package com.oracle.svm.core.jvmti;

import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.nodes.java.ArrayLengthNode;

public final class JvmtiObjectInfoUttil {

    private JvmtiObjectInfoUttil() {
    }

    public static JvmtiError getObjectSize(JNIObjectHandle jobject, CLongPointer sizePtr) {
        Object object;
        try {
            object = JNIObjectHandles.getObject(jobject);
        } catch (IllegalArgumentException e) {
            return JvmtiError.JVMTI_ERROR_INVALID_OBJECT;
        }
        if (object == null) {
            return JvmtiError.JVMTI_ERROR_INVALID_OBJECT;
        }

        long size = 0;
        int layoutEncoding = KnownIntrinsics.readHub(object).getLayoutEncoding();
        if (LayoutEncoding.isArray(layoutEncoding)) {
            int elementSize;
            if (LayoutEncoding.isPrimitiveArray(layoutEncoding)) {
                elementSize = LayoutEncoding.getArrayIndexScale(layoutEncoding);
            } else {
                elementSize = ConfigurationValues.getTarget().wordSize;
            }
            int length = ArrayLengthNode.arrayLength(object);
            size = (long) length * elementSize;
        } else {
            DynamicHub hub = DynamicHub.fromClass(object.getClass());
            int encoding = hub.getLayoutEncoding();
            if (LayoutEncoding.isPureInstance(encoding)) {
                /*
                 * May underestimate the object size if the identity hashcode field is optional.
                 * This is the best that what can do because the HPROF format does not support that
                 * instances of one class have different object sizes.
                 */
                size = (int) LayoutEncoding.getPureInstanceAllocationSize(encoding).rawValue();
            } else if (LayoutEncoding.isHybrid(encoding)) {
                /* For hybrid objects, return the size of the fields. */
                size = LayoutEncoding.getArrayBaseOffsetAsInt(encoding);
            }
        }
        sizePtr.write(size);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError getHashCode(JNIObjectHandle jobject, CIntPointer hashCodePtr) {
        Object object;
        try {
            object = JNIObjectHandles.getObject(jobject);
        } catch (IllegalArgumentException e) {
            return JvmtiError.JVMTI_ERROR_INVALID_OBJECT;
        }
        if (object == null) {
            return JvmtiError.JVMTI_ERROR_INVALID_OBJECT;
        }
        hashCodePtr.write(object.hashCode());
        return JvmtiError.JVMTI_ERROR_NONE;
    }
}
