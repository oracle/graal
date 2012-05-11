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

import com.oracle.graal.compiler.*;
import com.oracle.graal.cri.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

/**
 * Processes all {@link Lowerable} nodes to do their lowering.
 */
public class LoweringPhase extends Phase {

    private final GraalRuntime runtime;
    private final CiAssumptions assumptions;

    public LoweringPhase(GraalRuntime runtime, CiAssumptions assumptions) {
        this.runtime = runtime;
        this.assumptions = assumptions;
    }

    @Override
    protected void run(final StructuredGraph graph) {
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, true, true);

        NodeBitMap processed = graph.createNodeBitMap();
        NodeBitMap activeGuards = graph.createNodeBitMap();
        processBlock(cfg.getStartBlock(), activeGuards, processed, null);

        processed.negate();
        final CiLoweringTool loweringTool = new CiLoweringTool() {

            @Override
            public Node getGuardAnchor() {
                throw new UnsupportedOperationException();
            }

            @Override
            public GraalRuntime getRuntime() {
                return runtime;
            }

            @Override
            public Node createGuard(Node condition, RiDeoptReason deoptReason, RiDeoptAction action, long leafGraphId) {
                // TODO (thomaswue): Document why this must not be called on floating nodes.
                throw new UnsupportedOperationException();
            }

            @Override
            public CiAssumptions assumptions() {
                return assumptions;
            }
        };
        for (Node node : processed) {
            if (node instanceof CheckCastNode) {
                // This is a checkcast that was created while lowering some other node (e.g. StoreIndexed).
                // This checkcast must now be LIR lowered.
                // TODO (dnsimon) this is temp workaround that will be removed
            } else if (node instanceof Lowerable) {
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

        if (parentAnchor == null) {
            for (GuardNode guard : anchor.usages().filter(GuardNode.class)) {
                activeGuards.clear(guard);
            }
        }
    }

    private void process(final Block b, final NodeBitMap activeGuards, NodeBitMap processed, final Node anchor) {

        final CiLoweringTool loweringTool = new CiLoweringTool() {

            @Override
            public Node getGuardAnchor() {
                return anchor;
            }

            @Override
            public GraalRuntime getRuntime() {
                return runtime;
            }

            @Override
            public Node createGuard(Node condition, RiDeoptReason deoptReason, RiDeoptAction action, long leafGraphId) {
                FixedNode guardAnchor = (FixedNode) getGuardAnchor();
                if (GraalOptions.OptEliminateGuards) {
                    for (Node usage : condition.usages()) {
                        if (activeGuards.isMarked(usage)) {
                            return usage;
                        }
                    }
                }
                GuardNode newGuard = guardAnchor.graph().unique(new GuardNode((BooleanNode) condition, guardAnchor, deoptReason, action, leafGraphId));
                activeGuards.grow();
                activeGuards.mark(newGuard);
                return newGuard;
            }

            @Override
            public CiAssumptions assumptions() {
                return assumptions;
            }
        };

        // Lower the instructions of this block.
        for (Node node : b.getNodes()) {
            processed.mark(node);
            if (node instanceof Lowerable) {
                ((Lowerable) node).lower(loweringTool);
            }
        }
    }
}
