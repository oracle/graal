/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.*;

import com.oracle.graal.asm.sparc.SPARCAssembler.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.asm.*;

public final class SPARCJumpOp extends JumpOp implements SPARCDelayedControlTransfer {
    public static final LIRInstructionClass<SPARCJumpOp> TYPE = LIRInstructionClass.create(SPARCJumpOp.class);
    private boolean emitDone = false;
    private int delaySlotPosition = -1;

    public SPARCJumpOp(LabelRef destination) {
        super(TYPE, destination);
    }

    public void emitControlTransfer(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        assert !emitDone;
        if (!crb.isSuccessorEdge(destination())) {
            masm.bicc(ConditionFlag.Always, NOT_ANNUL, destination().label());
            delaySlotPosition = masm.position();
        }
        emitDone = true;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        if (!crb.isSuccessorEdge(destination())) {
            if (!emitDone) {
                SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
                masm.bicc(ConditionFlag.Always, NOT_ANNUL, destination().label());
                masm.nop();
            } else {
                assert crb.asm.position() - delaySlotPosition == 4;
            }
        }
    }

    public void resetState() {
        delaySlotPosition = -1;
        emitDone = false;
    }
}
