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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

/**
 * Pops the current frame off the stack including the return address.
 */
@Opcode("LEAVE_DEOPTIMIZED_STACK_FRAME")
final class AMD64HotSpotLeaveDeoptimizedStackFrameOp extends AMD64HotSpotEpilogueOp {

    @Use(REG) AllocatableValue frameSize;
    @Use(REG) AllocatableValue framePointer;

    public AMD64HotSpotLeaveDeoptimizedStackFrameOp(AllocatableValue frameSize, AllocatableValue initialInfo) {
        this.frameSize = frameSize;
        this.framePointer = initialInfo;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        masm.addq(rsp, asRegister(frameSize));
        // Restore the frame pointer before stack bang because if a stack overflow is thrown it
        // needs to be pushed (and preserved).
        masm.movq(rbp, asRegister(framePointer));
    }
}
