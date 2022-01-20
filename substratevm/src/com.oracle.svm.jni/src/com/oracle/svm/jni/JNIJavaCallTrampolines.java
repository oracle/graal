/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jni.hosted.JNICallTrampolineMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod.CallVariant;

/** Holder class for generated {@link JNICallTrampolineMethod} code. */
public final class JNIJavaCallTrampolines {

    private JNIJavaCallTrampolines() {
    }

    @Platforms(HOSTED_ONLY.class)
    public static String getTrampolineName(CallVariant variant, boolean nonVirtual) {
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
        return name.toString();
    }

    @Platforms(HOSTED_ONLY.class)
    public static boolean isNonVirtual(String trampolineName) {
        return trampolineName.endsWith("NonvirtualJavaCallTrampoline");
    }

    @Platforms(HOSTED_ONLY.class)
    public static CallVariant getVariant(String trampolineName) {
        if (trampolineName.startsWith("varargs")) {
            return CallVariant.VARARGS;
        }
        if (trampolineName.startsWith("array")) {
            return CallVariant.ARRAY;
        }
        if (trampolineName.startsWith("valist")) {
            return CallVariant.VA_LIST;
        }
        throw VMError.shouldNotReachHere();
    }

    private native void varargsJavaCallTrampoline();

    private native void arrayJavaCallTrampoline();

    private native void valistJavaCallTrampoline();

    private native void varargsNonvirtualJavaCallTrampoline();

    private native void arrayNonvirtualJavaCallTrampoline();

    private native void valistNonvirtualJavaCallTrampoline();
}
