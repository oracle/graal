/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.schedule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConvertNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.graal.compiler.util.CollectionsUtil;

/**
 * Performs partial redundancy elimination (PRE) on a graph. An expression is partially redundant if
 * the value computed by the expression is already available on some but not all paths through a
 * program to that expression. For example:
 *
 * <pre>
 * if (condition) {
 *     // some code that does not modify x
 *     y = x + 4;
 * }
 * z = x + 4;
 * </pre>
 *
 * The expression {@code x + 4} assigned to {@code z} is partially redundant because it is computed
 * twice if {@code condition == true}. PRE would yield the following code:
 *
 * <pre>
 * if (condition) {
 *     // some code that does not modify x
 *     t = x + 4;
 *     y = t;
 * } else {
 *     t = x + 4;
 * }
 * z = t;
 * </pre>
 */
public final class PartialRedundancySchedulePhase extends BasePhase<CoreProviders> {

    public static class Options {
        // @formatter:off
        @Option(help = "Enables partial redundancy scheduling. " +
                       "This is a special form of code scheduling that can revert the effects of " +
                       "partial redundancy elimination (for example, global value numbering) by duplicating expressions into branches. " +
                       "This can improve performance if partially redundant expressions are only used in cold branches but the global " +
                       "value numbered version not. ", type = OptionType.Expert)
        public static final OptionKey<Boolean> PartialRedundancyScheduling = new OptionKey<>(true);
        @Option(help = "")
        public static final OptionKey<Boolean> PruneLargeDominatorUsageTrees = new OptionKey<>(true);
        @Option(help = "")
        public static final OptionKey<Integer> DominatorUsageTreeMaxDepth = new OptionKey<>(16);
        @Option(help = "")
        public static final OptionKey<Integer> MaxSplitsPerNode = new OptionKey<>(32);
        // @formatter:on
    }

    private static final CounterKey usageNodeLeafMaxDepthCounter = DebugContext.counter("PartialRedundancyEliminiation_UsageNodeMaxDepthCounter");

    private static final boolean TO_STRING_DEPTH = false;

    // Constant cost added to each split child when summing up frequencies.
    private static final double EPSILON = 0.005;

    // Percentage win necessary to consider a split into children.
    private static final double MIN_WIN_FRACTION = 0.2;

    // Minimum frequency of the earliest schedule block to consider partial redundancy elimination
    // at all.
    private static final double MIN_CONSIDERED_BLOCK_FREQUENCY = EPSILON * 4;

    @Override
    public float codeSizeIncrease() {
        return 2.5f;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        new Instance(graph.getOptions(), context.getLowerer().supportsImplicitNullChecks()).run(graph, SchedulingStrategy.LATEST_OUT_OF_LOOPS_IMPLICIT_NULL_CHECKS, false);
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.setAfterStage(StageFlag.PARTIAL_REDUNDANCY_SCHEDULE);
    }

    public static class Instance extends SchedulePhase.Instance {

        final boolean pruneLargeDominatorUsageTrees;
        final int dominatorUsageTreeMaxDepth;
        final int maxSplitsPerNode;

        BlockMap<BitSet> cachedReachableBlocksMap;

        public Instance(OptionValues options, boolean supportsImplicitNullChecks) {
            super(supportsImplicitNullChecks);
            /*
             * Read option values only once per phase invocation because it is fairly expensive
             * (requires a hash map lookup of the option value).
             */
            this.pruneLargeDominatorUsageTrees = Options.PruneLargeDominatorUsageTrees.getValue(options);
            this.dominatorUsageTreeMaxDepth = Options.DominatorUsageTreeMaxDepth.getValue(options);
            this.maxSplitsPerNode = Options.MaxSplitsPerNode.getValue(options);
        }

        class UsageNode {
            UsageNode(HIRBlock block, int depth) {
                this.block = block;
                this.depth = depth;
            }

