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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

/**
 * Pushes an interpreter frame to the stack.
 */
@Opcode("PUSH_INTERPRETER_FRAME")
final class AMD64HotSpotPushInterpreterFrameOp extends AMD64LIRInstruction {

    @Alive(REG) AllocatableValue frameSize;
    @Alive(REG) AllocatableValue framePc;
    @Alive(REG) AllocatableValue senderSp;
    @Alive(REG) AllocatableValue initialInfo;

    AMD64HotSpotPushInterpreterFrameOp(AllocatableValue frameSize, AllocatableValue framePc, AllocatableValue senderSp, AllocatableValue initialInfo) {
        this.frameSize = frameSize;
        this.framePc = framePc;
        this.senderSp = senderSp;
        this.initialInfo = initialInfo;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        final Register frameSizeRegister = asRegister(frameSize);
        final Register framePcRegister = asRegister(framePc);
        final Register senderSpRegister = asRegister(senderSp);
        final Register initialInfoRegister = asRegister(initialInfo);
        final HotSpotVMConfig config = HotSpotGraalRuntime.runtime().getConfig();
        final int wordSize = HotSpotGraalRuntime.runtime().getTarget().wordSize;

        // We'll push PC and BP by hand.
        masm.subq(frameSizeRegister, 2 * wordSize);

        // Push return address.
        masm.push(framePcRegister);

        // Prolog
        masm.push(initialInfoRegister);
        masm.movq(initialInfoRegister, rsp);
        masm.subq(rsp, frameSizeRegister);

        // This value is corrected by layout_activation_impl.
        masm.movptr(new AMD64Address(initialInfoRegister, config.frameInterpreterFrameLastSpOffset * wordSize), 0);

        // Make the frame walkable.
        masm.movq(new AMD64Address(initialInfoRegister, config.frameInterpreterFrameSenderSpOffset * wordSize), senderSpRegister);
    }
}
