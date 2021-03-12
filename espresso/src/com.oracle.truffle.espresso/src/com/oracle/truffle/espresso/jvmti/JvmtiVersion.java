/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.jvmti;

@SuppressWarnings("unused")
class JvmtiVersion {
    // Known versions
    private static final int JVMTI_VERSION_1 = 0x30010000;
    private static final int JVMTI_VERSION_1_0 = 0x30010000;
    private static final int JVMTI_VERSION_1_1 = 0x30010100;
    private static final int JVMTI_VERSION_1_2 = 0x30010200;
    private static final int JVMTI_VERSION_9 = 0x30090000;
    private static final int JVMTI_VERSION_11 = 0x300B0000;
    /* version: 11.0. 0 */
    private static final int JVMTI_VERSION = 0x30000000 + (11 * 0x10000) + (0 * 0x100) + 0;

    // Version bit fiddling
    private static final int JVMTI_VERSION_MASK = 0x70000000;
    private static final int JVMTI_VERSION_VALUE = 0x30000000;

    public static boolean isJvmtiVersion(int version) {
        return (version & JVMTI_VERSION_MASK) == JVMTI_VERSION_VALUE;
    }

    public static boolean isSupportedJvmtiVersion(int version) {
        return DecodedJVMTIVersion.decode(version).isSupported();
    }

    private static final class DecodedJVMTIVersion {
        private static final int JVMTI_VERSION_MASK_MAJOR = 0x0FFF0000;
        private static final int JVMTI_VERSION_MASK_MINOR = 0x0000FF00;
        private static final int JVMTI_VERSION_MASK_MICRO = 0x000000FF;

        private static final int JVMTI_VERSION_SHIFT_MAJOR = 16;
        private static final int JVMTI_VERSION_SHIFT_MINOR = 8;
        private static final int JVMTI_VERSION_SHIFT_MICRO = 0;

        private final int major;
        private final int minor;
        private final int micro;

        DecodedJVMTIVersion(int major, int minor, int micro) {
            this.major = major;
            this.minor = minor;
            this.micro = micro;
        }

        static DecodedJVMTIVersion decode(int version) {
            return new DecodedJVMTIVersion((version & JVMTI_VERSION_MASK_MAJOR) >> JVMTI_VERSION_SHIFT_MAJOR,
                            (version & JVMTI_VERSION_MASK_MINOR) >> JVMTI_VERSION_SHIFT_MINOR,
                            (version & JVMTI_VERSION_MASK_MICRO) >> JVMTI_VERSION_SHIFT_MICRO);
        }

        boolean isSupported() {
            if (major == 1) {
                // version 1.{0,1,2}.<micro> is recognized
                return (minor == 0) || (minor == 1) || (minor == 2);
            }
            if (major == 9) {
                // version 9.0.<micro> is recognized
                return (minor == 0);
            }
            if (major == 11) {
                // version 11.0.<micro> is recognized
                return (minor == 0);
            }
            return false;
        }
    }
}