            UsageNode(HIRBlock block, Object firstUsage, int depth) {
                this(block, depth);
                this.usages.add(firstUsage);
            }

            public HIRBlock block;
            public ArrayList<Object> usages = new ArrayList<>();
            public ArrayList<UsageNode> children = new ArrayList<>();
            public double win;
            public int depth;

            /*
             * Checks whether a block was previously marked as a candidate for threaded switch
             * optimization. The scheduler should encourage splitting, when possible, if a floating
             * node is scheduled into such blocks.
             */
            private boolean isThreadedSwitchBlock() {
                return (block.getBeginNode() instanceof LoopBeginNode loopBeginNode && loopBeginNode.mayEmitThreadedCode()) ||
                                (block.getEndNode() instanceof IntegerSwitchNode integerSwitchNode && integerSwitchNode.mayEmitThreadedCode());
            }

            public double optimize() {
                double myFrequency = block.getRelativeFrequency();
                boolean isThreading = isThreadedSwitchBlock();
                if (this.usages.size() == 0) {
                    // This block does not have usages => check if we should split or accumulate.
                    double childSum = 0;
                    for (UsageNode child : children) {
                        childSum += child.optimize();
                        if (!isThreading) {
                            childSum += EPSILON;
                        }
                    }
                    double curWin = myFrequency - childSum;
                    if (isThreading || curWin > myFrequency * MIN_WIN_FRACTION) {
                        // Take decision to split => it is beneficial to put definition into
                        // children.
                        win = curWin;
                        return childSum;
                    } else {
                        if (children.size() == 1) {
                            // There is only one child.
                            if (curWin < -2 * EPSILON) {
                                // Collapse.
                            } else {
                                // Keep descending as in general we prefer to schedule later.
                                win = 0;
                                return myFrequency;
                            }
                        }
                    }
                }

                // Accumulate and drop children.
                collapse();
                return myFrequency;
            }

            DebugContext getDebug() {
                return block.getBeginNode().getDebug();
            }

            private void recomputeTreeDepths(boolean pruneToMaxDepth) {
                recomputeTreeDepths(depth, pruneToMaxDepth && pruneLargeDominatorUsageTrees);
            }

            private void recomputeTreeDepths(int newDepth, boolean pruneToMaxDepth) {
                this.depth = newDepth;
                if (pruneToMaxDepth) {
                    pruneSelfToDepth(dominatorUsageTreeMaxDepth);
                }
                if (this.children.size() == 0) {
                    return;
                }
                ArrayDeque<UsageNode> stack = new ArrayDeque<>(16);
                // start with the root
                stack.push(this);
                int currDepth = this.depth;
                while (true) { // TERMINATION ARGUMENT: processing a finite tree built from the dom
                               // tree
                    CompilationAlarm.checkProgress(block.getBeginNode().graph());
                    // iterate the next level of nodes
                    int levelNodes = stack.size();
                    // we do not have any further nodes to process
                    if (levelNodes == 0) {
                        return;
                    }
                    // we advanced one level
                    // collect all nodes from the next level of nodes
                    while (levelNodes > 0) {
                        UsageNode u = stack.remove();
                        // update depth & prune if necessary
                        u.depth = currDepth;
                        if (pruneToMaxDepth) {
                            u.pruneSelfToDepth(dominatorUsageTreeMaxDepth);
                        }
                        for (UsageNode child : u.children) {
                            stack.add(child);
                        }
                        levelNodes--;
                    }
                    // depth starts at 0, thus we advance after the current level
                    currDepth++;
                }
            }

            /**
             * Collapse the current dominator usage (sub) tree. This operation collects the usages
             * of all sub usage nodes and adds them to the current usage node.
             */
            private void collapse() {
                for (UsageNode child : children) {
                    child.collectSubtreeUsages(this.usages);
                }
                this.children.clear();
                win = 0;
            }

