/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.lir.LIRValueUtil.isStackSlotValue;

import java.util.List;

import jdk.vm.ci.code.TargetDescription;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp.ValueMoveOp;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.ssi.SSIUtil;

/**
 * TODO(zapster) document me!
 *
 */
public final class TraceRegisterAllocationFixupPhase extends TraceAllocationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, MoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        LIR lir = lirGenRes.getLIR();
        if (replaceStackToStackMoves(lir, spillMoveFactory)) {
            Debug.dump(lir, "After fixing stack to stack moves");
        }
        /*
         * Incoming Values are needed for the RegisterVerifier, otherwise SIGMAs/PHIs where the Out
         * and In value matches (ie. there is no resolution move) are falsely detected as errors.
         */
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            try (Indent i = Debug.logAndIndent("Fixup Block %s", block)) {
                if (block.getPredecessorCount() != 0) {
                    SSIUtil.removeIncoming(lir, block);
                } else {
                    assert lir.getControlFlowGraph().getStartBlock().equals(block);
                }
                SSIUtil.removeOutgoing(lir, block);
            }
        }

    }

    /**
     * Fixup stack to stack moves introduced by stack arguments.
     *
     * TODO (je) find a better solution.
     */
    private static boolean replaceStackToStackMoves(LIR lir, MoveFactory spillMoveFactory) {
        boolean changed = false;
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            List<LIRInstruction> instructions = lir.getLIRforBlock(block);
            for (int i = 0; i < instructions.size(); i++) {
                LIRInstruction inst = instructions.get(i);

                if (inst instanceof ValueMoveOp) {
                    ValueMoveOp move = (ValueMoveOp) inst;
                    if (isStackSlotValue(move.getInput()) && isStackSlotValue(move.getResult())) {
                        instructions.set(i, spillMoveFactory.createStackMove(move.getResult(), move.getInput()));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

}
