/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.lir;

import java.util.ArrayList;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.hotspot.HotSpotLIRGenerationResult;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInsertionBuffer;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.gen.DiagnosticLIRGeneratorTool;
import com.oracle.graal.lir.gen.DiagnosticLIRGeneratorTool.ZapRegistersAfterInstruction;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.TargetDescription;

/**
 * Inserts a {@link DiagnosticLIRGeneratorTool#createZapRegisters ZapRegistersOp} after
 * {@link ZapRegistersAfterInstruction}.
 */
public class HotSpotZapRegistersPhase extends PostAllocationOptimizationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        Stub stub = ((HotSpotLIRGenerationResult) lirGenRes).getStub();
        if (stub != null && !stub.preservesRegisters()) {
            LIR lir = lirGenRes.getLIR();
            processLIR(context.diagnosticLirGenTool, (HotSpotLIRGenerationResult) lirGenRes, lir);
        }
    }

    private void processLIR(DiagnosticLIRGeneratorTool diagnosticLirGenTool, HotSpotLIRGenerationResult res, LIR lir) {
        LIRInsertionBuffer buffer = new LIRInsertionBuffer();
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            if (block != null) {
                processBlock(diagnosticLirGenTool, res, lir, buffer, block);
            }
        }
    }

    @SuppressWarnings("try")
    private void processBlock(DiagnosticLIRGeneratorTool diagnosticLirGenTool, HotSpotLIRGenerationResult res, LIR lir, LIRInsertionBuffer buffer, AbstractBlockBase<?> block) {
        try (Indent indent = Debug.logAndIndent("Process block %s", block)) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            buffer.init(instructions);
            for (int index = 0; index < instructions.size(); index++) {
                LIRInstruction inst = instructions.get(index);
                LIRFrameState state = zapRegistersAfterInstruction(inst);
                if (state != null) {
                    SaveRegistersOp zap = diagnosticLirGenTool.createZapRegisters();
                    SaveRegistersOp old = res.getCalleeSaveInfo().put(state, zap);
                    assert old == null : "Already another SaveRegisterOp registered! " + old;
                    buffer.append(index + 1, (LIRInstruction) zap);
                    Debug.log("Insert ZapRegister after %s", inst);
                }
            }
            buffer.finish();
        }
    }

    /**
     * Returns a {@link LIRFrameState} if the registers should be zapped after the instruction.
     */
    protected LIRFrameState zapRegistersAfterInstruction(LIRInstruction inst) {
        if (inst instanceof ZapRegistersAfterInstruction) {
            final LIRFrameState[] lirState = {null};
            inst.forEachState(state -> {
                lirState[0] = state;
            });
            return lirState[0];
        }
        return null;
    }

}
