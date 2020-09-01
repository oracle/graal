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

import com.oracle.truffle.espresso.runtime.JavaVersion;

public enum JniVersion {
    JNI_VERSION_1_1(0x00010001),
    JNI_VERSION_1_2(0x00010002),
    JNI_VERSION_1_4(0x00010004),
    JNI_VERSION_1_6(0x00010006),
    JNI_VERSION_1_8(0x00010008),
    JNI_VERSION_9(0x00090000, true),
    JNI_VERSION_10(0x000A0000, true);

    private final int version;
    private final boolean v9OrLater;

    JniVersion(int version) {
        this(version, false);
    }

    JniVersion(int version, boolean v9OrLater) {
        this.version = version;
        this.v9OrLater = v9OrLater;
    }

    public int version() {
        return version;
    }

    private boolean supports(int v, JavaVersion contextVersion) {
        return v == version() && (!v9OrLater || contextVersion.java9OrLater());
    }

    /**
     * JNI version implemented by Espresso when running a java 8 home.
     */
    static final JniVersion JNI_VERSION_ESPRESSO_8 = JNI_VERSION_1_8;

    /**
     * JNI version implemented by Espresso when running a java 11 home.
     */
    static final JniVersion JNI_VERSION_ESPRESSO_11 = JNI_VERSION_10;

    public static boolean isSupported(int version, JavaVersion contextVersion) {
        for (JniVersion v : JniVersion.values()) {
            if (v.supports(version, contextVersion)) {
                return true;
            }
        }
        return false;
    }
}
