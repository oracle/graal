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
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.rdx;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm0;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm1;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm2;
import static com.oracle.truffle.espresso.runtime.panama.x64.X64Regs.xmm3;

import com.oracle.truffle.espresso.runtime.panama.ArgumentsCalculator;
import com.oracle.truffle.espresso.runtime.panama.VMStorage;
import com.oracle.truffle.espresso.runtime.panama.WindowsArgumentsCalculator;

public final class WindowsX64 extends X64Platform {
    public static final WindowsX64 INSTANCE = new WindowsX64();
    public static final VMStorage[] CALL_INT_REGS = {rcx, rdx, r8, r9};
    public static final VMStorage[] CALL_FLOAT_REGS = {xmm0, xmm1, xmm2, xmm3};

    private WindowsX64() {
    }

    @Override
    public boolean ignoreDownCallArgument(VMStorage reg) {
        return false;
    }

    @Override
    public ArgumentsCalculator getArgumentsCalculator() {
        return new WindowsArgumentsCalculator(this, CALL_INT_REGS, CALL_FLOAT_REGS, rax, xmm0);
    }
}
