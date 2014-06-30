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

    public static final RegisterCategory REG = new RegisterCategory("REG");
    public static final RegisterCategory SREG = new RegisterCategory("SREG");
    public static final RegisterCategory PARAM = new RegisterCategory("PARAM");

    // @formatter:off

    /*
     * Register State Space
     *
     * Registers (.reg state space) are fast storage locations. The
     * number of GPU architectural registers is limited, and will vary
     * from platform to platform. When the limit is exceeded, register
     * variables will be spilled to memory, causing changes in
     * performance. For each architecture, there is a recommended
     * maximum number of registers to use (see the "CUDA Programming
     * Guide" for details).
     *
     * TODD: XXX
     *
     * However, PTX supports virtual registers. So, the generated PTX
     * code does not need to use a specified number of registers. Till
     * we figure out how to model a virtual register set in Graal, we
     * will pretend that we can use only 16 registers.
     */

    public static final Register r0  = new Register(0,   0,  "r0",  REG);
    public static final Register r1  = new Register(1,   1,  "r1",  REG);
    public static final Register r2  = new Register(2,   2,  "r2",  REG);
    public static final Register r3  = new Register(3,   3,  "r3",  REG);
    public static final Register r4  = new Register(4,   4,  "r4",  REG);
    public static final Register r5  = new Register(5,   5,  "r5",  REG);
    public static final Register r6  = new Register(6,   6,  "r6",  REG);
    public static final Register r7  = new Register(7,   7,  "r7",  REG);

    public static final Register r8  = new Register(8,   8,  "r8",  REG);
    public static final Register r9  = new Register(9,   9,  "r9",  REG);
    public static final Register r10 = new Register(10, 10, "r10", REG);
    public static final Register r11 = new Register(11, 11, "r11", REG);
    public static final Register r12 = new Register(12, 12, "r12", REG);
    public static final Register r13 = new Register(13, 13, "r13", REG);
    public static final Register r14 = new Register(14, 14, "r14", REG);
    public static final Register r15 = new Register(15, 15, "r15", REG);

    public static final Register[] gprRegisters = {
        r0,  r1,  r2,  r3,  r4,  r5,  r6,  r7,
        r8,  r9,  r10, r11, r12, r13, r14, r15
    };

    /* Parameter State Space
     *
     * The parameter (.param) state space is used (1) to pass input
     * arguments from the host to the kernel, (2a) to declare formal
     * input and return parameters for device functions called from
     * within kernel execution, and (2b) to declare locally-scoped
     * byte array variables that serve as function call arguments,
     * typically for passing large structures by value to a function.
     *
     * TODO: XXX
     * The parameters are virtual symbols - just like registers. Bit,
     * Till we figure out how to model a virtual register set in Graal,
     * we will pretend that we can use only 8 parameters.
    */

    public static final Register param0  = new Register(16, 16,  "param0",  PARAM);
    public static final Register param1  = new Register(17, 17,  "param1",  PARAM);
    public static final Register param2  = new Register(18, 18,  "param2",  PARAM);
    public static final Register param3  = new Register(19, 19,  "param3",  PARAM);
    public static final Register param4  = new Register(20, 20,  "param4",  PARAM);
    public static final Register param5  = new Register(21, 21,  "param5",  PARAM);
    public static final Register param6  = new Register(22, 22,  "param6",  PARAM);
    public static final Register param7  = new Register(23, 23,  "param7",  PARAM);

    public static final Register[] paramRegisters = {
        param0,  param1,  param2,  param3,  param4,  param5,  param6,  param7
    };

    // Define a virtual register that holds return value
    public static final Register retReg = new Register(24, 24, "retReg", REG);

    // PTX ISA Manual: Section 9:. Special Registers

    // PTX includes a number of predefined, read-only variables, which
    // are visible as special registers and accessed through mov or
    // cvt instructions.
    // Thread identifier within a Co-operative Thread Array (CTA) - %tid
    public static final Register tid  = new Register(100,  100,  "tid", SREG);
    // Number of thread IDs per CTA - %ntid
    public static final Register ntid    = new Register(101, 101, "ntid", SREG);
    // Lane identifier
    public static final Register laneid  = new Register(102, 102, "laneid", SREG);
    // Warp identifier
    public static final Register warpid   = new Register(103, 103, "warid", SREG);
    // Number of warp IDs
    public static final Register nwarpid   = new Register(104, 104, "nwarpid", SREG);
    // CTA identifier
    public static final Register ctaid   = new Register(105, 105, "ctaid", SREG);
    // Number of CTA IDs per grid
    public static final Register nctaid   = new Register(106, 106, "nctaid", SREG);
    // Single Multiprocessor (SM) ID
    public static final Register smid    = new Register(107, 107, "smid", SREG);
    // Number of SM IDs
    public static final Register nsmid   = new Register(108, 108, "nsmid", SREG);
    // Grid ID
    public static final Register gridid  = new Register(109, 109, "gridid", SREG);
    // 32-bit mask with bit set in position equal to thread's lane number in the warp
    public static final Register lanemask_eq  = new Register(110, 110, "lanemask_eq", SREG);
    // 32-bit mask with bits set in positions less than or equal to thread's lane number in the warp
    public static final Register lanemask_le  = new Register(111, 111, "lanemask_le", SREG);
    // 32-bit mask with bits set in positions less than thread's lane number in the warp
    public static final Register lanemask_lt  = new Register(112, 112, "lanemask_lt", SREG);
    // 32-bit mask with bits set in positions greater than or equal to thread's lane number in the warp
    public static final Register lanemask_ge  = new Register(113, 113, "lanemask_ge", SREG);
    // 32-bit mask with bits set in positions greater than thread's lane number in the warp
    public static final Register lanemask_gt  = new Register(114, 114, "lanemask_gt", SREG);
    // A predefined, read-only 32-bit unsigned 32-bit unsigned cycle counter
    public static final Register clock  = new Register(114, 114, "clock", SREG);
    // A predefined, read-only 64-bit unsigned 32-bit unsigned cycle counter
    public static final Register clock64  = new Register(115, 115, "clock64", SREG);
    // Performance monitoring registers
    public static final Register pm0  = new Register(116, 116,  "pm0", SREG);
    public static final Register pm1  = new Register(117, 117,  "pm1", SREG);
    public static final Register pm2  = new Register(118, 118,  "pm2", SREG);
    public static final Register pm3  = new Register(119, 119,  "pm3", SREG);
    public static final Register pm4  = new Register(120, 120,  "pm4", SREG);
    public static final Register pm5  = new Register(121, 121,  "pm5", SREG);
    public static final Register pm6  = new Register(122, 122,  "pm6", SREG);
    public static final Register pm7  = new Register(123, 123,  "pm7", SREG);
    // TODO: Add Driver-defined read-only %envreg<32>
    //       and %globaltimer, %globaltimer_lo and %globaltimer_hi

    public static final Register[] specialRegisters = {
        tid, ntid, laneid, warpid, nwarpid, ctaid,
        nctaid, smid, nsmid, gridid,
        lanemask_eq, lanemask_le, lanemask_lt, lanemask_ge, lanemask_gt,
        clock, clock64,
        pm0, pm1, pm2, pm3, pm4, pm5, pm6, pm7
    };

    public static final Register[] allRegisters = {
        // Parameter State Space
        param0, param1, param2, param3,
        param4, param5, param6, param7,
        // Register State Space
        r0,  r1,  r2,  r3,  r4,  r5,  r6,  r7,
        r8,  r9,  r10, r11, r12, r13, r14, r15,
        // return register
        retReg,
        // Special Register State Space - SREG
        tid, ntid, laneid, warpid, nwarpid, ctaid,
        nctaid, smid, nsmid, gridid,
        lanemask_eq, lanemask_le, lanemask_lt, lanemask_ge, lanemask_gt,
        clock, clock64,
        pm0, pm1, pm2, pm3, pm4, pm5, pm6, pm7
    };

    // @formatter:on

    public PTX() {
        super("PTX", 8, ByteOrder.LITTLE_ENDIAN, false, allRegisters, LOAD_STORE | STORE_STORE, 0, r15.encoding + 1, 8);
    }

    @Override
    public boolean canStoreValue(RegisterCategory category, PlatformKind lirKind) {
        if (!(lirKind instanceof Kind)) {
            return false;
        }

        Kind kind = (Kind) lirKind;
        if (category == REG) {
            switch (kind) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                case Long:
                case Object:
                case Float:
                case Double:
                    return true;
            }
        }

        return false;
    }

    @Override
    public PlatformKind getLargestStorableKind(RegisterCategory category) {
        if (category == REG) {
            return Kind.Double;
        } else {
            return Kind.Illegal;
        }
    }

}
