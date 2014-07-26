/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.sparc;

import static com.oracle.graal.api.code.MemoryBarriers.*;

import java.nio.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Register.RegisterCategory;
import com.oracle.graal.api.meta.*;

/**
 * Represents the SPARC architecture.
 */
public class SPARC extends Architecture {

    public static final RegisterCategory CPU = new RegisterCategory("CPU");

    // General purpose registers
    public static final Register r0 = new Register(0, 0, "g0", CPU);
    public static final Register r1 = new Register(1, 1, "g1", CPU);
    public static final Register r2 = new Register(2, 2, "g2", CPU);
    public static final Register r3 = new Register(3, 3, "g3", CPU);
    public static final Register r4 = new Register(4, 4, "g4", CPU);
    public static final Register r5 = new Register(5, 5, "g5", CPU);
    public static final Register r6 = new Register(6, 6, "g6", CPU);
    public static final Register r7 = new Register(7, 7, "g7", CPU);

    public static final Register r8 = new Register(8, 8, "o0", CPU);
    public static final Register r9 = new Register(9, 9, "o1", CPU);
    public static final Register r10 = new Register(10, 10, "o2", CPU);
    public static final Register r11 = new Register(11, 11, "o3", CPU);
    public static final Register r12 = new Register(12, 12, "o4", CPU);
    public static final Register r13 = new Register(13, 13, "o5", CPU);
    public static final Register r14 = new Register(14, 14, "o6", CPU);
    public static final Register r15 = new Register(15, 15, "o7", CPU);

    public static final Register r16 = new Register(16, 16, "l0", CPU);
    public static final Register r17 = new Register(17, 17, "l1", CPU);
    public static final Register r18 = new Register(18, 18, "l2", CPU);
    public static final Register r19 = new Register(19, 19, "l3", CPU);
    public static final Register r20 = new Register(20, 20, "l4", CPU);
    public static final Register r21 = new Register(21, 21, "l5", CPU);
    public static final Register r22 = new Register(22, 22, "l6", CPU);
    public static final Register r23 = new Register(23, 23, "l7", CPU);

    public static final Register r24 = new Register(24, 24, "i0", CPU);
    public static final Register r25 = new Register(25, 25, "i1", CPU);
    public static final Register r26 = new Register(26, 26, "i2", CPU);
    public static final Register r27 = new Register(27, 27, "i3", CPU);
    public static final Register r28 = new Register(28, 28, "i4", CPU);
    public static final Register r29 = new Register(29, 29, "i5", CPU);
    public static final Register r30 = new Register(30, 30, "i6", CPU);
    public static final Register r31 = new Register(31, 31, "i7", CPU);

    public static final Register g0 = r0;
    public static final Register g1 = r1;
    public static final Register g2 = r2;
    public static final Register g3 = r3;
    public static final Register g4 = r4;
    public static final Register g5 = r5;
    public static final Register g6 = r6;
    public static final Register g7 = r7;

    public static final Register o0 = r8;
    public static final Register o1 = r9;
    public static final Register o2 = r10;
    public static final Register o3 = r11;
    public static final Register o4 = r12;
    public static final Register o5 = r13;
    public static final Register o6 = r14;
    public static final Register o7 = r15;

    public static final Register l0 = r16;
    public static final Register l1 = r17;
    public static final Register l2 = r18;
    public static final Register l3 = r19;
    public static final Register l4 = r20;
    public static final Register l5 = r21;
    public static final Register l6 = r22;
    public static final Register l7 = r23;

    public static final Register i0 = r24;
    public static final Register i1 = r25;
    public static final Register i2 = r26;
    public static final Register i3 = r27;
    public static final Register i4 = r28;
    public static final Register i5 = r29;
    public static final Register i6 = r30;
    public static final Register i7 = r31;

    public static final Register sp = o6;
    public static final Register fp = i6;

    // @formatter:off
    public static final Register[] cpuRegisters = {
        r0,  r1,  r2,  r3,  r4,  r5,  r6,  r7,
        r8,  r9,  r10, r11, r12, r13, r14, r15,
        r16, r17, r18, r19, r20, r21, r22, r23,
        r24, r25, r26, r27, r28, r29, r30, r31
    };
    // @formatter:on

    public static final RegisterCategory FPU = new RegisterCategory("FPU", cpuRegisters.length);

