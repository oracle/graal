/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libespresso.jniapi;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;

@CContext(JNIHeaderDirectives.class)
public class JNIVersion {
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

    // Unfortunately those are not always available and "default" values are not supported
    // @CConstant
    // public static native int JNI_VERSION_9();
    public static int JNI_VERSION_9 = 0x00090000;

    // @CConstant
    // public static native int JNI_VERSION_10();
    public static int JNI_VERSION_10 = 0x000A0000;

    // Checkstyle: resume

    public static String versionString(int version) {
        if (version == JNI_VERSION_1_1()) {
            return "1.1";
        } else if (version == JNI_VERSION_1_2()) {
            return "1.2";
        } else if (version == JNI_VERSION_1_4()) {
            return "1.4";
        } else if (version == JNI_VERSION_1_6()) {
            return "1.6";
        } else if (version == JNI_VERSION_1_8()) {
            return "1.8";
        } else if (version == JNI_VERSION_9) {
            return "9";
        } else if (version == JNI_VERSION_10) {
            return "10";
        }
        return "unknown";
    }

    public static int javaSpecVersion(int version) {
        if (version == JNI_VERSION_1_1()) {
            return 1;
        } else if (version == JNI_VERSION_1_2()) {
            return 2;
        } else if (version == JNI_VERSION_1_4()) {
            return 4;
        } else if (version == JNI_VERSION_1_6()) {
            return 6;
        } else if (version == JNI_VERSION_1_8()) {
            return 8;
        } else if (version == JNI_VERSION_9) {
            return 9;
        } else if (version == JNI_VERSION_10) {
            return 10;
        }
        return -1;
    }
}
