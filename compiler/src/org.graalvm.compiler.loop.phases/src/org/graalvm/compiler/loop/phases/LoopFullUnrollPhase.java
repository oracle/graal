/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopPolicies;
import org.graalvm.compiler.nodes.loop.LoopsData;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.contract.NodeCostUtil;

public class LoopFullUnrollPhase extends LoopPhase<LoopPolicies> {
    public static class Options {
        //@formatter:off
        @Option(help = "", type = OptionType.Expert)
        public static final OptionKey<Integer> FullUnrollMaxApplication = new OptionKey<>(60);

        @Option(help = "The threshold in terms of code size for a graph to be considered small for the purpose of full unrolling. "
                        + "Applied in conjunction with the FullUnrollCodeSizeBudgetFactorForSmallGraphs and "
                        + "FullUnrollCodeSizeBudgetFactorForLargeGraphs options.", type = OptionType.Expert)
        public static final OptionKey<Integer> FullUnrollSmallGraphThreshold = new OptionKey<>(2000);

        @Option(help = "Maximum factor by which full unrolling can increase code size for small graphs. "
                        + "The FullUnrollSmallGraphThreshold option determines which graphs are small", type = OptionType.Expert)
        public static final OptionKey<Double> FullUnrollCodeSizeBudgetFactorForSmallGraphs = new OptionKey<>(5D);

        @Option(help = "Maximum factor by which full unrolling can increase code size for large graphs. "
                        + "The FullUnrollSmallGraphThreshold option determines which graphs are small", type = OptionType.Expert)
        public static final OptionKey<Double> FullUnrollCodeSizeBudgetFactorForLargeGraphs = new OptionKey<>(2D);
        //@formatter:on
    }

    private static final CounterKey FULLY_UNROLLED_LOOPS = DebugContext.counter("FullUnrolls");
    public static final Comparator<LoopEx> LOOP_COMPARATOR;
    static {
        ToDoubleFunction<LoopEx> loopFreq = e -> e.loop().getHeader().getFirstPredecessor().getRelativeFrequency();
        ToIntFunction<LoopEx> loopDepth = e -> e.loop().getDepth();
        LOOP_COMPARATOR = Comparator.comparingDouble(loopFreq).thenComparingInt(loopDepth).reversed();
    }

    public LoopFullUnrollPhase(CanonicalizerPhase canonicalizer, LoopPolicies policies) {
        super(policies, canonicalizer);
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (GraalOptions.FullUnroll.getValue(graph.getOptions())) {
            DebugContext debug = graph.getDebug();
            if (graph.hasLoops()) {
                boolean peeled;
                int applications = 0;
                int graphSizeBefore = -1;
                int maxGraphSize = -1;
                do {
                    peeled = false;
                    final LoopsData dataCounted = context.getLoopsDataProvider().getLoopsData(graph);
                    dataCounted.detectCountedLoops();
                    List<LoopEx> countedLoops = dataCounted.countedLoops();
                    countedLoops.sort(LOOP_COMPARATOR);
                    for (LoopEx loop : countedLoops) {
                        if (getPolicies().shouldFullUnroll(loop)) {
                            if (graphSizeBefore == -1) {
                                graphSizeBefore = NodeCostUtil.computeGraphSize(graph);
                                final int smallGraphs = Options.FullUnrollSmallGraphThreshold.getValue(graph.getOptions());
                                if (graphSizeBefore > smallGraphs) {
                                    maxGraphSize = (int) (graphSizeBefore * Options.FullUnrollCodeSizeBudgetFactorForLargeGraphs.getValue(graph.getOptions()));
                                } else {
                                    maxGraphSize = (int) (graphSizeBefore * Options.FullUnrollCodeSizeBudgetFactorForSmallGraphs.getValue(graph.getOptions()));
                                    if (maxGraphSize > smallGraphs) {
                                        maxGraphSize = (int) (smallGraphs * Options.FullUnrollCodeSizeBudgetFactorForLargeGraphs.getValue(graph.getOptions()));
                                    }
                                }
                            }
                            debug.log("FullUnroll %s", loop);
                            LoopTransformations.fullUnroll(loop, context, canonicalizer);
                            FULLY_UNROLLED_LOOPS.increment(debug);
                            debug.dump(DebugContext.DETAILED_LEVEL, graph, "FullUnroll %s", loop);
                            peeled = true;
                            break;
                        }
                    }
                    if (graphSizeBefore != -1) {
                        int currentGraphSize = NodeCostUtil.computeGraphSize(graph);
                        if (currentGraphSize > maxGraphSize) {
                            debug.log(DebugContext.VERY_DETAILED_LEVEL, "Aborting full unroll, graphsize went from %d to %d in %s", graphSizeBefore, currentGraphSize, graph);
                            return;
                        }
                    }
                    dataCounted.deleteUnusedNodes();
                    applications++;
                } while (peeled && applications < Options.FullUnrollMaxApplication.getValue(graph.getOptions()));
            }
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
