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
package com.oracle.truffle.espresso.runtime.panama.x64;

import com.oracle.truffle.espresso.runtime.panama.VMStorage;

final class X64Regs {
    // See jdk.internal.foreign.abi.x64.X86_64Architecture
    static final short REG64_MASK = 0b0000000000001111;
    static final short XMM_MASK = 0b0000000000000001;

    static final VMStorage rax = integerRegister(0);
    static final VMStorage rcx = integerRegister(1);
    static final VMStorage rdx = integerRegister(2);
    static final VMStorage rbx = integerRegister(3);
    static final VMStorage rsp = integerRegister(4);
    static final VMStorage rbp = integerRegister(5);
    static final VMStorage rsi = integerRegister(6);
    static final VMStorage rdi = integerRegister(7);
    static final VMStorage r8 = integerRegister(8);
    static final VMStorage r9 = integerRegister(9);
    static final VMStorage r10 = integerRegister(10);
    static final VMStorage r11 = integerRegister(11);
    static final VMStorage r12 = integerRegister(12);
    static final VMStorage r13 = integerRegister(13);
    static final VMStorage r14 = integerRegister(14);
    static final VMStorage r15 = integerRegister(15);

    static final VMStorage xmm0 = vectorRegister(0);
    static final VMStorage xmm1 = vectorRegister(1);
    static final VMStorage xmm2 = vectorRegister(2);
    static final VMStorage xmm3 = vectorRegister(3);
    static final VMStorage xmm4 = vectorRegister(4);
    static final VMStorage xmm5 = vectorRegister(5);
    static final VMStorage xmm6 = vectorRegister(6);
    static final VMStorage xmm7 = vectorRegister(7);
    static final VMStorage xmm8 = vectorRegister(8);
    static final VMStorage xmm9 = vectorRegister(9);
    static final VMStorage xmm10 = vectorRegister(10);
    static final VMStorage xmm11 = vectorRegister(11);
    static final VMStorage xmm12 = vectorRegister(12);
    static final VMStorage xmm13 = vectorRegister(13);
    static final VMStorage xmm14 = vectorRegister(14);
    static final VMStorage xmm15 = vectorRegister(15);
    static final VMStorage xmm16 = vectorRegister(16);
    static final VMStorage xmm17 = vectorRegister(17);
    static final VMStorage xmm18 = vectorRegister(18);
    static final VMStorage xmm19 = vectorRegister(19);
    static final VMStorage xmm20 = vectorRegister(20);
    static final VMStorage xmm21 = vectorRegister(21);
    static final VMStorage xmm22 = vectorRegister(22);
    static final VMStorage xmm23 = vectorRegister(23);
    static final VMStorage xmm24 = vectorRegister(24);
    static final VMStorage xmm25 = vectorRegister(25);
    static final VMStorage xmm26 = vectorRegister(26);
    static final VMStorage xmm27 = vectorRegister(27);
    static final VMStorage xmm28 = vectorRegister(28);
    static final VMStorage xmm29 = vectorRegister(29);
    static final VMStorage xmm30 = vectorRegister(30);
    static final VMStorage xmm31 = vectorRegister(31);

    private X64Regs() {
    }

    private static VMStorage integerRegister(int index) {
        return new VMStorage(X64StorageType.INTEGER.getId(), REG64_MASK, index);
    }

    private static VMStorage vectorRegister(int index) {
        return new VMStorage(X64StorageType.VECTOR.getId(), XMM_MASK, index);
    }

    public static String getIntegerRegisterName(int idx) {
        return switch (idx) {
            case 0 -> "rax";
            case 1 -> "rcx";
            case 2 -> "rdx";
            case 3 -> "rbx";
            case 4 -> "rsp";
            case 5 -> "rbp";
            case 6 -> "rsi";
            case 7 -> "rdi";
            default -> "r" + idx;
        };
    }

    public static String getVectorRegisterName(int idx) {
        return "xmm" + idx;
    }
}