            private void pruneSelfToDepth(int maxDepth) {
                // +1 -> depth starts at 0
                if (pruneLargeDominatorUsageTrees && this.depth + 1 >= maxDepth && this.children.size() > 0) {
                    collapse();
                    usageNodeLeafMaxDepthCounter.increment(getDebug());
                }

            }

            private int treeHeight() {
                int height = 0;
                ArrayDeque<UsageNode> stack = new ArrayDeque<>();
                // start with the root
                stack.push(this);
                int rootDepth = this.depth;
                while (true) { // TERMINATION ARGUMENT: processing a finite tree built from the dom
                               // tree
                    CompilationAlarm.checkProgress(block.getBeginNode().graph());
                    // iterate the next level of nodes
                    int nrOfLevelNodes = stack.size();
                    // we do not have any further nodes to process
                    if (nrOfLevelNodes == 0) {
                        return height;
                    }
                    // we advanced one level
                    height++;
                    // collect all nodes from the next level of nodes
                    while (nrOfLevelNodes > 0) {
                        UsageNode u = stack.remove();
                        assert u.depth == height + rootDepth - 1/* height starts at 1 */ : "Node depth:" + u.depth + " height:" + height + " root depth:" + rootDepth;
                        for (UsageNode child : u.children) {
                            stack.add(child);
                        }
                        nrOfLevelNodes--;
                    }
                }
            }

            public boolean hasWin() {
                return win > 0;
            }

            private void collectSubtreeUsages(ArrayList<Object> destinationList) {
                destinationList.addAll(usages);
                for (UsageNode child : children) {
                    child.collectSubtreeUsages(destinationList);
                }
            }

            @SuppressWarnings("try")
            public void print() {
                DebugContext debug = getDebug();
                if (debug.isLogEnabled()) {
                    try (Indent i = debug.indent()) {
                        debug.log("block %d, usages={%s}, relFreq=%f, %s", block.getId(), Arrays.toString(usages.toArray()), block.getRelativeFrequency(),
                                        hasWin() ? ", SPLIT for win " + win + "!" : "");
                    }
                }
            }

            public boolean verify() {
                if (hasWin()) {
                    assert this.usages.isEmpty() : "Usages must be empty " + usages;
                }
                for (UsageNode child : children) {
                    assert block.strictlyDominates(child.block) : "Block " + block + " must dominate " + child.block;
                    for (UsageNode otherChild : children) {
                        if (otherChild != child) {
                            assert !child.block.dominates(otherChild.block) : "Child block " + child.block + " must not dominate the other child " + otherChild.block;
                        }
                    }
                    child.verify();
                }
                return true;
            }

            public boolean processConstrainingLocation(LocationIdentity constrainingLocation) {
                assert constrainingLocation.isMutable() : constrainingLocation;
                boolean changed = false;
                HIRBlock currentBlock = this.block;
                for (int i = 0; i < children.size(); ++i) {
                    UsageNode child = children.get(i);
                    HIRBlock childBlock = child.block;
                    HIRBlock newChildBlock = SchedulePhase.Instance.checkKillsBetween(currentBlock, childBlock, constrainingLocation);
                    assert newChildBlock.dominates(childBlock) : newChildBlock + " must dominate " + childBlock;
                    if (newChildBlock == currentBlock) {
                        // We are constraint in the current block => collapse.
                        collapse();
                        return true;
                    } else if (newChildBlock != childBlock) {
                        // We are constraint between current block and child => cut off subtree.
                        UsageNode newChild = new UsageNode(newChildBlock, this.depth + 1);
                        child.collectSubtreeUsages(newChild.usages);
                        children.set(i, newChild);
                        changed = true;
                    } else if (child.isLeaf()) {
                        // Nothing to do.
                    } else if (childBlock.canKill(constrainingLocation)) {
                        // Child kills => collapse.
                        child.collapse();
                        changed = true;
                    } else {
                        changed |= child.processConstrainingLocation(constrainingLocation);
                    }
                }

                return changed;
            }

