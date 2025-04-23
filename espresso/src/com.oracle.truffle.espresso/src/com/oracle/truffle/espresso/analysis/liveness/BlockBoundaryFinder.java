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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.espresso.analysis.AnalysisProcessor;
import com.oracle.truffle.espresso.analysis.BlockIterator.BlockProcessResult;
import com.oracle.truffle.espresso.analysis.BlockIteratorClosure;
import com.oracle.truffle.espresso.analysis.BlockLogger;
import com.oracle.truffle.espresso.analysis.Util;
import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;
import com.oracle.truffle.espresso.classfile.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.impl.Method;

/**
 * Does a single pass over all blocks in order to find the set of local variables that is alive at
 * block entry and at block end. This phase does not handle loops, and does the mostly wrong
 * speculation that loop entry states are empty.
 */
public final class BlockBoundaryFinder extends BlockIteratorClosure implements BlockBoundaryResult, BlockLogger {
    private final int maxLocals;
    private final int totalBlocks;

    private final Map<Integer, List<Integer>> loops;

    private final BitSet[] blockEntryLiveSet;
    private final History[] blockHistory;
    private final BitSet[] blockEndLiveSet;

    private final BitSet emptyBitSet;

    BlockBoundaryFinder(Method.MethodVersion m, History[] blockHistory) {
        this.blockHistory = blockHistory;
        this.totalBlocks = blockHistory.length;
        this.maxLocals = m.getMaxLocals();
        this.blockEntryLiveSet = new BitSet[totalBlocks];
        this.blockEndLiveSet = new BitSet[totalBlocks];
        this.loops = new HashMap<>();

        this.emptyBitSet = new BitSet(maxLocals);
    }

    @Override
    public BlockProcessResult processBlock(LinkedBlock b, BytecodeStream bs, AnalysisProcessor processor) {
        if (b.isLeaf() || Util.successorsAreDoneOrLoops(processor, b)) {
            // If a successor goes back to a block in process, it is a loop. Register this block as
            // a loop end
            identifyLoops(b, processor);
            // Compute live sets.
            getEntryLiveSet(b.id(), processor);
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

    @Override
    public Map<Integer, List<Integer>> getLoops() {
        return loops;
    }

    @Override
    public int maxLocals() {
        return maxLocals;
    }

    public BlockBoundaryResult result() {
        return this;
    }

    private void identifyLoops(LinkedBlock b, AnalysisProcessor processor) {
        for (int s : b.successorsID()) {
            if (processor.isInProcess(s)) {
                registerLoopEnd(s, s, b.id());
            }
        }
    }

    private void registerLoopEnd(int loopEntry, int successor, int curBlock) {
        List<Integer> registered = loops.get(loopEntry);
        if (registered == null) {
            registered = new ArrayList<>();
            loops.put(loopEntry, registered);
        }
        if (loopEntry == successor) {
            assert !registered.contains(curBlock);
            registered.add(curBlock);
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
        for (int i : Util.bitSetSetIterator(endState)) {
            if (!treated.get(i)) {
                // One of the successor needs the local
                entryLiveSet.set(i);
            }
        }
        blockEntryLiveSet[blockID] = entryLiveSet;
        return entryLiveSet;
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
        BitSet endState = Util.mergeBitSets(successorsLiveset, maxLocals);

        blockEndLiveSet[b.id()] = endState;
        return endState;
    }

    @Override
    public void log(int block, String tab, PrintStream err) {
        err.println(tab + entryFor(block));
        for (Record r : historyFor(block)) {
            err.println(tab + tab + r.type + " " + r.local);
        }
        err.println(tab + endFor(block));
    }
}
