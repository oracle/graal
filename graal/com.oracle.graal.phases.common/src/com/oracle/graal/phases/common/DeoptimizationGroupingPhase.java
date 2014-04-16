/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * This phase tries to find {@link AbstractDeoptimizeNode DeoptimizeNodes} which use the same
 * {@link FrameState} and merges them together.
 */
public class DeoptimizationGroupingPhase extends BasePhase<MidTierContext> {

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        ControlFlowGraph cfg = null;
        for (FrameState fs : graph.getNodes(FrameState.class)) {
            FixedNode target = null;
            PhiNode reasonActionPhi = null;
            PhiNode speculationPhi = null;
            List<AbstractDeoptimizeNode> obsoletes = null;
            for (AbstractDeoptimizeNode deopt : fs.usages().filter(AbstractDeoptimizeNode.class)) {
                if (target == null) {
                    target = deopt;
                } else {
                    if (cfg == null) {
                        cfg = ControlFlowGraph.compute(graph, true, true, false, false);
                    }
                    MergeNode merge;
                    if (target instanceof AbstractDeoptimizeNode) {
                        merge = graph.add(new MergeNode());
                        EndNode firstEnd = graph.add(new EndNode());
                        reasonActionPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(Kind.Int), merge));
                        speculationPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(Kind.Object), merge));
                        merge.addForwardEnd(firstEnd);
                        reasonActionPhi.addInput(((AbstractDeoptimizeNode) target).getActionAndReason(context.getMetaAccess()));
                        speculationPhi.addInput(((AbstractDeoptimizeNode) target).getSpeculation(context.getMetaAccess()));
                        target.predecessor().replaceFirstSuccessor(target, firstEnd);

                        exitLoops((AbstractDeoptimizeNode) target, firstEnd, cfg);

                        merge.setNext(graph.add(new DynamicDeoptimizeNode(reasonActionPhi, speculationPhi)));
                        obsoletes = new LinkedList<>();
                        obsoletes.add((AbstractDeoptimizeNode) target);
                        target = merge;
                    } else {
                        merge = (MergeNode) target;
                    }
                    EndNode newEnd = graph.add(new EndNode());
                    merge.addForwardEnd(newEnd);
                    reasonActionPhi.addInput(deopt.getActionAndReason(context.getMetaAccess()));
                    speculationPhi.addInput(deopt.getSpeculation(context.getMetaAccess()));
                    deopt.predecessor().replaceFirstSuccessor(deopt, newEnd);
                    exitLoops(deopt, newEnd, cfg);
                    obsoletes.add(deopt);
                }
            }
            if (obsoletes != null) {
                ((DynamicDeoptimizeNode) ((MergeNode) target).next()).setStateBefore(fs);
                for (AbstractDeoptimizeNode obsolete : obsoletes) {
                    obsolete.safeDelete();
                }
            }
        }
    }

    private static void exitLoops(AbstractDeoptimizeNode deopt, EndNode end, ControlFlowGraph cfg) {
        Block block = cfg.blockFor(deopt);
        Loop<Block> loop = block.getLoop();
        while (loop != null) {
            end.graph().addBeforeFixed(end, end.graph().add(new LoopExitNode((LoopBeginNode) loop.header.getBeginNode())));
            loop = loop.parent;
        }
    }
}
