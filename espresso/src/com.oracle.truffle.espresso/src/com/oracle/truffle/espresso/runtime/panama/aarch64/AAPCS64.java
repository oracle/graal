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

import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.r0;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.r1;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.r2;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.r3;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.r4;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.r5;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.r6;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.r7;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v0;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v1;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v2;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v3;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v4;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v5;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v6;
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v7;

import com.oracle.truffle.espresso.runtime.panama.ArgumentsCalculator;
import com.oracle.truffle.espresso.runtime.panama.DefaultArgumentsCalculator;
import com.oracle.truffle.espresso.runtime.panama.Platform;
import com.oracle.truffle.espresso.runtime.panama.StorageType;
import com.oracle.truffle.espresso.runtime.panama.VMStorage;

public class AAPCS64 extends Platform {
    public static final AAPCS64 INSTANCE = new AAPCS64();
    public static final VMStorage[] CALL_INT_REGS = {r0, r1, r2, r3, r4, r5, r6, r7};
    public static final VMStorage[] CALL_FLOAT_REGS = {v0, v1, v2, v3, v4, v5, v6, v7};

    AAPCS64() {
    }

    @Override
    public StorageType getStorageType(byte id) {
        return AArch64StorageType.get(id);
    }

    @Override
    public boolean ignoreDownCallArgument(VMStorage reg) {
        return false;
    }

    @Override
    public ArgumentsCalculator getArgumentsCalculator() {
        return new DefaultArgumentsCalculator(this, CALL_INT_REGS, CALL_FLOAT_REGS, r0, v0);
    }

    @Override
    protected String getIntegerRegisterName(int idx, int maskOrSize) {
        if (maskOrSize == AArch64Regs.REG64_MASK) {
            return AArch64Regs.getIntegerRegisterName(idx);
        } else {
            return "?INT_REG?[" + idx + ", " + maskOrSize + "]";
        }
    }

    @Override
    protected String getVectorRegisterName(int idx, int maskOrSize) {
        if (maskOrSize == AArch64Regs.V128_MASK) {
            return AArch64Regs.getVectorRegisterName(idx);
        } else {
            return "?VEC_REG?[" + idx + ", " + maskOrSize + "]";
        }
    }
}