            private boolean isLeaf() {
                return children.size() == 0;
            }

            private void addChild(UsageNode other) {
                HIRBlock myBlock = this.block;
                HIRBlock curBlock = other.block;
                UsageNode curOther = other;
                boolean recomputeDepth = false;
                // Check for loop headers between curBlock and myBlock.
                while (curBlock.getLoopDepth() > myBlock.getLoopDepth()) {
                    if (curBlock.isLoopHeader()) {
                        HIRBlock beforeLoopHeader = curBlock.getDominator();
                        if (beforeLoopHeader != myBlock) {
                            recomputeDepth = true;
                            // We need an intermediate node.
                            UsageNode intermediate = new UsageNode(beforeLoopHeader, 0);
                            intermediate.children.add(curOther);
                            curOther = intermediate;
                        }
                    }
                    curBlock = curBlock.getDominator();
                }
                children.add(curOther);
                if (recomputeDepth) {
                    this.recomputeTreeDepths(true);
                }
            }

            @SuppressWarnings("try")
            private UsageNode combine(Object usage, HIRBlock otherBlock) {
                UsageNode result = null;
                try {
                    if (this.block == otherBlock) {
                        // We match the node => add to usages.
                        this.usages.add(usage);
                        result = this;
                        return result;
                    } else if (otherBlock.dominates(this.block)) {
                        // We dominate the node => we become new this.
                        UsageNode newRoot = new UsageNode(otherBlock, usage, 0);
                        // add child updates the depths
                        newRoot.addChild(this);
                        // recompute the new depth from the old root which is this
                        this.recomputeTreeDepths(1, true);
                        result = newRoot;
                        return result;
                    } else if (this.block.dominates(otherBlock)) {
                        // We are dominated by the this => descend into correct child.
                        for (int i = 0; i < this.children.size(); ++i) {
                            UsageNode child = this.children.get(i);
                            if (child.block == otherBlock || child.block.dominates(otherBlock)) {
                                // Child fully dominates.
                                this.children.set(i, child.combine(usage, otherBlock));
                                result = this;
                                return result;
                            } else if (otherBlock.dominates(child.block)) {
                                // We fully dominate.
                                UsageNode newChild = new UsageNode(otherBlock, usage, this.depth + 1);
                                newChild.addChild(child);
                                this.children.set(i, newChild);
                                // recompute the depth from the new child which was added to this
                                newChild.recomputeTreeDepths(true);
                                result = this;
                                return result;
                            }
                        }

                        // If we reach here, none of the children was dominating nor we are
                        // dominating
                        // any of the children => there is a new intermediate node necessary.
                        UsageNode newChild = new UsageNode(otherBlock, usage, this.depth + 1);
                        for (int i = 0; i < this.children.size(); ++i) {
                            UsageNode child = this.children.get(i);
                            HIRBlock dominator = AbstractControlFlowGraph.commonDominatorTyped(child.block, otherBlock);
                            if (dominator != this.block) {
                                UsageNode dominatorNode = new UsageNode(dominator, this.depth + 1);
                                dominatorNode.children.add(child);
                                dominatorNode.children.add(newChild);
                                this.children.set(i, dominatorNode);
                                // recompute depth from the new child which is the dominator usage
                                // node
                                dominatorNode.recomputeTreeDepths(true);
                                result = this;
                                return result;
                            }
                        }
                        // There is no other common dominator with any of the children than the
                        // this.
                        // depth is already correctly set for new child
                        this.addChild(newChild);
                        result = this;
                        return result;
                    } else {
                        // We neither dominate nor are dominated by the this => we need a new this
                        // that
                        // combines us and the this.
                        int oldThisDepth = this.depth;
                        UsageNode newRoot = new UsageNode(AbstractControlFlowGraph.commonDominatorTyped(this.block, otherBlock), oldThisDepth);
                        this.depth = oldThisDepth + 1;
                        newRoot.addChild(this);
                        newRoot.addChild(new UsageNode(otherBlock, usage, oldThisDepth + 1));
                        // recompute new depth with this
                        this.recomputeTreeDepths(true);
                        result = newRoot;
                        return result;
                    }
                } finally {
                    if (result != null) {
                        result.pruneSelfToDepth(dominatorUsageTreeMaxDepth);
                    }
                }
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("Block:").append(block).append(", Usages:").append(Arrays.toString(usages.toArray()));
                sb.append(" children:").append(Arrays.toString(CollectionsUtil.mapToArray(children, x -> x.block.toString(), String[]::new)));
                if (TO_STRING_DEPTH) {
                    sb.append(" sub tree height:").append(treeHeight());
                }
                sb.append(" depth:").append(depth);
                return sb.toString();
            }
        }

