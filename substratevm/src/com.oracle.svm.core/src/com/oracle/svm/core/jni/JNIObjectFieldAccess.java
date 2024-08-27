/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.jni.access.JNIAccessibleField;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;

import jdk.internal.misc.Unsafe;

public class JNIObjectFieldAccess {
    @Fold
    public static JNIObjectFieldAccess singleton() {
        return ImageSingletons.lookup(JNIObjectFieldAccess.class);
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
}
