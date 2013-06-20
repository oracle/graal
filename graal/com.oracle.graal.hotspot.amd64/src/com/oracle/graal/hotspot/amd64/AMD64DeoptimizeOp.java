/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.HotSpotBackend.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.asm.*;

@Opcode("DEOPT")
final class AMD64DeoptimizeOp extends AMD64LIRInstruction {

    private DeoptimizationAction action;
    private DeoptimizationReason reason;
    @State private LIRFrameState info;

    AMD64DeoptimizeOp(DeoptimizationAction action, DeoptimizationReason reason, LIRFrameState info) {
        this.action = action;
        this.reason = reason;
        this.info = info;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        HotSpotGraalRuntime runtime = graalRuntime();
        Register thread = runtime.getRuntime().threadRegister();
        masm.movl(new AMD64Address(thread, runtime.getConfig().pendingDeoptimizationOffset), tasm.runtime.encodeDeoptActionAndReason(action, reason));
        AMD64Call.directCall(tasm, masm, tasm.runtime.lookupForeignCall(UNCOMMON_TRAP), null, false, info);
    }
}
