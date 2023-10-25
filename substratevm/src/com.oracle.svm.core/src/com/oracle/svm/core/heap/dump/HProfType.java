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

import com.oracle.svm.core.config.ConfigurationValues;

/* Enum of all relevant HPROF types (see enum hprofTag in HotSpot). */
public enum HProfType {
    NORMAL_OBJECT(0x2, 0),
    BOOLEAN(0x4, 1),
    CHAR(0x5, 2),
    FLOAT(0x6, 4),
    DOUBLE(0x7, 8),
    BYTE(0x8, 1),
    SHORT(0x9, 2),
    INT(0xA, 4),
    LONG(0xB, 8);

    private static final HProfType[] TYPES = HProfType.values();

    private final byte value;
    private final int size;

    HProfType(int value, int size) {
        this.value = NumUtil.safeToUByte(value);
        this.size = size;
    }

    public static HProfType get(byte value) {
        return TYPES[value];
    }

    public byte getValue() {
        return value;
    }

    public int getSize() {
        if (size == 0) {
            return ConfigurationValues.getTarget().wordSize;
        }
        return size;
    }
}
