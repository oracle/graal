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

import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

/**
 * Pops the current frame off the stack.
 */
@Opcode("LEAVE_CURRENT_STACK_FRAME")
final class SPARCHotSpotLeaveCurrentStackFrameOp extends SPARCLIRInstruction {

    public SPARCHotSpotLeaveCurrentStackFrameOp() {
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // Save O registers over restore.
        new Mov(o0, i0).emit(masm);
        new Mov(o1, i1).emit(masm);
        new Mov(o2, i2).emit(masm);
        new Mov(o3, i3).emit(masm);
        new Mov(o4, i4).emit(masm);

        crb.frameContext.leave(crb);
    }
}
