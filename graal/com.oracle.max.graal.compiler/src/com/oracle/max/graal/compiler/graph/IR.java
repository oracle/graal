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
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.phases.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.extensions.*;
import com.oracle.max.graal.graph.*;

/**
 * This class implements the overall container for the HIR (high-level IR) graph
 * and directs its construction, optimization, and finalization.
 */
public class IR {

    /**
     * The compilation associated with this IR.
     */
    public final GraalCompilation compilation;

    /**
     * The start block of this IR.
     */
    public LIRBlock startBlock;

    /**
     * The linear-scan ordered list of blocks.
     */
    private List<LIRBlock> linearScanOrder;

    /**
     * The order in which the code is emitted.
     */
    private List<LIRBlock> codeEmittingOrder;

    /**
     * Creates a new IR instance for the specified compilation.
     * @param compilation the compilation
     */
    public IR(GraalCompilation compilation) {
        this.compilation = compilation;
    }

    public Map<Node, LIRBlock> valueToBlock;

    /**
     * Builds the graph, optimizes it, and computes the linear scan block order.
     */
    public void build() {

//        Object stored = GraphBuilderPhase.cachedGraphs.get(compilation.method);
//        if (stored != null) {
//            Map<Node, Node> replacements = new HashMap<Node, Node>();
//            CompilerGraph duplicate = (CompilerGraph) stored;
//            replacements.put(duplicate.start(), compilation.graph.start());
//            compilation.graph.addDuplicate(duplicate.getNodes(), replacements);
//        } else {
            new GraphBuilderPhase(compilation, compilation.method, false).apply(compilation.graph);
//        }


        if (GraalOptions.TestGraphDuplication) {
            new DuplicationPhase().apply(compilation.graph);
        }

        new DeadCodeEliminationPhase().apply(compilation.graph);

        if (GraalOptions.Inline) {
            new InliningPhase(compilation, this, null).apply(compilation.graph);
        }

        Graph graph = compilation.graph;

        if (GraalOptions.OptCanonicalizer) {
            new CanonicalizerPhase().apply(graph);
            new DeadCodeEliminationPhase().apply(graph);
        }

        if (GraalOptions.Extend) {
            extensionOptimizations(graph);
        }

        if (GraalOptions.OptLoops) {
            new LoopPhase().apply(graph);
        }

        if (GraalOptions.EscapeAnalysis) {
            new EscapeAnalysisPhase(compilation, this).apply(graph);
            new CanonicalizerPhase().apply(graph);
            new DeadCodeEliminationPhase().apply(graph);
        }

        if (GraalOptions.OptGVN) {
            new GlobalValueNumberingPhase().apply(graph);
        }

        new LoweringPhase(compilation.runtime).apply(graph);
        if (GraalOptions.Lower) {
            new MemoryPhase().apply(graph);
            if (GraalOptions.OptGVN) {
                new GlobalValueNumberingPhase().apply(graph);
            }
            if (GraalOptions.OptReadElimination) {
                new ReadEliminationPhase().apply(graph);
            }
        }

        IdentifyBlocksPhase schedule = new IdentifyBlocksPhase(true);
        schedule.apply(graph);
        compilation.stats.loopCount = schedule.loopCount();


        List<Block> blocks = schedule.getBlocks();
        List<LIRBlock> lirBlocks = new ArrayList<LIRBlock>();
        Map<Block, LIRBlock> map = new HashMap<Block, LIRBlock>();
        for (Block b : blocks) {
            LIRBlock block = new LIRBlock(b.blockID());
            map.put(b, block);
            block.setInstructions(b.getInstructions());
            block.setLinearScanNumber(b.blockID());
            block.setLoopDepth(b.loopDepth());
            block.setLoopIndex(b.loopIndex());

            if (b.isLoopEnd()) {
                block.setLinearScanLoopEnd();
            }

            if (b.isLoopHeader()) {
                block.setLinearScanLoopHeader();
            }

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

        valueToBlock = new HashMap<Node, LIRBlock>();
        for (LIRBlock b : lirBlocks) {
            for (Node i : b.getInstructions()) {
                valueToBlock.put(i, b);
            }
        }
        startBlock = valueToBlock.get(graph.start());
        assert startBlock != null;
        assert startBlock.blockPredecessors().size() == 0;


        if (GraalOptions.Time) {
            GraalTimers.COMPUTE_LINEAR_SCAN_ORDER.start();
        }

        ComputeLinearScanOrder clso = new ComputeLinearScanOrder(lirBlocks.size(), compilation.stats.loopCount, startBlock);
        linearScanOrder = clso.linearScanOrder();
        codeEmittingOrder = clso.codeEmittingOrder();

        int z = 0;
        for (LIRBlock b : linearScanOrder) {
            b.setLinearScanNumber(z++);
        }

        printGraph("After linear scan order", compilation.graph);

        if (GraalOptions.Time) {
            GraalTimers.COMPUTE_LINEAR_SCAN_ORDER.stop();
        }

    }



    public static ThreadLocal<ServiceLoader<Optimizer>> optimizerLoader = new ThreadLocal<ServiceLoader<Optimizer>>();

    private void extensionOptimizations(Graph graph) {

        ServiceLoader<Optimizer> serviceLoader = optimizerLoader.get();
        if (serviceLoader == null) {
            serviceLoader = ServiceLoader.load(Optimizer.class);
            optimizerLoader.set(serviceLoader);
        }

        for (Optimizer o : serviceLoader) {
            o.optimize(compilation.runtime, graph);
        }
    }

    /**
     * Gets the linear scan ordering of blocks as a list.
     * @return the blocks in linear scan order
     */
    public List<LIRBlock> linearScanOrder() {
        return linearScanOrder;
    }

    public List<LIRBlock> codeEmittingOrder() {
        return codeEmittingOrder;
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
                FrameState current = (FrameState) node;
                int lockCount = 0;
                while (current != null) {
                    lockCount += current.locksSize();
                    current = current.outerFrameState();
                }
                if (lockCount > maxLocks) {
                    maxLocks = lockCount;
                }
            }
        }
        return maxLocks;
    }

    public FixedNodeWithNext getHIRStartBlock() {
        return (FixedNodeWithNext) compilation.graph.start().successors().get(0);
    }
}
