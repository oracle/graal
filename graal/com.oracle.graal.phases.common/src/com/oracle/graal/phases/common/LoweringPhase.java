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

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.schedule.*;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public class LoweringPhase extends Phase {

    final class LoweringToolImpl implements LoweringTool {

        private final FixedNode guardAnchor;
        private final NodeBitMap activeGuards;
        private FixedWithNextNode lastFixedNode;
        private ControlFlowGraph cfg;

        public LoweringToolImpl(FixedNode guardAnchor, NodeBitMap activeGuards, ControlFlowGraph cfg) {
            this.guardAnchor = guardAnchor;
            this.activeGuards = activeGuards;
            this.cfg = cfg;
        }

        @Override
        public TargetDescription getTarget() {
            return target;
        }

        @Override
        public GraalCodeCacheProvider getRuntime() {
            return runtime;
        }

        @Override
        public ValueNode createNullCheckGuard(ValueNode object, long leafGraphId) {
            return createGuard(object.graph().unique(new IsNullNode(object)), DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, true, leafGraphId);
        }

        @Override
        public ValueNode createGuard(BooleanNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, long leafGraphId) {
            return createGuard(condition, deoptReason, action, false, leafGraphId);
        }

        @Override
        public Assumptions assumptions() {
            return assumptions;
        }

        @Override
        public ValueNode createGuard(BooleanNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated, long leafGraphId) {
            if (GraalOptions.OptEliminateGuards) {
                for (Node usage : condition.usages()) {
                    if (!activeGuards.isNew(usage) && activeGuards.isMarked(usage)) {
                        return (ValueNode) usage;
                    }
                }
            }
            GuardNode newGuard = guardAnchor.graph().unique(new GuardNode(condition, guardAnchor, deoptReason, action, negated, leafGraphId));
            if (GraalOptions.OptEliminateGuards) {
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
    }

    private final TargetDescription target;
    private final GraalCodeCacheProvider runtime;
    private final Assumptions assumptions;

    private boolean deferred;

    public LoweringPhase(TargetDescription target, GraalCodeCacheProvider runtime, Assumptions assumptions) {
        this.target = target;
        this.runtime = runtime;
        this.assumptions = assumptions;
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
    protected void run(final StructuredGraph graph) {
        int  i = 0;
        NodeBitMap processed = graph.createNodeBitMap();
        while (true) {
            int mark = graph.getMark();
            final SchedulePhase schedule = new SchedulePhase();
            schedule.apply(graph, false);

            deferred = false;
            processBlock(schedule.getCFG().getStartBlock(), graph.createNodeBitMap(), null, schedule, processed);
            Debug.dump(graph, "Lowering iteration %d", i++);
            new CanonicalizerPhase(null, runtime, assumptions, mark, null).apply(graph);

            if (!deferred && !containsLowerable(graph.getNewNodes(mark))) {
                // No new lowerable nodes - done!
                break;
            }
            assert graph.verify();
            processed.grow();
        }
    }

    private void processBlock(Block block, NodeBitMap activeGuards, FixedNode parentAnchor, SchedulePhase schedule, NodeBitMap processed) {

        FixedNode anchor = parentAnchor;
        if (anchor == null) {
            anchor = block.getBeginNode();
        }
        process(block, activeGuards, anchor, schedule, processed);

        // Process always reached block first.
        Block alwaysReachedBlock = block.getPostdominator();
        if (alwaysReachedBlock != null && alwaysReachedBlock.getDominator() == block) {
            processBlock(alwaysReachedBlock, activeGuards, anchor, schedule, processed);
        }

        // Now go for the other dominators.
        for (Block dominated : block.getDominated()) {
            if (dominated != alwaysReachedBlock) {
                assert dominated.getDominator() == block;
                processBlock(dominated, activeGuards, null, schedule, processed);
            }
        }

        if (parentAnchor == null && GraalOptions.OptEliminateGuards) {
            for (GuardNode guard : anchor.usages().filter(GuardNode.class)) {
                activeGuards.clear(guard);
            }
        }
    }

    private void process(final Block b, final NodeBitMap activeGuards, final FixedNode anchor, SchedulePhase schedule, NodeBitMap processed) {

        final LoweringToolImpl loweringTool = new LoweringToolImpl(anchor, activeGuards, schedule.getCFG());

        // Lower the instructions of this block.
        List<ScheduledNode> nodes = schedule.nodesFor(b);

        for (Node node : nodes) {
            FixedNode lastFixedNext = null;
            if (node instanceof FixedWithNextNode) {
                FixedWithNextNode fixed = (FixedWithNextNode) node;
                lastFixedNext = fixed.next();
                loweringTool.lastFixedNode = fixed;
            }

            if (node.isAlive() && !processed.isMarked(node) && node instanceof Lowerable) {
                if (loweringTool.lastFixedNode == null) {
                    // We cannot lower the node now because we don't have a fixed node to anchor the replacements.
                    // This can happen when previous lowerings in this lowering iteration deleted the BeginNode of this block.
                    // In the next iteration, we will have the new BeginNode available, and we can lower this node.
                    deferred = true;
                } else {
                    processed.mark(node);
                    ((Lowerable) node).lower(loweringTool);
                }
            }

            if (loweringTool.lastFixedNode == node && !node.isAlive()) {
                if (lastFixedNext == null) {
                    loweringTool.lastFixedNode = null;
                } else {
                    Node prev = lastFixedNext.predecessor();
                    if (prev != node && prev instanceof FixedWithNextNode) {
                        loweringTool.lastFixedNode = (FixedWithNextNode) prev;
                    } else if (lastFixedNext instanceof FixedWithNextNode) {
                        loweringTool.lastFixedNode = (FixedWithNextNode) lastFixedNext;
                    } else {
                        loweringTool.lastFixedNode = null;
                    }
                }
            }
        }
    }
}
