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
package com.oracle.graal.compiler.common.alloc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;

/**
 * Computes traces by selecting the unhandled block with the highest execution frequency and going
 * in both directions, up and down, as long as possible.
 */
public final class BiDirectionalTraceBuilder<T extends AbstractBlockBase<T>> {

    public static <T extends AbstractBlockBase<T>> TraceBuilderResult<T> computeTraces(T startBlock, List<T> blocks) {
        TraceBuilderResult<T> traceBuilderResult = new BiDirectionalTraceBuilder<>(blocks).build(startBlock);
        traceBuilderResult.numberTraces();
        return traceBuilderResult;
    }

    private final Deque<T> worklist;
    private final BitSet processed;
    private final int[] blockToTrace;

    private BiDirectionalTraceBuilder(List<T> blocks) {
        processed = new BitSet(blocks.size());
        worklist = createQueue(blocks);
        blockToTrace = new int[blocks.size()];
    }

    private static <T extends AbstractBlockBase<T>> Deque<T> createQueue(List<T> blocks) {
        ArrayList<T> queue = new ArrayList<>(blocks);
        queue.sort(BiDirectionalTraceBuilder::compare);
        return new ArrayDeque<>(queue);
    }

    private static int compare(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
        return Double.compare(b.probability(), a.probability());
    }

    private boolean processed(T b) {
        return processed.get(b.getId());
    }

    @SuppressWarnings("try")
    private TraceBuilderResult<T> build(T startBlock) {
        try (Indent indent = Debug.logAndIndent("start trace building: %s", startBlock)) {
            ArrayList<Trace<T>> traces = buildTraces(startBlock);
            return new TraceBuilderResult<>(traces, blockToTrace);
        }
    }

    protected ArrayList<Trace<T>> buildTraces(T startBlock) {
        ArrayList<Trace<T>> traces = new ArrayList<>();
        // add start block
        worklist.add(startBlock);
        // process worklist
        while (!worklist.isEmpty()) {
            T block = worklist.pollFirst();
            assert block != null;
            if (!processed(block)) {
                traces.add(new Trace<>(startTrace(block, traces.size())));
            }
        }
        return traces;
    }

    /**
     * Build a new trace starting at {@code block}.
     */
    @SuppressWarnings("try")
    private Collection<T> startTrace(T block, int traceNumber) {
        ArrayDeque<T> trace = new ArrayDeque<>();
        try (Indent i = Debug.logAndIndent("StartTrace: %s", block)) {
            try (Indent indentFront = Debug.logAndIndent("Head:")) {
                for (T currentBlock = block; currentBlock != null; currentBlock = selectPredecessor(currentBlock)) {
                    addBlockToTrace(currentBlock, traceNumber);
                    trace.addFirst(currentBlock);
                }
            }
            /* Number head blocks. Can not do this in the loop as we go backwards. */
            int blockNr = 0;
            for (T b : trace) {
                b.setLinearScanNumber(blockNr++);
            }

            try (Indent indentBack = Debug.logAndIndent("Tail:")) {
                for (T currentBlock = selectSuccessor(block); currentBlock != null; currentBlock = selectSuccessor(currentBlock)) {
                    addBlockToTrace(currentBlock, traceNumber);
                    trace.addLast(currentBlock);
                    /* This time we can number the blocks immediately as we go forwards. */
                    currentBlock.setLinearScanNumber(blockNr++);
                }
            }
        }
        Debug.log("Trace: %s", trace);
        return trace;
    }

    private void addBlockToTrace(T currentBlock, int traceNumber) {
        Debug.log("add %s (prob: %f)", currentBlock, currentBlock.probability());
        processed.set(currentBlock.getId());
        blockToTrace[currentBlock.getId()] = traceNumber;
    }

    /**
     * @return The unprocessed predecessor with the highest probability, or {@code null}.
     */
    private T selectPredecessor(T currentBlock) {
        T next = null;
        for (T pred : currentBlock.getPredecessors()) {
            if (!processed(pred) && !isBackEdge(pred, currentBlock) && (next == null || pred.probability() > next.probability())) {
                next = pred;
            }
        }
        return next;
    }

    private boolean isBackEdge(T from, T to) {
        assert from.getSuccessors().contains(to) : "No edge from " + from + " to " + to;
        return from.isLoopEnd() && to.isLoopHeader() && from.getLoop().equals(to.getLoop());
    }

    /**
     * @return The unprocessed successor with the highest probability, or {@code null}.
     */
    private T selectSuccessor(T currentBlock) {
        T next = null;
        for (T succ : currentBlock.getSuccessors()) {
            if (!processed(succ) && (next == null || succ.probability() > next.probability())) {
                next = succ;
            }
        }
        return next;
    }
}
