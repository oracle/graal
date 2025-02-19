/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.aarch64;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

public class SubstrateAArch64MacroAssembler extends AArch64MacroAssembler {
    // r0-r7 are used for arguments as per platform ABI.
    // r8 is used as "indirect result location register" in the platform ABI.
    public static final Register scratch1 = AArch64.r9;
    public static final Register scratch2 = AArch64.r10;

    private final ScratchRegister[] scratchRegisters = new ScratchRegister[]{new ScratchRegister(scratch1), new ScratchRegister(scratch2)};

    public SubstrateAArch64MacroAssembler(TargetDescription target) {
        super(target);
    }

    @Override
    protected ScratchRegister[] getScratchRegisters() {
        return scratchRegisters;
    }
}
