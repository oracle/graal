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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.Opcode;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.asm.*;

/**
 * Writes well known garbage values to registers.
 */
@Opcode("ZAP_REGISTER")
public final class AMD64ZapRegistersOp extends AMD64RegistersPreservationOp {

    /**
     * The move instructions for zapping the registers.
     */
    protected final AMD64LIRInstruction[] zappingMoves;

    public AMD64ZapRegistersOp(AMD64LIRInstruction[] zappingMoves) {
        this.zappingMoves = zappingMoves;
    }

    @Override
    public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
        for (AMD64LIRInstruction zappingMove : zappingMoves) {
            if (zappingMove != null) {
                zappingMove.emitCode(tasm, masm);
            }
        }
    }

    /**
     * Prunes the set of registers zapped by this operation to exclude those in {@code ignored}.
     */
    @Override
    public void update(Set<Register> ignored, DebugInfo debugInfo, FrameMap frameMap) {
        for (int i = 0; i < zappingMoves.length; i++) {
            if (zappingMoves[i] != null) {
                Register register = ValueUtil.asRegister(((MoveOp) zappingMoves[i]).getResult());
                if (ignored.contains(register)) {
                    zappingMoves[i] = null;
                }
            }
        }
    }
}
