/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.loop.LoopPolicies;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;

public class LoopPeelingPhase extends LoopPhase<LoopPolicies> {

    public static class Options {
        // @formatter:off
        @Option(help = "Allow iterative peeling of loops up to this many times (each time the peeling phase runs).", type = OptionType.Debug)
        public static final OptionKey<Integer> IterativePeelingLimit = new OptionKey<>(2);
        // @formatter:on
    }

    public LoopPeelingPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
        super(policies, canonicalizer);
    }

    /**
     * Determine if the given loop can be peeled.
     */
    public static boolean canPeel(LoopEx loop) {
        return stateAllowsPeeling(loop.loopBegin().graph().getGraphState()) && loop.canDuplicateLoop() && loop.loopBegin().getLoopEndCount() > 0;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        // keep in sync with stateAllowsPeeling()
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState));
    }

    private static boolean stateAllowsPeeling(GraphState graphState) {
        // keep in sync with notApplicableTo()
        return graphState.isBeforeStage(StageFlag.FSA) && graphState.isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL);
    }

    @Override
    @SuppressWarnings({"try", "deprecation"})
    protected void run(StructuredGraph graph, CoreProviders context) {
        DebugContext debug = graph.getDebug();
        if (graph.hasLoops()) {
            LoopsData data = context.getLoopsDataProvider().getLoopsData(graph);
            boolean shouldPeelAlot = LoopPolicies.Options.PeelALot.getValue(graph.getOptions());
            int shouldPeelOnly = LoopPolicies.Options.PeelOnlyLoopWithNodeID.getValue(graph.getOptions());
            try (DebugContext.Scope s = debug.scope("peeling", data.getCFG())) {
                for (LoopEx loop : data.outerFirst()) {
                    if (canPeel(loop)) {
                        for (int iteration = 0; iteration < Options.IterativePeelingLimit.getValue(graph.getOptions()); iteration++) {
                            if ((shouldPeelAlot || getPolicies().shouldPeel(loop, data.getCFG(), context, iteration)) &&
                                            (shouldPeelOnly == -1 || shouldPeelOnly == loop.loopBegin().getId())) {
                                LoopTransformations.peel(loop);
                                loop.invalidateFragmentsAndIVs();
                                data.getCFG().updateCachedLocalLoopFrequency(loop.loopBegin(), f -> f.decrementFrequency(1.0));
                                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After peeling loop %s", loop);
                            }
                        }
                    }
                }
                data.deleteUnusedNodes();
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
    }

    @Override
    public float codeSizeIncrease() {
        return 10.0f;
    }
}
