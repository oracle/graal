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
import com.oracle.graal.lir.asm.*;

/**
 * Saves registers to stack slots.
 */
@Opcode("SAVE_REGISTER")
public class AMD64SaveRegistersOp extends AMD64RegistersPreservationOp {

    /**
     * The registers (potentially) saved by this operation.
     */
    protected final Register[] savedRegisters;

    /**
     * The slots to which the registers are saved.
     */
    @Def(STACK) protected final StackSlot[] slots;

    public AMD64SaveRegistersOp(Register[] savedRegisters, StackSlot[] slots) {
        this.savedRegisters = savedRegisters;
        this.slots = slots;
    }

    protected void saveRegister(TargetMethodAssembler tasm, AMD64MacroAssembler masm, StackSlot result, Register register) {
        RegisterValue input = register.asValue(result.getKind());
        AMD64Move.move(tasm, masm, result, input);
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        for (int i = 0; i < savedRegisters.length; i++) {
            if (savedRegisters[i] != null) {
                saveRegister(tasm, masm, slots[i], savedRegisters[i]);
            }
        }
    }

    public StackSlot[] getSlots() {
        return slots;
    }

    /**
     * Prunes the set of registers saved by this operation to exclude those in {@code ignored} and
     * updates {@code debugInfo} with a {@linkplain DebugInfo#getCalleeSaveInfo() description} of
     * where each preserved register is saved.
     */
    @Override
    public void update(Set<Register> ignored, DebugInfo debugInfo, FrameMap frameMap) {
        int preserved = 0;
        for (int i = 0; i < savedRegisters.length; i++) {
            if (savedRegisters[i] != null) {
                if (ignored.contains(savedRegisters[i])) {
                    savedRegisters[i] = null;
                } else {
                    preserved++;
                }
            }
        }
        if (preserved != 0) {
            Register[] keys = new Register[preserved];
            int[] values = new int[keys.length];
            int mapIndex = 0;
            for (int i = 0; i < savedRegisters.length; i++) {
                if (savedRegisters[i] != null) {
                    keys[mapIndex] = savedRegisters[i];
                    values[mapIndex] = frameMap.indexForStackSlot(slots[i]);
                    mapIndex++;
                }
            }
            assert mapIndex == preserved;
            if (debugInfo != null) {
                debugInfo.setCalleeSaveInfo(new RegisterSaveLayout(keys, values));
            }
        }
    }
}
