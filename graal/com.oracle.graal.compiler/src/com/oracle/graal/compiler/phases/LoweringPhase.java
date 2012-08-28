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
package com.oracle.graal.compiler.phases;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.schedule.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public class LoweringPhase extends Phase {

    private abstract class LoweringToolBase implements LoweringTool {

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
    }

    private final GraalCodeCacheProvider runtime;
    private final Assumptions assumptions;

    public LoweringPhase(GraalCodeCacheProvider runtime, Assumptions assumptions) {
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
            schedule.apply(graph);

            processBlock(schedule.getCFG().getStartBlock(), graph.createNodeBitMap(), null, schedule, processed);
            Debug.dump(graph, "Lowering iteration %d", i++);
            new CanonicalizerPhase(null, runtime, assumptions, mark, null).apply(graph);

            if (!containsLowerable(graph.getNewNodes(mark))) {
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

    private void process(final Block b, final NodeBitMap activeGuards, final ValueNode anchor, SchedulePhase schedule, NodeBitMap processed) {

        final LoweringTool loweringTool = new LoweringToolBase() {

            @Override
            public ValueNode getGuardAnchor() {
                return anchor;
            }

            @Override
            public ValueNode createGuard(BooleanNode condition, DeoptimizationReason deoptReason, DeoptimizationAction action, boolean negated, long leafGraphId) {
                FixedNode guardAnchor = (FixedNode) getGuardAnchor();
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
        };

        // Lower the instructions of this block.
        for (Node node : schedule.nodesFor(b)) {
            if (!processed.isMarked(node)) {
                processed.mark(node);
                if (node instanceof Lowerable) {
                    ((Lowerable) node).lower(loweringTool);
                }
            }
        }
    }
}
