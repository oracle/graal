/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.espresso.classfile.JavaVersion;

public enum JniVersion {
    JNI_VERSION_1_1(0x00010001),
    JNI_VERSION_1_2(0x00010002),
    JNI_VERSION_1_4(0x00010004),
    JNI_VERSION_1_6(0x00010006),
    JNI_VERSION_1_8(0x00010008),
    JNI_VERSION_9(0x00090000),
    JNI_VERSION_10(0x000A0000),
    JNI_VERSION_19(0x00130000),
    JNI_VERSION_20(0x00140000),
    JNI_VERSION_21(0x00150000),
    JNI_VERSION_24(0x00180000),
    ;

    private final int version;

    JniVersion(int version) {
        this.version = version;
    }

    public int version() {
        return version;
    }

    public JavaVersion getJavaVersion() {
        int major = (version >> 16) & 0xffff;
        int minor = version & 0xffff;
        if (major == 1) {
            return JavaVersion.forVersion(minor);
        } else {
            return JavaVersion.forVersion(major);
        }
    }

    public static JniVersion decodeVersion(int encoded) {
        for (JniVersion version : JniVersion.values()) {
            if (version.version == encoded) {
                return version;
            }
        }
        return null;
    }

    public static JniVersion getJniVersion(JavaVersion javaVersion) {
        JniVersion latestSupportedJniVersion = null;
        for (JniVersion version : JniVersion.values()) {
            assert latestSupportedJniVersion == null || latestSupportedJniVersion.getJavaVersion().compareTo(version.getJavaVersion()) < 0;
            if (version.getJavaVersion().compareTo(javaVersion) <= 0) {
                latestSupportedJniVersion = version;
            }
        }
        assert latestSupportedJniVersion != null;
        return latestSupportedJniVersion;
    }
}
