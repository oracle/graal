/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.asm.sparc.SPARCAssembler.BPCC;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Annul.NOT_ANNUL;
import static com.oracle.graal.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_TAKEN;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.Xcc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag.Always;

import com.oracle.graal.asm.sparc.SPARCMacroAssembler;
import com.oracle.graal.lir.LIRInstructionClass;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.asm.CompilationResultBuilder;

public final class SPARCJumpOp extends JumpOp implements SPARCDelayedControlTransfer, SPARCLIRInstructionMixin {
    public static final LIRInstructionClass<SPARCJumpOp> TYPE = LIRInstructionClass.create(SPARCJumpOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(2);

    private boolean emitDone = false;
    private int delaySlotPosition = -1;
    private final SPARCLIRInstructionMixinStore store;

    public SPARCJumpOp(LabelRef destination) {
        super(TYPE, destination);
        this.store = new SPARCLIRInstructionMixinStore(SIZE);
    }

    public void emitControlTransfer(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        assert !emitDone;
        if (!crb.isSuccessorEdge(destination())) {
            BPCC.emit(masm, Xcc, Always, NOT_ANNUL, PREDICT_TAKEN, destination().label());
            delaySlotPosition = masm.position();
        }
        emitDone = true;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        if (!crb.isSuccessorEdge(destination())) {
            if (!emitDone) {
                SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
                masm.jmp(destination().label());
            } else {
                int disp = crb.asm.position() - delaySlotPosition;
                assert disp == 4 : disp;
            }
        }
    }

    public void resetState() {
        delaySlotPosition = -1;
        emitDone = false;
    }

    public SPARCLIRInstructionMixinStore getSPARCLIRInstructionStore() {
        return store;
    }
}
