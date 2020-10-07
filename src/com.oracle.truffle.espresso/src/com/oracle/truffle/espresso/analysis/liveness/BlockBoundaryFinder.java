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
import java.util.List;

import com.oracle.truffle.espresso.analysis.AnalysisProcessor;
import com.oracle.truffle.espresso.analysis.BlockIterator;
import com.oracle.truffle.espresso.analysis.BlockIteratorClosure;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Method;

public class BlockBoundaryFinder extends BlockIteratorClosure implements BlockBoundaryResult {
    private final int maxLocals;

    private final List<List<LinkedBlock>>[] loops;

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

        this.emptyBitSet = new BitSet(maxLocals);
    }

    @Override
    public BlockIterator.BlockProcessResult processBlock(LinkedBlock b, BytecodeStream bs, AnalysisProcessor processor) {
        if (b.isLeaf() || successorsAreDoneOrLoops(processor, b)) {
            identifyLoops(b, processor);
            BitSet entryState = getEntryLiveSet(b.id(), processor);
            propagateLoop(entryState, b, processor);
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
                registerLoop(succ, processor);
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
                        entryLiveSet.set(record.local);
                        break;
                    case STORE:
                        break;
                }
                treated.set(record.local);
            }
        }
        BitSet endState = getEndState(processor.idToBlock(blockID), processor);
        for (int i = 0; i < maxLocals; i++) {
            if (!treated.get(i) && endState.get(i)) {
                entryLiveSet.set(i);
            }
        }
        blockEntryLiveSet[blockID] = entryLiveSet;
        return entryLiveSet;
    }

    private void propagateLoop(BitSet loopEntryState, LinkedBlock b, AnalysisProcessor processor) {
        List<List<LinkedBlock>> registeredLoops = loops[b.id()];
        if (registeredLoops != null) {
            for (List<LinkedBlock> loop : registeredLoops) {
                BitSet toPropagate = (BitSet) loopEntryState.clone();
                for (LinkedBlock block : loop) {
                    BitSet endState = getEndState(block, processor);
                    if (isSuperSet(endState, toPropagate)) {
                        break;
                    }
                    BitSet entryState = getEntryLiveSet(block.id(), processor);
                    propagateLoop(endState, entryState, toPropagate, block, processor);
                }
            }
            loops[b.id()] = null;
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

    private void registerLoop(int loopEntry, AnalysisProcessor processor) {
        List<List<LinkedBlock>> registered = loops[loopEntry];
        if (registered == null) {
            registered = new ArrayList<>();
            loops[loopEntry] = registered;
        }
        registered.add(processor.findLoop(loopEntry));
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
            return blockEndLiveSet[b.id()];
        }
        BitSet[] successorsLiveset = new BitSet[b.successorsID().length];
        for (int i = 0; i < b.successorsID().length; i++) {
            int succ = b.successorsID()[i];
            if (processor.isInProcess(succ)) {
                successorsLiveset[i] = emptyBitSet;
            } else {
                successorsLiveset[i] = getEntryLiveSet(succ, processor);
            }
        }
        BitSet endState = mergeSuccessors(successorsLiveset);
        blockEndLiveSet[b.id()] = endState;
        return endState;
    }

    private BitSet mergeSuccessors(BitSet[] lives) {
        // TODO: use BitSet.or
        BitSet merges = new BitSet(maxLocals);
        for (BitSet live : lives) {
            for (int i = 0; i < maxLocals; i++) {
                if (live.get(i)) {
                    merges.set(i);
                }
            }
        }
        return merges;
    }
}
