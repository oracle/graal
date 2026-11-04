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

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.phases.LowTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.vector.phases.RemoveEmptyLoopsPhase;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;

/**
 * Tests that deleting an empty loop preserves whether execution can reach the following statement. For
 * example:
 *
 * <pre>
 * for (int i = 0; (long) i &lt; limit; i++) {
 * }
 * return 42;
 * </pre>
 *
 * The root IV is int {@code i}; the checked IV is {@code (long) i}. If {@code limit = 2147483648L},
 * incrementing {@code i} overflows and the condition never becomes false. Deleting the loop would incorrectly
 * make {@code return 42} reachable.
 *
 * <p>Check direct empty-loop removal and the cleanup after sinking a repeated store out of the loop. Ordinary
 * int loops and a widened loop whose limit is masked to {@code [0, 1023]} are positive controls: they
 * terminate and should be removed. Potentially nonterminating cases are inspected as graphs, not executed.
 */
public class RemoveEmptyLoopsOverflowTest extends GraalCompilerTest {

    public static int emptyWidenedLoop(long limit) {
        for (int i = 0; (long) i < limit; i++) {
        }
        return 42;
    }

    public static int emptyIntLoop(int limit) {
        for (int i = 0; i < limit; i++) {
        }
        return 42;
    }

    public static int emptySafeWidenedLoop(long limit) {
        for (int i = 0; (long) i < (limit & 1023); i++) {
        }
        return 42;
    }

    public static void writeOnlyWidenedLoop(Box box, long limit) {
        for (int i = 0; (long) i < limit; i++) {
            box.value = 42;
        }
    }

    public static final class Box {
        public int value;
    }

    @Test
    public void testWrappingRootIsNotRemoved() {
        StructuredGraph graph = parseEager("emptyWidenedLoop", StructuredGraph.AllowAssumptions.NO, options());
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertNotSame(counted.getLimitCheckedIV(), counted.getLimitCheckedIV().getRootIV());
        Assert.assertTrue(counted.counterNeverOverflows());
        Assert.assertNull(counted.getOverFlowGuard());
        Assert.assertFalse(counted.ivCanNeverOverflow(counted.getLimitCheckedIV().getRootIV()));
        new RemoveEmptyLoopsPhase(createCanonicalizerPhase()).apply(graph, getDefaultMidTierContext());
        Assert.assertEquals("A widened check does not make the wrapping root finite", 1, graph.getNodes(LoopBeginNode.TYPE).count());
    }

    @Test
    public void testFiniteLoopsAreRemoved() {
        for (String snippet : new String[]{"emptyIntLoop", "emptySafeWidenedLoop"}) {
            StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO, options());
            Assert.assertEquals(1, graph.getNodes(LoopBeginNode.TYPE).count());
            new RemoveEmptyLoopsPhase(createCanonicalizerPhase()).apply(graph, getDefaultMidTierContext());
            Assert.assertEquals(snippet, 0, graph.getNodes(LoopBeginNode.TYPE).count());
        }
    }

    @Test
    public void testWrappingRootSurvivesWriteSinkingCleanup() {
        // For for (int i = 0; (long) i < limit; i++) { box.value = 42; }, sinking the store leaves a loop
        // that may still run indefinitely if int i overflows. First verify that sinking occurs without
        // cleanup, then use a fresh graph to verify production cleanup retains that empty loop.
        checkWriteSinking(false);
        checkWriteSinking(true);
    }

    private void checkWriteSinking(boolean cleanup) {
        OptionValues options = options();
        StructuredGraph graph = parseEager("writeOnlyWidenedLoop", StructuredGraph.AllowAssumptions.NO, options);
        Suites suites = createSuites(options);
        suites.getHighTier().apply(graph, getDefaultHighTierContext());
        suites.getMidTier().apply(graph, getDefaultMidTierContext());
        // Remove safepoint nodes so they cannot keep the loop nonempty and accidentally make the cleanup test
        // pass. After the store sinks, the long comparison still does not prove that int i reaches limit. The
        // cleanup must retain the loop for that reason. This deliberately altered graph is inspected only,
        // never installed or executed.
        for (SafepointNode poll : graph.getNodes().filter(SafepointNode.class).snapshot()) {
            GraphUtil.removeFixedWithUnusedInputs(poll);
        }
        PhaseSuite<LowTierContext> lowTier = suites.getLowTier();
        if (!cleanup) {
            Assert.assertTrue(lowTier.replacePhase(WriteSinkingPhase.class, new WriteSinkingPhase()));
        }
        lowTier.insertBeforePhase(WriteSinkingPhase.class, new TestPhase() {
            @Override
            protected void run(StructuredGraph current) {
                Assert.assertFalse(current.getGuardsStage().allowsFloatingGuards());
                Assert.assertEquals("The store must still be in the loop before sinking", 1, loopWrites(current));
            }
        });
        lowTier.insertAfterPhase(WriteSinkingPhase.class, new TestPhase() {
            @Override
            protected void run(StructuredGraph current) {
                Assert.assertEquals("Cleanup must preserve a potentially infinite loop", 1, current.getNodes(LoopBeginNode.TYPE).count());
                Assert.assertEquals("The store must have moved out of the loop", 0, loopWrites(current));
            }
        });
        lowTier.apply(graph, getDefaultLowTierContext());
        Assert.assertTrue("Sinking must retain the store outside the loop", graph.getNodes().filter(WriteNode.class).isNotEmpty());
    }

    private static int loopWrites(StructuredGraph graph) {
        var loops = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build().getLoops();
        Assert.assertEquals(1, loops.size());
        CFGLoop<HIRBlock> loop = loops.get(0);
        int writes = 0;
        for (HIRBlock block : loop.getBlocks()) {
            if (block.getLoop() == loop) {
                for (FixedNode node : block.getNodes()) {
                    if (node instanceof WriteNode) {
                        writes++;
                    }
                }
            }
        }
        return writes;
    }

    private OptionValues options() {
        return new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false,
                        GraalOptions.FullUnroll, false, GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopPeeling, false, LowTier.Options.OptWriteSinking, true);
    }
}