        /**
         * Collects all splits given a root UsageNode.
         */
        private static ArrayList<UsageNode> collectSplits(UsageNode root) {
            ArrayList<UsageNode> splits = new ArrayList<>();
            ArrayDeque<UsageNode> worklist = new ArrayDeque<>(16);
            worklist.add(root);
            while (!worklist.isEmpty()) {
                UsageNode current = worklist.remove();
                if (current.isLeaf()) {
                    splits.add(current);
                } else {
                    worklist.addAll(current.children);
                }
            }
            return splits;
        }

        /**
         * Returns a bitset denoting all blocks reachable by {@code block} in control flow graph.
         */
        private BitSet getReachableBlocks(HIRBlock block) {
            if (cachedReachableBlocksMap == null) {
                // we have to lazily instantiate reachableBlocksMap because cfg is also lazily
                // instantiated
                cachedReachableBlocksMap = new BlockMap<>(cfg);
            }

            BitSet reachableBlocks = cachedReachableBlocksMap.get(block);
            if (reachableBlocks == null) {
                reachableBlocks = new BitSet(cfg.getBlocks().length);

                // mark all its direct or indirect successors as reachable
                ArrayDeque<HIRBlock> worklist = new ArrayDeque<>(16);
                worklist.add(block);
                while (!worklist.isEmpty()) {
                    HIRBlock current = worklist.remove();
                    int id = current.getId();
                    if (!reachableBlocks.get(id)) {
                        reachableBlocks.set(id);

                        if (current.isLoopEnd()) {
                            // for control flows originated from outside the loop, the loop header
                            // should be already visited; for those from inside the loop, we avoid
                            // re-read within a single loop body and loop exits.
                        } else {
                            for (int i = 0; i < current.getSuccessorCount(); i++) {
                                worklist.add(current.getSuccessorAt(i));
                            }
                        }
                    }
                }
                cachedReachableBlocksMap.put(block, reachableBlocks);
            }
            return reachableBlocks;
        }

