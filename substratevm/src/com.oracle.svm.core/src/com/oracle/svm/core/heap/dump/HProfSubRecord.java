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
package com.oracle.svm.core.heap.dump;

import jdk.graal.compiler.core.common.NumUtil;

/* Enum of all relevant HPROF sub-records (see enum hprofTag in HotSpot). */
public enum HProfSubRecord {
    GC_ROOT_UNKNOWN(0xFF),
    GC_ROOT_JNI_GLOBAL(0x01),
    GC_ROOT_JNI_LOCAL(0x02),
    GC_ROOT_JAVA_FRAME(0x03),
    GC_ROOT_NATIVE_STACK(0x04),
    GC_ROOT_STICKY_CLASS(0x05),
    GC_ROOT_THREAD_BLOCK(0x06),
    GC_ROOT_MONITOR_USED(0x07),
    GC_ROOT_THREAD_OBJ(0x08),
    GC_CLASS_DUMP(0x20),
    GC_INSTANCE_DUMP(0x21),
    GC_OBJ_ARRAY_DUMP(0x22),
    GC_PRIM_ARRAY_DUMP(0x23);

    private final byte value;

    HProfSubRecord(int value) {
        this.value = NumUtil.safeToUByte(value);
    }

    public byte getValue() {
        return value;
    }
}
