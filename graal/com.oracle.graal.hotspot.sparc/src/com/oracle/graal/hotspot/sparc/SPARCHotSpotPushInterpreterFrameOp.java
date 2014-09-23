/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.asm.*;

/**
 * Pushes an interpreter frame to the stack.
 */
@Opcode("PUSH_INTERPRETER_FRAME")
final class SPARCHotSpotPushInterpreterFrameOp extends SPARCLIRInstruction {

    @Alive(REG) AllocatableValue frameSize;
    @Alive(REG) AllocatableValue framePc;
    @Alive(REG) AllocatableValue senderSp;
    @Alive(REG) AllocatableValue initialInfo;

    SPARCHotSpotPushInterpreterFrameOp(AllocatableValue frameSize, AllocatableValue framePc, AllocatableValue senderSp, AllocatableValue initialInfo) {
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
        new Mov(senderSpRegister, o5).emit(masm);

        new Neg(frameSizeRegister).emit(masm);
        new Save(sp, frameSizeRegister, sp).emit(masm);

        new Mov(i0, o0).emit(masm);
        new Mov(i1, o1).emit(masm);
        new Mov(i2, o2).emit(masm);
        new Mov(i3, o3).emit(masm);
        new Mov(i4, o4).emit(masm);

        // NOTE: Don't touch I5 as it contains valuable saved SP!

        // Move frame's new PC into i7
        new Mov(framePcRegister, i7).emit(masm);
    }

    @Override
    public boolean leavesRegisterWindow() {
        return true;
    }
}
