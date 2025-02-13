/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.analysis.liveness;

import static com.oracle.truffle.espresso.analysis.BlockIterator.BlockProcessResult.DONE;
import static com.oracle.truffle.espresso.analysis.BlockIterator.BlockProcessResult.SKIP;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.espresso.analysis.AnalysisProcessor;
import com.oracle.truffle.espresso.analysis.BlockIterator;
import com.oracle.truffle.espresso.analysis.BlockIteratorClosure;
import com.oracle.truffle.espresso.analysis.DepthFirstBlockIterator;
import com.oracle.truffle.espresso.analysis.Util;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;

/**
 * Glues together loop entries and loop ends by forcing loop ends to have an end state that is a
 * super set of the loop entry state. This means that any local that must be alive on loop entry
 * should also be alive when looping back.
 * <p>
 * Once this is enforced, the changes are propagated upwards the graph to maintain consistency
 * between a block's end and its successors entry.
 */
public final class LoopPropagatorClosure extends BlockIteratorClosure {
    private final BlockBoundaryResult boundaries;

    // Records propagations to apply for loop ends.
    private final BitSet[] toPropagateToBlocks;

    // Reports a meaningful change that will require another iteration of the propagation.
    private boolean meaningfulChange = false;

    // Records blocks for which a change in the entry state has been observed.
    private final BitSet changedBlocks;

    public LoopPropagatorClosure(Graph<? extends LinkedBlock> graph, BlockBoundaryResult boundaries) {
        this.boundaries = boundaries;
        this.changedBlocks = new BitSet(graph.totalBlocks());
        this.toPropagateToBlocks = new BitSet[graph.totalBlocks()];
    }

    public boolean process(Graph<? extends LinkedBlock> graph) {
        // Set loop entry propagation.
        init();
        if (!meaningfulChange) {
            // Nothing to propagate
            return false;
        }

        // Do actual propagation.
        meaningfulChange = false;
        DepthFirstBlockIterator.analyze(graph, this);
        // If a loop entry has been modified, we need to re-propagate.
        detectMeaningfulChange();
        changedBlocks.clear();
        return meaningfulChange;
    }

    private void detectMeaningfulChange() {
        for (int loopEntry : boundaries.getLoops().keySet()) {
            if (changedBlocks.get(loopEntry)) {
                meaningfulChange = true;
                return;
            }
        }
    }

    @Override
    public BlockIterator.BlockProcessResult processBlock(LinkedBlock b, BytecodeStream bs, AnalysisProcessor processor) {
        if (b.isLeaf() || Util.successorsAreDoneOrLoops(processor, b)) {
            doPropagate(b);
            return DONE;
        }
        return SKIP;
    }

    private void doPropagate(LinkedBlock b) {
        int currentID = b.id();
        BitSet endState = boundaries.endFor(currentID);
        BitSet toPropagate = toPropagateToBlocks[currentID];
        if (toPropagate != null || changedSuccessor(b)) {
            List<BitSet> lives = new ArrayList<>();
            for (int s : b.successorsID()) {
                if (changedBlocks.get(s)) {
                    lives.add(boundaries.entryFor(s));
                }
            }
            BitSet merged = Util.mergeBitSets(lives, boundaries.maxLocals());
            if (toPropagate == null) {
                toPropagate = merged;
            } else {
                toPropagate.or(merged);
                // Clear for next iteration.
                toPropagateToBlocks[currentID] = null;
            }
            toPropagate.andNot(endState);
        }
        if (toPropagate == null || toPropagate.isEmpty()) {
            return;
        }
        //
        BitSet entryState = boundaries.entryFor(currentID);
        propagateLoop(endState, entryState, toPropagate, currentID);
    }

    private boolean changedSuccessor(LinkedBlock b) {
        for (int s : b.successorsID()) {
            if (changedBlocks.get(s)) {
                return true;
            }
        }
        return false;
    }

    private void propagateLoop(BitSet endState, BitSet entryState, BitSet toPropagate, int current) {
        endState.or(toPropagate);
        for (Record record : boundaries.historyFor(current).reverse()) {
            if (toPropagate.get(record.local)) {
                toPropagate.clear(record.local);
            }
        }
        toPropagate.andNot(entryState);
        if (!toPropagate.isEmpty()) {
            entryState.or(toPropagate);
            changedBlocks.set(current);
        }
    }

    private void init() {
        for (Map.Entry<Integer, List<Integer>> entry : boundaries.getLoops().entrySet()) {
            int loopEntry = entry.getKey();
            List<Integer> loopEnds = entry.getValue();
            for (int end : loopEnds) {
                BitSet toPropagate = (BitSet) boundaries.entryFor(loopEntry).clone();
                BitSet endState = boundaries.endFor(end);
                toPropagate.andNot(endState);
                if (!toPropagate.isEmpty()) {
                    toPropagateToBlocks[end] = toPropagate;
                    meaningfulChange = true;
                }
            }
        }
    }
}
