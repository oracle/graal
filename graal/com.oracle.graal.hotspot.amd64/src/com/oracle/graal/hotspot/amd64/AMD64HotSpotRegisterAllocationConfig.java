/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.compiler.common.alloc.*;

class AMD64HotSpotRegisterAllocationConfig extends RegisterAllocationConfig {
    /**
     * Specify priority of register selection within phases of register allocation. Highest priority
     * is first. A useful heuristic is to give registers a low priority when they are required by
     * machine instructions, like EAX and EDX on I486, and choose no-save registers before
     * save-on-call, & save-on-call before save-on-entry. Registers which participate in fixed
     * calling sequences should come last. Registers which are used as pairs must fall on an even
     * boundary.
     *
     * Adopted from x86_64.ad.
     */
    // @formatter:off
    static final Register[] registerAllocationOrder = {
        r10, r11, r8, r9, r12, rcx, rbx, rdi, rdx, rsi, rax, rbp, r13, r14, /*r15,*/ /*rsp,*/
        xmm0, xmm1, xmm2,  xmm3,  xmm4,  xmm5,  xmm6,  xmm7,
        xmm8, xmm9, xmm10, xmm11, xmm12, xmm13, xmm14, xmm15
    };
    // @formatter:on

    public AMD64HotSpotRegisterAllocationConfig(RegisterConfig registerConfig) {
        super(registerConfig);
    }

    @Override
    protected Register[] initAllocatable(Register[] registers) {
        BitSet regMap = new BitSet(registerConfig.getAllocatableRegisters().length);
        Register[] regs = super.initAllocatable(registers);
        for (Register reg : regs) {
            regMap.set(reg.number);
        }

        Register[] allocatableRegisters = new Register[regs.length];
        int i = 0;
        for (Register reg : registerAllocationOrder) {
            if (regMap.get(reg.number)) {
                allocatableRegisters[i++] = reg;
            }
        }

        assert i == allocatableRegisters.length;
        return allocatableRegisters;
    }

    @Override
    protected AllocatableRegisters createAllocatableRegisters(Register[] registers) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Register reg : registers) {
            int number = reg.number;
            if (number < min) {
                min = number;
            }
            if (number > max) {
                max = number;
            }
        }
        assert min < max;
        return new AllocatableRegisters(registers, min, max);
    }
}
