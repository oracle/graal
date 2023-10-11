/*
 * Copyright (c) 2013, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.compiler.graal.phases.common;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import jdk.compiler.graal.core.common.cfg.Loop;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.debug.DebugCloseable;
import jdk.compiler.graal.nodes.AbstractDeoptimizeNode;
import jdk.compiler.graal.nodes.AbstractMergeNode;
import jdk.compiler.graal.nodes.DynamicDeoptimizeNode;
import jdk.compiler.graal.nodes.EndNode;
import jdk.compiler.graal.nodes.FrameState;
import jdk.compiler.graal.nodes.GraphState;
import jdk.compiler.graal.nodes.LoopBeginNode;
import jdk.compiler.graal.nodes.LoopExitNode;
import jdk.compiler.graal.nodes.MergeNode;
import jdk.compiler.graal.nodes.PhiNode;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.ValuePhiNode;
import jdk.compiler.graal.nodes.cfg.HIRBlock;
import jdk.compiler.graal.nodes.cfg.ControlFlowGraph;
import jdk.compiler.graal.phases.BasePhase;
import jdk.compiler.graal.phases.tiers.MidTierContext;

/**
 * This phase tries to find {@link AbstractDeoptimizeNode DeoptimizeNodes} which use the same
 * {@link FrameState} and merges them together.
 */
public class DeoptimizationGroupingPhase extends BasePhase<MidTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, MidTierContext context) {
        ControlFlowGraph cfg = null;
        for (FrameState fs : graph.getNodes(FrameState.TYPE)) {
            Iterator<AbstractDeoptimizeNode> iterator = fs.usages().filter(AbstractDeoptimizeNode.class).iterator();
            if (!iterator.hasNext()) {
                // No deopt
                continue;
            }
            AbstractDeoptimizeNode first = iterator.next();
            if (!iterator.hasNext()) {
                // Only 1 deopt
                continue;
            }
            // There is more than one deopt, create a merge
            if (cfg == null) {
                cfg = ControlFlowGraph.compute(graph, true, true, false, false);
            }
            try (DebugCloseable position = first.withNodeSourcePosition()) {
                AbstractMergeNode merge = graph.add(new MergeNode());
                EndNode firstEnd = graph.add(new EndNode());
                ValueNode actionAndReason = first.getActionAndReason(context.getMetaAccess());
                ValueNode speculation = first.getSpeculation(context.getMetaAccess());
                PhiNode reasonActionPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(actionAndReason.getStackKind()), merge));
                PhiNode speculationPhi = graph.addWithoutUnique(new ValuePhiNode(StampFactory.forKind(speculation.getStackKind()), merge));
                merge.addForwardEnd(firstEnd);
                reasonActionPhi.addInput(actionAndReason);
                speculationPhi.addInput(speculation);
                first.replaceAtPredecessor(firstEnd);
                exitLoops(first, firstEnd, cfg);
                DynamicDeoptimizeNode dynamicDeopt = new DynamicDeoptimizeNode(reasonActionPhi, speculationPhi);
                merge.setNext(graph.add(dynamicDeopt));

                List<AbstractDeoptimizeNode> obsoletes = new LinkedList<>();
                obsoletes.add(first);

                do {
                    AbstractDeoptimizeNode deopt = iterator.next();
                    EndNode newEnd = graph.add(new EndNode());
                    merge.addForwardEnd(newEnd);
                    reasonActionPhi.addInput(deopt.getActionAndReason(context.getMetaAccess()));
                    speculationPhi.addInput(deopt.getSpeculation(context.getMetaAccess()));
                    deopt.replaceAtPredecessor(newEnd);
                    exitLoops(deopt, newEnd, cfg);
                    obsoletes.add(deopt);
                } while (iterator.hasNext());

                dynamicDeopt.setStateBefore(fs);
                for (AbstractDeoptimizeNode obsolete : obsoletes) {
                    obsolete.safeDelete();
                }
                graph.getOptimizationLog().report(getClass(), "DeoptimizationGrouping", first);
            }
        }
    }

    private static void exitLoops(AbstractDeoptimizeNode deopt, EndNode end, ControlFlowGraph cfg) {
        HIRBlock block = cfg.blockFor(deopt);
        Loop<HIRBlock> loop = block.getLoop();
        while (loop != null) {
            end.graph().addBeforeFixed(end, end.graph().add(new LoopExitNode((LoopBeginNode) loop.getHeader().getBeginNode())));
            loop = loop.getParent();
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 2.5f;
    }
}
