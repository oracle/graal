/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.ssa;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.phases.*;

public final class SSADestructionPhase extends PreAllocationOptimizationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, LIRGeneratorTool lirGen) {
        LIR lir = lirGenRes.getLIR();
        AbstractControlFlowGraph<?> cfg = lir.getControlFlowGraph();
        for (AbstractBlockBase<?> block : cfg.getBlocks()) {
            doBlock(block, lir, lirGen);

        }

    }

    private static void doBlock(AbstractBlockBase<?> block, LIR lir, LIRGeneratorTool lirGen) {
        if (block.getPredecessorCount() > 1) {
            LabelOp label = (LabelOp) lir.getLIRforBlock(block).get(0);
            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                assert pred.getSuccessorCount() == 1 : String.format("Merge predecessor block %s has more than one successor? %s", pred, pred.getSuccessors());

                List<LIRInstruction> instructions = lir.getLIRforBlock(pred);
                JumpOp jump = (JumpOp) instructions.get(instructions.size() - 1);

                resolvePhi(label, jump, lirGen, instructions);
                jump.clearOutgoingValues();
            }
            label.clearIncomingValues();
        }
    }

    private static void resolvePhi(LabelOp label, JumpOp jump, LIRGeneratorTool gen, List<LIRInstruction> instructions) {
        int incomingSize = label.getIncomingSize();
        int outgoingSize = jump.getOutgoingSize();
        assert incomingSize == outgoingSize : String.format("Phi In/Out size mismatch: in=%d vs. out=%d", incomingSize, outgoingSize);

        int insertBefore = instructions.size() - 1;
        assert instructions.get(insertBefore) == jump;

        PhiResolver resolver = PhiResolver.create(gen, new LIRInsertionBuffer(), instructions, insertBefore);
        for (int i = 0; i < incomingSize; i++) {
            Value phiIn = label.getIncomingValue(i);
            Value phiOut = jump.getOutgoingValue(i);
            resolver.move(phiIn, phiOut);
        }
        resolver.dispose();

    }

}
