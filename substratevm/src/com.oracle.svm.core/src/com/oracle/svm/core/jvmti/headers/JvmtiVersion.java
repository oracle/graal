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
package com.oracle.svm.core.jvmti.headers;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;

@CContext(JvmtiDirectives.class)
public final class JvmtiVersion {
    private static final int JVMTI_VERSION_INTERFACE_JVMTI = 0x30000000;
    private static final int LATEST_SUPPORTED_MAJOR_VERSION = 21;

    public static final int CURRENT_VERSION = JVMTI_VERSION_INTERFACE_JVMTI + LATEST_SUPPORTED_MAJOR_VERSION * 0x10000;

    @Platforms(Platform.HOSTED_ONLY.class)
    private JvmtiVersion() {
    }

    public static boolean isSupported(int version) {
        if (isJvmtiVersion(version)) {
            return isSupported0(version);
        }
        return false;
    }

    private static boolean isJvmtiVersion(int version) {
        return (version & JVMTI_VERSION_MASK_INTERFACE_TYPE()) == JVMTI_VERSION_INTERFACE_JVMTI;
    }

    private static boolean isSupported0(int version) {
        int major = (version & JVMTI_VERSION_MASK_MAJOR()) >> JVMTI_VERSION_SHIFT_MAJOR();
        int minor = (version & JVMTI_VERSION_MASK_MINOR()) >> JVMTI_VERSION_SHIFT_MINOR();
        /* The "micro" part of the version doesn't matter at the moment. */

        if (major == 1) {
            return minor >= 0 && minor <= 2;
        } else if (major == 9 || major == 11) {
            return minor == 0;
        } else {
            /* Since version 13, we do not care about minor versions. */
            return major >= 13 && major <= LATEST_SUPPORTED_MAJOR_VERSION;
        }
    }

    // Checkstyle: stop: MethodName

    @CConstant
    private static native int JVMTI_VERSION_MASK_INTERFACE_TYPE();

    @CConstant
    private static native int JVMTI_VERSION_MASK_MAJOR();

    @CConstant
    private static native int JVMTI_VERSION_MASK_MINOR();

    @CConstant
    private static native int JVMTI_VERSION_SHIFT_MAJOR();

    @CConstant
    private static native int JVMTI_VERSION_SHIFT_MINOR();

    // Checkstyle: resume
}
