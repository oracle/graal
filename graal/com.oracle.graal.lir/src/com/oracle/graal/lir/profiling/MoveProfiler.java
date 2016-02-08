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
package com.oracle.graal.lir.profiling;

import java.util.List;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.MoveOp;

public final class MoveProfiler {

    public static BlockMap<MoveStatistics> profile(LIR lir) {
        MoveProfiler profiler = new MoveProfiler(lir);
        profiler.run();
        return profiler.blockMap;
    }

    static class MoveStatistics {

        private final int[] cnt;

        public MoveStatistics() {
            cnt = new int[MoveType.values().length];

        }

        public void add(MoveType moveType) {
            cnt[moveType.ordinal()]++;
        }

        public int get(MoveType moveType) {
            return cnt[moveType.ordinal()];
        }

        public void add(MoveType moveType, int value) {
            cnt[moveType.ordinal()] += value;
        }
    }

    private final LIR lir;
    private final BlockMap<MoveStatistics> blockMap;

    private MoveProfiler(LIR lir) {
        this.lir = lir;
        blockMap = new BlockMap<>(lir.getControlFlowGraph());
    }

    private void run() {
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            doBlock(block);
        }
    }

    private void doBlock(AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = lir.getLIRforBlock(block);
        assert instructions.size() >= 2 : "Malformed block: " + block + ", " + instructions;
        assert instructions.get(instructions.size() - 1) instanceof BlockEndOp : "Not a BlockEndOp: " + instructions.get(instructions.size() - 1);
        assert !(instructions.get(instructions.size() - 2) instanceof BlockEndOp) : "Is a BlockEndOp: " + instructions.get(instructions.size() - 2);
        assert instructions.get(0) instanceof LabelOp : "Not a LabelOp: " + instructions.get(0);
        assert !(instructions.get(1) instanceof LabelOp) : "Is a LabelOp: " + instructions.get(1);

        MoveStatistics stats = null;
        // analysis phase
        for (LIRInstruction inst : instructions) {
            if (inst instanceof MoveOp) {
                if (stats == null) {
                    stats = new MoveStatistics();
                    blockMap.put(block, stats);
                }
                stats.add(MoveType.get((MoveOp) inst));
            }
        }
    }

}
