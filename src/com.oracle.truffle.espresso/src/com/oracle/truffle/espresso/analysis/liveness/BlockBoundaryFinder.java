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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.espresso.analysis.AnalysisProcessor;
import com.oracle.truffle.espresso.analysis.BlockIterator;
import com.oracle.truffle.espresso.analysis.BlockIteratorClosure;
import com.oracle.truffle.espresso.analysis.BlockLogger;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Method;

public class BlockBoundaryFinder extends BlockIteratorClosure implements BlockBoundaryResult, BlockLogger {
    private final int maxLocals;

    // For each loop entry, records all paths that have been discovered to loop back.
    private final List<LoopRecord>[] loops;
    // For each block, records through which predecessor a given block has been registered to a
    // loop.
    private final Map<Integer, Set<Integer>>[] registeredInLoops;

    private final BitSet[] blockEntryLiveSet;
    private final History[] blockHistory;
    private final BitSet[] blockEndLiveSet;

    private final BitSet emptyBitSet;

    @SuppressWarnings("unchecked")
    public BlockBoundaryFinder(Method m, History[] blockHistory) {
        this.blockHistory = blockHistory;
        this.maxLocals = m.getMaxLocals();
        this.blockEntryLiveSet = new BitSet[blockHistory.length];
        this.blockEndLiveSet = new BitSet[blockHistory.length];
        this.loops = new List[blockHistory.length];
        this.registeredInLoops = new Map[blockHistory.length];

        this.emptyBitSet = new BitSet(maxLocals);
    }

    @Override
    public BlockIterator.BlockProcessResult processBlock(LinkedBlock b, BytecodeStream bs, AnalysisProcessor processor) {
        if (b.isLeaf() || successorsAreDoneOrLoops(processor, b)) {
            // If a successor goes back to a block in process, it is a loop. Register this block as
            // a loop end
            identifyLoops(b, processor);
            // Compute live sets.
            getEntryLiveSet(b.id(), processor);
            // Propagate loop information through loop ends
            propagateLoop(b, processor);
            return BlockIterator.BlockProcessResult.DONE;
        }
        return BlockIterator.BlockProcessResult.SKIP;
    }

    @Override
    public BitSet entryFor(int blockID) {
        return blockEntryLiveSet[blockID];
    }

    @Override
    public History historyFor(int blockID) {
        return blockHistory[blockID];
    }

    @Override
    public BitSet endFor(int blockID) {
        return blockEndLiveSet[blockID];
    }

    public BlockBoundaryResult result() {
        return this;
    }

    private void identifyLoops(LinkedBlock b, AnalysisProcessor processor) {
        for (int succ : b.successorsID()) {
            if (processor.isInProcess(succ)) {
                registerLoopPath(succ, succ, processor);
            } else if (registeredInLoops[succ] != null) {
                Map<Integer, Set<Integer>> successorEntries = registeredInLoops[succ];
                for (int le : successorEntries.keySet()) {
                    if (!successorEntries.get(le).contains(b.id())) {
                        registerLoopPath(le, succ, processor);
                    }
                }
            }
        }
    }

    private BitSet getEntryLiveSet(int blockID, AnalysisProcessor processor) {
        if (blockEntryLiveSet[blockID] != null) {
            return blockEntryLiveSet[blockID];
        }
        BitSet treated = new BitSet(maxLocals);
        BitSet entryLiveSet = new BitSet(maxLocals);
        for (Record record : blockHistory[blockID]) {
            if (!treated.get(record.local)) {
                switch (record.type) {
                    case LOAD: // Fallthrough
                    case IINC:
                        // Load: must be alive before here.
                        entryLiveSet.set(record.local);
                        break;
                    case STORE:
                        // Store: need not be alive
                        break;
                }
                treated.set(record.local);
            }
        }
        BitSet endState = getEndState(processor.idToBlock(blockID), processor);
        for (int i = 0; i < maxLocals; i++) {
            if (!treated.get(i) && endState.get(i)) {
                // One of the successor needs the local
                entryLiveSet.set(i);
            }
        }
        blockEntryLiveSet[blockID] = entryLiveSet;
        return entryLiveSet;
    }

    /**
     * TODO: Rework the loop handling so that we simply register each loop end, then propagate
     * upwards using predecessors.
     */
    private void propagateLoop(LinkedBlock loopEntry, AnalysisProcessor processor) {
        List<LoopRecord> registeredLoops = loops[loopEntry.id()];
        if (registeredLoops != null) {
            for (LoopRecord loop : registeredLoops) {
                correctNestedLoop(loopEntry.id(), loop);
                propagateLoopPath(processor, loop);
            }
            for (LoopRecord loop : registeredLoops) {
                unregisterLoop(loopEntry, loop);
            }
            loops[loopEntry.id()] = null;
        }
    }

    private void correctNestedLoop(int loopEntry, LoopRecord loop) {
        Map<Integer, Set<Integer>> map = registeredInLoops[loopEntry];
        if (map != null) {
            for (int entry : map.keySet()) {
                if (entry != loopEntry) {
                    int lastBlockInPath = loop.loop.get(0).id();
                    if (registeredInLoops[lastBlockInPath].get(entry) == null) {
                        assert loops[entry] != null;
                        loops[entry].add(loop);
                    }
                }
            }
        }
    }

