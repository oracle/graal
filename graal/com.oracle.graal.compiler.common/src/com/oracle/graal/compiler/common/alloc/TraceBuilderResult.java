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

import java.util.BitSet;
import java.util.Iterator;
import java.util.List;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;

public final class TraceBuilderResult<T extends AbstractBlockBase<T>> {
    private final List<Trace<T>> traces;
    private final int[] blockToTrace;

    TraceBuilderResult(List<Trace<T>> traces, int[] blockToTrace) {
        this.traces = traces;
        this.blockToTrace = blockToTrace;
    }

    public int getTraceForBlock(AbstractBlockBase<?> block) {
        return blockToTrace[block.getId()];
    }

    public List<Trace<T>> getTraces() {
        return traces;
    }

    public boolean incomingEdges(int traceNr) {
        Iterator<T> traceIt = getTraces().get(traceNr).getBlocks().iterator();
        return incomingEdges(traceNr, traceIt);
    }

    public boolean incomingSideEdges(int traceNr) {
        Iterator<T> traceIt = getTraces().get(traceNr).getBlocks().iterator();
        if (!traceIt.hasNext()) {
            return false;
        }
        traceIt.next();
        return incomingEdges(traceNr, traceIt);
    }

    private boolean incomingEdges(int traceNr, Iterator<T> trace) {
        /* TODO (je): not efficient. find better solution. */
        while (trace.hasNext()) {
            T block = trace.next();
            for (T pred : block.getPredecessors()) {
                if (getTraceForBlock(pred) != traceNr) {
                    return true;
                }
            }
        }
        return false;
    }

    public static <T extends AbstractBlockBase<T>> boolean verify(TraceBuilderResult<T> traceBuilderResult, int expectedLength) {
        List<Trace<T>> traces = traceBuilderResult.getTraces();
        assert verifyAllBlocksScheduled(traceBuilderResult, expectedLength) : "Not all blocks assigned to traces!";
        for (Trace<T> trace : traces) {
            T last = null;
            int blockNumber = 0;
            for (T current : trace.getBlocks()) {
                assert last == null || current.getPredecessors().contains(last) : "Last block (" + last + ") not a predecessor of " + current;
                assert current.getLinearScanNumber() == blockNumber : "Blocks not numbered correctly: " + current.getLinearScanNumber() + " vs. " + blockNumber;
                last = current;
                blockNumber++;
            }
        }
        return true;
    }

    private static <T extends AbstractBlockBase<T>> boolean verifyAllBlocksScheduled(TraceBuilderResult<T> traceBuilderResult, int expectedLength) {
        List<Trace<T>> traces = traceBuilderResult.getTraces();
        BitSet handled = new BitSet(expectedLength);
        for (Trace<T> trace : traces) {
            for (T block : trace.getBlocks()) {
                assert !handled.get(block.getId()) : "Block added twice: " + block;
                handled.set(block.getId());
            }
        }
        return handled.cardinality() == expectedLength;
    }

    void numberTraces() {
        for (int i = 0; i < traces.size(); i++) {
            traces.get(i).setId(i);
        }
    }

}
