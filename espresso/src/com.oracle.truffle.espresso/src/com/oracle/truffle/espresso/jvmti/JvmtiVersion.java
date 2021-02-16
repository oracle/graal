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

class JvmtiVersion {
    private static final int JVMTI_VERSION_1 = 0x30010000;
    private static final int JVMTI_VERSION_1_0 = 0x30010000;
    private static final int JVMTI_VERSION_1_1 = 0x30010100;
    private static final int JVMTI_VERSION_1_2 = 0x30010200;
    private static final int JVMTI_VERSION_9 = 0x30090000;
    private static final int JVMTI_VERSION_11 = 0x300B0000;
    /* version: 11.0. 0 */
    private static final int JVMTI_VERSION = 0x30000000 + (11 * 0x10000) + (0 * 0x100) + 0;

    public static boolean isSupportedJvmtiVersion(int version) {
        return version == JVMTI_VERSION || version == JVMTI_VERSION_1_1;
    }
}
