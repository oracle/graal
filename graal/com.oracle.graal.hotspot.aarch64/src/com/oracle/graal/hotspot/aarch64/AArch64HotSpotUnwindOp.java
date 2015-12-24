/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import com.oracle.graal.asm.aarch64.AArch64MacroAssembler;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.hotspot.stubs.UnwindExceptionToCallerStub;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.aarch64.AArch64Call;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.hotspot.HotSpotVMConfig;

/**
 * Removes the current frame and jumps to the {@link UnwindExceptionToCallerStub}.
 */
@Opcode("UNWIND")
public class AArch64HotSpotUnwindOp extends AArch64HotSpotEpilogueOp {
    public static final LIRInstructionClass<AArch64HotSpotUnwindOp> TYPE = LIRInstructionClass.create(AArch64HotSpotUnwindOp.class);

    @Use protected RegisterValue exception;

    public AArch64HotSpotUnwindOp(HotSpotVMConfig config, RegisterValue exception) {
        super(TYPE, config);
        this.exception = exception;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        leaveFrame(crb, masm, /* emitSafepoint */false);

        ForeignCallLinkage linkage = crb.foreignCalls.lookupForeignCall(UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention cc = linkage.getOutgoingCallingConvention();
        assert cc.getArgumentCount() == 2;
        assert exception.equals(cc.getArgument(0));

        // Get return address (is in lr after frame leave)
        Register returnAddress = asRegister(cc.getArgument(1));
        masm.movx(returnAddress, lr);

        AArch64Call.directJmp(crb, masm, linkage);
    }
}
