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
package com.oracle.graal.hotspot.sparc;

import com.oracle.jvmci.asm.sparc.*;
import com.oracle.jvmci.code.Register;
import com.oracle.jvmci.meta.AllocatableValue;

import static com.oracle.jvmci.code.ValueUtil.*;
import static com.oracle.jvmci.sparc.SPARC.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

/**
 * Pushes an interpreter frame to the stack.
 */
@Opcode("PUSH_INTERPRETER_FRAME")
final class SPARCHotSpotPushInterpreterFrameOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotPushInterpreterFrameOp> TYPE = LIRInstructionClass.create(SPARCHotSpotPushInterpreterFrameOp.class);

    @Alive(REG) AllocatableValue frameSize;
    @Alive(REG) AllocatableValue framePc;
    @Alive(REG) AllocatableValue senderSp;
    @Alive(REG) AllocatableValue initialInfo;

    SPARCHotSpotPushInterpreterFrameOp(AllocatableValue frameSize, AllocatableValue framePc, AllocatableValue senderSp, AllocatableValue initialInfo) {
        super(TYPE);
        this.frameSize = frameSize;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.initialInfo = initialInfo;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        final Register frameSizeRegister = asRegister(frameSize);
        final Register framePcRegister = asRegister(framePc);
        final Register senderSpRegister = asRegister(senderSp);

        // Save sender SP to O5_savedSP.
        masm.mov(senderSpRegister, o5);

        masm.neg(frameSizeRegister);
        masm.save(sp, frameSizeRegister, sp);

        masm.mov(i0, o0);
        masm.mov(i1, o1);
        masm.mov(i2, o2);
        masm.mov(i3, o3);
        masm.mov(i4, o4);

        // NOTE: Don't touch I5 as it contains valuable saved SP!

        // Move frame's new PC into i7
        masm.mov(framePcRegister, i7);
    }

    @Override
    public boolean leavesRegisterWindow() {
        return true;
    }
}
