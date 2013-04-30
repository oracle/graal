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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.*;

/**
 * Saves registers to stack slots.
 */
@Opcode("SAVE_REGISTER")
public final class AMD64SaveRegistersOp extends AMD64LIRInstruction {

    /**
     * The move instructions for saving the registers.
     */
    protected final AMD64LIRInstruction[] savingMoves;

    /**
     * The move instructions for restoring the registers.
     */
    protected final AMD64LIRInstruction[] restoringMoves;

    /**
     * The slots to which the registers are saved.
     */
    @Def(STACK) protected final StackSlot[] slots;

    public AMD64SaveRegistersOp(AMD64LIRInstruction[] savingMoves, AMD64LIRInstruction[] restoringMoves, StackSlot[] slots) {
        this.savingMoves = savingMoves;
        this.restoringMoves = restoringMoves;
        this.slots = slots;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        for (AMD64LIRInstruction savingMove : savingMoves) {
            if (savingMove != null) {
                savingMove.emitCode(tasm, masm);
            }
        }
    }

    /**
     * Prunes the set of registers saved by this operation to exclude those in {@code notSaved} and
     * updates {@code debugInfo} with a {@linkplain DebugInfo#getCalleeSaveInfo() description} of
     * where each preserved register is saved.
     */
    public void updateAndDescribePreservation(Set<Register> notSaved, DebugInfo debugInfo, FrameMap frameMap) {
        int preserved = 0;
        for (int i = 0; i < savingMoves.length; i++) {
            if (savingMoves[i] != null) {
                Register register = ValueUtil.asRegister(((MoveOp) savingMoves[i]).getInput());
                if (notSaved.contains(register)) {
                    savingMoves[i] = null;
                    restoringMoves[i] = null;
                } else {
                    preserved++;
                }
            }
        }
        if (preserved != 0) {
            Register[] keys = new Register[preserved];
            int[] values = new int[keys.length];
            int mapIndex = 0;
            for (int i = 0; i < savingMoves.length; i++) {
                if (savingMoves[i] != null) {
                    keys[mapIndex] = ValueUtil.asRegister(((MoveOp) savingMoves[i]).getInput());
                    values[mapIndex] = frameMap.indexForStackSlot(slots[i]);
                    mapIndex++;
                }
            }
            assert mapIndex == preserved;
            debugInfo.setCalleeSaveInfo(new RegisterSaveLayout(keys, values));
        }
    }
}
