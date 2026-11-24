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
package jdk.graal.compiler.loop.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.injectIterationCount;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.loop.phases.SimpleLoopPartialUnrollPhase;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.LoopSafepointInsertionPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

/**
 * Tests that late partial unrolling retains a safepoint check on paths whose loop counter is not proved to
 * stop. The input includes:
 *
 * <pre>
 * for (int i = Integer.MAX_VALUE - 1; (long) i &lt; limit; i += 3) {
 *     result += i;
 * }
 * </pre>
 *
 * The root IV is the int {@code i}; the checked IV is {@code (long) i}. With a limit above every int, the
 * first increment overflows and the comparison never ends the loop. A safepoint poll is the runtime check
 * that allows such a running thread to respond to a safepoint request. A poll in a later main loop cannot
 * help if execution remains in an earlier pre-loop.
 *
 * <p>Inspect graphs after safepoint insertion, after late unrolling and after the low tier. Potentially
 * nonterminating inputs are compiled only; execution is limited to bounded warmup inputs.
 */
public class LoopRootSafepointTest extends GraalCompilerTest {

    private final List<String> missingPolls = new ArrayList<>();
    private int unprovedAfterInsertion = -1;
    private int unprovedAfterUnroll = -1;

    public static long widenedRoot(long limit) {
        long result = 0;
        for (int i = Integer.MAX_VALUE - 1; injectIterationCount(100, (long) i < limit); i += 3) {
            result += i;
        }
        return result;
    }

    public static long profiledWidenedRoot(int start, long limit) {
        long result = 0;
        for (int i = start; (long) i < limit; i += 3) {
            result += i;
        }
        return result;
    }

    /**
     * Checks that each counted loop whose counter may overflow has a safepoint poll on every path back to its
     * header. For the class's {@code for (int i = Integer.MAX_VALUE - 1; (long) i < limit; i += 3)} example,
     * the checked long IV fits but the int root {@code i} may overflow, so the exit test alone does not
     * justify removing that poll.
     *
     * <p>Recompute loop information at each checkpoint. For every loop lacking the combined checked-IV/root
     * proof, require a {@link SafepointNode} inside that loop whose block dominates each backedge. A poll in
     * a later main loop does not protect an earlier pre-loop that might never exit. Record each missing
     * backedge poll and return the number of loops requiring this check, so tests can also assert that they
     * exercised the risky shape.
     */
    private int inspectPolls(StructuredGraph graph, String checkpoint) {
        LoopsData loops = getDefaultMidTierContext().getLoopsDataProvider().getLoopsData(graph);
        loops.detectCountedLoops();
        int unproved = 0;
        for (Loop loop : loops.loops()) {
            if (loop.isCounted() && !loop.counted().loopAndRootCanNeverOverflow()) {
                unproved++;
                for (LoopEndNode end : loop.loopBegin().loopEnds()) {
                    boolean hasPoll = false;
                    for (SafepointNode poll : loop.inside().nodes().filter(SafepointNode.class)) {
                        hasPoll |= loops.getCFG().blockFor(poll).dominates(loops.getCFG().blockFor(end));
                    }
                    if (!hasPoll) {
                        missingPolls.add(checkpoint + ": " + loop.loopBegin() + " pre=" + loop.loopBegin().isPreLoop() + " backedge=" + end);
                    }
                }
            }
        }
        return unproved;
    }

    private final class InspectPollsPhase extends BasePhase<MidTierContext> {
        private final boolean afterInsertion;

        InspectPollsPhase(boolean afterInsertion) {
            this.afterInsertion = afterInsertion;
        }

        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return Optional.empty();
        }

        @Override
        protected void run(StructuredGraph graph, MidTierContext context) {
            if (afterInsertion) {
                unprovedAfterInsertion = inspectPolls(graph, "after insertion");
            } else {
                unprovedAfterUnroll = inspectPolls(graph, "after late partial unrolling");
            }
        }
    }

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options).copy();
        suites.getMidTier().insertAfterPhase(LoopSafepointInsertionPhase.class, new InspectPollsPhase(true));
        suites.getMidTier().insertAfterPhase(SimpleLoopPartialUnrollPhase.class, new InspectPollsPhase(false));
        return suites;
    }

    private void checkPolls(String snippet, OptionValues options) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(snippet), options);
        inspectPolls(graph, "after low tier");
        Assert.assertTrue("Expected a loop with an unproved root before late unrolling", unprovedAfterInsertion > 0);
        Assert.assertTrue("The late-unroll observer must run", unprovedAfterUnroll >= 0);
        Assert.assertTrue("Every unproved backedge needs a reachable host poll: " + missingPolls, missingPolls.isEmpty());
    }

    @Test
    public void controlledUnrollRetainsPoll() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.FullUnroll, false,
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.PartialUnroll, true,
                        LoopInversionPhase.Options.LoopInversion, false,
                        AggressivePartialUnrollPhase.Options.AggressivePartialUnroll, false,
                        VectorIntrinsics.Options.Vectorization, false);
        checkPolls("widenedRoot", options);
    }

    @Test
    public void defaultUnrollRetainsPoll() {
        checkPolls("widenedRoot", getInitialOptions());
    }

    @Test
    public void profiledDefaultUnrollRetainsPoll() {
        long observed = 0;
        for (int n = 0; n < 20_000; n++) {
            observed += profiledWidenedRoot(0, 100);
        }
        Assert.assertEquals(33_660_000L, observed);
        checkPolls("profiledWidenedRoot", getInitialOptions());
    }
}
