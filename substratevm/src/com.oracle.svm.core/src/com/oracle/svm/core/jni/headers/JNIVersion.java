/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jni.headers;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;

import com.oracle.svm.core.Uninterruptible;

@CContext(JNIHeaderDirectives.class)
public final class JNIVersion {
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static boolean isSupported(int version) {
        return (JavaVersionUtil.JAVA_SPEC > 17 && version == JNIVersionJDK19OrLater.JNI_VERSION_19()) ||
                        version == JNI_VERSION_10() ||
                        version == JNI_VERSION_9() ||
                        version == JNI_VERSION_1_8() ||
                        version == JNI_VERSION_1_6() ||
                        version == JNI_VERSION_1_4() ||
                        version == JNI_VERSION_1_2() ||
                        version == JNI_VERSION_1_1();
    }

    // Checkstyle: stop

    @CConstant
    public static native int JNI_VERSION_1_1();

    @CConstant
    public static native int JNI_VERSION_1_2();

    @CConstant
    public static native int JNI_VERSION_1_4();

    @CConstant
    public static native int JNI_VERSION_1_6();

    @CConstant
    public static native int JNI_VERSION_1_8();

    @CConstant
    public static native int JNI_VERSION_9();

    @CConstant
    public static native int JNI_VERSION_10();

    // Checkstyle: resume

    private JNIVersion() {
    }
}
