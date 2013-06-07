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
            if (object.objectStamp().nonNull()) {
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
            if (loweringType == LoweringType.AFTER_GUARDS) {
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

        public void setLastFixedNode(FixedWithNextNode n) {
            assert n == null || n.isAlive() : n;
            lastFixedNode = n;
        }
    }

    private final LoweringType loweringType;

    public LoweringPhase(LoweringType loweringType) {
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
        NodeBitMap processed = graph.createNodeBitMap();
        while (true) {
            Round round = new Round(i++, context, processed);
            int mark = graph.getMark();

            IncrementalCanonicalizerPhase<PhaseContext> canonicalizer = new IncrementalCanonicalizerPhase<>();
            canonicalizer.addPhase(round);
            canonicalizer.apply(graph, context);

            if (!round.deferred && !containsLowerable(graph.getNewNodes(mark))) {
                // No new lowerable nodes - done!
                break;
            }
            assert graph.verify();
            processed.grow();
        }
    }

    private final class Round extends Phase {

        private final PhaseContext context;
        private final NodeBitMap processed;
        private final SchedulePhase schedule;
        private boolean deferred = false;

        private Round(int iteration, PhaseContext context, NodeBitMap processed) {
            super(String.format("Lowering iteration %d", iteration));
            this.context = context;
            this.processed = processed;
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

            for (Node node : nodes) {
                FixedNode nextFixedNode = null;
                if (node instanceof FixedWithNextNode && node.isAlive()) {
                    FixedWithNextNode fixed = (FixedWithNextNode) node;
                    nextFixedNode = fixed.next();
                    loweringTool.setLastFixedNode(fixed);
                }

                if (node.isAlive() && !processed.isMarked(node) && node instanceof Lowerable) {
                    if (loweringTool.lastFixedNode() == null) {
                        /*
                         * We cannot lower the node now because we don't have a fixed node to anchor
                         * the replacements. This can happen when previous lowerings in this
                         * lowering iteration deleted the BeginNode of this block. In the next
                         * iteration, we will have the new BeginNode available, and we can lower
                         * this node.
                         */
                        deferred = true;
                    } else {
                        processed.mark(node);
                        ((Lowerable) node).lower(loweringTool, loweringType);
                    }
                }

                if (loweringTool.lastFixedNode() == node && !node.isAlive()) {
                    if (nextFixedNode == null || !nextFixedNode.isAlive()) {
                        loweringTool.setLastFixedNode(null);
                    } else {
                        Node prev = nextFixedNode.predecessor();
                        if (prev != node && prev instanceof FixedWithNextNode) {
                            loweringTool.setLastFixedNode((FixedWithNextNode) prev);
                        } else if (nextFixedNode instanceof FixedWithNextNode) {
                            loweringTool.setLastFixedNode((FixedWithNextNode) nextFixedNode);
                        } else {
                            loweringTool.setLastFixedNode(null);
                        }
                    }
                }
            }
        }
    }
}
