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

import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.spi.Lowerable.LoweringType;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public class LoweringPhase extends BasePhase<PhaseContext> {

    final class LoweringToolImpl implements LoweringTool {

        private final PhaseContext context;
        private final GuardingNode guardAnchor;
        private final NodeBitMap activeGuards;
        private FixedWithNextNode lastFixedNode;
        private ControlFlowGraph cfg;

        public LoweringToolImpl(PhaseContext context, GuardingNode guardAnchor, NodeBitMap activeGuards, ControlFlowGraph cfg) {
            this.context = context;
            this.guardAnchor = guardAnchor;
            this.activeGuards = activeGuards;
            this.cfg = cfg;
        }

        @Override
        public GraalCodeCacheProvider getRuntime() {
            return (GraalCodeCacheProvider) context.getRuntime();
        }

        @Override
        public Replacements getReplacements() {
            return context.getReplacements();
        }

        @Override
        public LoweringType getLoweringType() {
            return loweringType;
        }

        @Override
        public GuardingNode createNullCheckGuard(GuardedNode guardedNode, ValueNode object) {
            if (ObjectStamp.isObjectNonNull(object)) {
                // Short cut creation of null check guard if the object is known to be non-null.
                return null;
            }
            GuardingNode guard = createGuard(object.graph().unique(new IsNullNode(object)), DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true);
            assert guardedNode.getGuard() == null;
            guardedNode.setGuard(guard);
            return guard;
        }

        @Override
        public GuardingNode createGuard(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action) {
            return createGuard(condition, deoptReason, action, false);
        }

        @Override
        public Assumptions assumptions() {
            return context.getAssumptions();
        }

        @Override
        public GuardingNode createGuard(LogicNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated) {
            if (loweringType != LoweringType.BEFORE_GUARDS) {
                throw new GraalInternalError("Cannot create guards in after-guard lowering");
            }
            if (OptEliminateGuards.getValue()) {
                for (Node usage : condition.usages()) {
                    if (!activeGuards.isNew(usage) && activeGuards.isMarked(usage) && ((GuardNode) usage).negated() == negated) {
                        return (GuardNode) usage;
                    }
                }
            }
            GuardNode newGuard = guardAnchor.asNode().graph().unique(new GuardNode(condition, guardAnchor, deoptReason, action, negated));
            if (OptEliminateGuards.getValue()) {
                activeGuards.grow();
                activeGuards.mark(newGuard);
            }
            return newGuard;
        }

        @Override
        public Block getBlockFor(Node node) {
            return cfg.blockFor(node);
        }

        public FixedWithNextNode lastFixedNode() {
            return lastFixedNode;
        }

        private void setLastFixedNode(FixedWithNextNode n) {
            assert n == null || n.isAlive() : n;
            lastFixedNode = n;
        }
    }

    private final LoweringType loweringType;

    public LoweringPhase(LoweringType loweringType) {
        super("Lowering (" + loweringType.name() + ")");
        this.loweringType = loweringType;
    }

    private static boolean containsLowerable(NodeIterable<Node> nodes) {
        for (Node n : nodes) {
            if (n instanceof Lowerable) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void run(final StructuredGraph graph, PhaseContext context) {
        int i = 0;
        while (true) {
            Round round = new Round(i++, context);
            int mark = graph.getMark();

            IncrementalCanonicalizerPhase<PhaseContext> canonicalizer = new IncrementalCanonicalizerPhase<>();
            canonicalizer.appendPhase(round);
            canonicalizer.apply(graph, context);

            if (!containsLowerable(graph.getNewNodes(mark))) {
                // No new lowerable nodes - done!
                break;
            }
            assert graph.verify();
        }
    }

    private final class Round extends Phase {

        private final PhaseContext context;
        private final SchedulePhase schedule;

        private Round(int iteration, PhaseContext context) {
            super(String.format("Lowering iteration %d", iteration));
            this.context = context;
            this.schedule = new SchedulePhase();
        }

        @Override
        public void run(StructuredGraph graph) {
            schedule.apply(graph, false);
            processBlock(schedule.getCFG().getStartBlock(), graph.createNodeBitMap(), null);
        }

        private void processBlock(Block block, NodeBitMap activeGuards, GuardingNode parentAnchor) {

            GuardingNode anchor = parentAnchor;
            if (anchor == null) {
                anchor = block.getBeginNode();
            }
            process(block, activeGuards, anchor);

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
                    activeGuards.clear(guard);
                }
            }
        }

        private void process(final Block b, final NodeBitMap activeGuards, final GuardingNode anchor) {

            final LoweringToolImpl loweringTool = new LoweringToolImpl(context, anchor, activeGuards, schedule.getCFG());

            // Lower the instructions of this block.
            List<ScheduledNode> nodes = schedule.nodesFor(b);
            loweringTool.setLastFixedNode(null);
            for (Node node : nodes) {

                if (node.isDeleted()) {
                    // This case can happen when previous lowerings deleted nodes.
                    continue;
                }

                if (loweringTool.lastFixedNode() == null) {
                    AbstractBeginNode beginNode = b.getBeginNode();
                    if (node == beginNode) {
                        loweringTool.setLastFixedNode(beginNode);
                    } else {
                        continue;
                    }
                }

                // Cache the next node to be able to reconstruct the previous of the next node
                // after lowering.
                FixedNode nextNode = null;
                if (node instanceof FixedWithNextNode) {
                    FixedWithNextNode fixed = (FixedWithNextNode) node;
                    nextNode = fixed.next();
                } else {
                    nextNode = loweringTool.lastFixedNode().next();
                }

                if (node instanceof Lowerable) {
                    assert checkUsagesAreScheduled(node);
                    ((Lowerable) node).lower(loweringTool, loweringType);
                }

                if (!nextNode.isAlive()) {
                    break;
                } else {
                    Node nextLastFixed = nextNode.predecessor();
                    if (nextLastFixed instanceof FixedWithNextNode) {
                        loweringTool.setLastFixedNode((FixedWithNextNode) nextLastFixed);
                    } else {
                        loweringTool.setLastFixedNode((FixedWithNextNode) nextNode);
                    }
                }
            }
        }

        /**
         * Checks that all usages of a floating, lowerable node are scheduled.
         * <p>
         * Given that the lowering of such nodes may introduce fixed nodes, they must be lowered in
         * the context of a usage that dominates all other usages. The fixed nodes resulting from
         * lowering are attached to the fixed node context of the dominating usage. This ensures the
         * post-lowering graph still has a valid schedule.
         * 
         * @param node a {@link Lowerable} node
         */
        private boolean checkUsagesAreScheduled(Node node) {
            if (node instanceof FloatingNode) {
                for (Node usage : node.usages()) {
                    if (usage instanceof ScheduledNode) {
                        Block usageBlock = schedule.getCFG().blockFor(usage);
                        assert usageBlock != null : node.graph() + ": cannot lower floatable node " + node + " that has non-scheduled usage " + usage;
                    }
                }
            }
            return true;
        }
    }
}
