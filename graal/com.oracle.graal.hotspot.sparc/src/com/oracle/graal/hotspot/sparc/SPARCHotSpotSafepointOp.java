/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldx;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setx;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.HotSpotCodeCacheProvider.MarkId;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.sparc.*;

import edu.umd.cs.findbugs.annotations.*;

/**
 * Emits a safepoint poll.
 */
@Opcode("SAFEPOINT")
public class SPARCHotSpotSafepointOp extends SPARCLIRInstruction {

    @State protected LIRFrameState state;
    @SuppressFBWarnings(value = "BC_IMPOSSIBLE_CAST", justification = "changed by the register allocator") @Temp({OperandFlag.REG}) private AllocatableValue temp;

    private final HotSpotVMConfig config;

    public SPARCHotSpotSafepointOp(LIRFrameState state, HotSpotVMConfig config, LIRGeneratorTool tool) {
        this.state = state;
        this.config = config;
        temp = tool.newVariable(tool.target().wordKind);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        Register scratch = ((RegisterValue) temp).getRegister();
        emitCode(crb, masm, config, false, state, scratch);
    }

    public static void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm, HotSpotVMConfig config, boolean atReturn, LIRFrameState state, Register scratch) {
        final int pos = masm.position();
        new Setx(config.safepointPollingAddress, scratch).emit(masm);
        MarkId.recordMark(crb, atReturn ? MarkId.POLL_RETURN_FAR : MarkId.POLL_FAR);
        if (state != null) {
            crb.recordInfopoint(pos, state, InfopointReason.SAFEPOINT);
        }
        new Ldx(new SPARCAddress(scratch, 0), g0).emit(masm);
    }
}
