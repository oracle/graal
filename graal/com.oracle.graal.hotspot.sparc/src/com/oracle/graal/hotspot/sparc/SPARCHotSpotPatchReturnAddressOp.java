/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;
import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.api.code.ValueUtil.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.sparc.*;

/**
 * Patch the return address of the current frame.
 */
@Opcode("PATCH_RETURN")
final class SPARCHotSpotPatchReturnAddressOp extends SPARCLIRInstruction {

    @Use(REG) AllocatableValue address;

    SPARCHotSpotPatchReturnAddressOp(AllocatableValue address) {
        this.address = address;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // FIXME This is non-trivial. On SPARC we need to flush all register windows first before we
        // can patch the return address (see: frame::patch_pc).
        // new Flushw().emit(masm);
        // int frameSize = crb.frameMap.frameSize();
        // new SPARCAssembler.Ldx(new SPARCAddress(o7, 1), g3).emit(masm);
        // new Setx(8 * 15 - 1, g4, false).emit(masm);
        Register addrRegister = asLongReg(address);
        // new SPARCAssembler.Ldx(new SPARCAddress(o7, 1), g3).emit(masm);
        new Sub(addrRegister, Return.PC_RETURN_OFFSET, i7).emit(masm);
// new Save(sp, -3000, sp).emit(masm);
// new Flushw().emit(masm);
        // new Stx(g4, new SPARCAddress(fp, o7.number * crb.target.wordSize)).emit(masm);
// new Restore(g0, g0, g0).emit(masm);
// new Flushw().emit(masm);
        // new Ldx(new SPARCAddress(g0, 0x123), g0).emit(masm);
    }
}
