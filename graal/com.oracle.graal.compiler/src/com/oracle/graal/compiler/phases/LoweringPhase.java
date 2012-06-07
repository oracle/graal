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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.max.cri.ci.*;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public class LoweringPhase extends Phase {

    private class LoweringToolBase implements CiLoweringTool {

        @Override
        public ExtendedRiRuntime getRuntime() {
            return runtime;
        }

        @Override
        public ValueNode getGuardAnchor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ValueNode createNullCheckGuard(ValueNode object, long leafGraphId) {
            return createGuard(object.graph().unique(new IsNullNode(object)), RiDeoptReason.NullCheckException, CiDeoptAction.InvalidateReprofile, true, leafGraphId);
        }

        @Override
        public ValueNode createGuard(BooleanNode condition, RiDeoptReason deoptReason, CiDeoptAction action, long leafGraphId) {
            return createGuard(condition, deoptReason, action, false, leafGraphId);
        }

        @Override
        public ValueNode createGuard(BooleanNode condition, RiDeoptReason deoptReason, CiDeoptAction action, boolean negated, long leafGraphId) {
            // TODO (thomaswue): Document why this must not be called on floating nodes.
            throw new UnsupportedOperationException();
        }

        @Override
        public CiAssumptions assumptions() {
            return assumptions;
        }
    }

    private final ExtendedRiRuntime runtime;
    private final CiAssumptions assumptions;

    public LoweringPhase(ExtendedRiRuntime runtime, CiAssumptions assumptions) {
        this.runtime = runtime;
        this.assumptions = assumptions;
    }

    @Override
    protected void run(final StructuredGraph graph) {
        // Step 1: repeatedly lower fixed nodes until no new ones are created
        NodeBitMap processed = graph.createNodeBitMap();
        int  i = 0;
        while (true) {
            int mark = graph.getMark();
            ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, true, true);
            processBlock(cfg.getStartBlock(), graph.createNodeBitMap(), processed, null);
            Debug.dump(graph, "Lowering iteration %d", i++);
            new CanonicalizerPhase(null, runtime, assumptions, mark, null).apply(graph);

            if (graph.getNewNodes(mark).filter(FixedNode.class).isEmpty()) {
                break;
            }
            graph.verify();
            processed.grow();
        }

        // Step 2: lower the floating nodes
        processed.negate();
        final CiLoweringTool loweringTool = new LoweringToolBase();
        for (Node node : processed) {
            if (node instanceof Lowerable) {
                assert !(node instanceof FixedNode) || node.predecessor() == null : node;
                ((Lowerable) node).lower(loweringTool);
            }
        }
    }

    private void processBlock(Block block, NodeBitMap activeGuards, NodeBitMap processed, FixedNode parentAnchor) {

        FixedNode anchor = parentAnchor;
        if (anchor == null) {
            anchor = block.getBeginNode();
        }
        process(block, activeGuards, processed, anchor);

        // Process always reached block first.
        Block alwaysReachedBlock = block.getPostdominator();
        if (alwaysReachedBlock != null && alwaysReachedBlock.getDominator() == block) {
            assert alwaysReachedBlock.getDominator() == block;
            processBlock(alwaysReachedBlock, activeGuards, processed, anchor);
        }

        // Now go for the other dominators.
        for (Block dominated : block.getDominated()) {
            if (dominated != alwaysReachedBlock) {
                assert dominated.getDominator() == block;
                processBlock(dominated, activeGuards, processed, null);
            }
        }

        if (parentAnchor == null && GraalOptions.OptEliminateGuards) {
            for (GuardNode guard : anchor.usages().filter(GuardNode.class)) {
                activeGuards.clear(guard);
            }
        }
    }

    private void process(final Block b, final NodeBitMap activeGuards, NodeBitMap processed, final ValueNode anchor) {

        final CiLoweringTool loweringTool = new LoweringToolBase() {

            @Override
            public ValueNode getGuardAnchor() {
                return anchor;
            }

            @Override
            public ValueNode createGuard(BooleanNode condition, RiDeoptReason deoptReason, CiDeoptAction action, boolean negated, long leafGraphId) {
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
        for (Node node : b.getNodes()) {
            if (!processed.isMarked(node)) {
                processed.mark(node);
                if (node instanceof Lowerable) {
                    ((Lowerable) node).lower(loweringTool);
                }
            }
        }
    }
}
