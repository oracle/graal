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
import static com.oracle.truffle.espresso.runtime.panama.aarch64.AArch64Regs.v0;

import com.oracle.truffle.espresso.runtime.panama.ArgumentsCalculator;
import com.oracle.truffle.espresso.runtime.panama.DarwinAArch64ArgumentsCalculator;

public final class DarwinAAPCS64 extends AAPCS64 {
    public static final DarwinAAPCS64 INSTANCE = new DarwinAAPCS64();

    private DarwinAAPCS64() {
    }

    @Override
    public ArgumentsCalculator getArgumentsCalculator() {
        return new DarwinAArch64ArgumentsCalculator(this, CALL_INT_REGS, CALL_FLOAT_REGS, r0, v0);
    }
}
