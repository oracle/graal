/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.graph;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.observer.*;
import com.sun.c1x.value.*;

/**
 * This class implements the overall container for the HIR (high-level IR) graph
 * and directs its construction, optimization, and finalization.
 *
 * @author Thomas Wuerthinger
 * @author Ben L. Titzer
 */
public class IR {

    /**
     * The compilation associated with this IR.
     */
    public final C1XCompilation compilation;

    /**
     * The start block of this IR.
     */
    public BlockBegin startBlock;

    private int maxLocks;

    /**
     * The linear-scan ordered list of blocks.
     */
    private List<BlockBegin> orderedBlocks;

    /**
     * Creates a new IR instance for the specified compilation.
     * @param compilation the compilation
     */
    public IR(C1XCompilation compilation) {
        this.compilation = compilation;
    }

    /**
     * Builds the graph, optimizes it, and computes the linear scan block order.
     */
    public void build() {
        if (C1XOptions.PrintTimers) {
            C1XTimers.HIR_CREATE.start();
        }

        buildGraph();

        if (C1XOptions.PrintTimers) {
            C1XTimers.HIR_CREATE.stop();
            C1XTimers.HIR_OPTIMIZE.start();
        }

        computeLinearScanOrder();

        if (C1XOptions.PrintTimers) {
            C1XTimers.HIR_OPTIMIZE.stop();
        }
    }

    private void buildGraph() {
        // Graph builder must set the startBlock and the osrEntryBlock
        new GraphBuilder(compilation, this, compilation.graph).build();
        assert startBlock != null;
        verifyAndPrint("After graph building");

        if (C1XOptions.PrintCompilation) {
            TTY.print(String.format("%3d blocks | ", this.numberOfBlocks()));
        }
    }

    private void computeLinearScanOrder() {
        if (C1XOptions.GenLIR) {
            makeLinearScanOrder();
            verifyAndPrint("After linear scan order");
        }
    }

    private void makeLinearScanOrder() {
        if (orderedBlocks == null) {
            CriticalEdgeFinder finder = new CriticalEdgeFinder(this);
            startBlock.iteratePreOrder(finder);
            finder.splitCriticalEdges();
            ComputeLinearScanOrder computeLinearScanOrder = new ComputeLinearScanOrder(compilation.stats.blockCount, startBlock);
            orderedBlocks = computeLinearScanOrder.linearScanOrder();
            compilation.stats.loopCount = computeLinearScanOrder.numLoops();
            computeLinearScanOrder.printBlocks();
        }
    }

    /**
     * Gets the linear scan ordering of blocks as a list.
     * @return the blocks in linear scan order
     */
    public List<BlockBegin> linearScanOrder() {
        return orderedBlocks;
    }

    private void print(boolean cfgOnly) {
        if (!TTY.isSuppressed()) {
            TTY.println("IR for " + compilation.method);
            final InstructionPrinter ip = new InstructionPrinter(TTY.out());
            final BlockPrinter bp = new BlockPrinter(this, ip, cfgOnly);
            startBlock.iteratePreOrder(bp);
        }
    }

    /**
     * Verifies the IR and prints it out if the relevant options are set.
     * @param phase the name of the phase for printing
     */
    public void verifyAndPrint(String phase) {
        if (C1XOptions.PrintHIR && !TTY.isSuppressed()) {
            TTY.println(phase);
            print(false);
        }

        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, phase, startBlock, true, false));
        }
    }

    /**
     * Creates and inserts a new block between this block and the specified successor,
     * altering the successor and predecessor lists of involved blocks appropriately.
     * @param source the source of the edge
     * @param target the successor before which to insert a block
     * @return the new block inserted
     */
    public BlockBegin splitEdge(BlockBegin source, BlockBegin target) {
        int bci;
        if (target.blockPredecessors().size() == 1) {
            bci = target.bci();
        } else {
            bci = source.end().bci();
        }

        // create new successor and mark it for special block order treatment
        BlockBegin newSucc = new BlockBegin(bci, nextBlockNumber(), compilation.graph);

        newSucc.setCriticalEdgeSplit(true);

        // This goto is not a safepoint.
        Goto e = new Goto(target, null, false, compilation.graph);
        newSucc.appendNext(e, bci);
        newSucc.setEnd(e);
        // setup states
        FrameState s = source.end().stateAfter();
        newSucc.setStateBefore(s);
        e.setStateAfter(s);
        assert newSucc.stateBefore().localsSize() == s.localsSize();
        assert newSucc.stateBefore().stackSize() == s.stackSize();
        assert newSucc.stateBefore().locksSize() == s.locksSize();
        // link predecessor to new block
        source.end().substituteSuccessor(target, newSucc);

        // The ordering needs to be the same, so remove the link that the
        // set_end call above added and substitute the new_sux for this
        // block.
        target.removePredecessor(newSucc);

        // the successor could be the target of a switch so it might have
        // multiple copies of this predecessor, so substitute the new_sux
        // for the first and delete the rest.
        List<BlockBegin> list = target.blockPredecessors();
        int x = list.indexOf(source);
        assert x >= 0;
        list.set(x, newSucc);
        newSucc.addPredecessor(source);
        Iterator<BlockBegin> iterator = list.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == source) {
                iterator.remove();
                newSucc.addPredecessor(source);
            }
        }
        return newSucc;
    }

    public void replaceBlock(BlockBegin oldBlock, BlockBegin newBlock) {
        assert !oldBlock.isExceptionEntry() : "cannot replace exception handler blocks (yet)";
        for (BlockBegin succ : oldBlock.end().blockSuccessors()) {
            succ.removePredecessor(oldBlock);
        }
        for (BlockBegin pred : oldBlock.blockPredecessors()) {
            // substitute the new successor for this block in each predecessor
            pred.end().substituteSuccessor(oldBlock, newBlock);
            // and add each predecessor to the successor
            newBlock.addPredecessor(pred);
        }
        // this block is now disconnected; remove all its incoming and outgoing edges
//        oldBlock.blockPredecessors().clear();
//        oldBlock.end().blockSuccessors().clear();
    }

    /**
     * Disconnects the specified block from all other blocks.
     * @param block the block to remove from the graph
     */
    public void disconnectFromGraph(BlockBegin block) {
        for (BlockBegin p : block.blockPredecessors()) {
            p.end().blockSuccessors().remove(block);
        }
        for (BlockBegin s : block.end().blockSuccessors()) {
            s.blockPredecessors().remove(block);
        }
    }

    public int nextBlockNumber() {
        return compilation.stats.blockCount++;
    }

    public int numberOfBlocks() {
        return compilation.stats.blockCount;
    }

    public int numLoops() {
        return compilation.stats.loopCount;
    }

    /**
     * Updates the maximum number of locks held at any one time.
     *
     * @param locks a lock count that will replace the current {@linkplain #maxLocks() max locks} if it is greater
     */
    public void updateMaxLocks(int locks) {
        if (locks > maxLocks) {
            maxLocks = locks;
        }
    }

    /**
     * Gets the number of locks.
     * @return the number of locks
     */
    public final int maxLocks() {
        return maxLocks;
    }
}
