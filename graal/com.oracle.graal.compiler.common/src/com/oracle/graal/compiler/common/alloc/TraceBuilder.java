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
package com.oracle.graal.compiler.common.alloc;

import java.util.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;

public final class TraceBuilder<T extends AbstractBlockBase<T>> {

    public static final class TraceBuilderResult<T extends AbstractBlockBase<T>> {
        private final List<List<T>> traces;
        private final int[] blockToTrace;

        private TraceBuilderResult(List<List<T>> traces, int[] blockToTrace) {
            this.traces = traces;
            this.blockToTrace = blockToTrace;
        }

        public int getTraceForBlock(AbstractBlockBase<?> block) {
            return blockToTrace[block.getId()];
        }

        public List<List<T>> getTraces() {
            return traces;
        }
    }

    /**
     * Build traces of sequentially executed blocks
     */
    public static <T extends AbstractBlockBase<T>> TraceBuilderResult<T> computeTraces(T startBlock, List<T> blocks) {
        return new TraceBuilder<>(blocks).build(startBlock);
    }

    private final PriorityQueue<T> worklist;
    private final BitSet processed;
    /**
     * Contains the number of unprocessed predecessors for every {@link AbstractBlockBase#getId()
     * block}.
     */
    private final int[] blocked;
    private final int[] blockToTrace;

    private TraceBuilder(List<T> blocks) {
        processed = new BitSet(blocks.size());
        worklist = new PriorityQueue<T>(TraceBuilder::compare);
        blocked = new int[blocks.size()];
        blockToTrace = new int[blocks.size()];
        for (T block : blocks) {
            blocked[block.getId()] = block.getPredecessorCount();
        }
    }

    private static <T extends AbstractBlockBase<T>> int compare(T a, T b) {
        return Double.compare(b.probability(), a.probability());
    }

    private boolean processed(T b) {
        return processed.get(b.getId());
    }

    private TraceBuilderResult<T> build(T startBlock) {
        try (Scope s = Debug.scope("TraceBuilder"); Indent i = Debug.logAndIndent("start trace building: " + startBlock)) {
            ArrayList<List<T>> traces = buildTraces(startBlock);

            assert verify(traces);
            return new TraceBuilderResult<>(traces, blockToTrace);
        }
    }

    private boolean verify(ArrayList<List<T>> traces) {
        assert traces.stream().flatMap(l -> l.stream()).distinct().count() == blocked.length : "Not all blocks assigned to traces!";
        for (List<T> trace : traces) {
            T last = null;
            for (T current : trace) {
                assert last == null || current.getPredecessors().contains(last);
                last = current;
            }
        }
        return true;
    }

    protected ArrayList<List<T>> buildTraces(T startBlock) {
        ArrayList<List<T>> traces = new ArrayList<>();
        // add start block
        worklist.add(startBlock);
        // process worklist
        while (!worklist.isEmpty()) {
            T block = worklist.poll();
            assert block != null;
            traces.add(startTrace(block, traces.size()));
        }
        return traces;
    }

    /**
     * Build a new trace starting at {@code block}.
     */
    private List<T> startTrace(T block, int traceNumber) {
        assert block.getPredecessors().stream().allMatch(this::processed) : "Predecessor unscheduled: " + block.getPredecessors().stream().filter(this::processed).findFirst().get();
        ArrayList<T> trace = new ArrayList<>();
        int blockNumber = 0;
        try (Indent i = Debug.logAndIndent("StartTrace: " + block)) {
            for (T currentBlock = block; currentBlock != null; currentBlock = selectNext(currentBlock)) {
                Debug.log("add %s (prob: %f)", currentBlock, currentBlock.probability());
                processed.set(currentBlock.getId());
                worklist.remove(currentBlock);
                trace.add(currentBlock);
                unblock(currentBlock);
                currentBlock.setLinearScanNumber(blockNumber++);
                blockToTrace[currentBlock.getId()] = traceNumber;
            }
        }
        return trace;
    }

    /**
     * Decrease the {@link #blocked} count for all predecessors and add them to the worklist once
     * the count reaches 0.
     */
    private void unblock(T currentBlock) {
        for (T successor : currentBlock.getSuccessors()) {
            if (!processed(successor)) {
                int blockCount = --blocked[successor.getId()];
                assert blockCount >= 0;
                if (blockCount == 0) {
                    worklist.add(successor);
                }
            }
        }
    }

    /**
     * @return The unprocessed predecessor with the highest probability, or {@code null}.
     */
    private T selectNext(T currentBlock) {
        T next = null;
        for (T succ : currentBlock.getSuccessors()) {
            if (!processed(succ) && (next == null || succ.probability() > next.probability())) {
                next = succ;
            }
        }
        return next;
    }
}
