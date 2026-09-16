/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import java.util.Optional;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactOverflowNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactOverflowNode;

/**
 * This phase optimizes exact math addition operations by rewriting them to normal addition
 * operations. It does so by piggybacking the arithmetic exception on the loop limit deopt. However,
 * this means we delete the original exception in favor of deoptimizing to the interpreter earlier
 * if the loop counter overflows. In such a case the interpreter will throw the correct arithmetic
 * exception later.
 */
public class OptimizeExactArithmeticPhase extends BasePhase<CoreProviders> implements FloatingGuardPhase {

    private final CanonicalizerPhase canonicalizer;

    public OptimizeExactArithmeticPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        canonicalizer.notApplicableTo(graphState),
                        NotApplicable.when(!graphState.getGuardsStage().allowsFloatingGuards(), "Floating guards must be allowed"));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders providers) {
        if (graph.getNodes(IntegerAddExactNode.TYPE).isEmpty() && graph.getNodes(IntegerAddExactOverflowNode.TYPE).isEmpty()) {
            return;
        }
        DebugContext debug = graph.getDebug();
        EconomicSetNodeEventListener canonAfter = null;
        if (graph.hasLoops()) {
            LoopsData loops = providers.getLoopsDataProvider().getLoopsData(graph);
            loops.detectCountedLoops();
            for (Loop loop : loops.innerFirst()) {
                if (loop.isCounted()) {
                    IntegerAddExactOverflowNode overFlowAddExactNode = getOverFlowAddExactNode(loop);
                    if (overFlowAddExactNode != null) {
                        GuardingNode overFlowGuard = loop.counted().getOverFlowGuard();
                        if (overFlowGuard == null) {
                            if (loop.localLoopFrequency() > 4) {
                                overFlowGuard = loop.counted().createOverFlowGuard();
                                debug.log("OptimizeExactArithmeticPhase creating guard %s", loop);
                            } else {
                                debug.log("OptimizeExactArithmeticPhase freq too small %s", loop);
                                continue;
                            }
                        }
                        if (canonAfter == null) {
                            canonAfter = new EconomicSetNodeEventListener();
                        }
                        try (NodeEventScope nes = graph.trackNodeEvents(canonAfter)) {
                            for (GuardNode usage : overFlowAddExactNode.usages().filter(GuardNode.class).snapshot()) {
                                usage.replaceAtUsagesAndDelete((ValueNode) overFlowGuard);
                            }
                        }
                        graph.getOptimizationLog().report(getClass(), "AddExactOptimization", overFlowAddExactNode);
                    }
                }
            }
        }
        if (canonAfter != null && !canonAfter.getNodes().isEmpty()) {
            canonicalizer.applyIncremental(graph, providers, canonAfter.getNodes());
        }
    }

    private static IntegerAddExactOverflowNode getOverFlowAddExactNode(Loop loop) {
        ValueNode counter = loop.counted().getLimitCheckedIV().valueNode();
        IntegerAddExactOverflowNode overFlowAddExactNode = null;
        if (counter instanceof IntegerAddExactNode integerAddExactNode) {
            for (IntegerExactOverflowNode overflowCheck : integerAddExactNode.getX().usages().filter(IntegerExactOverflowNode.class).snapshot()) {
                if (overflowCheck instanceof IntegerAddExactOverflowNode) {
                    if (overflowCheck.getY() == integerAddExactNode.getY()) {
                        overFlowAddExactNode = (IntegerAddExactOverflowNode) overflowCheck;
                    }
                }
            }
        } else if (counter instanceof PhiNode) {
            for (Node usage : counter.usages()) {
                if (usage instanceof IntegerAddExactOverflowNode) {
                    // can only be exactly one usage since the floating node is
                    // value numerable
                    overFlowAddExactNode = (IntegerAddExactOverflowNode) usage;
                    if (overFlowAddExactNode.getX() != counter || loop.counted().getLimitCheckedIV().strideNode() != overFlowAddExactNode.getY()) {
                        overFlowAddExactNode = null;
                    }
                }
            }
        }
        return overFlowAddExactNode;
    }

    public static boolean isLikelyOptimizable(Loop loop) {
        return loop.isCounted() && getOverFlowAddExactNode(loop) != null;
    }
}
