/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import com.oracle.jvmci.asm.sparc.*;
import com.oracle.jvmci.code.ForeignCallLinkage;
import com.oracle.jvmci.code.CallingConvention;
import com.oracle.jvmci.code.Register;
import com.oracle.jvmci.code.RegisterValue;

import static com.oracle.jvmci.code.ValueUtil.*;
import static com.oracle.jvmci.sparc.SPARC.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

/**
 * Removes the current frame and jumps to the {@link UnwindExceptionToCallerStub}.
 */
@Opcode("UNWIND")
final class SPARCHotSpotUnwindOp extends SPARCHotSpotEpilogueOp {
    public static final LIRInstructionClass<SPARCHotSpotUnwindOp> TYPE = LIRInstructionClass.create(SPARCHotSpotUnwindOp.class);

    @Use({REG}) protected RegisterValue exception;

    SPARCHotSpotUnwindOp(RegisterValue exception) {
        super(TYPE);
        this.exception = exception;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // This Frame is left but the called unwind (which is sibling) method needs the exception as
        // input in i0
        masm.mov(o0, i0);
        leaveFrame(crb);

        ForeignCallLinkage linkage = crb.foreignCalls.lookupForeignCall(UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention cc = linkage.getOutgoingCallingConvention();
        assert cc.getArgumentCount() == 2;
        assert exception.equals(cc.getArgument(0));

        // Get return address (is in o7 after leave).
        Register returnAddress = asRegister(cc.getArgument(1));
        masm.add(o7, SPARCAssembler.PC_RETURN_OFFSET, returnAddress);
        Register scratch = g5;
        SPARCCall.indirectJmp(crb, masm, scratch, linkage);
    }
}