    private void unregisterLoop(LinkedBlock b, LoopRecord loop) {
        for (LinkedBlock block : loop) {
            Map<Integer, Set<Integer>> map = registeredInLoops[block.id()];
            if (map != null) {
                map.remove(b.id());
                if (map.isEmpty()) {
                    registeredInLoops[block.id()] = null;
                }
            }
        }
    }

    private void propagateLoopPath(AnalysisProcessor processor, LoopRecord loop) {
        // Ensure that the live set of the loop entry lives through the entire loop.
        BitSet toPropagate = (BitSet) getEntryLiveSet(loop.successor, processor).clone();
        for (LinkedBlock block : loop) {
            BitSet endState = getEndState(block, processor);
            if (isSuperSet(endState, toPropagate)) {
                break;
            }
            BitSet entryState = getEntryLiveSet(block.id(), processor);
            propagateLoop(endState, entryState, toPropagate, block, processor);
        }
    }

    private void propagateLoop(BitSet endState, BitSet entryState, BitSet toPropagate, LinkedBlock current, AnalysisProcessor processor) {
        for (int i = 0; i < maxLocals; i++) {
            if (toPropagate.get(i))
                if (endState.get(i)) {
                    toPropagate.clear(i);
                } else {
                    endState.set(i);
                }
        }
        for (Record record : blockHistory[current.id()].reverse()) {
            if (toPropagate.get(record.local)) {
                toPropagate.clear(record.local);
            }
        }
        for (int i = 0; i < maxLocals; i++) {
            if (toPropagate.get(i)) {
                entryState.set(i);
            }
        }
    }

    private boolean isSuperSet(BitSet state1, BitSet state2) {
        for (int i = 0; i < maxLocals; i++) {
            if (state2.get(i) && !state1.get(i)) {
                return false;
            }
        }
        return true;
    }

    private void registerLoopPath(int loopEntry, int successor, AnalysisProcessor processor) {
        List<LoopRecord> registered = loops[loopEntry];
        if (registered == null) {
            registered = new ArrayList<>();
            loops[loopEntry] = registered;
        }
        List<LinkedBlock> loop = processor.findLoop(loopEntry);
        registered.add(new LoopRecord(loop, successor));

        registerPredecessorLoop(loopEntry, processor.idToBlock(successor), loop.get(0).id());
        for (int i = 1; i < loop.size(); i++) {
            LinkedBlock block = loop.get(i - 1);
            int successorID = loop.get(i).id();
            registerPredecessorLoop(loopEntry, block, successorID);
        }
    }

    private void registerPredecessorLoop(int loopEntry, LinkedBlock block, int successorID) {
        Map<Integer, Set<Integer>> map = registeredInLoops[block.id()];
        if (map == null) {
            map = new HashMap<>();
            registeredInLoops[block.id()] = map;
        }
        Set<Integer> set = map.get(loopEntry);
        if (set == null) {
            set = new HashSet<>();
            map.put(loopEntry, set);
        }
        set.add(successorID);
    }

    private boolean successorsAreDoneOrLoops(AnalysisProcessor processor, LinkedBlock b) {
        for (int succ : b.successorsID()) {
            if (!(processor.isDone(succ) || processor.isInProcess(succ))) {
                return false;
            }
        }
        return true;
    }

    private BitSet getEndState(LinkedBlock b, AnalysisProcessor processor) {
        if (blockEndLiveSet[b.id()] != null) {
            // Leaf block: no need to keep anything alive.
            return blockEndLiveSet[b.id()];
        }
        BitSet[] successorsLiveset = new BitSet[b.successorsID().length];
        for (int i = 0; i < b.successorsID().length; i++) {
            int succ = b.successorsID()[i];
            if (processor.isInProcess(succ)) {
                // Loop: speculate (certainly wrongly) that the loop entry needs no more live local
                // than us.
                successorsLiveset[i] = emptyBitSet;
            } else {
                // Should be already computed (DFS traversal)
                successorsLiveset[i] = getEntryLiveSet(succ, processor);
            }
        }
        BitSet endState = mergeSuccessorLiveSets(successorsLiveset);

        blockEndLiveSet[b.id()] = endState;
        return endState;
    }

    private BitSet mergeSuccessorLiveSets(BitSet[] lives) {
        BitSet merges = new BitSet(maxLocals);
        for (BitSet live : lives) {
            merges.or(live);
        }
        return merges;
    }

    private static class LoopRecord implements Iterable<LinkedBlock> {
        final List<LinkedBlock> loop;
        final int successor;

        public LoopRecord(List<LinkedBlock> loop, int successor) {
            this.loop = loop;
            this.successor = successor;
        }

        @Override
        public Iterator<LinkedBlock> iterator() {
            return loop.iterator();
        }
    }

    @Override
    public void log(int block, String tab) {
        System.err.println(tab + entryFor(block));
        for (Record r : historyFor(block)) {
            System.err.println(tab + tab + r.type + " " + r.local);
        }
        System.err.println(tab + endFor(block));
    }
}
