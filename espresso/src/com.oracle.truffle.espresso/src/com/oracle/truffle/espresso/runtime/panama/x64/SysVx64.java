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

import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.r8;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.r9;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.rax;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.rcx;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.rdi;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.rdx;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.rsi;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm0;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm1;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm2;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm3;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm4;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm5;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm6;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm7;

import com.oracle.truffle.espresso.runtime.panama.ArgumentsCalculator;
import com.oracle.truffle.espresso.runtime.panama.DefaultArgumentsCalculator;
import com.oracle.truffle.espresso.runtime.panama.VMStorage;

public final class SysVx64 extends X64Platform {
    public static final SysVx64 INSTANCE = new SysVx64();
    public static final VMStorage[] CALL_INT_REGS = {rdi, rsi, rdx, rcx, r8, r9};
    public static final VMStorage[] CALL_FLOAT_REGS = {xmm0, xmm1, xmm2, xmm3, xmm4, xmm5, xmm6, xmm7};

    private SysVx64() {
    }

    @Override
    public boolean ignoreDownCallArgument(VMStorage reg) {
        // used for variadic functions to store the number of arguments
        return X64Regs.rax.equals(reg);
    }

    @Override
    public ArgumentsCalculator getArgumentsCalculator() {
        return new DefaultArgumentsCalculator(this, CALL_INT_REGS, CALL_FLOAT_REGS, rax, xmm0);
    }
}
