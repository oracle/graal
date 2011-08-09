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

import java.util.*;

import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public class LoweringPhase extends Phase {



    private final RiRuntime runtime;

    public LoweringPhase(RiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(final Graph graph) {
        final IdentifyBlocksPhase s = new IdentifyBlocksPhase(false);
        s.apply(graph);

        NodeBitMap processed = graph.createNodeBitMap();
        List<Block> blocks = s.getBlocks();
        for (final Block b : blocks) {
            process(b, processed);
        }

        processed.negate();
        final CiLoweringTool loweringTool = new CiLoweringTool() {
            @Override
            public Node getGuardAnchor() {
                throw new UnsupportedOperationException();
            }
            @Override
            public RiRuntime getRuntime() {
                return runtime;
            }
            @Override
            public Node createGuard(Node condition) {
                throw new UnsupportedOperationException();
            }
        };
        for (Node node : processed) {
            LoweringOp op = node.lookup(LoweringOp.class);
            if (op != null) {
                op.lower(node, loweringTool);
            }
        }
    }

    private void process(final Block b, NodeBitMap processed) {

        final Node anchor = b.javaBlock().createAnchor();
        final CiLoweringTool loweringTool = new CiLoweringTool() {

            @Override
            public Node getGuardAnchor() {
                return anchor;
            }

            @Override
            public RiRuntime getRuntime() {
                return runtime;
            }

            @Override
            public Node createGuard(Node condition) {
                AnchorNode anchor = (AnchorNode) getGuardAnchor();
                GuardNode newGuard = new GuardNode((BooleanNode) condition, anchor.graph());
                newGuard.setAnchor(anchor);
                return newGuard;
            }
        };


        // Lower the instructions of this block.
        for (final Node n : b.getInstructions()) {
            processed.mark(n);
            LoweringOp op = n.lookup(LoweringOp.class);
            if (op != null) {
                op.lower(n, loweringTool);
            }
        }
    }
}
