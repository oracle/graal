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
package com.oracle.graal.lir.ssi;

import java.util.*;

import jdk.internal.jvmci.code.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.lir.ssa.*;

public final class SSIConstructionPhase extends PreAllocationOptimizationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, LIRGeneratorTool lirGen) {
        assert SSAUtil.verifySSAForm(lirGenRes.getLIR());
        LIR lir = lirGenRes.getLIR();
        new SSIBuilder(lir).build(lirGen);
    }

    private static class SSIBuilder {
        private final SSIBlockValueMapImpl valueMap;
        private final LIR lir;
        private final BitSet processed;

        private SSIBuilder(LIR lir) {
            this.lir = lir;
            valueMap = new SSIBlockValueMapImpl(lir.getControlFlowGraph());
            processed = new BitSet(lir.getControlFlowGraph().getBlocks().size());
        }

        private void build(LIRGeneratorTool lirGen) {
            Deque<AbstractBlockBase<?>> worklist = new ArrayDeque<>(lir.getControlFlowGraph().getBlocks());
            while (!worklist.isEmpty()) {
                AbstractBlockBase<?> block = worklist.poll();
                if (!processed.get(block.getId())) {
                    try (Indent indent = Debug.logAndIndent("Try processing Block %s", block)) {
                        // check predecessors
                        boolean reschedule = false;
                        for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                            if (!processed.get(pred.getId()) && !isLoopBackEdge(pred, block)) {
                                Debug.log("Schedule predecessor: %s", pred);
                                worklist.addLast(pred);
                                reschedule = true;
                            }
                        }
                        if (reschedule) {
                            Debug.log("Reschedule block %s", block);
                            worklist.addLast(block);
                        } else {
                            processBlock(block);
                        }
                    }
                }
            }
            valueMap.finish(lirGen);
        }

        public void processBlock(AbstractBlockBase<?> block) {
            assert !processed.get(block.getId()) : "Block already processed " + block;
            try (Indent indent = Debug.logAndIndent("Process Block %s", block)) {
                // track values
                ValueConsumer useConsumer = (value, mode, flags) -> {
                    if (flags.contains(OperandFlag.UNINITIALIZED)) {
                        AbstractBlockBase<?> startBlock = lir.getControlFlowGraph().getStartBlock();
                        Debug.log("Set definition of %s in block %s", value, startBlock);
                        valueMap.defineOperand(value, startBlock);
                    } else {
                        Debug.log("Access %s in block %s", value, block);
                        valueMap.accessOperand(value, block);
                    }
                };
                ValueConsumer defConsumer = (value, mode, flags) -> {
                    Debug.log("Set definition of %s in block %s", value, block);
                    valueMap.defineOperand(value, block);
                };
                for (LIRInstruction op : lir.getLIRforBlock(block)) {
                    // use
                    op.visitEachInput(useConsumer);
                    op.visitEachAlive(useConsumer);
                    op.visitEachState(useConsumer);
                    // def
                    op.visitEachTemp(defConsumer);
                    op.visitEachOutput(defConsumer);
                }
                processed.set(block.getId());
            }
        }

        private static boolean isLoopBackEdge(AbstractBlockBase<?> from, AbstractBlockBase<?> to) {
            return from.isLoopEnd() && to.isLoopHeader() && from.getLoop().equals(to.getLoop());
        }
    }
}
