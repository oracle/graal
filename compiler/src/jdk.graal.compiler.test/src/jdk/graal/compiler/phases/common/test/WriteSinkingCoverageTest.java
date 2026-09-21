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

package jdk.graal.compiler.phases.common.test;

import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingPhase;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingUtil;
import jdk.graal.compiler.phases.common.writesinking.writesink.ConditionalWriteNode;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.core.phases.LowTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.duplication.phases.DeDuplicationPhase;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import org.graalvm.word.LocationIdentity;

/**
 * Active coverage tests for enterprise low-tier write sinking.
 * <p>
 * Most methods compile normal snippets through the production suite, where write sinking is enabled
 * by default. A smaller set of observer phases is inserted around {@link WriteSinkingPhase} only to
 * inspect transient graph states, such as {@link ConditionalWriteNode}, that are deliberately
 * removed by the next canonicalizer.
 */
public class WriteSinkingCoverageTest extends GraalCompilerTest {

    /**
     * How many iterations should the loops being tested execute.
     */
    private static final int LOOP_LIMIT = 128;

    @Before
    public void checkOptions() {
        assumeTrue(GraalOptions.OptFloatingReads.getValue(writeSinkingOptions()));
    }

    public static int simpleFieldSnippet(WriteSinkingCoverageBox box, int limit) {
        for (int i = 0; i < limit; i++) {
            box.a = i;
        }
        return box.a;
    }

    public static int writeOnlyLoopSnippet(WriteSinkingCoverageBox box, int limit) {
        for (int i = 1024; i > 0; i--) {
            box.a = 127;
        }
        return box.a;
    }

    public static int nestedWriteOnlyLoopSnippet(WriteSinkingCoverageBox box, int limit) {
        for (int i = limit; i > 0; i--) {
            for (int j = limit; j > 0; j--) {
                box.a = 127;
            }
        }
        return box.a;
    }

    public static int constantArraySnippet(int[] values, int limit) {
        for (int i = 0; i < limit; i++) {
            values[0] = i;
        }
        return values[0];
    }

    public static int conditionalCascadeSnippet(WriteSinkingCoverageBox box, int limit) {
        for (int i = 0; i < limit; i++) {
            box.a = i;
            box.b = i + 1;
        }
        return box.a + box.b;
    }

    public static int excludedFieldsSnippet(WriteSinkingCoverageBox box, int limit) {
        for (int i = 0; i < limit; i++) {
            box.a = i;
            box.d = i + 1;
        }
        return box.a + box.d;
    }

