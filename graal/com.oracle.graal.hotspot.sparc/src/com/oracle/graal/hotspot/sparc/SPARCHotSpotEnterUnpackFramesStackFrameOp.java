/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.asm.*;

/**
 * Emits code that enters a stack frame which is tailored to call the C++ method
 * {@link HotSpotBackend#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@Opcode("ENTER_UNPACK_FRAMES_STACK_FRAME")
final class SPARCHotSpotEnterUnpackFramesStackFrameOp extends SPARCLIRInstruction {

    private final Register thread;
    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    @Alive(REG) AllocatableValue framePc;
    @Alive(REG) AllocatableValue senderSp;
    @Temp(REG) AllocatableValue scratch;
    private SaveRegistersOp saveRegisterOp;

    SPARCHotSpotEnterUnpackFramesStackFrameOp(Register thread, int threadLastJavaSpOffset, int threadLastJavaPcOffset, AllocatableValue framePc, AllocatableValue senderSp, AllocatableValue scratch,
                    SaveRegistersOp saveRegisterOp) {
        this.thread = thread;
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.scratch = scratch;
        this.saveRegisterOp = saveRegisterOp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        final int totalFrameSize = crb.frameMap.totalFrameSize();
        Register framePcRegister = asRegister(framePc);
        Register senderSpRegister = asRegister(senderSp);
        Register scratchRegister = asRegister(scratch);

        // Save final sender SP to O5_savedSP.
        new Mov(senderSpRegister, o5).emit(masm);

        // Load final frame PC.
        new Mov(framePcRegister, o7).emit(masm);

        // Allocate a full sized frame.
        new Save(sp, -totalFrameSize, sp).emit(masm);

        new Mov(i0, o0).emit(masm);
        new Mov(i1, o1).emit(masm);
        new Mov(i2, o2).emit(masm);
        new Mov(i3, o3).emit(masm);
        new Mov(i4, o4).emit(masm);

        // Set up last Java values.
        new Add(sp, STACK_BIAS, scratchRegister).emit(masm);
        new Stx(scratchRegister, new SPARCAddress(thread, threadLastJavaSpOffset)).emit(masm);

        // Clear last Java PC.
        new Stx(g0, new SPARCAddress(thread, threadLastJavaPcOffset)).emit(masm);

        /*
         * Safe thread register manually since we are not using LEAF_SP for {@link
         * DeoptimizationStub#UNPACK_FRAMES}.
         */
        new Mov(thread, l7).emit(masm);
    }
}
