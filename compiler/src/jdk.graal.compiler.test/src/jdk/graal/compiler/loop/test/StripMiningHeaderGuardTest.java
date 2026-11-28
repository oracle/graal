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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.ProfileProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Tests strip mining of a head counted loop with a check in front of the limit test.
 *
 * <pre>
 * for (long i = start;; i++) {
 *     if (!check(i)) {
 *         deoptimize();
 *     }
 *     if (i &gt;= limit) {
 *         break;
 *     }
 *     body(i);
 * }
 * </pre>
 *
 * The check becomes a floating guard that is anchored in front of the loop (or at a fixed node of
 * the loop header in front of the limit test). Only its input {@code i} keeps it in the loop: it
 * is checked in the loop header in front of the limit test, also for the value of {@code i} the
 * loop is left with. Strip mining duplicates the loop, puts the original loop as the inner loop
 * behind the limit test of the duplicate and removes the guards of the duplicate. If the limit
 * test already fails on loop entry the inner loop is never entered and the check of the first
 * value of {@code i} is lost. The guard of the outer loop header has to stay.
 *
 * A hot loop with a {@code long} counter is strip mined with default options to remove its
 * safepoint, the other loops need {@code StripMineALot}.
 *
 * The decisive inputs run on the code of a compilation whose graph is checked: the loop is strip
 * mined and the outer loop still has the guard. Profiles are not used, so a deoptimization of an
 * earlier run does not change a later compilation.
 */
public class StripMiningHeaderGuardTest extends GraalCompilerTest {

    static long sink;
    private static volatile boolean invalidateWarmupOnce;

    /** Set for one compilation: anchor the header guards at a new node of the loop header. */
    private boolean anchorHeaderGuards;
    private boolean chainHeaderGuards;
    private boolean anchorAtLoopBegin;
    /** Recorded by the phases added to the mid tier, for the last compilation. */
    private int anchoredGuards;
    private int stripMinedLoops;
    private int outerLoopGuards;
    private boolean lastRunLostGuard;