    // Floating point registers
    public static final Register f0 = new Register(32, 0, "f0", FPU);
    public static final Register f1 = new Register(33, 1, "f1", FPU);
    public static final Register f2 = new Register(34, 2, "f2", FPU);
    public static final Register f3 = new Register(35, 3, "f3", FPU);
    public static final Register f4 = new Register(36, 4, "f4", FPU);
    public static final Register f5 = new Register(37, 5, "f5", FPU);
    public static final Register f6 = new Register(38, 6, "f6", FPU);
    public static final Register f7 = new Register(39, 7, "f7", FPU);

    public static final Register f8 = new Register(40, 8, "f8", FPU);
    public static final Register f9 = new Register(41, 9, "f9", FPU);
    public static final Register f10 = new Register(42, 10, "f10", FPU);
    public static final Register f11 = new Register(43, 11, "f11", FPU);
    public static final Register f12 = new Register(44, 12, "f12", FPU);
    public static final Register f13 = new Register(45, 13, "f13", FPU);
    public static final Register f14 = new Register(46, 14, "f14", FPU);
    public static final Register f15 = new Register(47, 15, "f15", FPU);

    public static final Register f16 = new Register(48, 16, "f16", FPU);
    public static final Register f17 = new Register(49, 17, "f17", FPU);
    public static final Register f18 = new Register(50, 18, "f18", FPU);
    public static final Register f19 = new Register(51, 19, "f19", FPU);
    public static final Register f20 = new Register(52, 20, "f20", FPU);
    public static final Register f21 = new Register(53, 21, "f21", FPU);
    public static final Register f22 = new Register(54, 22, "f22", FPU);
    public static final Register f23 = new Register(55, 23, "f23", FPU);

    public static final Register f24 = new Register(56, 24, "f24", FPU);
    public static final Register f25 = new Register(57, 25, "f25", FPU);
    public static final Register f26 = new Register(58, 26, "f26", FPU);
    public static final Register f27 = new Register(59, 27, "f27", FPU);
    public static final Register f28 = new Register(60, 28, "f28", FPU);
    public static final Register f29 = new Register(61, 29, "f29", FPU);
    public static final Register f30 = new Register(62, 30, "f30", FPU);
    public static final Register f31 = new Register(63, 31, "f31", FPU);

    // @formatter:off
    public static final Register[] fpuRegisters = {
        f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
        f8,  f9,  f10, f11, f12, f13, f14, f15,
        f16, f17, f18, f19, f20, f21, f22, f23,
        f24, f25, f26, f27, f28, f29, f30, f31
    };
    // @formatter:on

    // @formatter:off
    public static final Register[] allRegisters = {
        // CPU
        r0,  r1,  r2,  r3,  r4,  r5,  r6,  r7,
        r8,  r9,  r10, r11, r12, r13, r14, r15,
        r16, r17, r18, r19, r20, r21, r22, r23,
        r24, r25, r26, r27, r28, r29, r30, r31,
        // FPU
        f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
        f8,  f9,  f10, f11, f12, f13, f14, f15,
        f16, f17, f18, f19, f20, f21, f22, f23,
        f24, f25, f26, f27, f28, f29, f30, f31
    };
    // @formatter:on

    /**
     * Stack bias for stack and frame pointer loads.
     */
    public static final int STACK_BIAS = 0x7ff;
    /**
     * In fact there are 64 single floating point registers, 32 of them could be accessed. TODO:
     * Improve handling of these float registers
     */
    public static final int FLOAT_REGISTER_COUNT = 64;

    public SPARC() {
        super("SPARC", 8, ByteOrder.BIG_ENDIAN, false, allRegisters, LOAD_STORE | STORE_STORE, 1, r31.encoding + FLOAT_REGISTER_COUNT + 1, 8);
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind lirKind) {
        if (!(lirKind instanceof Kind)) {
            return false;
        }

        Kind kind = (Kind) lirKind;
        if (category == CPU) {
            switch (kind) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                case Long:
                case Object:
                    return true;
            }
        } else if (category == FPU) {
            switch (kind) {
                case Float:
                case Double:
                    return true;
            }
        }
        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        if (category == CPU) {
            return Kind.Long;
        } else if (category == FPU) {
            return Kind.Double;
        } else {
            return Kind.Illegal;
        }
    }
}