        /**
         * Asserts that the splits are not repeated in any control flow path.
         */
        private boolean assertNoReread(ArrayList<UsageNode> splits) {
            for (UsageNode split : splits) {
                GraalError.guarantee(split.isLeaf() && !split.usages.isEmpty(), "invalid split at block %d", split.block.getId());
                BitSet reachableBlocks = getReachableBlocks(split.block);
                for (UsageNode otherSplit : splits) {
                    if (split != otherSplit && reachableBlocks.get(otherSplit.block.getId())) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Asserts that the actual splits are not repeated in any control flow path.
         */
        private boolean assertNoReread(ArrayList<Node> splits, NodeMap<HIRBlock> currentNodeMap) {
            for (Node split : splits) {
                BitSet reachableBlocks = getReachableBlocks(currentNodeMap.get(split));
                for (Node otherSplit : splits) {
                    if (split != otherSplit && reachableBlocks.get(currentNodeMap.get(otherSplit).getId())) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Asserts that the split dominates all inputs of the guarded phi.
         */
        private static boolean assertSplitDominatesGuardedPhisInputs(HIRBlock splitBlock, GuardPhiNode phi, NodeMap<HIRBlock> currentNodeMap) {
            for (ValueNode guard : phi.values()) {
                if (guard instanceof FixedNode) {
                    HIRBlock guardBlock = currentNodeMap.get(guard);
                    if (!AbstractControlFlowGraph.dominates(splitBlock, guardBlock)) {
                        return false;
                    }
                }
            }
            return true;
        }

        /**
         * Asserts that if a split's usage (including indirect usages if the intermediate usage is a
         * ValueProxy or ConvertNode) is a PiNode with a guarded phi, all inputs of the guarded phi
         * should be dominated by this split.
         */
        private static boolean assertPiUsage(ArrayList<UsageNode> splits, NodeMap<HIRBlock> currentNodeMap) {
            for (UsageNode split : splits) {
                for (Object usage : split.usages) {
                    if (usage instanceof PhiUsage) {
                        // skip
                    } else {
                        ArrayDeque<Node> worklist = new ArrayDeque<>(16);
                        worklist.add((Node) usage);

                        while (!worklist.isEmpty()) {
                            Node current = worklist.remove();
                            if (current instanceof PiNode) {
                                PiNode pi = (PiNode) current;
                                if (pi.getGuard() instanceof GuardPhiNode && !assertSplitDominatesGuardedPhisInputs(split.block,
                                                (GuardPhiNode) pi.getGuard(), currentNodeMap)) {
                                    return false;
                                }
                            } else if (current instanceof ValueProxy || current instanceof ConvertNode) {
                                for (Node currentUsage : current.usages()) {
                                    worklist.add(currentUsage);
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }

        @Override
        protected void calcLatestBlock(HIRBlock earliestBlock, SchedulingStrategy strategy, Node currentNode, NodeMap<HIRBlock> currentNodeMap, LocationIdentity constrainingLocation,
                        BlockMap<ArrayList<FloatingReadNode>> watchListMap, BlockMap<List<Node>> latestBlockToNodesMap, NodeBitMap visited, boolean immutableGraph,
                        NodeBitMap moveInputsIntoDominator) {
            if (currentNode.getUsageCount() > 1 &&
                            /* Splitting can only apply if current block dominates other blocks. */
                            earliestBlock.getFirstDominated() != null &&
                            /*
                             * Splitting can only be profitable if current frequency is above a
                             * minimum threshold.
                             */
                            earliestBlock.getRelativeFrequency() >= MIN_CONSIDERED_BLOCK_FREQUENCY &&
                            /* Splitting must be allowed for current node. */
                            maySplit(currentNode) &&
                            /*
                             * Don't apply splitting in case this is an implicit null check
                             * opportunity.
                             */
                            !isImplicitNullOpportunity(currentNode, earliestBlock, supportsImplicitNullChecks)) {
                UsageNode usageNode = new UsageNode(earliestBlock, 0);
                assert currentNode.hasUsages() : "Must not have usages " + currentNode;
                for (Node usage : currentNode.usages()) {
                    usageNode = calcBlockForUsage(currentNode, usage, usageNode, currentNodeMap);
                    if (usageNode.block == earliestBlock && usageNode.usages.size() > 0) {
                        // We have to stay in the earliest block.
                        SchedulePhase.Instance.selectLatestBlock(currentNode, earliestBlock, earliestBlock, currentNodeMap, watchListMap, constrainingLocation, latestBlockToNodesMap);
                        return;
                    }
                }

                assert usageNode.verify();
                usageNode.optimize();
                assert usageNode.verify();
                if (constrainingLocation != null && usageNode.processConstrainingLocation(constrainingLocation)) {
                    assert usageNode.verify();
                    usageNode.optimize();
                    assert usageNode.verify();
                }

                // Before actual splitting, we should:
                // 1. avoid splitting if the number of splits exceeds MaxSplitsPerNode
                ArrayList<UsageNode> splits = collectSplits(usageNode);
                if (!usageNode.isThreadedSwitchBlock() && splits.size() > maxSplitsPerNode) {
                    super.calcLatestBlock(earliestBlock, strategy, currentNode, currentNodeMap, constrainingLocation, watchListMap, latestBlockToNodesMap, visited, immutableGraph,
                                    moveInputsIntoDominator);
                    return;
                }

                // 2. avoid re-read in ANY control flow path because it may lead to two memory
                // states from a single application-level read, which violates the original program
                // semantics;
                // Consider the following control flow graph:
                // @formatter:off
                //      READ0
                //     /    \
                //  USE0     B1
                // /   \    /  \
                // E0   USE1   B2
                //     /    \  /
                //    E1     E2
                // @formatter:on
                // Say a read originated from block READ0 is used in both block USE0 and USE1.
                // We cannot split this read because otherwise the path
                // READ0 -> USE0 -> USE1 -> E1
                // will have two reads that may potentially return different results.
                if (currentNode instanceof FloatingReadNode && !assertNoReread(splits)) {
                    super.calcLatestBlock(earliestBlock, strategy, currentNode, currentNodeMap, constrainingLocation, watchListMap, latestBlockToNodesMap, visited, immutableGraph,
                                    moveInputsIntoDominator);
                    return;
                }

                // 3. avoid splitting on PiNode whose guard is a GuardPhiNode, and not all the phi
                // inputs are dominated by the split. This is because there could be optimizations
                // performed based on the Pi and the splitting may negate that.
                // Consider the following control flow graph:
                // @formatter:off
                //        IS_ODD
                //     /          \
                //  IS_POS0     IS_POS1
                // F/     \    /     \F
                // E0   MUST_BE_POS   E2
                //          |
                //          E1
                // @formatter:on
                // Say an integer read originated from block IS_ODD is used in block IS_POS0,
                // IS_POS1 and MUST_BE_POS. And in block MUST_BE_POS we construct a PiNode narrowing
                // the read value's stamp to always positive. We cannot split this read because
                // otherwise the proven precondition no longer holds.
                if (!assertPiUsage(splits, currentNodeMap)) {
                    super.calcLatestBlock(earliestBlock, strategy, currentNode, currentNodeMap, constrainingLocation, watchListMap, latestBlockToNodesMap, visited, immutableGraph,
                                    moveInputsIntoDominator);
                    return;
                }

                splitAndSchedule(splits, currentNode, earliestBlock, currentNodeMap, watchListMap, constrainingLocation, latestBlockToNodesMap, visited);
            } else {
                // Fallback to faster schedule without constructing the dominator usage tree for
                // non-final schedule.
                super.calcLatestBlock(earliestBlock, strategy, currentNode, currentNodeMap, constrainingLocation, watchListMap, latestBlockToNodesMap, visited, immutableGraph,
                                moveInputsIntoDominator);
            }
        }

        private static boolean maySplit(Node currentNode) {
            return GraphUtil.isFloatingNode(currentNode) && currentNode instanceof ValueNode && !(currentNode.getNodeClass().isLeafNode());
        }

        private static Node splitNode(UsageNode usageNode, Node currentNode) {
            DebugContext debug = currentNode.getDebug();
            debug.dump(DebugContext.DETAILED_LEVEL, currentNode.graph(), "Before splitting node %s", currentNode);
            Node newNode = currentNode.copyWithInputs();
            // Replace at registered usages.
            for (Object usage : usageNode.usages) {
                if (usage instanceof PhiUsage) {
                    PhiUsage phiUsage = (PhiUsage) usage;
                    phiUsage.phi.setValueAt(phiUsage.index, (ValueNode) newNode);
                } else {
                    ((Node) usage).replaceFirstInput(currentNode, newNode);
                }
            }
            debug.dump(DebugContext.DETAILED_LEVEL, currentNode.graph(), "After splitting node %s", currentNode);
            return newNode;
        }

        private void splitAndSchedule(ArrayList<UsageNode> splits, Node currentNode, HIRBlock earliestBlock, NodeMap<HIRBlock> currentNodeMap,
                        BlockMap<ArrayList<FloatingReadNode>> watchListMap, LocationIdentity constrainingLocation, BlockMap<List<Node>> latestBlockToNodesMap, NodeBitMap visited) {
            boolean mayReuse = true;
            ArrayList<Node> splitNodes = new ArrayList<>();

            for (UsageNode split : splits) {
                GraalError.guarantee(split.isLeaf() && !split.usages.isEmpty(), "invalid split at block %d", split.block.getId());
                DebugContext debug = currentNode.getDebug();
                Node nodeToSchedule = currentNode;
                if (!mayReuse) {
                    // Need to split for current usages.
                    Node newNode = splitNode(split, currentNode);
                    visited.markAndGrow(newNode);
                    nodeToSchedule = newNode;
                    StructuredGraph graph = (StructuredGraph) currentNode.graph();
                    graph.getOptimizationLog().report(PartialRedundancySchedulePhase.class, "NodeSplit", currentNode);
                }
                debug.log(DebugContext.VERBOSE_LEVEL, "Scheduling node %s in PRE in block %s", nodeToSchedule, split.block);
                splitNodes.add(nodeToSchedule);
                SchedulePhase.Instance.selectLatestBlock(nodeToSchedule, earliestBlock, split.block, currentNodeMap, watchListMap, constrainingLocation, latestBlockToNodesMap);
                mayReuse = false;
            }

            assert !(currentNode instanceof FloatingReadNode) || assertNoReread(splitNodes, currentNodeMap) : "Must not be a floating node or no reread " + currentNode;
        }

        static class PhiUsage {
            public final int index;
            public final PhiNode phi;

            PhiUsage(PhiNode phi, int index) {
                this.phi = phi;
                this.index = index;
            }

            @Override
            public String toString() {
                return "<PhiUsage -> " + phi + ":" + index + ">";
            }
        }

        private static UsageNode calcBlockForUsage(Node node, Node usage, UsageNode startUsageNode, NodeMap<HIRBlock> currentNodeMap) {
            assert !(node instanceof PhiNode) : "Must not be a phi " + node;
            UsageNode currentBlock = startUsageNode;
            if (usage instanceof PhiNode) {
                // An input to a PhiNode is used at the end of the predecessor block that
                // corresponds to
                // the PhiNode input. One PhiNode can use an input multiple times.
                PhiNode phi = (PhiNode) usage;
                AbstractMergeNode merge = phi.merge();
                HIRBlock mergeBlock = currentNodeMap.get(merge);
                for (int i = 0; i < phi.valueCount(); ++i) {
                    if (phi.valueAt(i) == node) {
                        HIRBlock otherBlock = mergeBlock.getPredecessorAt(i);
                        currentBlock = currentBlock.combine(new PhiUsage(phi, i), otherBlock);
                    }
                }
            } else if (usage instanceof AbstractBeginNode) {
                AbstractBeginNode abstractBeginNode = (AbstractBeginNode) usage;
                if (abstractBeginNode instanceof StartNode) {
                    currentBlock = currentBlock.combine(usage, currentNodeMap.get(abstractBeginNode));
                } else {
                    HIRBlock otherBlock = currentNodeMap.get(abstractBeginNode).getDominator();
                    currentBlock = currentBlock.combine(usage, otherBlock);
                }
            } else {
                // All other types of usages: Put the input into the same block as the usage.
                HIRBlock otherBlock = currentNodeMap.get(usage);
                assert otherBlock != null;
                currentBlock = currentBlock.combine(usage, otherBlock);
            }
            return currentBlock;
        }
    }
}