    public static int neverWriteSinkSnippet(int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            GraalDirectives.neverWriteSink();
            sum += i;
        }
        return sum;
    }

    public static int nestedNeverWriteSinkSnippet(WriteSinkingCoverageBox box, int limit) {
        for (int i = 0; i < limit; i++) {
            GraalDirectives.neverWriteSink();
            for (int j = 0; j < limit; j++) {
                box.a = j;
            }
        }
        return box.a;
    }

    public static int statefulLoopBodySnippet(WriteSinkingCoverageBox box, int limit) {
        for (int i = 0; i < limit; i++) {
            box.a = i;
            GraalDirectives.sideEffect();
        }
        return box.a;
    }

    public static int multiMemoryKillSnippet(WriteSinkingCoverageBox box, int[] src, int[] dst, int limit) {
        for (int i = 0; i < limit; i++) {
            box.a = i;
            System.arraycopy(src, 0, dst, 0, 1);
        }
        return box.a + dst[0];
    }

    public static int volatileFieldSnippet(WriteSinkingVolatileBox box, int limit) {
        for (int i = 0; i < limit; i++) {
            box.a = i;
        }
        return box.a;
    }

    public static int loopDependentAddressSnippet(WriteSinkingCoverageBox[] boxes, int limit) {
        for (int i = 0; i < limit; i++) {
            boxes[i & 1].a = i;
        }
        return boxes[0].a + boxes[1].a;
    }

    public static int dynamicArrayOffsetSnippet(int[] values, int limit) {
        for (int i = 0; i < limit; i++) {
            values[i & 1] = i;
        }
        return values[0] + values[1];
    }

    public static int readAfterWriteSnippet(WriteSinkingCoverageBox box, int limit) {
        int sum = 0;
        for (int i = 0; i < limit; i++) {
            box.a = i;
            sum += box.a;
        }
        return sum;
    }

    /**
     * Matches the six-field sequence pattern from {@code WriteSinkingBenchmark} without depending
     * on the JMH harness.
     */
    public static int benchmarkSequence6Snippet(WriteSinkingCoverageBox box, int limit) {
        for (int i = 0; i < limit; i++) {
            box.a = i;
            box.b = i;
            box.c = i;
            box.d = i;
            box.e = i;
            box.f = i;
        }
        return box.a;
    }

    /**
     * Matches the split-order benchmark pattern where writes to two receivers are interleaved
     * across an if diamond inside the loop.
     */
    public static int benchmarkSplitOrderSnippet(WriteSinkingCoverageBox c1, WriteSinkingCoverageBox c2, int limit) {
        for (int i = 0; i < limit; i++) {
            if (c1.c < c2.c) {
                c1.a = c1.e;
                c2.a = c1.f;
            } else {
                c2.a = c1.e;
                c1.a = c1.f;
            }
            c1.c = c2.d;
        }
        return c1.e + c1.f;
    }

    @Test
    public void codeSizeIncreaseIsExplicitlyLarge() {
        Assert.assertEquals(10.0f, new WriteSinkingPhase().codeSizeIncrease(), 0.0f);
    }

    /**
     * Verifies that simple write-sinking candidates still reduce loop-body writes when the test
     * observes the low tier directly.
     */
    @Test
    public void simpleWritesSinkOutOfLoop() {
        assertLoopWritesDecrease("simpleFieldSnippet", null);
        assertLoopWritesDecrease("constantArraySnippet", null);
        test(writeSinkingOptions(), "simpleFieldSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        test(writeSinkingOptions(), "constantArraySnippet", new int[]{0}, LOOP_LIMIT);
    }

    /**
     * A loop whose only payload is a sinkable write can become empty of memory-killing body work
     * after write sinking. The empty-loop cleanup following write sinking then removes it.
     */
    @Test
    public void writeOnlyLoopIsRemovedAfterWriteSinkingCleanup() {
        EconomicMap<FixedNode, Integer> loopWrites = EconomicMap.create();
        EconomicSet<FixedNode> emptiedLoopHeaders = EconomicSet.create();
        doLowTierTest("writeOnlyLoopSnippet",
                        countLoopWrites(loopWrites),
                        recordLoopsEmptiedByWriteSinking(loopWrites, emptiedLoopHeaders),
                        null,
                        null);
        doProductionLowTierTest("writeOnlyLoopSnippet", null, null, null, assertNoLoops(), null);

        Result result = test(writeSinkingOptions(), "writeOnlyLoopSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(LOOP_LIMIT - 1, result.returnValue);
    }

    /**
     * When write sinking empties an inner loop, the cleanup candidate set must include the affected
     * loop nest. This checks the graph immediately after write sinking, before later low-tier phases
     * add their own memory-killing nodes.
     */
    @Test
    public void nestedWriteOnlyLoopBodyIsEmptiedByWriteSinking() {
        EconomicMap<FixedNode, Integer> loopWrites = EconomicMap.create();
        EconomicSet<FixedNode> emptiedLoopHeaders = EconomicSet.create();
        doLowTierTest("nestedWriteOnlyLoopSnippet",
                        countLoopWrites(loopWrites),
                        recordLoopsEmptiedByWriteSinking(loopWrites, emptiedLoopHeaders),
                        null,
                        null);

        Result result = test(writeSinkingOptions(), "nestedWriteOnlyLoopSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(127, result.returnValue);
    }

    /**
     * Exercises representative snippets through the normal test pipeline, without inserting
     * observer phases around write sinking.
     */
    @Test
    public void defaultPipelineCompilesWriteSinkingPatterns() {
        Result simple = test("simpleFieldSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(LOOP_LIMIT - 1, simple.returnValue);

        Result array = test("constantArraySnippet", new int[]{0}, LOOP_LIMIT);
        Assert.assertEquals(LOOP_LIMIT - 1, array.returnValue);

        Result cascade = test("conditionalCascadeSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(2 * LOOP_LIMIT - 1, cascade.returnValue);

        Result excludedFields = test("excludedFieldsSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(2 * LOOP_LIMIT - 1, excludedFields.returnValue);

        Result neverWriteSink = test("neverWriteSinkSnippet", LOOP_LIMIT);
        Assert.assertEquals((LOOP_LIMIT - 1) * LOOP_LIMIT / 2, neverWriteSink.returnValue);

        int[] source = {3};
        int[] destination = {0};
        Result multiKill = test("multiMemoryKillSnippet", new WriteSinkingCoverageBox(), source, destination, LOOP_LIMIT);
        Assert.assertEquals(LOOP_LIMIT - 1 + source[0], multiKill.returnValue);

        Result sequence = test("benchmarkSequence6Snippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(LOOP_LIMIT - 1, sequence.returnValue);

        WriteSinkingCoverageBox left = new WriteSinkingCoverageBox();
        WriteSinkingCoverageBox right = new WriteSinkingCoverageBox();
        left.c = 1;
        left.d = 3;
        left.e = 5;
        left.f = 7;
        right.c = 2;
        right.d = 11;
        Result split = test("benchmarkSplitOrderSnippet", left, right, LOOP_LIMIT);
        Assert.assertEquals(12, split.returnValue);
    }

    @Test
    public void conditionalWriteCascadeLowersToDiamond() {
        EconomicSet<MergeNode> existingMerges = EconomicSet.create();
        CoverageCounters counters = new CoverageCounters();
        doLowTierTest("conditionalCascadeSnippet",
                        rememberMergeNodes(existingMerges),
                        countConditionalWrites(counters, 2),
                        checkConditionalWritesLowered(existingMerges),
                        null);

        Result nonEmpty = test(writeSinkingOptions(), "conditionalCascadeSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(255, nonEmpty.returnValue);

        WriteSinkingCoverageBox zeroTripBox = new WriteSinkingCoverageBox();
        zeroTripBox.a = 7;
        zeroTripBox.b = 11;
        Result zeroTrip = test(writeSinkingOptions(), "conditionalCascadeSnippet", zeroTripBox, 0);
        Assert.assertEquals(18, zeroTrip.returnValue);
    }

    /**
     * Runs the option parser through exact, comma-separated, and glob field exclusion cases.
     */
    @Test
    public void excludeFieldsOptionParsesDuringWriteSinking() {
        assertLoopWritesDecrease("excludedFieldsSnippet", withExcludedFields(excludeFields(WriteSinkingCoverageBox.class, "a")));
        test(writeSinkingOptions(), "excludedFieldsSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
    }

    /**
     * Verifies matcher semantics without relying on a specific graph shape.
     */
    @Test
    public void fieldExclusionMatcherSupportsExactAndGlobFilters() {
        LocationIdentity fieldIdentity = new TestLocationIdentity("WriteSinkingCoverageBox.a");
        Assert.assertTrue(WriteSinkingUtil.isFieldExcluded(MethodFilter.parse("WriteSinkingCoverageBox.a"), fieldIdentity));
        Assert.assertTrue(WriteSinkingUtil.isFieldExcluded(MethodFilter.parse("WriteSinkingCoverageBox.*"), fieldIdentity));
        Assert.assertFalse(WriteSinkingUtil.isFieldExcluded(MethodFilter.parse("WriteSinkingCoverageBox.b"), fieldIdentity));
    }

    /**
     * Checks that the directive-forced commit path preserves the loop-body write.
     */
    @Test
    public void neverWriteSinkDirectiveKeepsLoopBodyWrite() {
        doLowTierTest("neverWriteSinkSnippet", null, scheduleGraph(), null, null);
        Result result = test(writeSinkingOptions(), "neverWriteSinkSnippet", LOOP_LIMIT);
        Assert.assertEquals((LOOP_LIMIT - 1) * LOOP_LIMIT / 2, result.returnValue);
    }

    /**
     * Nested loops under a {@code neverWriteSink()} region must use the no-sinking traversal too.
     * They should not create synthetic movable writes that later commit as extra stores.
     */
    @Test
    public void nestedNeverWriteSinkDirectiveKeepsInnerLoopBodyWrite() {
        EconomicMap<FixedNode, Integer> loopWrites = EconomicMap.create();
        doLowTierTest("nestedNeverWriteSinkSnippet",
                        countLoopWrites(loopWrites),
                        checkLoopWritesNotReduced(loopWrites),
                        scheduleGraph(),
                        null);
        Result result = test(writeSinkingOptions(), "nestedNeverWriteSinkSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(LOOP_LIMIT - 1, result.returnValue);
    }

    /**
     * Checks that a loop-body node with state forces movable writes to commit instead of sinking
     * past the stateful point.
     */
    @Test
    public void statefulLoopBodyNodeKeepsLoopBodyWrite() {
        EconomicMap<FixedNode, Integer> loopWrites = EconomicMap.create();
        doLowTierTest("statefulLoopBodySnippet",
                        countLoopWrites(loopWrites),
                        checkLoopWritesNotReduced(loopWrites),
                        scheduleGraph(),
                        null);
        Result result = test(writeSinkingOptions(), "statefulLoopBodySnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        Assert.assertEquals(LOOP_LIMIT - 1, result.returnValue);
    }

    /**
     * Covers the multi-memory-kill path when the platform lowers {@code System.arraycopy} to a
     * multi-kill node on this checkout.
     */
    @Test
    public void multiMemoryKillClearsMovableWrites() {
        CoverageCounters counters = new CoverageCounters();
        doLowTierTest("multiMemoryKillSnippet",
                        countMultiMemoryKills(counters),
                        checkMultiMemoryKillCandidate(counters),
                        null,
                        null);
    }

    @Test
    public void writeSinkingDetailsCSVRecordsRejectionReasons() throws IOException {
        assumeTrue("runtime compiler image does not include WriteSinkingDetailsCSV schema v3 ineligible reason names yet", hasRuntimeWriteSinkingDetailsSchema3IneligibleNames());
        Path output = Files.createTempDirectory("write-sinking-details").resolve("details.csv");
        test(withDetailsCSV(output, withExcludedFields("WriteSinkingCoverageBox.a")), "excludedFieldsSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        test(withDetailsCSV(output, null), "volatileFieldSnippet", new WriteSinkingVolatileBox(), LOOP_LIMIT);
        test(withDetailsCSV(output, null), "dynamicArrayOffsetSnippet", new int[]{0, 0}, LOOP_LIMIT);
        test(withDetailsCSV(output, null), "loopDependentAddressSnippet", new WriteSinkingCoverageBox[]{new WriteSinkingCoverageBox(), new WriteSinkingCoverageBox()}, LOOP_LIMIT);
        test(withDetailsCSV(output, null), "readAfterWriteSnippet", new WriteSinkingCoverageBox(), LOOP_LIMIT);
        test(withDetailsCSV(output, null), "neverWriteSinkSnippet", LOOP_LIMIT);

        List<String> lines = Files.readAllLines(output);
        Assert.assertFalse("expected write-sinking details CSV to be written", lines.isEmpty());
        Assert.assertEquals(
                        "schema_version;compiled_method;event;reason_category;reason;detail_note;loop_header_id;loop_depth;loop_frequency;write_node_id;write_location;source_method;source_bci;source_file;source_line;source_stack",
                        lines.get(0));
        String csv = String.join("\n", lines);
        Assert.assertTrue("expected schema version 3 rows in CSV:\n" + csv, csv.contains("\n3;"));
        assertCSVContains(csv, "WRITE_REJECTED", "WRITE_ADDRESS_SHAPE", "NON_CONSTANT_OFFSET", "write offset is not a compile-time constant");
        assertCSVContains(csv, "LOOP_OUTCOME", "WRITE_ADDRESS_SHAPE", "INELIGIBLE_DYNAMIC_OFFSET",
                        "loop writes are structurally ineligible; primary write rejection: write offset is not a compile-time constant");
        assertCSVContains(csv, "CANDIDATE_REJECTED", "LOOP_DEPENDENT_ADDRESS", "LOOP_DEPENDENT_ADDRESS", "write address depends on loop state");
        assertCSVContains(csv, "LOOP_OUTCOME", "LOOP_POLICY", "GLOBAL_KILL_OR_FORCED_COMMIT");
        assertCSVContains(csv, "LOOP_OUTCOME", "LOOP_POLICY", "EXPLICITLY_DISABLED", "neverWriteSink marker active");
        Assert.assertTrue(csv.contains("WriteSinkingCoverageTest."));
    }

    private void assertLoopWritesDecrease(String snippet, OptionValues additionalOptions) {
        EconomicMap<FixedNode, Integer> loopWrites = EconomicMap.create();
        doLowTierTest(snippet,
                        countLoopWrites(loopWrites),
                        checkLoopWritesDecrease(loopWrites),
                        scheduleGraph(),
                        additionalOptions);
    }

    private void assertLoopWritesNotReduced(String snippet, OptionValues additionalOptions) {
        EconomicMap<FixedNode, Integer> loopWrites = EconomicMap.create();
        doLowTierTest(snippet,
                        countLoopWrites(loopWrites),
                        checkLoopWritesNotReduced(loopWrites),
                        scheduleGraph(),
                        additionalOptions);
    }

    /**
     * Runs high tier, mid tier, and low tier manually so tests can install probes around
     * {@link WriteSinkingPhase}. Normal behavioral tests should prefer {@link #test(String,
     * Object...)}.
     */
    private void doLowTierTest(String snippet, BasePhase<? super LowTierContext> beforeWriteSinking, BasePhase<? super LowTierContext> afterWriteSinking,
                    BasePhase<? super LowTierContext> afterCanonicalizer, OptionValues additionalOptions) {
        doLowTierTest(snippet, beforeWriteSinking, afterWriteSinking, afterCanonicalizer, null, additionalOptions, false);
    }

    private void doLowTierTest(String snippet, BasePhase<? super LowTierContext> beforeWriteSinking, BasePhase<? super LowTierContext> afterWriteSinking,
                    BasePhase<? super LowTierContext> afterCanonicalizer, BasePhase<? super LowTierContext> afterLowTier, OptionValues additionalOptions) {
        doLowTierTest(snippet, beforeWriteSinking, afterWriteSinking, afterCanonicalizer, afterLowTier, additionalOptions, false);
    }

    private void doProductionLowTierTest(String snippet, BasePhase<? super LowTierContext> beforeWriteSinking, BasePhase<? super LowTierContext> afterWriteSinking,
                    BasePhase<? super LowTierContext> afterCanonicalizer, BasePhase<? super LowTierContext> afterLowTier, OptionValues additionalOptions) {
        doLowTierTest(snippet, beforeWriteSinking, afterWriteSinking, afterCanonicalizer, afterLowTier, additionalOptions, true);
    }

    private void doLowTierTest(String snippet, BasePhase<? super LowTierContext> beforeWriteSinking, BasePhase<? super LowTierContext> afterWriteSinking,
                    BasePhase<? super LowTierContext> afterCanonicalizer, BasePhase<? super LowTierContext> afterLowTier, OptionValues additionalOptions, boolean useProductionWriteSinkingCleanup) {
        OptionValues options = mergeOptions(writeSinkingOptions(), additionalOptions);

        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO, options);
        Suites suites = createSuites(options);
        suites.getHighTier().apply(graph, getDefaultHighTierContext());
        suites.getMidTier().apply(graph, getDefaultMidTierContext());

        PhaseSuite<LowTierContext> lowTier = suites.getLowTier();
        if (!useProductionWriteSinkingCleanup) {
            Assert.assertTrue(lowTier.replacePhase(WriteSinkingPhase.class, new WriteSinkingPhase()));
        }
        if (beforeWriteSinking != null) {
            lowTier.insertBeforePhase(WriteSinkingPhase.class, beforeWriteSinking);
        }
        if (afterWriteSinking != null) {
            lowTier.insertAfterPhase(WriteSinkingPhase.class, afterWriteSinking);
        }
        if (afterCanonicalizer != null) {
            lowTier.insertAfterPhase(CanonicalizerPhase.class, afterCanonicalizer);
        }
        if (afterLowTier != null) {
            lowTier.appendPhase(afterLowTier);
        }
        lowTier.apply(graph, getDefaultLowTierContext());
    }

    private static BasePhase<? super LowTierContext> countLoopWrites(EconomicMap<FixedNode, Integer> loopWrites) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                for (CFGLoop<HIRBlock> loop : loops(graph)) {
                    int writes = 0;
                    for (HIRBlock block : loop.getBlocks()) {
                        if (block.getLoop() == loop) {
                            for (FixedNode node : block.getNodes()) {
                                if (node instanceof WriteNode writeNode && !isDeoptimizationWrite(writeNode)) {
                                    writes++;
                                }
                            }
                        }
                    }
                    loopWrites.put(loop.getHeader().getBeginNode(), writes);
                }
            }
        };
    }

    private static BasePhase<? super LowTierContext> checkLoopWritesDecrease(EconomicMap<FixedNode, Integer> oldLoopWrites) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                boolean sawDecrease = false;
                for (CFGLoop<HIRBlock> loop : loops(graph)) {
                    int old = oldLoopWrites.get(loop.getHeader().getBeginNode());
                    int writes = 0;
                    for (HIRBlock block : loop.getBlocks()) {
                        if (block.getLoop() == loop) {
                            for (FixedNode node : block.getNodes()) {
                                if (node instanceof WriteNode && !isDeoptimizationWrite((WriteNode) node)) {
                                    writes++;
                                }
                            }
                        }
                    }
                    if (old > 0) {
                        Assert.assertTrue("expected loop writes to decrease from " + old + " but saw " + writes, writes < old);
                        sawDecrease = true;
                    } else {
                        Assert.assertEquals(0, writes);
                    }
                }
                Assert.assertTrue("expected at least one loop with a write before write sinking", sawDecrease);
            }
        };
    }

    private static BasePhase<? super LowTierContext> checkLoopWritesNotReduced(EconomicMap<FixedNode, Integer> oldLoopWrites) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                boolean sawLoopWrite = false;
                for (CFGLoop<HIRBlock> loop : loops(graph)) {
                    int old = oldLoopWrites.get(loop.getHeader().getBeginNode());
                    int writes = 0;
                    for (HIRBlock block : loop.getBlocks()) {
                        if (block.getLoop() == loop) {
                            for (FixedNode node : block.getNodes()) {
                                if (node instanceof WriteNode && !isDeoptimizationWrite((WriteNode) node)) {
                                    writes++;
                                }
                            }
                        }
                    }
                    if (old > 0) {
                        Assert.assertEquals("expected stateful loop-body node to keep loop writes", old, writes);
                        sawLoopWrite = true;
                    } else {
                        Assert.assertEquals(0, writes);
                    }
                }
                Assert.assertTrue("expected at least one loop with a write before write sinking", sawLoopWrite);
            }
        };
    }

    private static BasePhase<? super LowTierContext> recordLoopsEmptiedByWriteSinking(EconomicMap<FixedNode, Integer> oldLoopWrites, EconomicSet<FixedNode> emptiedLoopHeaders) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                for (CFGLoop<HIRBlock> loop : loops(graph)) {
                    FixedNode header = loop.getHeader().getBeginNode();
                    int old = oldLoopWrites.get(header);
                    if (old > 0) {
                        int writes = countLoopBodyWrites(loop);
                        int memoryKills = countLoopBodyMemoryKills(loop);
                        Assert.assertEquals("expected write sinking to remove the loop-body write", 0, writes);
                        Assert.assertEquals("expected the loop body to be empty of memory kills after write sinking", 0, memoryKills);
                        emptiedLoopHeaders.add(header);
                    }
                }
                Assert.assertFalse("expected at least one loop to become empty after write sinking", emptiedLoopHeaders.isEmpty());
            }
        };
    }

    private static BasePhase<? super LowTierContext> assertNoLoops() {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                Assert.assertFalse("expected write-only loop to be removed by low-tier cleanup", loops(graph).iterator().hasNext());
            }
        };
    }

    private static BasePhase<? super LowTierContext> rememberMergeNodes(EconomicSet<MergeNode> mergeNodes) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                for (MergeNode merge : graph.getNodes(MergeNode.TYPE)) {
                    mergeNodes.add(merge);
                }
            }
        };
    }

    private static BasePhase<? super LowTierContext> countConditionalWrites(CoverageCounters counters, int minimum) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                counters.conditionalWrites = graph.getNodes(ConditionalWriteNode.TYPE).count();
                Assert.assertTrue("expected at least " + minimum + " conditional writes, saw " + counters.conditionalWrites, counters.conditionalWrites >= minimum);
                for (ConditionalWriteNode node : graph.getNodes(ConditionalWriteNode.TYPE)) {
                    Assert.assertTrue(node.canNullCheck());
                    Assert.assertNotNull(node.getKilledLocationIdentity());
                }
            }
        };
    }

    private static BasePhase<? super LowTierContext> checkConditionalWritesLowered(EconomicSet<MergeNode> oldMergeNodes) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                Assert.assertEquals(0, graph.getNodes(ConditionalWriteNode.TYPE).count());
                boolean sawGeneratedDiamond = false;
                for (MergeNode merge : graph.getNodes(MergeNode.TYPE)) {
                    if (oldMergeNodes.contains(merge)) {
                        continue;
                    }
                    if (hasWritePredecessor(merge)) {
                        sawGeneratedDiamond = true;
                    }
                }
                Assert.assertTrue("expected conditional writes to lower into a write diamond", sawGeneratedDiamond);
            }
        };
    }

    private static BasePhase<? super LowTierContext> countMultiMemoryKills(CoverageCounters counters) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                for (Node node : graph.getNodes()) {
                    if (MemoryKill.isMultiMemoryKill(node)) {
                        counters.multiMemoryKills++;
                    }
                }
            }
        };
    }

    private static BasePhase<? super LowTierContext> checkMultiMemoryKillCandidate(CoverageCounters counters) {
        return new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                assumeTrue("System.arraycopy did not lower to a MultiMemoryKill on this checkout", counters.multiMemoryKills > 0);
                for (Node node : graph.getNodes()) {
                    if (node instanceof MultiMemoryKill multiMemoryKill) {
                        Assert.assertNotNull(multiMemoryKill.getKilledLocationIdentities());
                    }
                }
            }
        };
    }

    private static BasePhase<? super LowTierContext> scheduleGraph() {
        return new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS);
    }

    private static boolean hasWritePredecessor(MergeNode merge) {
        for (MemoryPhiNode memoryPhi : merge.memoryPhis()) {
            for (ValueNode value : memoryPhi.values()) {
                if (value instanceof WriteNode && branchContainsWrite(merge, (WriteNode) value)) {
                    return true;
                }
            }
        }
        for (EndNode end : merge.forwardEnds()) {
            if (branchContainsWrite(end)) {
                return true;
            }
        }
        return false;
    }

    private static boolean branchContainsWrite(MergeNode merge, WriteNode write) {
        for (EndNode end : merge.forwardEnds()) {
            if (branchContainsWrite(end, write)) {
                return true;
            }
        }
        return false;
    }

    private static boolean branchContainsWrite(Node endNode) {
        for (Node cur = endNode; cur != null && !(cur instanceof IfNode); cur = cur.predecessor()) {
            if (cur instanceof WriteNode) {
                return true;
            }
        }
        return false;
    }

    private static boolean branchContainsWrite(Node endNode, WriteNode writeNode) {
        for (Node cur = endNode; cur != null && !(cur instanceof IfNode); cur = cur.predecessor()) {
            if (cur == writeNode) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDeoptimizationWrite(WriteNode write) {
        return write.next() != null && write.next().getClass().getSimpleName().contains("Deopt");
    }

    private static int countLoopBodyWrites(CFGLoop<HIRBlock> loop) {
        int writes = 0;
        for (HIRBlock block : loop.getBlocks()) {
            if (block.getLoop() == loop) {
                for (FixedNode node : block.getNodes()) {
                    if (node instanceof WriteNode writeNode && !isDeoptimizationWrite(writeNode)) {
                        writes++;
                    }
                }
            }
        }
        return writes;
    }

    private static int countLoopBodyMemoryKills(CFGLoop<HIRBlock> loop) {
        int memoryKills = 0;
        for (HIRBlock block : loop.getBlocks()) {
            if (block.getLoop() == loop) {
                for (FixedNode node : block.getNodes()) {
                    if (MemoryKill.isMemoryKill(node) && (!(node instanceof WriteNode writeNode) || !isDeoptimizationWrite(writeNode))) {
                        memoryKills++;
                    }
                }
            }
        }
        return memoryKills;
    }

    private static Iterable<CFGLoop<HIRBlock>> loops(StructuredGraph graph) {
        return ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build().getLoops();
    }

    private static OptionValues withExcludedFields(String excludeFields) {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        map.put(WriteSinkingPhase.Options.WriteSinkingExcludeFields, excludeFields);
        return new OptionValues(map);
    }

    private static String excludeFields(Class<?> declaringClass, String... fields) {
        return Arrays.stream(fields).map(field -> declaringClass.getName() + "." + field).collect(Collectors.joining(","));
    }

    private static OptionValues withDetailsCSV(Path output, OptionValues additionalOptions) {
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create();
        map.put(WriteSinkingPhase.Options.WriteSinkingDetailsCSV, output.toString());
        return mergeOptions(new OptionValues(map), additionalOptions);
    }

    private static void assertCSVContains(String csv, String event, String category, String reason, String detailNote) {
        assertCSVContains(csv, event, category, reason);
        for (String line : csv.split("\n")) {
            String normalizedLine = line.replace("\"", "");
            if (normalizedLine.contains(";" + event + ";") &&
                            normalizedLine.contains(";" + category + ";") &&
                            normalizedLine.contains(";" + reason + ";") &&
                            normalizedLine.contains(detailNote)) {
                return;
            }
        }
        Assert.fail("expected row with detail note " + detailNote + " in CSV:\n" + csv);
    }

    private static void assertCSVContains(String csv, String event, String category, String reason) {
        Assert.assertTrue("expected event " + event + " in CSV:\n" + csv, csv.contains(";" + event + ";"));
        Assert.assertTrue("expected category " + category + " in CSV:\n" + csv, csv.contains(";" + category + ";"));
        Assert.assertTrue("expected reason " + reason + " in CSV:\n" + csv, csv.contains(";" + reason + ";"));
    }

    private static boolean hasRuntimeWriteSinkingDetailsSchema3IneligibleNames() {
        try {
            if (WriteSinkingPhase.Options.class.getDeclaredField("WriteSinkingDetailsCSV") == null) {
                return false;
            }
            var schemaVersion = WriteSinkingDiagnostics.class.getDeclaredField("DETAILS_SCHEMA_VERSION");
            schemaVersion.setAccessible(true);
            var noEligibleReason = WriteSinkingDiagnostics.class.getDeclaredMethod("noEligibleReason", WriteSinkingDiagnostics.WriteRejection.class);
            noEligibleReason.setAccessible(true);
            return schemaVersion.getInt(null) == 3 &&
                            "INELIGIBLE_DYNAMIC_OFFSET".equals(noEligibleReason.invoke(null, WriteSinkingDiagnostics.WriteRejection.NON_CONSTANT_OFFSET));
        } catch (LinkageError | ReflectiveOperationException e) {
            return false;
        }
    }

    private static OptionValues writeSinkingOptions() {
        OptionValues options = getInitialOptions();
        EconomicMap<OptionKey<?>, Object> map = EconomicMap.create(options.getMap());
        map.put(GraalOptions.PartialUnroll, false);
        map.put(GraalOptions.OptDuplication, false);
        map.put(DeDuplicationPhase.Options.OptDeDuplication, false);
        map.put(GraalOptions.LoopUnswitch, false);
        map.put(GraalOptions.LoopPeeling, false);
        map.put(LowTier.Options.OptWriteSinking, true);
        return new OptionValues(options, map);
    }

    private static OptionValues mergeOptions(OptionValues options, OptionValues additionalOptions) {
        if (additionalOptions == null) {
            return options;
        }
        return new OptionValues(options, additionalOptions.getMap());
    }

    private static final class CoverageCounters {
        int conditionalWrites;
        int multiMemoryKills;
    }

    private static final class TestLocationIdentity extends LocationIdentity {
        private final String name;

        private TestLocationIdentity(String name) {
            this.name = name;
        }

        @Override
        public boolean isImmutable() {
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TestLocationIdentity && name.equals(((TestLocationIdentity) obj).name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

class WriteSinkingCoverageBox {
    int a;
    int b;
    int c;
    int d;
    int e;
    int f;
}

class WriteSinkingVolatileBox {
    volatile int a;
}
