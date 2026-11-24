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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.loop.phases.LoopSafepointEliminationPhase;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;

public class LoopSafepointEliminationTest extends GraalCompilerTest {

    private int expectedLoopSafepoints = -1;

    private void reset() {
        expectedLoopSafepoints = -1;
    }

    public static long descendingLoopBeyondIntRangeSnippet(boolean condition) {
        long start = condition ? Long.MAX_VALUE : 1_000L;
        long limit = condition ? Long.MIN_VALUE : -1_000L;
        long result = 0;
        for (long i = start; i > limit; i--) {
            result += i;
            GraalDirectives.sideEffect();
        }
        return result;
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        assert expectedLoopSafepoints >= 0 : "Must be set";
        int loopSafepoints = 0;
        for (SafepointNode safepoint : graph.getNodes().filter(SafepointNode.class)) {
            if (safepoint.getLoopLink() instanceof LoopBeginNode) {
                loopSafepoints++;
            }
        }
        Assert.assertEquals("Loop safepoints", expectedLoopSafepoints, loopSafepoints);
    }

    @Test
    public void testDescendingLoopBeyondIntRange() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.FullUnroll, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopPeeling, false);
        expectedLoopSafepoints = 1;
        test(options, "descendingLoopBeyondIntRangeSnippet", false);
        reset();
    }

    public static void widenedCounterSnippet(int[] state, long limit) {
        for (int i = 0; (long) i < limit; i++) {
            state[i & 1] = i;
        }
    }

    @BytecodeParserNeverInline
    private static int bodyCall(int value) {
        return value;
    }

    public static void widenedCounterWithCallSnippet(int[] state, long limit) {
        for (int i = 0; (long) i < limit; i++) {
            state[i & 1] = bodyCall(i);
        }
    }

    @Test
    public void testWrappingRootRetainsSafepoint() {
        checkWidenedCounter("widenedCounterSnippet", (long) Integer.MAX_VALUE + 1, false, false);
    }

    @Test
    public void testFiniteWideningDisablesSafepoint() {
        checkWidenedCounter("widenedCounterSnippet", 1024, true, false);
    }

    @Test
    public void testBodyCallStillDisablesSafepoint() {
        checkWidenedCounter("widenedCounterWithCallSnippet", (long) Integer.MAX_VALUE + 1, false, true);
    }

    private void checkWidenedCounter(String snippet, long limit, boolean rootSafe, boolean bodySafepoint) {
        OptionValues options = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false,
                        GraalOptions.FullUnroll, false, GraalOptions.PartialUnroll, false, GraalOptions.LoopPeeling, false);
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO, options);
        // Bind limit after parsing to retain the widened test (long) i < limit. The root is int i: limit =
        // 1024 stops it before overflow, whereas 2147483648L does not. Safepoint elimination must see this
        // distinction before deciding whether the loop needs its own poll.
        graph.getParameter(1).replaceAndDelete(ConstantNode.forLong(limit, graph));
        LoopsData loops = getDefaultMidTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertNotSame(counted.getLimitCheckedIV(), counted.getLimitCheckedIV().getRootIV());
        Assert.assertTrue(counted.counterNeverOverflows());
        Assert.assertNull(counted.getOverFlowGuard());
        Assert.assertEquals(rootSafe, counted.ivCanNeverOverflow(counted.getLimitCheckedIV().getRootIV()));
        Assert.assertTrue(LoopSafepointEliminationPhase.iterationRangeIsIn32Bit(loop));
        boolean hasBodySafepoint = false;
        for (FixedNode node : loop.whole().nodes().filter(FixedNode.class)) {
            hasBodySafepoint |= LoopSafepointEliminationPhase.canDisableSafepoint(node, getDefaultMidTierContext());
        }
        Assert.assertEquals(bodySafepoint, hasBodySafepoint);
        new LoopSafepointEliminationPhase.SafepointOptimizer(graph, getDefaultMidTierContext()).optimizeSafepoints(loops).apply();
        for (LoopEndNode end : loop.loopBegin().loopEnds()) {
            Assert.assertEquals("A wrapping root needs a poll unless the body already safepoints", !rootSafe && !bodySafepoint, end.getSafepointState().canSafepoint());
        }
    }

}
