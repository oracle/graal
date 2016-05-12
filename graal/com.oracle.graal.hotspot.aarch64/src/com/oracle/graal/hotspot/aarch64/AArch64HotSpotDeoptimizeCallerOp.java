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
package com.oracle.graal.hotspot.aarch64;

import static com.oracle.graal.hotspot.HotSpotHostBackend.UNCOMMON_TRAP_HANDLER;

import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.aarch64.AArch64Call;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.hotspot.HotSpotVMConfig;

/**
 * Removes the current frame and tail calls the uncommon trap routine.
 */
@Opcode("DEOPT_CALLER")
public class AArch64HotSpotDeoptimizeCallerOp extends AArch64HotSpotEpilogueOp {
    public static final LIRInstructionClass<AArch64HotSpotDeoptimizeCallerOp> TYPE = LIRInstructionClass.create(AArch64HotSpotDeoptimizeCallerOp.class);

    public AArch64HotSpotDeoptimizeCallerOp(HotSpotVMConfig config) {
        super(TYPE, config);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        leaveFrame(crb, masm, /* emitSafepoint */false);
        AArch64Call.directJmp(crb, masm, crb.foreignCalls.lookupForeignCall(UNCOMMON_TRAP_HANDLER));
    }
}
