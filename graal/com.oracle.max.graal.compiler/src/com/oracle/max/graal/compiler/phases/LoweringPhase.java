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

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

public class LoweringPhase extends Phase {

    public static final LoweringOp DELEGATE_TO_RUNTIME = new LoweringOp() {
        @Override
        public Node lower(Node n, CiLoweringTool tool) {
            return tool.getRuntime().lower(n, tool);
        }
    };

    private final RiRuntime runtime;

    public LoweringPhase(RiRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(final Graph graph) {
        final IdentifyBlocksPhase s = new IdentifyBlocksPhase(false);
        s.apply(graph);

        for (Block b : s.getBlocks()) {
            //final Node firstNode = b.firstNode();

            final CiLoweringTool loweringTool = new CiLoweringTool() {
                @Override
                public Node getGuardAnchor() {
                    throw Util.unimplemented();
//                    if (!(firstNode instanceof Anchor) && !(firstNode instanceof Merge)) {
//                        Anchor a = new Anchor(graph);
//                        assert firstNode.predecessors().size() == 1;
//                        Node pred = firstNode.predecessors().get(0);
//                        int predIndex = firstNode.predecessorsIndex().get(0);
//                        a.successors().setAndClear(Instruction.SUCCESSOR_NEXT, pred, predIndex);
//                        pred.successors().set(predIndex, a);
//                        return a;
//                    }
//                    return firstNode;
                }

                @Override
                public RiRuntime getRuntime() {
                    return runtime;
                }
            };

            for (final Node n : b.getInstructions()) {
                if (n instanceof FixedNode) {
                    LoweringOp op = n.lookup(LoweringOp.class);
                    if (op != null) {
                        Node newNode = op.lower(n, loweringTool);
                        if (newNode != null) {
                            n.replace(newNode);
                        }
                    }
                }
            }
        }
    }

    public interface LoweringOp extends Op {
        Node lower(Node n, CiLoweringTool tool);
    }
}
