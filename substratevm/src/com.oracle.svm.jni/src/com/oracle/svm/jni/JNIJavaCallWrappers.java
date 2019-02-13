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

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod.CallVariant;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Holder class for generated {@link JNIJavaCallWrapperMethod} code.
 */
public final class JNIJavaCallWrappers {
    public static ConstantPool getConstantPool(MetaAccessProvider metaAccess) {
        // Each generated call wrapper needs an actual constant pool, so we provide our
        // private constructor's
        return metaAccess.lookupJavaType(JNIJavaCallWrappers.class).getDeclaredConstructors()[0].getConstantPool();
    }

    public static ResolvedJavaMethod lookupJavaCallTrampoline(MetaAccessProvider metaAccess, CallVariant variant, boolean nonVirtual) {
        StringBuilder name = new StringBuilder(48);
        if (variant == CallVariant.VARARGS) {
            name.append("varargs");
        } else if (variant == CallVariant.ARRAY) {
            name.append("array");
        } else if (variant == CallVariant.VA_LIST) {
            name.append("valist");
        } else {
            throw VMError.shouldNotReachHere();
        }
        if (nonVirtual) {
            name.append("Nonvirtual");
        }
        name.append("JavaCallTrampoline");
        try {
            return metaAccess.lookupJavaMethod(JNIJavaCallWrappers.class.getDeclaredMethod(name.toString()));
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private JNIJavaCallWrappers() {
    }

    private native void varargsJavaCallTrampoline();

    private native void arrayJavaCallTrampoline();

    private native void valistJavaCallTrampoline();

    private native void varargsNonvirtualJavaCallTrampoline();

    private native void arrayNonvirtualJavaCallTrampoline();

    private native void valistNonvirtualJavaCallTrampoline();
}
