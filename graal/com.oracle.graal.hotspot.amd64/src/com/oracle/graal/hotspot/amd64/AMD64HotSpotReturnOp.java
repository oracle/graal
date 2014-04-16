/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.asm.*;

/**
 * Returns from a function.
 */
@Opcode("RETURN")
final class AMD64HotSpotReturnOp extends AMD64HotSpotEpilogueOp implements BlockEndOp {

    @Use({REG, ILLEGAL}) protected Value value;
    private final boolean isStub;
    private final Register scratchForSafepointOnReturn;

    AMD64HotSpotReturnOp(Value value, boolean isStub, Register scratchForSafepointOnReturn) {
        this.value = value;
        this.isStub = isStub;
        this.scratchForSafepointOnReturn = scratchForSafepointOnReturn;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        leaveFrameAndRestoreRbp(crb, masm);
        if (!isStub) {
            // Every non-stub compile method must have a poll before the return.
            AMD64HotSpotSafepointOp.emitCode(crb, masm, runtime().getConfig(), true, null, scratchForSafepointOnReturn);
        }
        masm.ret(0);
    }
}
