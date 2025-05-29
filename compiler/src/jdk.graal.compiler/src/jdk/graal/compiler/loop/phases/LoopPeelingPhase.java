/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.loop.phases;

import java.util.ArrayList;
import java.util.Optional;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.util.GraphOrder;

public class LoopPeelingPhase extends LoopPhase<LoopPolicies> {

    public static class Options {
        // @formatter:off
        @Option(help = "Allow iterative peeling of loops up to this many times (each time the peeling phase runs).", type = OptionType.Debug)
        public static final OptionKey<Integer> IterativePeelingLimit = new OptionKey<>(2);
        @Option(help = "Run the canonicalizer incrementally between iterative peelings to improve the heuristics.", type = OptionType.Debug)
        public static final OptionKey<Boolean> IncrementalCanonDuringPeel = new OptionKey<>(true);
        // @formatter:on
    }

    public LoopPeelingPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
        super(policies, canonicalizer);
    }

    /**
     * Determine if the given loop can be peeled.
     */
    public static boolean canPeel(Loop loop) {
        if (LoopUtility.excludeLoopFromOptimizer(loop)) {
            return false;
        }
        return stateAllowsPeeling(loop.loopBegin().graph().getGraphState()) && loop.canDuplicateLoop() && loop.loopBegin().getLoopEndCount() > 0;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        // keep in sync with stateAllowsPeeling()
                        NotApplicable.unlessRunAfter(this, StageFlag.LOOP_OVERFLOWS_CHECKED, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState));
    }

    private static boolean stateAllowsPeeling(GraphState graphState) {
        // keep in sync with notApplicableTo()
        return graphState.isAfterStage(StageFlag.LOOP_OVERFLOWS_CHECKED) &&
                        graphState.isBeforeStage(StageFlag.FSA) &&
                        graphState.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL);
    }

    @Override
    @SuppressWarnings({"try", "deprecation"})
    protected void run(StructuredGraph graph, CoreProviders context) {
        final boolean shouldPeelAlot = LoopPolicies.Options.PeelALot.getValue(graph.getOptions());
        final int shouldPeelOnly = LoopPolicies.Options.PeelOnlyLoopWithNodeID.getValue(graph.getOptions());
        final boolean incrementalCanon = Options.IncrementalCanonDuringPeel.getValue(graph.getOptions());
        /*
         * When guards are floating we need to update the CFG on every peeling operation because
         * answering the question which floating guards are inside a loop potentially need cfg
         * blocks for anchors which can change. See
         * "NOTE: Guard Handling: Referenced by loop phases" in LoopFragment.java .
         */
        final boolean floatingGuardsNeedCFGUpdates = graph.getGuardsStage().allowsFloatingGuards();

        /*
         * Given that we potentially have to recompute the loops data between peeling iterations we
         * design peeling the following way: We iterate all loops and decide for them if we want to
         * peel them. If so, we peel them and compute the cfg in between if necessary, then redo the
         * same logic again.
         */

        // we use a list to preserve the outer first order
        ArrayList<LoopBeginNode> toPeel = new ArrayList<>();
        EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
        for (int iteration = 0; iteration < Options.IterativePeelingLimit.getValue(graph.getOptions()); iteration++) {
            try (NodeEventScope s = graph.trackNodeEvents(ec)) {
                LoopsData data = context.getLoopsDataProvider().getLoopsData(graph);
                // record the shape of the graph before peeling
                Mark before = graph.getMark();
                toPeel.clear();
                for (Loop loop : data.outerFirst()) {
                    if (!canPeel(loop)) {
                        continue;
                    }
                    final boolean peelOnlyIDDisabledOrMatches = shouldPeelOnly == -1 || shouldPeelOnly == loop.loopBegin().getId();
                    if (!peelOnlyIDDisabledOrMatches) {
                        continue;
                    }
                    if (shouldPeelAlot || getPolicies().shouldPeel(loop, data.getCFG(), context, iteration)) {
                        toPeel.add(loop.loopBegin());
                    }
                }
                if (!before.isCurrent()) {
                    /*
                     * Peeling heuristics may create overflow guards of loops and run counted loop
                     * detection. If that happens and we added additional nodes we need to reset the
                     * loop fragments because additional code can be reachable in the loop body.
                     */
                    for (Loop l : data.loops()) {
                        l.invalidateFragmentsAndIVs();
                    }
                }
                for (LoopBeginNode marked : toPeel) {
                    for (Loop loop : data.loops()) {
                        if (loop.loopBegin() == marked) {
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before peeling loop %s", loop);
                            LoopTransformations.peel(loop);
                            if (floatingGuardsNeedCFGUpdates) {
                                data = context.getLoopsDataProvider().getLoopsData(graph);
                            } else {
                                loop.invalidateFragmentsAndIVs();
                                data.getCFG().updateCachedLocalLoopFrequency(loop.loopBegin(), f -> f.decrementFrequency(1.0));
                            }
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After peeling loop %s", loop);
                            if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
                                assert GraphOrder.assertSchedulableGraph(graph);
                            }
                        }
                    }
                }
                data.deleteUnusedNodes();
            }
            if (incrementalCanon) {
                canonicalizer.applyIncremental(graph, context, ec.getNodes());
            }
            ec.getNodes().clear();
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 10.0f;
    }
}
