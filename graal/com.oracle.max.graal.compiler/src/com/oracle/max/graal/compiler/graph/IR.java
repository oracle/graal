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
package com.oracle.max.graal.compiler.graph;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;

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

        new GraphBuilderPhase(compilation, compilation.method, false).apply(compilation.graph);
        new DuplicationPhase().apply(compilation.graph);
        new DeadCodeEliminationPhase().apply(compilation.graph);

        if (C1XOptions.Inline) {
            new InliningPhase(compilation, this).apply(compilation.graph);
        }

        if (C1XOptions.PrintTimers) {
            C1XTimers.HIR_CREATE.stop();
            C1XTimers.HIR_OPTIMIZE.start();
        }

        Graph graph = compilation.graph;

        if (C1XOptions.OptCanonicalizer) {
            new CanonicalizerPhase().apply(graph);
        }

        new SplitCriticalEdgesPhase().apply(graph);

        Schedule schedule = new Schedule(graph);
        List<Block> blocks = schedule.getBlocks();
        List<LIRBlock> lirBlocks = new ArrayList<LIRBlock>();
        Map<Block, LIRBlock> map = new HashMap<Block, LIRBlock>();
        for (Block b : blocks) {
            LIRBlock block = new LIRBlock(b.blockID());
            map.put(b, block);
            block.setInstructions(b.getInstructions());
            block.setLinearScanNumber(b.blockID());

            block.setFirstInstruction(b.firstNode());
            block.setLastInstruction(b.lastNode());
            lirBlocks.add(block);
        }

        for (Block b : blocks) {
            for (Block succ : b.getSuccessors()) {
                map.get(b).blockSuccessors().add(map.get(succ));
            }

            for (Block pred : b.getPredecessors()) {
                map.get(b).blockPredecessors().add(map.get(pred));
            }
        }

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

    /**
     * Gets the linear scan ordering of blocks as a list.
     * @return the blocks in linear scan order
     */
    public List<LIRBlock> linearScanOrder() {
        return orderedBlocks;
    }

    /**
     * Verifies the IR and prints it out if the relevant options are set.
     * @param phase the name of the phase for printing
     */
    public void verifyAndPrint(String phase) {
        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, phase, compilation.graph, true, false));
        }
    }

    public void printGraph(String phase, Graph graph) {
        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, phase, graph, true, false));
        }
    }

    public int numLoops() {
        return compilation.stats.loopCount;
    }

    /**
     * Gets the maximum number of locks in the graph's frame states.
     */
    public final int maxLocks() {
        int maxLocks = 0;
        for (Node node : compilation.graph.getNodes()) {
            if (node instanceof FrameState) {
                int lockCount = ((FrameState) node).locksSize();
                if (lockCount > maxLocks) {
                    maxLocks = lockCount;
                }
            }
        }
        return maxLocks;
    }

    public Instruction getHIRStartBlock() {
        return (Instruction) compilation.graph.start().successors().get(0);
    }
}
