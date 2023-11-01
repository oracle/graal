/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.lir.constopt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.cfg.DominatorOptimizationProblem;
import jdk.graal.compiler.core.common.cfg.PropertyConsumable;

/**
 * Represents a dominator (sub-)tree for a constant definition.
 */
public class ConstantTree extends DominatorOptimizationProblem<ConstantTree.Flags, ConstantTree.NodeCost> {

    public enum Flags {
        SUBTREE,
        USAGE,
        MATERIALIZE,
        CANDIDATE,
    }

    /**
     * Costs associated with a block.
     */
    public static class NodeCost implements PropertyConsumable {
        private List<UseEntry> usages;
        private double bestCost;
        private int numMat;

        public NodeCost(double bestCost, List<UseEntry> usages, int numMat) {
            this.bestCost = bestCost;
            this.usages = usages;
            this.numMat = numMat;
        }

        @Override
        public void forEachProperty(BiConsumer<String, String> action) {
            action.accept("bestCost", Double.toString(getBestCost()));
            action.accept("numMat", Integer.toString(getNumMaterializations()));
            action.accept("numUsages", Integer.toString(usages.size()));
        }

        public List<UseEntry> getUsages() {
            if (usages == null) {
                return Collections.emptyList();
            }
            return usages;
        }

        public double getBestCost() {
            return bestCost;
        }

        public int getNumMaterializations() {
            return numMat;
        }

        @Override
        public String toString() {
            return "NodeCost [bestCost=" + bestCost + ", numUsages=" + usages.size() + ", numMat=" + numMat + "]";
        }
    }

    private final BlockMap<List<UseEntry>> blockMap;

    public ConstantTree(AbstractControlFlowGraph<?> cfg, DefUseTree tree) {
        super(Flags.class, cfg);
        this.blockMap = new BlockMap<>(cfg);
        tree.forEach(u -> getOrInitList(u.getBlock()).add(u));
    }

    private List<UseEntry> getOrInitList(BasicBlock<?> block) {
        List<UseEntry> list = blockMap.get(block);
        if (list == null) {
            list = new ArrayList<>();
            blockMap.put(block, list);
        }
        return list;
    }

    public List<UseEntry> getUsages(BasicBlock<?> block) {
        List<UseEntry> list = blockMap.get(block);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns the cost object associated with {@code block}. If there is none, a new cost object is
     * created.
     */
    NodeCost getOrInitCost(BasicBlock<?> block) {
        NodeCost cost = getCost(block);
        if (cost == null) {
            cost = new NodeCost(block.getRelativeFrequency(), blockMap.get(block), 1);
            setCost(block, cost);
        }
        return cost;
    }

    @Override
    public String getName(Flags type) {
        switch (type) {
            case USAGE:
                return "hasUsage";
            case SUBTREE:
                return "inSubtree";
            case MATERIALIZE:
                return "materialize";
            case CANDIDATE:
                return "candidate";
        }
        return super.getName(type);
    }

    public BasicBlock<?> getStartBlock() {
        return stream(Flags.SUBTREE).findFirst().get();
    }

    public void markBlocks() {
        for (BasicBlock<?> block : getBlocks()) {
            if (get(Flags.USAGE, block)) {
                setDominatorPath(Flags.SUBTREE, block);
            }
        }
    }

    public boolean isMarked(BasicBlock<?> block) {
        return get(Flags.SUBTREE, block);
    }

    public boolean isLeafBlock(BasicBlock<?> block) {
        BasicBlock<?> dom = block.getFirstDominated();
        while (dom != null) {
            if (isMarked(dom)) {
                return false;
            }
            dom = dom.getDominatedSibling();
        }
        return true;
    }

    public void setSolution(BasicBlock<?> block) {
        set(Flags.MATERIALIZE, block);
    }

    public int size() {
        return getBlocks().length;
    }

}
