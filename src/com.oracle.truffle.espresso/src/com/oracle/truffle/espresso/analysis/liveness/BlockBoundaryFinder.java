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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.espresso.analysis.AnalysisProcessor;
import com.oracle.truffle.espresso.analysis.BlockIterator.BlockProcessResult;
import com.oracle.truffle.espresso.analysis.BlockIteratorClosure;
import com.oracle.truffle.espresso.analysis.BlockLogger;
import com.oracle.truffle.espresso.analysis.DepthFirstBlockIterator;
import com.oracle.truffle.espresso.analysis.graph.Graph;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Method;

public class BlockBoundaryFinder extends BlockIteratorClosure implements BlockBoundaryResult, BlockLogger {
    private final int maxLocals;
    private final int totalBlocks;

    private final LoopRecord[] loops;
    private final Set<Integer>[] registeredInLoops;

    private final BitSet[] blockEntryLiveSet;
    private final History[] blockHistory;
    private final BitSet[] blockEndLiveSet;

    private final BitSet emptyBitSet;

    @SuppressWarnings("unchecked")
    public BlockBoundaryFinder(Method m, History[] blockHistory) {
        this.blockHistory = blockHistory;
        this.totalBlocks = blockHistory.length;
        this.maxLocals = m.getMaxLocals();
        this.blockEntryLiveSet = new BitSet[totalBlocks];
        this.blockEndLiveSet = new BitSet[totalBlocks];
        this.loops = new LoopRecord[totalBlocks];
        this.registeredInLoops = new Set[totalBlocks];
        this.emptyBitSet = new BitSet(maxLocals);
    }

    @Override
    public BlockProcessResult processBlock(LinkedBlock b, BytecodeStream bs, AnalysisProcessor processor) {
        if (b.isLeaf() || successorsAreDoneOrLoops(processor, b)) {
            // If a successor goes back to a block in process, it is a loop. Register this block as
            // a loop end
            identifyLoops(b, processor);
            // Compute live sets.
            getEntryLiveSet(b.id(), processor);
            // Propagate loop information through loop ends
            propagateLoop(b, processor);
            return DONE;
        }
        return BlockProcessResult.SKIP;
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
        for (int s : b.successorsID()) {
            if (processor.isInProcess(s)) {
                registerLoopPath(s, s, b.id(), processor);
            } else if (registeredInLoops[s] != null) {
                Set<Integer> successorLoops = registeredInLoops[s];
                Set<Integer> thisLoops = getOrCreateLoopSet(b);
                for (int le : successorLoops) {
                    if (!thisLoops.contains(le)) {
                        registerLoopPath(le, s, b.id(), processor);
                    }
                }
            }
        }
    }

    private void registerLoopPath(int loopEntry, int successor, int curBlock, AnalysisProcessor processor) {
        LoopRecord registered = loops[loopEntry];
        if (registered == null) {
            registered = new LoopRecord(totalBlocks);
            loops[loopEntry] = registered;
        }
        List<LinkedBlock> loop = processor.findLoop(loopEntry);
        for (LinkedBlock b : loop) {
            registered.recordBlock(b.id());
            getOrCreateLoopSet(b).add(loopEntry);
        }
        if (loopEntry == successor) {
            registered.addLoopEnd(curBlock);
        }
    }

    private Set<Integer> getOrCreateLoopSet(LinkedBlock b) {
        Set<Integer> res = registeredInLoops[b.id()];
        if (res == null) {
            res = registeredInLoops[b.id()] = new HashSet<>();
        }
        return res;
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

    private void propagateLoop(LinkedBlock b, AnalysisProcessor processor) {
        int loopEntry = b.id();
        LoopRecord loopRecord = loops[loopEntry];
        if (loopRecord != null) {
            LoopPropagatorClosure closure = new LoopPropagatorClosure(loopRecord, loopEntry);
            while (closure.hasNext()) {
                DepthFirstBlockIterator.analyze(b.graph(), closure);
            }
        }
    }

    private class LoopPropagatorClosure extends BlockIteratorClosure implements Iterator<LoopPropagatorClosure> {
        final LoopRecord loopRecord;
        final int loopEntry;
        final BitSet[] toPropagate;

        int loopEndIndex = -1;

        public LoopPropagatorClosure(LoopRecord loopRecord, int loopEntry) {
            this.toPropagate = new BitSet[totalBlocks];
            this.loopRecord = loopRecord;
            this.loopEntry = loopEntry;
        }

        @Override
        public BlockProcessResult processBlock(LinkedBlock b, BytecodeStream bs, AnalysisProcessor processor) {
            if (!loopRecord.contains(b.id())) {
                return DONE;
            }
            if (predecessorsAreDone(b, processor)) {
                return DONE;
            }
            if (doPropagateLoop(b, processor)) {
                return DONE;
            }
            return SKIP;
        }

        private boolean doPropagateLoop(LinkedBlock b, AnalysisProcessor processor) {
            BitSet endState = getEndState(b, processor);
            BitSet prop = toPropagate[b.id()];
            if (isSuperSet(endState, prop)) {
                return true;
            }
            BitSet entryState = getEntryLiveSet(b.id(), processor);
            propagateLoop(endState, entryState, prop, b.id(), processor);
            int count = 0;
            for (int pred : b.predecessorsID()) {
                if (loopRecord.contains(pred)) {
                    BitSet toPut;
                    if (count == 0) {
                        toPut = prop;
                    } else {
                        toPut = (BitSet) prop.clone();
                    }
                    toPropagate[pred] = toPut;
                    count++;
                }
            }
            return false;
        }

        private boolean predecessorsAreDone(LinkedBlock b, AnalysisProcessor processor) {
            for (int pred : b.predecessorsID()) {
                if (!processor.isDone(pred)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int[] getSuccessors(LinkedBlock b) {
            return b.predecessorsID();
        }

        @Override
        public LinkedBlock getEntry(Graph<? extends LinkedBlock> graph) {
            int entry = loopRecord.loopEnds.get(loopEndIndex);
            toPropagate[entry] = (BitSet) getEntryLiveSet(loopEntry, null /* should be cached */).clone();
            return graph.get(entry);
        }

        @Override
        public boolean hasNext() {
            return loopEndIndex < loopRecord.loopEnds.size();
        }

        @Override
        public LoopPropagatorClosure next() {
            loopEndIndex++;
            return this;
        }
    }

    private void propagateLoop(BitSet endState, BitSet entryState, BitSet toPropagate, int current, AnalysisProcessor processor) {
        for (int i = 0; i < maxLocals; i++) {
            if (toPropagate.get(i))
                if (endState.get(i)) {
                    toPropagate.clear(i);
                } else {
                    endState.set(i);
                }
        }
        for (Record record : blockHistory[current].reverse()) {
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

    private static class LoopRecord implements Iterable<Integer> {
        final List<Integer> loopEnds;
        final BitSet blocks;

        public LoopRecord(int totalBlocks) {
            this.loopEnds = new ArrayList<>();
            this.blocks = new BitSet(totalBlocks);
        }

        @Override
        public Iterator<Integer> iterator() {
            return loopEnds.iterator();
        }

        public void recordBlock(int b) {
            blocks.set(b);
        }

        public void addLoopEnd(int end) {
            loopEnds.add(end);
        }

        public boolean contains(int id) {
            return blocks.get(id);
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
