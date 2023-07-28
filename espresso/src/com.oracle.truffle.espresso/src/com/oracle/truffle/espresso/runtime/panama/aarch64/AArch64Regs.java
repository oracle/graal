/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime.panama.aarch64;

import com.oracle.truffle.espresso.runtime.panama.VMStorage;

final class AArch64Regs {
    // See aarch64.AArch64Architecture
    static final short REG64_MASK = 0b0000_0000_0000_0001;
    static final short V128_MASK = 0b0000_0000_0000_0001;

    static final VMStorage r0 = integerRegister(0);
    static final VMStorage r1 = integerRegister(1);
    static final VMStorage r2 = integerRegister(2);
    static final VMStorage r3 = integerRegister(3);
    static final VMStorage r4 = integerRegister(4);
    static final VMStorage r5 = integerRegister(5);
    static final VMStorage r6 = integerRegister(6);
    static final VMStorage r7 = integerRegister(7);
    static final VMStorage r8 = integerRegister(8);
    static final VMStorage r9 = integerRegister(9);
    static final VMStorage r10 = integerRegister(10);
    static final VMStorage r11 = integerRegister(11);
    static final VMStorage r12 = integerRegister(12);
    static final VMStorage r13 = integerRegister(13);
    static final VMStorage r14 = integerRegister(14);
    static final VMStorage r15 = integerRegister(15);
    static final VMStorage r16 = integerRegister(16);
    static final VMStorage r17 = integerRegister(17);
    static final VMStorage r18 = integerRegister(18);
    static final VMStorage r19 = integerRegister(19);
    static final VMStorage r20 = integerRegister(20);
    static final VMStorage r21 = integerRegister(21);
    static final VMStorage r22 = integerRegister(22);
    static final VMStorage r23 = integerRegister(23);
    static final VMStorage r24 = integerRegister(24);
    static final VMStorage r25 = integerRegister(25);
    static final VMStorage r26 = integerRegister(26);
    static final VMStorage r27 = integerRegister(27);
    static final VMStorage r28 = integerRegister(28);
    static final VMStorage r29 = integerRegister(29);
    static final VMStorage r30 = integerRegister(30);
    static final VMStorage r31 = integerRegister(31);
    static final VMStorage v0 = vectorRegister(0);
    static final VMStorage v1 = vectorRegister(1);
    static final VMStorage v2 = vectorRegister(2);
    static final VMStorage v3 = vectorRegister(3);
    static final VMStorage v4 = vectorRegister(4);
    static final VMStorage v5 = vectorRegister(5);
    static final VMStorage v6 = vectorRegister(6);
    static final VMStorage v7 = vectorRegister(7);
    static final VMStorage v8 = vectorRegister(8);
    static final VMStorage v9 = vectorRegister(9);
    static final VMStorage v10 = vectorRegister(10);
    static final VMStorage v11 = vectorRegister(11);
    static final VMStorage v12 = vectorRegister(12);
    static final VMStorage v13 = vectorRegister(13);
    static final VMStorage v14 = vectorRegister(14);
    static final VMStorage v15 = vectorRegister(15);
    static final VMStorage v16 = vectorRegister(16);
    static final VMStorage v17 = vectorRegister(17);
    static final VMStorage v18 = vectorRegister(18);
    static final VMStorage v19 = vectorRegister(19);
    static final VMStorage v20 = vectorRegister(20);
    static final VMStorage v21 = vectorRegister(21);
    static final VMStorage v22 = vectorRegister(22);
    static final VMStorage v23 = vectorRegister(23);
    static final VMStorage v24 = vectorRegister(24);
    static final VMStorage v25 = vectorRegister(25);
    static final VMStorage v26 = vectorRegister(26);
    static final VMStorage v27 = vectorRegister(27);
    static final VMStorage v28 = vectorRegister(28);
    static final VMStorage v29 = vectorRegister(29);
    static final VMStorage v30 = vectorRegister(30);
    static final VMStorage v31 = vectorRegister(31);

    private AArch64Regs() {
    }

    private static VMStorage integerRegister(int index) {
        return new VMStorage(AArch64StorageType.INTEGER.getId(), REG64_MASK, index);
    }

    private static VMStorage vectorRegister(int index) {
        return new VMStorage(AArch64StorageType.VECTOR.getId(), V128_MASK, index);
    }

    public static String getIntegerRegisterName(int idx) {
        return "r" + idx;
    }

    public static String getVectorRegisterName(int idx) {
        return "v" + idx;
    }
}
