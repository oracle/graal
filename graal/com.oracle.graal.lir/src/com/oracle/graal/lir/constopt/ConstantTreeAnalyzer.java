/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.constopt;

import java.util.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.constopt.ConstantTree.Flags;
import com.oracle.graal.lir.constopt.ConstantTree.NodeCost;

/**
 * Analyzes a {@link ConstantTree} and marks potential materialization positions.
 */
public class ConstantTreeAnalyzer {
    private final ConstantTree tree;
    private final BitSet visited;

    public static NodeCost analyze(ConstantTree tree, AbstractBlock<?> startBlock) {
        try (Scope s = Debug.scope("ConstantTreeAnalyzer")) {
            ConstantTreeAnalyzer analyzer = new ConstantTreeAnalyzer(tree);
            analyzer.analyzeBlocks(startBlock);
            return tree.getCost(startBlock);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private ConstantTreeAnalyzer(ConstantTree tree) {
        this.tree = tree;
        this.visited = new BitSet(tree.size());
    }

    /**
     * Queues all relevant blocks for {@linkplain #process processing}.
     *
     * This is a worklist-style algorithm because a (more elegant) recursive implementation may
     * cause {@linkplain StackOverflowError stack overflows} on larger graphs.
     *
     * @param startBlock The start block of the dominator subtree.
     */
    private void analyzeBlocks(AbstractBlock<?> startBlock) {
        Deque<AbstractBlock<?>> worklist = new ArrayDeque<>();
        worklist.offerLast(startBlock);
        while (!worklist.isEmpty()) {
            AbstractBlock<?> block = worklist.pollLast();
            try (Indent i = Debug.logAndIndent(3, "analyze: %s", block)) {
                assert block != null : "worklist is empty!";
                assert isMarked(block) : "Block not part of the dominator tree: " + block;

                if (isLeafBlock(block)) {
                    Debug.log(3, "leaf block");
                    leafCost(block);
                    continue;
                }

                if (!visited.get(block.getId())) {
                    // if not yet visited (and not a leaf block) process all children first!
                    Debug.log(3, "not marked");
                    worklist.offerLast(block);
                    List<? extends AbstractBlock<?>> children = block.getDominated();
                    children.forEach(child -> filteredPush(worklist, child));
                    visited.set(block.getId());
                } else {
                    Debug.log(3, "marked");
                    // otherwise, process block
                    process(block);
                }
            }
        }
    }

    /**
     * Calculates the cost of a {@code block}. It is assumed that all {@code children} have already
     * been {@linkplain #process processed}
     *
     * @param block The block to be processed.
     */
    private void process(AbstractBlock<?> block) {
        List<UseEntry> usages = new ArrayList<>();
        double bestCost = 0;
        int numMat = 0;
        List<? extends AbstractBlock<?>> children = block.getDominated();
        assert children.stream().anyMatch(this::isMarked) : "no children? should have called leafCost(): " + block;

        // collect children costs
        for (AbstractBlock<?> child : children) {
            if (isMarked(child)) {
                NodeCost childCost = tree.getCost(child);
                assert childCost != null : "Child with null cost? block: " + child;
                usages.addAll(childCost.getUsages());
                numMat += childCost.getNumMaterializations();
                bestCost += childCost.getBestCost();
            }
        }
        assert numMat > 0 : "No materialization? " + numMat;

        // choose block
        List<UseEntry> usagesBlock = tree.getUsages(block);
        double probabilityBlock = block.probability();

        if (!usagesBlock.isEmpty() || shouldMaterializerInCurrentBlock(probabilityBlock, bestCost, numMat)) {
            // mark current block as potential materialization position
            usages.addAll(usagesBlock);
            bestCost = probabilityBlock;
            numMat = 1;
            tree.set(Flags.CANDIDATE, block);
        } else {
            // stick with the current solution
        }

        assert (new HashSet<>(usages)).size() == usages.size() : "doulbe entries? " + usages;
        NodeCost nodeCost = new NodeCost(bestCost, usages, numMat);
        tree.setCost(block, nodeCost);
    }

    /**
     * This is the cost function that decides whether a materialization should be inserted in the
     * current block.
     * <p>
     * Note that this function does not take into account if a materialization is required despite
     * the probabilities (e.g. there are usages in the current block).
     *
     * @param probabilityBlock Probability of the current block.
     * @param probabilityChildren Accumulated probability of the children.
     * @param numMat Number of materializations along the subtrees. We use {@code numMat - 1} to
     *            insert materializations as late as possible if the probabilities are the same.
     */
    private static boolean shouldMaterializerInCurrentBlock(double probabilityBlock, double probabilityChildren, int numMat) {
        return probabilityBlock * Math.pow(0.9, numMat - 1) < probabilityChildren;
    }

    private void filteredPush(Deque<AbstractBlock<?>> worklist, AbstractBlock<?> block) {
        if (isMarked(block)) {
            Debug.log(3, "adding %s to the worklist", block);
            worklist.offerLast(block);
        }
    }

    private void leafCost(AbstractBlock<?> block) {
        tree.set(Flags.CANDIDATE, block);
        tree.getOrInitCost(block);
    }

    private boolean isMarked(AbstractBlock<?> block) {
        return tree.isMarked(block);
    }

    private boolean isLeafBlock(AbstractBlock<?> block) {
        return tree.isLeafBlock(block);
    }

}
