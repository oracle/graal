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

/* Enum of all relevant HPROF top-level records (see enum hprofTag in HotSpot). */
public enum HProfTopLevelRecord {
    UTF8(0x01),
    LOAD_CLASS(0x02),
    UNLOAD_CLASS(0x03),
    FRAME(0x04),
    TRACE(0x05),
    ALLOC_SITES(0x06),
    HEAP_SUMMARY(0x07),
    START_THREAD(0x0A),
    END_THREAD(0x0B),
    HEAP_DUMP(0x0C),
    CPU_SAMPLES(0x0D),
    CONTROL_SETTINGS(0x0E),
    HEAP_DUMP_SEGMENT(0x1C),
    HEAP_DUMP_END(0x2C);

    private final byte value;

    HProfTopLevelRecord(int value) {
        this.value = NumUtil.safeToUByte(value);
    }

    public byte getValue() {
        return value;
    }
}
