/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.compiler.graal.lir.amd64.phases;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.compiler.graal.core.common.cfg.BasicBlock;
import jdk.compiler.graal.debug.CounterKey;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.lir.LIR;
import jdk.compiler.graal.lir.LIRInstruction;
import jdk.compiler.graal.lir.RedundantMoveElimination;
import jdk.compiler.graal.lir.amd64.AMD64Move;
import jdk.compiler.graal.lir.gen.LIRGenerationResult;
import jdk.compiler.graal.lir.phases.LIRPhase;
import jdk.compiler.graal.lir.phases.PostAllocationOptimizationPhase;
import jdk.compiler.graal.options.NestedBooleanOptionKey;
import jdk.compiler.graal.options.Option;
import jdk.compiler.graal.options.OptionType;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Replaces sequential {@link AMD64Move.AMD64StackMove}s of the same type with a single
 * {@link AMD64Move.AMD64MultiStackMove} to avoid storing/restoring the scratch register multiple
 * times.
 *
 * Note: this phase must be inserted <b>after</b> {@link RedundantMoveElimination} phase because
 * {@link AMD64Move.AMD64MultiStackMove} are not probably detected.
 */
public class StackMoveOptimizationPhase extends PostAllocationOptimizationPhase {
    public static class Options {
        // @formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final NestedBooleanOptionKey LIROptStackMoveOptimizer = new NestedBooleanOptionKey(LIRPhase.Options.LIROptimization, true);
        // @formatter:on
    }

    private static final CounterKey eliminatedBackup = DebugContext.counter("StackMoveOptimizer[EliminatedScratchBackupRestore]");

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        LIR lir = lirGenRes.getLIR();
        DebugContext debug = lir.getDebug();
        for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            new Closure().process(debug, instructions);
        }
    }

    private static class Closure {
        private static final int NONE = -1;

        private int begin = NONE;
        private Register reg = null;
        private List<AllocatableValue> dst;
        private List<Value> src;
        private AllocatableValue slot;
        private boolean removed = false;

        public void process(DebugContext debug, List<LIRInstruction> instructions) {
            for (int i = 0; i < instructions.size(); i++) {
                LIRInstruction inst = instructions.get(i);

                if (isStackMove(inst)) {
                    AMD64Move.AMD64StackMove move = asStackMove(inst);

                    if (reg != null && !reg.equals(move.getScratchRegister())) {
                        // end of trace & start of new
                        replaceStackMoves(debug, instructions);
                    }

                    // lazy initialize
                    if (dst == null) {
                        assert src == null;
                        dst = new ArrayList<>();
                        src = new ArrayList<>();
                    }

                    dst.add(move.getResult());
                    src.add(move.getInput());

                    if (begin == NONE) {
                        // trace begin
                        begin = i;
                        reg = move.getScratchRegister();
                        slot = move.getBackupSlot();
                    }

                } else if (begin != NONE) {
                    // end of trace
                    replaceStackMoves(debug, instructions);
                }
            }
            // remove instructions
            if (removed) {
                instructions.removeAll(Collections.singleton(null));
            }

        }

        private void replaceStackMoves(DebugContext debug, List<LIRInstruction> instructions) {
            int size = dst.size();
            if (size > 1) {
                AMD64Move.AMD64MultiStackMove multiMove = new AMD64Move.AMD64MultiStackMove(dst.toArray(new AllocatableValue[size]), src.toArray(new AllocatableValue[size]), reg, slot);
                // replace first instruction
                instructions.set(begin, multiMove);
                // and null out others
                Collections.fill(instructions.subList(begin + 1, begin + size), null);
                // removed
                removed = true;
                eliminatedBackup.add(debug, size - 1);
            }
            // reset
            dst.clear();
            src.clear();
            begin = NONE;
            reg = null;
            slot = null;
        }
    }

    private static AMD64Move.AMD64StackMove asStackMove(LIRInstruction inst) {
        assert isStackMove(inst);
        return (AMD64Move.AMD64StackMove) inst;
    }

    private static boolean isStackMove(LIRInstruction inst) {
        return inst instanceof AMD64Move.AMD64StackMove;
    }

}
