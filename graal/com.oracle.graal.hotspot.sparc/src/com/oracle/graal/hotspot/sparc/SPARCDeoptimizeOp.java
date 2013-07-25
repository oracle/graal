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

import static com.oracle.graal.asm.sparc.SPARCMacroAssembler.*;
import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.asm.*;

@Opcode("DEOPT")
final class SPARCDeoptimizeOp extends SPARCLIRInstruction {

    private DeoptimizationAction action;
    private DeoptimizationReason reason;
    @State private LIRFrameState info;

    SPARCDeoptimizeOp(DeoptimizationAction action, DeoptimizationReason reason, LIRFrameState info) {
        this.action = action;
        this.reason = reason;
        this.info = info;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, SPARCMacroAssembler masm) {
        HotSpotGraalRuntime runtime = graalRuntime();
        Register thread = runtime.getRuntime().threadRegister();
        Register scratch = g3;
        new Mov(tasm.runtime.encodeDeoptActionAndReason(action, reason), scratch).emit(masm);
        new Stw(scratch, new SPARCAddress(thread, runtime.getConfig().pendingDeoptimizationOffset)).emit(masm);
        // TODO the patched call address looks odd (and is invalid) compared to other runtime calls:
// 0xffffffff749bb5fc: call 0xffffffff415a720c ; {runtime_call}
// [Exception Handler]
// 0xffffffff749bb604: call 0xffffffff749bb220 ; {runtime_call}
// 0xffffffff749bb608: nop
// [Deopt Handler Code]
// 0xffffffff749bb60c: call 0xffffffff748da540 ; {runtime_call}
// 0xffffffff749bb610: nop
        SPARCCall.directCall(tasm, masm, tasm.runtime.lookupForeignCall(UNCOMMON_TRAP), null, false, info);
    }
}
