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

import com.oracle.graal.graph.*;
import com.oracle.max.graal.schedule.*;
import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.gen.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.observer.*;

/**
 * This class implements the overall container for the HIR (high-level IR) graph
 * and directs its construction, optimization, and finalization.
 */
public class IR {

    /**
     * The compilation associated with this IR.
     */
    public final C1XCompilation compilation;

    /**
     * The start block of this IR.
     */
    public LIRBlock startBlock;

    private int maxLocks;

    /**
     * The linear-scan ordered list of blocks.
     */
    private List<LIRBlock> orderedBlocks;

    /**
     * Creates a new IR instance for the specified compilation.
     * @param compilation the compilation
     */
    public IR(C1XCompilation compilation) {
        this.compilation = compilation;
    }

    public Map<Node, LIRBlock> valueToBlock;

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

        new PhiSimplifier(this);
        Schedule schedule = new Schedule(this.compilation.graph);
        List<Block> blocks = schedule.getBlocks();
        List<LIRBlock> lirBlocks = new ArrayList<LIRBlock>();
        Map<Block, LIRBlock> map = new HashMap<Block, LIRBlock>();
        for (Block b : blocks) {
            LIRBlock block = new LIRBlock(b.blockID());
            block.setExceptionEntry(b.isExceptionEntry());
            map.put(b, block);
            block.setInstructions(b.getInstructions());
            block.setLinearScanNumber(b.blockID());

            block.setFirstInstruction(b.firstNode());
            block.setLastInstruction(b.lastNode());
            lirBlocks.add(block);
        }

        for (Block b : blocks) {
            for (Block succ : b.getSuccessors()) {
                if (succ.isExceptionEntry()) {
                    map.get(b).getExceptionHandlerSuccessors().add(map.get(succ));
                } else {
                    map.get(b).blockSuccessors().add(map.get(succ));
                }
            }

            for (Block pred : b.getPredecessors()) {
                map.get(b).blockPredecessors().add(map.get(pred));
            }
        }


     // TODO(tw): Schedule nodes within a block.


        CriticalEdgeFinder finder = new CriticalEdgeFinder(lirBlocks, compilation.graph);
        finder.splitCriticalEdges();


        orderedBlocks = lirBlocks;

        valueToBlock = new HashMap<Node, LIRBlock>();
        for (LIRBlock b : orderedBlocks) {
            for (Node i : b.getInstructions()) {
                valueToBlock.put(i, b);
            }
        }
        startBlock = lirBlocks.get(0);
        assert startBlock != null;
        assert startBlock.blockPredecessors().size() == 0;

/*        if (startBlock.blockPredecessors().size() > 0) {
            LIRBlock oldStartBlock = startBlock;
            startBlock = new LIRBlock(orderedBlocks.size());
            startBlock.blockSuccessors().add(oldStartBlock);

            orderedBlocks.add(startBlock);
        }*/

        ComputeLinearScanOrder clso = new ComputeLinearScanOrder(lirBlocks.size(), startBlock);
        orderedBlocks = clso.linearScanOrder();
        this.compilation.stats.loopCount = clso.numLoops();

        int z = 0;
        for (LIRBlock b : orderedBlocks) {
            b.setLinearScanNumber(z++);
        }

        verifyAndPrint("After linear scan order");

        if (C1XOptions.PrintTimers) {
            C1XTimers.HIR_OPTIMIZE.stop();
        }
    }

    private void buildGraph() {
        // Graph builder must set the startBlock and the osrEntryBlock
        new GraphBuilder(compilation, this, compilation.graph).build();

//        Graph newGraph = new Graph();
//        HashMap<Node, Node> replacement = new HashMap<Node, Node>();
//        replacement.put(compilation.graph.start(), newGraph.start());
//        replacement.put(compilation.graph.end(), newGraph.end());
//        newGraph.addDuplicate(compilation.graph.getNodes(), replacement);
//
//        compilation.graph = newGraph;

        verifyAndPrint("After graph building");

        if (C1XOptions.PrintCompilation) {
            TTY.print(String.format("%3d blocks | ", this.numberOfBlocks()));
        }
    }

    /**
     * Gets the linear scan ordering of blocks as a list.
     * @return the blocks in linear scan order
     */
    public List<LIRBlock> linearScanOrder() {
        return orderedBlocks;
    }

    private void print(boolean cfgOnly) {
        if (!TTY.isSuppressed()) {
            TTY.println("IR for " + compilation.method);
            final InstructionPrinter ip = new InstructionPrinter(TTY.out());
            final BlockPrinter bp = new BlockPrinter(this, ip, cfgOnly);
            //getHIRStartBlock().iteratePreOrder(bp);
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
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, phase, compilation.graph, true, false));
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

    public Instruction getHIRStartBlock() {
        return (Instruction) compilation.graph.start().successors().get(0);
    }
}
