/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import static com.oracle.graal.compiler.common.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public class LoweringPhase extends BasePhase<PhaseContext> {

    final class LoweringToolImpl implements LoweringTool {

        private final PhaseContext context;
        private final NodeBitMap activeGuards;
        private AnchoringNode guardAnchor;
        private FixedWithNextNode lastFixedNode;
        private ControlFlowGraph cfg;

        public LoweringToolImpl(PhaseContext context, AnchoringNode guardAnchor, NodeBitMap activeGuards, FixedWithNextNode lastFixedNode, ControlFlowGraph cfg) {
            this.context = context;
            this.guardAnchor = guardAnchor;
            this.activeGuards = activeGuards;
            this.lastFixedNode = lastFixedNode;
            this.cfg = cfg;
        }

        @Override
        public LoweringStage getLoweringStage() {
            return loweringStage;
        }

        @Override
        public ConstantReflectionProvider getConstantReflection() {
            return context.getConstantReflection();
        }

        @Override
        public MetaAccessProvider getMetaAccess() {
            return context.getMetaAccess();
        }

        @Override
        public LoweringProvider getLowerer() {
            return context.getLowerer();
        }

        @Override
        public Replacements getReplacements() {
            return context.getReplacements();
        }

        @Override
        public AnchoringNode getCurrentGuardAnchor() {
            return guardAnchor;
        }

        @Override
        public GuardingNode createGuard(FixedNode before, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action) {
            return createGuard(before, condition, deoptReason, action, false);
        }

        @Override
        public Assumptions assumptions() {
            return context.getAssumptions();
        }

        private class DummyGuardHandle extends ValueNode implements GuardedNode {
            @Input(InputType.Guard) private GuardingNode guard;

            public DummyGuardHandle(GuardingNode guard) {
                super(StampFactory.forVoid());
                this.guard = guard;
            }

            public GuardingNode getGuard() {
                return guard;
            }

            public void setGuard(GuardingNode guard) {
                updateUsagesInterface(this.guard, guard);
                this.guard = guard;
            }

            @Override
            public ValueNode asNode() {
                return this;
            }

        }

        @Override
        public GuardingNode createGuard(FixedNode before, LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated) {
            if (OptEliminateGuards.getValue()) {
                for (Node usage : condition.usages()) {
                    if (!activeGuards.isNew(usage) && activeGuards.isMarked(usage) && ((GuardNode) usage).negated() == negated) {
                        return (GuardNode) usage;
                    }
                }
            }
            StructuredGraph graph = before.graph();
            if (condition.graph().getGuardsStage().ordinal() >= StructuredGraph.GuardsStage.FIXED_DEOPTS.ordinal()) {
                FixedGuardNode fixedGuard = graph.add(new FixedGuardNode(condition, deoptReason, action, negated));
                graph.addBeforeFixed(before, fixedGuard);
                DummyGuardHandle handle = graph.add(new DummyGuardHandle(fixedGuard));
                fixedGuard.lower(this);
                return handle.getGuard();
            } else {
                GuardNode newGuard = graph.unique(new GuardNode(condition, guardAnchor, deoptReason, action, negated, Constant.NULL_OBJECT));
                if (OptEliminateGuards.getValue()) {
                    activeGuards.grow();
                    activeGuards.mark(newGuard);
                }
                return newGuard;
            }
        }

        @Override
        public Block getBlockFor(Node node) {
            return cfg.blockFor(node);
        }

        public FixedWithNextNode lastFixedNode() {
            return lastFixedNode;
        }

        private void setLastFixedNode(FixedWithNextNode n) {
            assert n.isAlive() : n;
            lastFixedNode = n;
        }
    }

    private final CanonicalizerPhase canonicalizer;
    private final LoweringTool.LoweringStage loweringStage;

    public LoweringPhase(CanonicalizerPhase canonicalizer, LoweringTool.LoweringStage loweringStage) {
        this.canonicalizer = canonicalizer;
        this.loweringStage = loweringStage;
    }

    /**
     * Checks that second lowering of a given graph did not introduce any new nodes.
     *
     * @param graph a graph that was just {@linkplain #lower lowered}
     * @throws AssertionError if the check fails
     */
    private boolean checkPostLowering(StructuredGraph graph, PhaseContext context) {
        Mark expectedMark = graph.getMark();
        lower(graph, context, 1);
        Mark mark = graph.getMark();
        assert mark.equals(expectedMark) : graph + ": a second round in the current lowering phase introduced these new nodes: " + graph.getNewNodes(expectedMark).snapshot();
        return true;
    }

    @Override
    protected void run(final StructuredGraph graph, PhaseContext context) {
        lower(graph, context, 0);
        assert checkPostLowering(graph, context);
    }

    private void lower(StructuredGraph graph, PhaseContext context, int i) {
        IncrementalCanonicalizerPhase<PhaseContext> incrementalCanonicalizer = new IncrementalCanonicalizerPhase<>(canonicalizer);
        incrementalCanonicalizer.appendPhase(new Round(i, context));
        incrementalCanonicalizer.apply(graph, context);
        assert graph.verify();
    }

    /**
     * Checks that lowering of a given node did not introduce any new {@link Lowerable} nodes that
     * could be lowered in the current {@link LoweringPhase}. Such nodes must be recursively lowered
     * as part of lowering {@code node}.
     *
     * @param node a node that was just lowered
     * @param preLoweringMark the graph mark before {@code node} was lowered
     * @param unscheduledUsages set of {@code node}'s usages that were unscheduled before it was
     *            lowered
     * @throws AssertionError if the check fails
     */
    private static boolean checkPostNodeLowering(Node node, LoweringToolImpl loweringTool, Mark preLoweringMark, Collection<Node> unscheduledUsages) {
        StructuredGraph graph = (StructuredGraph) node.graph();
        Mark postLoweringMark = graph.getMark();
        NodeIterable<Node> newNodesAfterLowering = graph.getNewNodes(preLoweringMark);
        if (node instanceof FloatingNode) {
            if (!unscheduledUsages.isEmpty()) {
                for (Node n : newNodesAfterLowering) {
                    assert !(n instanceof FixedNode) : node.graph() + ": cannot lower floatable node " + node + " as it introduces fixed node(s) but has the following unscheduled usages: " +
                                    unscheduledUsages;
                }
            }
        }
        for (Node n : newNodesAfterLowering) {
            if (n instanceof Lowerable) {
                ((Lowerable) n).lower(loweringTool);
                Mark mark = graph.getMark();
                assert postLoweringMark.equals(mark) : graph + ": lowering of " + node + " produced lowerable " + n + " that should have been recursively lowered as it introduces these new nodes: " +
                                graph.getNewNodes(postLoweringMark).snapshot();
            }
        }
        return true;
    }

    private final class Round extends Phase {

        private final PhaseContext context;
        private final SchedulePhase schedule;
        private final int iteration;

        private Round(int iteration, PhaseContext context) {
            this.iteration = iteration;
            this.context = context;
            this.schedule = new SchedulePhase();
        }

        @Override
        protected CharSequence createName() {
            return "LoweringIteration" + iteration;
        }

        @Override
        public void run(StructuredGraph graph) {
            schedule.apply(graph, false);
            processBlock(schedule.getCFG().getStartBlock(), graph.createNodeBitMap(), null);
        }

        private void processBlock(Block block, NodeBitMap activeGuards, AnchoringNode parentAnchor) {

            AnchoringNode anchor = parentAnchor;
            if (anchor == null) {
                anchor = block.getBeginNode();
            }
            anchor = process(block, activeGuards, anchor);

            // Process always reached block first.
            Block alwaysReachedBlock = block.getPostdominator();
            if (alwaysReachedBlock != null && alwaysReachedBlock.getDominator() == block) {
                processBlock(alwaysReachedBlock, activeGuards, anchor);
            }

            // Now go for the other dominators.
            for (Block dominated : block.getDominated()) {
                if (dominated != alwaysReachedBlock) {
                    assert dominated.getDominator() == block;
                    processBlock(dominated, activeGuards, null);
                }
            }

            if (parentAnchor == null && OptEliminateGuards.getValue()) {
                for (GuardNode guard : anchor.asNode().usages().filter(GuardNode.class)) {
                    if (activeGuards.contains(guard)) {
                        activeGuards.clear(guard);
                    }
                }
            }
        }

        private AnchoringNode process(final Block b, final NodeBitMap activeGuards, final AnchoringNode startAnchor) {

            final LoweringToolImpl loweringTool = new LoweringToolImpl(context, startAnchor, activeGuards, b.getBeginNode(), schedule.getCFG());

            // Lower the instructions of this block.
            List<ScheduledNode> nodes = schedule.nodesFor(b);
            for (Node node : nodes) {

                if (node.isDeleted()) {
                    // This case can happen when previous lowerings deleted nodes.
                    continue;
                }

                // Cache the next node to be able to reconstruct the previous of the next node
                // after lowering.
                FixedNode nextNode = null;
                if (node instanceof FixedWithNextNode) {
                    nextNode = ((FixedWithNextNode) node).next();
                } else {
                    nextNode = loweringTool.lastFixedNode().next();
                }

                if (node instanceof Lowerable) {
                    Collection<Node> unscheduledUsages = null;
                    assert (unscheduledUsages = getUnscheduledUsages(node)) != null;
                    Mark preLoweringMark = node.graph().getMark();
                    ((Lowerable) node).lower(loweringTool);
                    if (loweringTool.guardAnchor.asNode().isDeleted()) {
                        // TODO nextNode could be deleted but this is not currently supported
                        assert nextNode.isAlive();
                        loweringTool.guardAnchor = BeginNode.prevBegin(nextNode);
                    }
                    assert checkPostNodeLowering(node, loweringTool, preLoweringMark, unscheduledUsages);
                }

                if (!nextNode.isAlive()) {
                    // can happen when the rest of the block is killed by lowering
                    // (e.g. by an unconditional deopt)
                    break;
                } else {
                    Node nextLastFixed = nextNode.predecessor();
                    if (!(nextLastFixed instanceof FixedWithNextNode)) {
                        // insert begin node, to have a valid last fixed for next lowerable node.
                        // This is about lowering a FixedWithNextNode to a control split while this
                        // FixedWithNextNode is followed by some kind of BeginNode.
                        // For example the when a FixedGuard followed by a loop exit is lowered to a
                        // control-split + deopt.
                        BeginNode begin = node.graph().add(new BeginNode());
                        nextLastFixed.replaceFirstSuccessor(nextNode, begin);
                        begin.setNext(nextNode);
                        nextLastFixed = begin;
                    }
                    loweringTool.setLastFixedNode((FixedWithNextNode) nextLastFixed);
                }
            }
            return loweringTool.getCurrentGuardAnchor();
        }

        /**
         * Gets all usages of a floating, lowerable node that are unscheduled.
         * <p>
         * Given that the lowering of such nodes may introduce fixed nodes, they must be lowered in
         * the context of a usage that dominates all other usages. The fixed nodes resulting from
         * lowering are attached to the fixed node context of the dominating usage. This ensures the
         * post-lowering graph still has a valid schedule.
         *
         * @param node a {@link Lowerable} node
         */
        private Collection<Node> getUnscheduledUsages(Node node) {
            List<Node> unscheduledUsages = new ArrayList<>();
            if (node instanceof FloatingNode) {
                for (Node usage : node.usages()) {
                    if (usage instanceof ScheduledNode) {
                        Block usageBlock = schedule.getCFG().blockFor(usage);
                        if (usageBlock == null) {
                            unscheduledUsages.add(usage);
                        }
                    }
                }
            }
            return unscheduledUsages;
        }
    }
}
