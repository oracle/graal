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

import static com.oracle.graal.hotspot.HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.REG;
import static jdk.internal.jvmci.code.ValueUtil.asRegister;
import static jdk.internal.jvmci.sparc.SPARC.g5;
import static jdk.internal.jvmci.sparc.SPARC.i0;
import static jdk.internal.jvmci.sparc.SPARC.o0;
import static jdk.internal.jvmci.sparc.SPARC.o7;
import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.Register;
import jdk.internal.jvmci.code.RegisterValue;

import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.hotspot.stubs.UnwindExceptionToCallerStub;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.Opcode;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.sparc.SPARCCall;

/**
 * Removes the current frame and jumps to the {@link UnwindExceptionToCallerStub}.
 */
@Opcode("UNWIND")
final class SPARCHotSpotUnwindOp extends SPARCHotSpotEpilogueOp {
    public static final LIRInstructionClass<SPARCHotSpotUnwindOp> TYPE = LIRInstructionClass.create(SPARCHotSpotUnwindOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(32);

    @Use({REG}) protected RegisterValue exception;

    SPARCHotSpotUnwindOp(RegisterValue exception) {
        super(TYPE, SIZE);
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
