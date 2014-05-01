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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

/**
 * Emits code that enters a stack frame which is tailored to call the C++ method
 * {@link DeoptimizationStub#UNPACK_FRAMES Deoptimization::unpack_frames}.
 */
@Opcode("ENTER_UNPACK_FRAMES_STACK_FRAME")
final class AMD64HotSpotEnterUnpackFramesStackFrameOp extends AMD64LIRInstruction {

    private final Register threadRegister;
    private final int threadLastJavaSpOffset;
    private final int threadLastJavaPcOffset;
    private final int threadLastJavaFpOffset;
    @Alive(REG) AllocatableValue framePc;
    @Alive(REG) AllocatableValue senderSp;
    @Alive(REG) AllocatableValue senderFp;

    private final SaveRegistersOp saveRegisterOp;

    AMD64HotSpotEnterUnpackFramesStackFrameOp(Register threadRegister, int threadLastJavaSpOffset, int threadLastJavaPcOffset, int threadLastJavaFpOffset, AllocatableValue framePc,
                    AllocatableValue senderSp, AllocatableValue senderFp, SaveRegistersOp saveRegisterOp) {
        this.threadRegister = threadRegister;
        this.threadLastJavaSpOffset = threadLastJavaSpOffset;
        this.threadLastJavaPcOffset = threadLastJavaPcOffset;
        this.threadLastJavaFpOffset = threadLastJavaFpOffset;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.senderFp = senderFp;
        this.saveRegisterOp = saveRegisterOp;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        FrameMap frameMap = crb.frameMap;
        RegisterConfig registerConfig = frameMap.registerConfig;
        RegisterSaveLayout registerSaveLayout = saveRegisterOp.getMap(frameMap);
        Register stackPointerRegister = registerConfig.getFrameRegister();
        final int totalFrameSize = frameMap.totalFrameSize();

        // Push return address.
        masm.push(asRegister(framePc));

        // Push base pointer.
        masm.push(asRegister(senderFp));
        masm.movq(rbp, stackPointerRegister);

        /*
         * Allocate a full sized frame. Since return address and base pointer are already in place
         * (see above) we allocate two words less.
         */
        masm.decrementq(stackPointerRegister, totalFrameSize - 2 * crb.target.wordSize);

        // Save return registers after moving the frame.
        final int stackSlotSize = frameMap.stackSlotSize();
        Register integerResultRegister = registerConfig.getReturnRegister(Kind.Long);
        masm.movptr(new AMD64Address(stackPointerRegister, registerSaveLayout.registerToSlot(integerResultRegister) * stackSlotSize), integerResultRegister);

        Register floatResultRegister = registerConfig.getReturnRegister(Kind.Double);
        masm.movdbl(new AMD64Address(stackPointerRegister, registerSaveLayout.registerToSlot(floatResultRegister) * stackSlotSize), floatResultRegister);

        // Set up last Java values.
        masm.movq(new AMD64Address(threadRegister, threadLastJavaSpOffset), stackPointerRegister);

        /*
         * Save the PC since it cannot easily be retrieved using the last Java SP after we aligned
         * SP. Don't need the precise return PC here, just precise enough to point into this code
         * blob.
         */
        masm.leaq(rax, new AMD64Address(rip, 0));
        masm.movq(new AMD64Address(threadRegister, threadLastJavaPcOffset), rax);

        // Use BP because the frames look interpreted now.
        masm.movq(new AMD64Address(threadRegister, threadLastJavaFpOffset), rbp);

        // Align the stack for the following unpackFrames call.
        masm.andq(stackPointerRegister, -(crb.target.stackAlignment));
    }
}
