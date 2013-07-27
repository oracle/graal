/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.ptx;

import static com.oracle.graal.api.code.MemoryBarriers.*;

import java.nio.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Register.RegisterCategory;
import com.oracle.graal.api.meta.*;

/**
 * Represents the PTX architecture.
 */
public class PTX extends Architecture {

    public static final RegisterCategory CPU = new RegisterCategory("CPU");
    public static final RegisterCategory FPU = new RegisterCategory("FPU");

    // @formatter:off

    /*
     * Register State Space
     *
     * Registers (.reg state space) are fast storage locations. The number of
     * registers is limited, and will vary from platform to platform. When the
     * limit is exceeded, register variables will be spilled to memory, causing
     * changes in performance. For each architecture, there is a recommended
     * maximum number of registers to use (see the "CUDA Programming Guide" for
     * details).
     */

    // General purpose registers
    public static final Register r0  = new Register(0,  0,  "r0",  CPU);
    public static final Register r1  = new Register(1,  1,  "r1",  CPU);
    public static final Register r2  = new Register(2,  2,  "r2",  CPU);
    public static final Register r3  = new Register(3,  3,  "r3",  CPU);
    public static final Register r4  = new Register(4,  4,  "r4",  CPU);
    public static final Register r5  = new Register(5,  5,  "r5",  CPU);
    public static final Register r6  = new Register(6,  6,  "r6",  CPU);
    public static final Register r7  = new Register(7,  7,  "r7",  CPU);

    public static final Register r8  = new Register(8,  8,  "r8",  CPU);
    public static final Register r9  = new Register(9,  9,  "r9",  CPU);
    public static final Register r10 = new Register(10, 10, "r10", CPU);
    public static final Register r11 = new Register(11, 11, "r11", CPU);
    public static final Register r12 = new Register(12, 12, "r12", CPU);
    public static final Register r13 = new Register(13, 13, "r13", CPU);
    public static final Register r14 = new Register(14, 14, "r14", CPU);
    public static final Register r15 = new Register(15, 15, "r15", CPU);

    public static final Register[] gprRegisters = {
        r0,  r1,  r2,  r3,  r4,  r5,  r6,  r7,
        r8,  r9,  r10, r11, r12, r13, r14, r15
    };

    // Floating point registers
    public static final Register f0  = new Register(16, 0,  "f0",  FPU);
    public static final Register f1  = new Register(17, 1,  "f1",  FPU);
    public static final Register f2  = new Register(18, 2,  "f2",  FPU);
    public static final Register f3  = new Register(19, 3,  "f3",  FPU);
    public static final Register f4  = new Register(20, 4,  "f4",  FPU);
    public static final Register f5  = new Register(21, 5,  "f5",  FPU);
    public static final Register f6  = new Register(22, 6,  "f6",  FPU);
    public static final Register f7  = new Register(23, 7,  "f7",  FPU);

    public static final Register f8  = new Register(24, 8,  "f8",  FPU);
    public static final Register f9  = new Register(25, 9,  "f9",  FPU);
    public static final Register f10 = new Register(26, 10, "f10", FPU);
    public static final Register f11 = new Register(27, 11, "f11", FPU);
    public static final Register f12 = new Register(28, 12, "f12", FPU);
    public static final Register f13 = new Register(29, 13, "f13", FPU);
    public static final Register f14 = new Register(30, 14, "f14", FPU);
    public static final Register f15 = new Register(31, 15, "f15", FPU);

    public static final Register[] fpuRegisters = {
        f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
        f8,  f9,  f10, f11, f12, f13, f14, f15
    };

    public static final Register[] allRegisters = {
        // GPR
        r0,  r1,  r2,  r3,  r4,  r5,  r6,  r7,
        r8,  r9,  r10, r11, r12, r13, r14, r15,
        // FPU
        f0,  f1,  f2,  f3,  f4,  f5,  f6,  f7,
        f8,  f9,  f10, f11, f12, f13, f14, f15
    };

    // @formatter:on

    public PTX() {
        super("PTX", 8, ByteOrder.LITTLE_ENDIAN, false, allRegisters, LOAD_STORE | STORE_STORE, 0, r15.encoding + 1, 8);
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind platformKind) {
        if (!(platformKind instanceof Kind)) {
            return false;
        }

        Kind kind = (Kind) platformKind;
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
