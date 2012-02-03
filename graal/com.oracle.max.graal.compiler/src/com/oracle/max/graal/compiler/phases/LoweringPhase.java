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
package com.oracle.max.graal.compiler.phases;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.cri.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.spi.*;

public class LoweringPhase extends Phase {

    private final GraalRuntime runtime;

    public LoweringPhase(GraalRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(final StructuredGraph graph) {
        final IdentifyBlocksPhase s = new IdentifyBlocksPhase(false);
        s.apply(graph);
        s.calculateAlwaysReachedBlock();

        NodeBitMap processed = graph.createNodeBitMap();
        NodeBitMap activeGuards = graph.createNodeBitMap();
        processBlock(s.getStartBlock(), activeGuards, processed, null);

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
            public Node createGuard(Node condition) {
                throw new UnsupportedOperationException();
            }
        };
        for (Node node : processed) {
            if (node instanceof Lowerable) {
                assert !(node instanceof FixedNode) || node.predecessor() == null;
                ((Lowerable) node).lower(loweringTool);
            }
        }
    }

    private void processBlock(Block block, NodeBitMap activeGuards, NodeBitMap processed, FixedNode parentAnchor) {

        FixedNode anchor = parentAnchor;
        if (anchor == null) {
            anchor = block.createAnchor();
        }
        process(block, activeGuards, processed, anchor);

        // Process always reached block first.
        Block alwaysReachedBlock = block.alwaysReachedBlock();
        if (alwaysReachedBlock != null && alwaysReachedBlock.dominator() == block) {
            assert alwaysReachedBlock.dominator() == block;
            processBlock(alwaysReachedBlock, activeGuards, processed, anchor);
        }

        // Now go for the other dominators.
        for (Block dominated : block.getDominated()) {
            if (dominated != alwaysReachedBlock) {
                assert dominated.dominator() == block;
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
            public Node createGuard(Node condition) {
                FixedNode guardAnchor = (FixedNode) getGuardAnchor();
                if (GraalOptions.OptEliminateGuards) {
                    for (Node usage : condition.usages()) {
                        if (activeGuards.isMarked(usage)) {
                            return usage;
                        }
                    }
                }
                GuardNode newGuard = guardAnchor.graph().unique(new GuardNode((BooleanNode) condition, guardAnchor));
                activeGuards.grow();
                activeGuards.mark(newGuard);
                return newGuard;
            }
        };

        // Lower the instructions of this block.
        for (final Node node : b.getInstructions()) {
            processed.mark(node);
            if (node instanceof Lowerable) {
                ((Lowerable) node).lower(loweringTool);
            }
        }
    }
}
