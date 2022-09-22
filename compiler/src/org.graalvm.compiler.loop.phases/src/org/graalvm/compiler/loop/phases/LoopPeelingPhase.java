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
package org.graalvm.compiler.loop.phases;

import java.util.Optional;

import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.GraphState.StageFlag;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopPolicies;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

public class LoopPeelingPhase extends LoopPhase<LoopPolicies> {

    public static class Options {
        // @formatter:off
        @Option(help = "Allow iterative peeling of loops up to this many times (each time the peeling phase runs).")
        public static final OptionKey<Integer> IterativePeelingLimit = new OptionKey<>(2);
        // @formatter:on
    }

    public static final CounterKey PEELED = DebugContext.counter("Peeled");

    public LoopPeelingPhase(LoopPolicies policies, CanonicalizerPhase canonicalizer) {
        super(policies, canonicalizer);
    }

    /**
     * Determine if the given loop can be peeled.
     */
    public static boolean canPeel(LoopEx loop) {
        return loop.canDuplicateLoop() && loop.loopBegin().getLoopEndCount() > 0;
    }

    @Override
    public Optional<NotApplicable> canApply(GraphState graphState) {
        return NotApplicable.combineConstraints(
                        super.canApply(graphState),
                        NotApplicable.mustRunBefore(this, StageFlag.FSA, graphState),
                        NotApplicable.mustRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState));
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph, CoreProviders context) {
        DebugContext debug = graph.getDebug();
        if (graph.hasLoops()) {
            LoopsData data = context.getLoopsDataProvider().getLoopsData(graph);
            try (DebugContext.Scope s = debug.scope("peeling", data.getCFG())) {
                for (LoopEx loop : data.outerFirst()) {
                    if (canPeel(loop)) {
                        for (int iteration = 0; iteration < Options.IterativePeelingLimit.getValue(graph.getOptions()); iteration++) {
                            if (LoopPolicies.Options.PeelALot.getValue(graph.getOptions()) || getPolicies().shouldPeel(loop, data.getCFG(), context, iteration)) {
                                debug.log("Peeling %s, iteration %s", loop, iteration);
                                PEELED.increment(debug);
                                LoopTransformations.peel(loop);
                                loop.invalidateFragmentsAndIVs();
                                data.getCFG().updateCachedLocalLoopFrequency(loop.loopBegin(), f -> f.decrementFrequency(1.0));
                                debug.dump(DebugContext.DETAILED_LEVEL, graph, "Peeling %s, iteration %s", loop, iteration);
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