    public static long remainderCheckSnippet(long start, long limit, long len) {
        long s = 0;
        long i = start;
        while (true) {
            if (i % 3 >= len) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1 - i;
            }
            if (i >= limit) {
                break;
            }
            s += i;
            GraalDirectives.sideEffect(s);
            i++;
        }
        return s * 31 + i;
    }

    /** Invalidates the first warm-up installation once, before the ordinary guard-preservation loop. */
    public static long warmupInvalidationSnippet(long start, long limit, long len) {
        if (invalidateWarmupOnce) {
            invalidateWarmupOnce = false;
            GraalDirectives.deoptimizeAndInvalidate();
        }
        return remainderCheckSnippet(start, limit, len);
    }

    /** The shape of the fuzzer finding: the check is the bounds check of an array access. */
    public static long boundsCheckSnippet(long start, long limit, long len) {
        int[] a = new int[(int) len];
        long s = 0;
        long i = start;
        while (true) {
            s += a[(int) (i % 3)];
            if (i >= limit) {
                break;
            }
            GraalDirectives.sideEffect(s);
            i++;
        }
        return s * 31 + i;
    }

    public static long intCounterSnippet(long start, long limit, long len) {
        long s = 0;
        int i = (int) start;
        while (true) {
            if ((i & 3) >= len) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1 - i;
            }
            if (i >= (int) limit) {
                break;
            }
            s += i;
            GraalDirectives.sideEffect(s);
            i++;
        }
        return s * 31 + i;
    }

    public static long downCountingSnippet(long start, long limit, long len) {
        long s = 0;
        long i = start;
        while (true) {
            if ((i & 3) >= len) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1 - i;
            }
            if (i <= limit) {
                break;
            }
            s += i;
            GraalDirectives.sideEffect(s);
            i--;
        }
        return s * 31 + i;
    }

    public static long strideTwoSnippet(long start, long limit, long len) {
        long s = 0;
        long i = start;
        while (true) {
            if (i % 5 >= len) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1 - i;
            }
            if (i >= limit) {
                break;
            }
            s += i;
            GraalDirectives.sideEffect(s);
            i += 2;
        }
        return s * 31 + i;
    }

    /** A hot loop that writes to memory: it is strip mined with default options. */
    public static long hotLoopSnippet(long start, long limit, long len) {
        long s = 0;
        long i = start;
        while (true) {
            if (i % 3 >= len) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1 - i;
            }
            if (!GraalDirectives.injectIterationCount(10000, i < limit)) {
                break;
            }
            s += i * 3;
            sink ^= s;
            i++;
        }
        return s * 31 + i;
    }

    /** As {@link #hotLoopSnippet} with a second check of another value. */
    public static long hotLoopTwoChecksSnippet(long start, long limit, long len) {
        long s = 0;
        long i = start;
        while (true) {
            if (i % 3 >= len) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1 - i;
            }
            if ((i & 7) == len + 4) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1000 - i;
            }
            if (!GraalDirectives.injectIterationCount(10000, i < limit)) {
                break;
            }
            s += i * 3;
            sink ^= s;
            i++;
        }
        return s * 31 + i;
    }

    /**
     * The check follows the limit test: its guard is anchored in the loop body and is not checked
     * for the value the loop is left with.
     */
    public static long checkInBodySnippet(long start, long limit, long len) {
        long s = 0;
        long i = start;
        while (true) {
            if (i >= limit) {
                break;
            }
            if (i % 3 >= len) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1 - i;
            }
            s += i;
            GraalDirectives.sideEffect(s);
            i++;
        }
        return s * 31 + i;
    }

    /** The header check prevents inversion; the body check must not run on an exiting visit. */
    public static long headerAndBodyChecksSnippet(long start, long limit, long len) {
        long s = 0;
        long i = start;
        while (true) {
            if (i % 3 >= len) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1 - i;
            }
            if (i >= limit) {
                break;
            }
            if ((i & 7) == len + 4) {
                GraalDirectives.deoptimizeAndInvalidate();
                return -1000 - i;
            }
            s += i;
            GraalDirectives.sideEffect(s);
            i++;
        }
        return s * 31 + i;
    }

    /**
     * The check is the bounds check of an array read that is not needed: the array is new and the
     * loop does not write to it, so the read is replaced by 0 and its guard has no usage.
     */
    public static long unusedReadSnippet(long start, long limit, long len) {
        int[] a = new int[(int) len];
        long s = 0;
        long i = start;
        while (true) {
            s += a[(int) (i % 3)];
            if (i >= limit) {
                break;
            }
            s += i;
            sink = s;
            i++;
        }
        return s * 31 + i;
    }

    /**
     * Moves header guards to one shared {@link ValueAnchorNode}, or a chain with one anchor per
     * guard, between the loop begin and the limit test. Counted loop detection skips these nodes,
     * so the loop stays head counted. Separate anchors exercise guards before the last header node.
     * A separate control anchors the guards directly at the loop begin.
     */
    private final class AnchorHeaderGuardsPhase extends BasePhase<MidTierContext> {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, MidTierContext context) {
            if (!anchorHeaderGuards) {
                return;
            }
            LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
            for (Loop loop : loops.loops()) {
                LoopBeginNode loopBegin = loop.loopBegin();
                if (!(loopBegin.next() instanceof IfNode)) {
                    continue;
                }
                List<GuardNode> guards = new ArrayList<>();
                for (GuardNode guard : loop.whole().nodes().filter(GuardNode.class)) {
                    Node anchor = guard.getAnchor().asNode();
                    if (anchor == loopBegin || loop.isOutsideLoop(anchor)) {
                        guards.add(guard);
                    }
                }
                if (!guards.isEmpty()) {
                    if (anchorAtLoopBegin) {
                        for (GuardNode guard : guards) {
                            guard.setAnchor(loopBegin);
                            anchoredGuards++;
                        }
                        continue;
                    }
                    ValueAnchorNode headerAnchor = graph.add(new ValueAnchorNode());
                    graph.addAfterFixed(loopBegin, headerAnchor);
                    for (int index = 0; index < guards.size(); index++) {
                        if (chainHeaderGuards && index > 0) {
                            ValueAnchorNode nextAnchor = graph.add(new ValueAnchorNode());
                            graph.addAfterFixed(headerAnchor, nextAnchor);
                            headerAnchor = nextAnchor;
                        }
                        guards.get(index).setAnchor(headerAnchor);
                        anchoredGuards++;
                    }
                }
            }
        }
    }

    /** Counts the strip mined loops and the guards of the outer loops after strip mining. */
    private final class RecordOuterLoopGuardsPhase extends BasePhase<MidTierContext> {
        @Override
        public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
            return ALWAYS_APPLICABLE;
        }

        @Override
        protected void run(StructuredGraph graph, MidTierContext context) {
            LoopsData loops = context.getLoopsDataProvider().getLoopsData(graph);
            for (Loop outer : loops.loops()) {
                if (!outer.loopBegin().isAnyStripMinedOuter()) {
                    continue;
                }
                stripMinedLoops++;
                for (GuardNode guard : outer.whole().nodes().filter(GuardNode.class)) {
                    boolean inInnerLoop = false;
                    for (Loop inner : loops.loops()) {
                        if (inner != outer && inner.loopBegin().isAnyStripMinedInner() && inner.whole().contains(guard)) {
                            inInnerLoop = true;
                        }
                    }
                    if (!inInnerLoop) {
                        outerLoopGuards++;
                    }
                }
            }
        }
    }

    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites suites = super.createSuites(opts).copy();
        ListIterator<BasePhase<? super MidTierContext>> position = suites.getMidTier().findPhase(CountedStripMiningPhase.class);
        Assert.assertNotNull("no counted strip mining in the mid tier", position);
        position.add(new RecordOuterLoopGuardsPhase());
        position.previous();
        position.previous();
        position.add(new AnchorHeaderGuardsPhase());
        return suites;
    }

    /** No profiles: every compilation of a snippet with the same options sees the same input. */
    @Override
    protected ProfileProvider getProfileProvider(ResolvedJavaMethod method) {
        return NO_PROFILE_PROVIDER;
    }

    OptionValues stripMineALotOptions() {
        return new OptionValues(getInitialOptions(), CountedStripMiningPhase.Options.StripMineALot, true);
    }

    OptionValues highTierInversionStripMineALotOptions() {
        return new OptionValues(stripMineALotOptions(), LoopInversionPhase.Options.HighTierInversion, true);
    }

    /**
     * Compiles the snippet, checks the graph of this compilation (the loop is strip mined, the
     * outer loop has a guard) and runs exactly this code with the given arguments. All snippets
     * return for the arguments (3, 3, 5) and (7, 2, 5) without a failing check.
     *
     * @param anchored the header guards are anchored at a node of the loop header
     * @param outerGuard the outer loop must have a guard that is not in the inner loop
     * @return the result, or the exception the call ended with
     */
    private Object runChecked(String snippet, OptionValues options, boolean anchored, boolean outerGuard, long start, long limit, long len) {
        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        InstalledCode code = null;
        /*
         * The first compilations can contain speculative guards that fail for these short loops.
         * Compile until the code survives runs that leave the loop at once and pass the check,
         * so that the decisive run does not end in the interpreter for another reason.
         */
        for (int round = 0; round < 4; round++) {
            StructuredGraph graph = parseForCompile(method, options);
            anchoredGuards = 0;
            stripMinedLoops = 0;
            outerLoopGuards = 0;
            anchorHeaderGuards = anchored;
            try {
                code = getCode(method, graph, true, false, options);
            } finally {
                anchorHeaderGuards = false;
            }
            try {
                code.executeVarargs(3L, 3L, 5L);
                if (!code.isValid()) {
                    continue;
                }
                code.executeVarargs(7L, 2L, 5L);
            } catch (Throwable t) {
                throw new AssertionError(t);
            }
            if (code.isValid()) {
                break;
            }
        }
        Assert.assertTrue("the code of " + snippet + " must be valid before the decisive run", code.isValid());
        Assert.assertTrue("the loop of " + snippet + " must be strip mined", stripMinedLoops > 0);
        if (anchored) {
            Assert.assertTrue("no header guard to anchor in " + snippet, anchoredGuards > 0);
        }
        Object result;
        try {
            result = code.executeVarargs(start, limit, len);
        } catch (Throwable t) {
            result = t;
        }
        lastRunLostGuard = outerGuard && outerLoopGuards == 0;
        return result;
    }

    private void assertResult(long expected, String snippet, OptionValues options, boolean anchored, long start, long limit, long len) {
        Object result = runChecked(snippet, options, anchored, true, start, limit, len);
        String call = snippet + "(" + start + ", " + limit + ", " + len + ")";
        Assert.assertEquals(call + (lastRunLostGuard ? " (the outer loop lost the guard of the loop header)" : ""), expected, result);
        Assert.assertFalse(call + ": the outer loop lost the guard of the loop header", lastRunLostGuard);
    }

    /**
     * Arguments {@code start, limit, len}. The first ones pass all checks; then the check fails in
     * a later iteration, in the header visit that leaves the loop and on loop entry with a limit
     * test that fails at once.
     */
    static final long[][] ARGS = {{0, 10, 5}, {0, 5000, 5}, {3, 3, 5}, {7, 2, 5}, {0, 10, 2}, {0, 1, 1}, {0, 2, 2}, {1, 1, 1}, {4, 2, 1}, {2, 0, 2}, {7, 7, 1}, {5, -3, 0}, {0, 0, 0}};

    /** Compares compiled code and interpreter for all of {@link #ARGS}. */
    private void testArgs(String snippet, OptionValues options) {
        for (long[] args : ARGS) {
            test(options, snippet, args[0], args[1], args[2]);
        }
    }

    /** As {@link #testArgs} for a counter that counts down to {@code limit}. */
    private void testArgsDown(String snippet, OptionValues options) {
        for (long[] args : ARGS) {
            test(options, snippet, args[1], args[0], args[2]);
        }
    }

    /**
     * The decisive inputs: the check fails in the header visit that leaves the loop, which is the
     * second one (the first one of the loop that is left when the first iteration is peeled), the
     * first one (the limit test fails on loop entry) or the third one. {@code -1 - i} is the result of the
     * deoptimized call. The last input passes the check.
     */
    private void assertLeavingVisit(String snippet, OptionValues options, boolean anchored) {
        assertResult(-2, snippet, options, anchored, 0, 1, 1);
        assertResult(-2, snippet, options, anchored, 1, 1, 1);
        assertResult(-3, snippet, options, anchored, 0, 2, 2);
        assertResult(-5, snippet, options, anchored, 4, 2, 1);
        assertResult(-8, snippet, options, anchored, 7, 7, 1);
        assertResult(3, snippet, options, anchored, 3, 3, 5);
    }

    @Test
    public void testRemainderCheck() {
        assertLeavingVisit("remainderCheckSnippet", stripMineALotOptions(), false);
        testArgs("remainderCheckSnippet", stripMineALotOptions());
    }

    @Test
    public void testRetryAfterWarmupInvalidation() {
        invalidateWarmupOnce = true;
        try {
            assertResult(-2, "warmupInvalidationSnippet", stripMineALotOptions(), false, 0, 1, 1);
            Assert.assertFalse("the first warm-up must trigger invalidation", invalidateWarmupOnce);
        } finally {
            invalidateWarmupOnce = false;
        }
    }

    /** The guard is anchored at a {@link ValueAnchorNode} between loop begin and limit test. */
    @Test
    public void testRemainderCheckHeaderAnchor() {
        assertLeavingVisit("remainderCheckSnippet", stripMineALotOptions(), true);
    }

    @Test
    public void testIntCounter() {
        assertResult(-2, "intCounterSnippet", stripMineALotOptions(), false, 1, 1, 1);
        assertResult(-2, "intCounterSnippet", stripMineALotOptions(), false, 0, 1, 1);
        assertResult(-4, "intCounterSnippet", stripMineALotOptions(), false, 3, 3, 2);
        testArgs("intCounterSnippet", stripMineALotOptions());
    }

    @Test
    public void testStrideTwo() {
        assertResult(-2, "strideTwoSnippet", stripMineALotOptions(), false, 1, 1, 1);
        assertResult(-3, "strideTwoSnippet", stripMineALotOptions(), false, 0, 2, 2);
        assertResult(-3, "strideTwoSnippet", stripMineALotOptions(), false, 2, 0, 2);
        testArgs("strideTwoSnippet", stripMineALotOptions());
    }

    @Test
    public void testHotLoopDefault() {
        assertLeavingVisit("hotLoopSnippet", getInitialOptions(), false);
        testArgs("hotLoopSnippet", getInitialOptions());
    }

    @Test
    public void testHotLoopHeaderAnchorDefault() {
        assertLeavingVisit("hotLoopSnippet", getInitialOptions(), true);
    }

    @Test
    public void testHotLoopHeaderAnchorChainDefault() {
        chainHeaderGuards = true;
        try {
            assertLeavingVisit("hotLoopTwoChecksSnippet", getInitialOptions(), true);
            assertResult(-1006, "hotLoopTwoChecksSnippet", getInitialOptions(), true, 6, 6, 2);
            Assert.assertTrue("expected separate header guards", anchoredGuards > 1);
        } finally {
            chainHeaderGuards = false;
        }
    }

    @Test
    public void testRemainderCheckLoopBeginAnchor() {
        anchorAtLoopBegin = true;
        try {
            assertLeavingVisit("remainderCheckSnippet", stripMineALotOptions(), true);
        } finally {
            anchorAtLoopBegin = false;
        }
    }

    @Test
    public void testHeaderAndBodyChecks() {
        assertLeavingVisit("headerAndBodyChecksSnippet", stripMineALotOptions(), false);
        assertResult(6, "headerAndBodyChecksSnippet", stripMineALotOptions(), false, 6, 6, 2);
        assertResult(-1006, "headerAndBodyChecksSnippet", stripMineALotOptions(), false, 6, 7, 2);
        testArgs("headerAndBodyChecksSnippet", stripMineALotOptions());
    }

    @Test
    public void testHotLoopTwoChecksDefault() {
        assertLeavingVisit("hotLoopTwoChecksSnippet", getInitialOptions(), false);
        // only the second check fails: (start & 7) == len + 4
        assertResult(-1006, "hotLoopTwoChecksSnippet", getInitialOptions(), false, 6, 6, 2);
        testArgs("hotLoopTwoChecksSnippet", getInitialOptions());
    }

    /**
     * The bounds check guards a read that is replaced by 0. The interpreter throws an
     * {@link ArrayIndexOutOfBoundsException} for index 1 of an array of length 1.
     */
    @Test
    public void testUnusedRead() {
        for (long start = 0; start <= 1; start++) {
            Object result = runChecked("unusedReadSnippet", stripMineALotOptions(), false, false, start, 1, 1);
            Assert.assertTrue("unusedReadSnippet(" + start + ", 1, 1): " + result, result instanceof ArrayIndexOutOfBoundsException);
        }
        testArgs("unusedReadSnippet", stripMineALotOptions());
    }

    /** The guard of the bounds check is used by the read: it stays in the outer loop anyway. */
    @Test
    public void testBoundsCheck() {
        for (long start = 0; start <= 1; start++) {
            Object result = runChecked("boundsCheckSnippet", stripMineALotOptions(), false, false, start, 1, 1);
            Assert.assertTrue("boundsCheckSnippet(" + start + ", 1, 1): " + result, result instanceof ArrayIndexOutOfBoundsException);
        }
        testArgs("boundsCheckSnippet", stripMineALotOptions());
    }

    @Test
    public void testDownCounting() {
        testArgsDown("downCountingSnippet", stripMineALotOptions());
    }

    @Test
    public void testCheckInBody() {
        testArgs("checkInBodySnippet", stripMineALotOptions());
    }

    @Test
    public void testOutsideAnchorWithInvariantCondition() throws ReflectiveOperationException {
        StructuredGraph graph = parseEager("checkInBodySnippet", AllowAssumptions.YES);
        Loop loop = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph).loops().get(0);
        Assert.assertTrue("expected a head-counted loop", loop.detectCounted() && !loop.counted().isInverted());
        ConstantNode bound = ConstantNode.forLong(42, graph);
        LogicNode invariantCondition = graph.addOrUnique(new IntegerLessThanNode(graph.getParameter(2), bound));
        LogicNode dependentCondition = graph.addOrUnique(new IntegerLessThanNode(loop.counted().getLimitCheckedIV().valueNode(), bound));
        GuardNode invariantGuard = graph.addOrUnique(new GuardNode(invariantCondition, graph.start(), DeoptimizationReason.TransferToInterpreter,
                        DeoptimizationAction.None, false, SpeculationLog.NO_SPECULATION, null));
        GuardNode dependentGuard = graph.addOrUnique(new GuardNode(dependentCondition, graph.start(), DeoptimizationReason.TransferToInterpreter,
                        DeoptimizationAction.None, false, SpeculationLog.NO_SPECULATION, null));
        // Refresh loop membership after adding the guards and their conditions.
        loop = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph).loops().get(0);
        Assert.assertTrue("both guards have an outside anchor", loop.isOutsideLoop(graph.start()));
        Assert.assertTrue("parameter-based condition must be outside the loop", loop.isOutsideLoop(invariantCondition));
        Assert.assertFalse("IV-based condition must be inside the loop", loop.isOutsideLoop(dependentCondition));

        Method classify = CountedStripMiningPhase.class.getDeclaredMethod("mayExecuteBeforeLimitTest", Loop.class, GuardNode.class);
        classify.setAccessible(true);
        Assert.assertEquals("retain guards whose condition depends on the loop", Boolean.TRUE, classify.invoke(null, loop, dependentGuard));
        Assert.assertEquals("do not retain an outer duplicate of a loop-invariant guard with an outside anchor", Boolean.FALSE, classify.invoke(null, loop, invariantGuard));
    }

    /**
     * The options of the fuzzer finding. High tier loop inversion does not invert these loops (the
     * check in the loop header is in its way), they are strip mined as head counted loops.
     */
    @Test
    public void testStripMineALotWithHighTierInversionOption() {
        assertLeavingVisit("remainderCheckSnippet", highTierInversionStripMineALotOptions(), false);
        testArgs("remainderCheckSnippet", highTierInversionStripMineALotOptions());
        testArgs("boundsCheckSnippet", highTierInversionStripMineALotOptions());
    }

}
