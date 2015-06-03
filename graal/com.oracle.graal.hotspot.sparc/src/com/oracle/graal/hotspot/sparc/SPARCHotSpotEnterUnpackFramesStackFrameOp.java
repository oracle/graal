/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

/**
 * Emits code that enters a stack frame which is tailored to call the C++ method
 * {@link HotSpotBackend#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@Opcode("ENTER_UNPACK_FRAMES_STACK_FRAME")
final class SPARCHotSpotEnterUnpackFramesStackFrameOp extends SPARCLIRInstruction {
    public static final LIRInstructionClass<SPARCHotSpotEnterUnpackFramesStackFrameOp> TYPE = LIRInstructionClass.create(SPARCHotSpotEnterUnpackFramesStackFrameOp.class);

    private final Register thread;
    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    @Alive(REG) AllocatableValue framePc;
    @Alive(REG) AllocatableValue senderSp;
    @Temp(REG) AllocatableValue scratch;

    SPARCHotSpotEnterUnpackFramesStackFrameOp(Register thread, int threadLastJavaSpOffset, int threadLastJavaPcOffset, AllocatableValue framePc, AllocatableValue senderSp, AllocatableValue scratch) {
        super(TYPE);
        this.thread = thread;
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.scratch = scratch;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        final int totalFrameSize = crb.frameMap.totalFrameSize();
        Register framePcRegister = asRegister(framePc);
        Register senderSpRegister = asRegister(senderSp);
        Register scratchRegister = asRegister(scratch);

        // Save final sender SP to O5_savedSP.
        masm.mov(senderSpRegister, o5);

        // Load final frame PC.
        masm.mov(framePcRegister, o7);

        // Allocate a full sized frame.
        masm.save(sp, -totalFrameSize, sp);

        masm.mov(i0, o0);
        masm.mov(i1, o1);
        masm.mov(i2, o2);
        masm.mov(i3, o3);
        masm.mov(i4, o4);

        // Set up last Java values.
        masm.add(sp, STACK_BIAS, scratchRegister);
        masm.stx(scratchRegister, new SPARCAddress(thread, threadLastJavaSpOffset));

        // Clear last Java PC.
        masm.stx(g0, new SPARCAddress(thread, threadLastJavaPcOffset));

        /*
         * Safe thread register manually since we are not using LEAF_SP for {@link
         * DeoptimizationStub#UNPACK_FRAMES}.
         */
        masm.mov(thread, l7);
    }

    @Override
    public boolean leavesRegisterWindow() {
        return true;
    }
}
