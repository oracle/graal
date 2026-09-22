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
package jdk.graal.compiler.duplication.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationPhase;
import jdk.graal.compiler.duplication.phases.simulation.FixedDuplicationSimulationConfig;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.ConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.common.EarlyExpandCheckCastPhase;

/**
 * Regression test for checkcast merge hints steering duplication simulation exploration.
 *
 * <p>The test expands checkcasts before duplication, which marks generated merge nodes as
 * {@code EXPLORE}. It then compares default duplication options versus an explicit no-prune
 * control and verifies they converge to the same post-duplication simplification shape.
 */
public class DuplicationAdaptivePruningTest extends GraalCompilerTest {

    private static final class CastPatternType {
        private final int value;

        private CastPatternType(int value) {
            this.value = value;
        }
    }

    public static int adaptivePruningSnippet(Object value, int a, int b) {
        CastPatternType cast = (CastPatternType) value;

        // Mirror the repeated post-checkcast control flow from GR-73710 so duplication
        // can isolate the null branch and simplify the type-checked path for each use.
        int result = 0;
        if (cast == null) {
            result += a + 3;
        } else {
            result += cast.value + b + 7;
        }
        if (cast == null) {
            result += a + 11;
        } else {
            result += cast.value + b + 13;
        }
        if (cast == null) {
            result += a + 17;
        } else {
            result += cast.value + b + 19;
        }

        return result;
    }

    /**
     * Verifies that merges marked as {@code EXPLORE} by EarlyExpandCheckCast are duplicated under
     * default pruning exactly like an explicit no-prune control run.
     */
    @Test
    public void testEarlyExpandCheckCastHintExploresSimulation() {
        DuplicationShape defaultShape = runDuplicationAndCollectShape(defaultOptions());
        DuplicationShape noPruneShape = runDuplicationAndCollectShape(noPruneOptions());

        Assert.assertTrue("EarlyExpandCheckCast must create at least one merge marked with explore duplication hint",
                        defaultShape.hintedMergeCountBeforeDuplication > 0);
        Assert.assertEquals("Both runs must start from the same number of hinted merges",
                        noPruneShape.hintedMergeCountBeforeDuplication, defaultShape.hintedMergeCountBeforeDuplication);
        Assert.assertTrue("Control run must demonstrate a real duplication opportunity for hinted merges",
                        noPruneShape.hintedMergeCountAfterDuplication < noPruneShape.hintedMergeCountBeforeDuplication);
        Assert.assertEquals("Hinted merges must be handled identically in default and no-prune runs",
                        noPruneShape.hintedMergeCountAfterDuplication, defaultShape.hintedMergeCountAfterDuplication);
    }

    private record DuplicationShape(long hintedMergeCountBeforeDuplication, long hintedMergeCountAfterDuplication, long mergeCountBeforeDuplication,
                    long mergeCountAfterDuplication, long ifCountAfterCleanup) {
    }

    private DuplicationShape runDuplicationAndCollectShape(OptionValues options) {
        StructuredGraph graph = parseEager("adaptivePruningSnippet", AllowAssumptions.NO, options);
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope _ = debug.scope("AdaptivePruningDuplication", graph)) {
            new DisableOverflownCountedLoopsPhase().apply(graph);
            CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
            canonicalizer.apply(graph, getProviders());
            new EarlyExpandCheckCastPhase(canonicalizer).apply(graph, getDefaultHighTierContext());
            long hintedMergesBeforeDuplication = countExploreDuplicationHintMerges(graph);
            long mergesBeforeDuplication = graph.getNodes(MergeNode.TYPE).count();

            new DuplicationPhase(FixedDuplicationSimulationConfig.defaultForDepth(16), true, true, DuplicationPhase.FACTORS_INCLUDING_PEA, canonicalizer,
                            graph.getOptions()).apply(graph, getProviders());
            long hintedMergesAfterDuplication = countExploreDuplicationHintMerges(graph);
            long mergesAfterDuplication = graph.getNodes(MergeNode.TYPE).count();
            new ConditionalEliminationPhase(canonicalizer, false).apply(graph, getProviders());
            long ifCountAfterCleanup = graph.getNodes(IfNode.TYPE).count();

            return new DuplicationShape(hintedMergesBeforeDuplication, hintedMergesAfterDuplication, mergesBeforeDuplication, mergesAfterDuplication, ifCountAfterCleanup);
        } catch (Throwable t) {
            throw debug.handle(t);
        }
    }

    private static long countExploreDuplicationHintMerges(StructuredGraph graph) {
        long count = 0;
        for (MergeNode merge : graph.getNodes(MergeNode.TYPE)) {
            if (merge.getDuplicationHint() == MergeNode.DuplicationHint.EXPLORE) {
                count++;
            }
        }
        return count;
    }

    private OptionValues defaultOptions() {
        return new OptionValues(getInitialOptions(),
                        DuplicationOptions.DuplicationBudgetFactor, 10D,
                        DuplicationOptions.DuplicationCostReductionFactor, 20,
                        DuplicationOptions.DuplicateALot, true);
    }

    private OptionValues noPruneOptions() {
        return new OptionValues(getInitialOptions(),
                        DuplicationOptions.DuplicationBudgetFactor, 10D,
                        DuplicationOptions.DuplicationMinBranchFrequency, 0D,
                        DuplicationOptions.DuplicationCostReductionFactor, 20,
                        DuplicationOptions.DuplicateALot, true,
                        DuplicationOptions.SimulationPruneUnlikelyBranches, false);
    }
}
