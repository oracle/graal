/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationReason;

/** Tests speculative guard movement, including guard placement and endpoint-safety cases. */
public class SpeculativeGuardMovementTest extends GraalCompilerTest {

    /** Verify that guards were hoisted out of the loop. */
    private boolean verifyGuardMovement;
    /** {@link DeoptimizationReason} of the guards that should be moved out of the loop. */
    private DeoptimizationReason verifyGuardMovementDeoptReason;

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        if (verifyGuardMovement) {
            addGuardMovementVerificationPhase(suites, verifyGuardMovementDeoptReason);
        }
        return suites;
    }

    /**
     * Adds a phase that verifies guards with the given reason are moved before guard lowering.
     *
     * @param suites compilation suites to augment
     * @param reason deoptimization reason of the guards to verify
     */
    public static void addGuardMovementVerificationPhase(Suites suites, DeoptimizationReason reason) {
        suites.getMidTier().insertBeforePhase(GuardLoweringPhase.class, new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                assertTransferGuardsHoisted(graph, reason);
            }
        });
    }

    /** Verifies that a guard with the given reason was hoisted out of the innermost loop. */
    private static void assertTransferGuardsHoisted(StructuredGraph graph, DeoptimizationReason reason) {
        ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(graph);
        Assert.assertTrue("Loop expected in graph for " + graph.method(), cfg.getLoops().size() > 0);
        int maxLoopDepth = cfg.getLoops().stream().mapToInt(CFGLoop::getDepth).max().orElseThrow();
        List<GuardNode> transferGuards = graph.getNodes(GuardNode.TYPE).stream().filter(n -> n.getReason() == reason).toList();
        Assert.assertFalse("Expected a " + reason + " guard for " + graph.method(), transferGuards.isEmpty());
        boolean hasHoistedTransferGuard = false;
        for (GuardNode guard : transferGuards) {
            HIRBlock anchorBlock = cfg.getNodeToBlock().get(guard.getAnchor().asNode());
            if (anchorBlock.getLoopDepth() < maxLoopDepth) {
                hasHoistedTransferGuard = true;
            }
        }
        Assert.assertTrue(reason + "guard must be hoisted out of the innermost loop", hasHoistedTransferGuard);
    }

    private OptionValues guardMovementOptions() {
        return new OptionValues(getInitialOptions(),
                        GraalOptions.LoopPredication, false,
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopUnswitch, false);
    }

    private InstalledCode getGuardMovementCode(String methodName, DeoptimizationReason reason) {
        verifyGuardMovement = true;
        verifyGuardMovementDeoptReason = reason;
        return getCode(getResolvedJavaMethod(methodName), null, true, false, guardMovementOptions());
    }

    public static void snippet01(int init, int limit, int offset) {
        for (int i = init; GraalDirectives.injectIterationCount(1000, i < limit); i++) {
            if (Integer.compareUnsigned(i + offset, Integer.MIN_VALUE + 5) > 0) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                throw new IndexOutOfBoundsException();
            }
        }
    }

    @Test
    public void testOverflowUnsignedGuard() {
        int init = 0;
        int limit = 10;
        int offset = Integer.MAX_VALUE;
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.PartialUnroll, false);
        InstalledCode code = getCode(getResolvedJavaMethod("snippet01"), opt);
        assertTrue(code.isValid());
        try {
            code.executeVarargs(init, limit, offset);
            throw new RuntimeException("should have thrown");
        } catch (Throwable e) {
            if (!(e instanceof IndexOutOfBoundsException)) {
                throw new RuntimeException("unexpected exception " + e);
            }
        }
    }

    public static int snippetOverflowInt(int min, int max, int[] arr) {
        if (arr == null) {
            return 0;
        }
        int counter = 0;
        int i;
        int result = 0;
        for (i = min; GraalDirectives.injectIterationCount(1000, i <= max); i++) {
            counter++;
            if (counter >= 3) {
                result += arr[i];
                if (counter == 1222) {
                    GraalDirectives.controlFlowAnchor();
                    continue;
                }
                return -1;
            }
            GraalDirectives.neverStripMine();
            GraalDirectives.neverWriteSink();
        }
        return counter + result;
    }

    @Test
    public void testOverflow() {
        final int min = Byte.MAX_VALUE - 5;
        final int max = Byte.MAX_VALUE;
        int[] arr = new int[1000];
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false, GraalOptions.LoopUnswitch, false);
        test(opt, "snippetOverflowInt", min, max, arr);
    }

    public static void snippetInstanceOf(int bound, A a) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < bound; i++) {
            GraalDirectives.sideEffect();
            if (GraalDirectives.injectBranchProbability(0.0000000001, i > 12312)) {
                Object o = optAway(a);
                if (o instanceof B) {
                    GraalDirectives.controlFlowAnchor();
                    GraalDirectives.blackhole(o);
                }
            }
        }
    }

    public static void snippetInstanceOfHoisted(int bound, A a) {
        if (a == null) {
            return;
        }
        for (int i = 0; i < bound; i++) {
            GraalDirectives.sideEffect();
            if (GraalDirectives.injectBranchProbability(0.01, i > 12312)) {
                Object o = optAway(a);
                if (o instanceof B) {
                    GraalDirectives.controlFlowAnchor();
                    GraalDirectives.blackhole(o);
                }
            }
        }
    }

    static class A {

    }

    static class B extends A {

    }

    static class B1 extends B {

    }

    static class C extends A {

    }

    @BytecodeParserNeverInline
    static Object optAway(Object o) {
        return o;
    }

    @Test
    public void testInstanceOf() {
        // >15k iterations needed to get java profiles
        for (int i = 0; i < 15000; i++) {
            snippetInstanceOf(i, new B1());
            snippetInstanceOfHoisted(i, new B1());
        }
        assertTrue(instanceOfGuardPresent("snippetInstanceOf"), "Unexpected graph after parsing! InstanceOf guard expected.");
        assertTrue(instanceOfGuardPresent("snippetInstanceOfHoisted"), "Unexpected graph after parsing! InstanceOf guard expected.");

        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false, GraalOptions.LoopUnswitch, false);

        test(opt, "snippetInstanceOf", 0, new C());
        int deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.TypeCheckedInliningViolated, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 1, String.format("Guard should still be inside loop. Actual loop depth: %s", deoptLoopDepth));

        test(opt, "snippetInstanceOfHoisted", 0, new C());
        deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.TypeCheckedInliningViolated, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 0, String.format("Guard should be hoisted from loop. Actual loop depth: %s", deoptLoopDepth));
    }

    private boolean instanceOfGuardPresent(String toParse) {
        StructuredGraph g = parseEager(toParse, AllowAssumptions.YES);
        assertTrue(g.getNodes().filter(InstanceOfNode.class).count() == 1, "Unexpected graph after parsing! Single InstanceOfNode expected.");

        InstanceOfNode iOf = g.getNodes().filter(InstanceOfNode.class).first();
        assertTrue(iOf.profile() != null, "InstanceOfNode needs a profile to create a guard!");
        return iOf.usages().count() == 1 && iOf.usages().first() instanceof FixedGuardNode;
    }

    public static int findDeoptLoopDepth(DeoptimizationReason reason, StructuredGraph g) {
        ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(g);
        assertTrue(cfg.getLoops().size() > 0, "Loop(s) in graph expected!");

        List<DeoptimizeNode> deopts = g.getNodes(DeoptimizeNode.TYPE).stream().filter(n -> n.getReason().equals(reason)).toList();
        assertTrue(deopts.size() == 1, String.format("Exactly one DeoptimizeNode with reason %s in graph expected!", reason));

        CFGLoop<HIRBlock> loop = cfg.getNodeToBlock().get(deopts.get(0)).getFirstPredecessor().getLoop();
        return loop == null ? 0 : loop.getDepth();
    }

    public static void snippetInstanceOfLoopNest(int bound, A[] a) {
        if (a == null) {
            return;
        }
        for (int j = 0; j < 50; j++) {
            if (j >= a.length) {
                return;
            }
            A a1 = a[j];
            if (a1 == null) {
                return;
            }
            GraalDirectives.blackhole(a1);
            // 1) instanceof could be moved here but fq ~= 50
            for (int k = 0; k < 50; k++) {
                if (GraalDirectives.injectBranchProbability(0.01, bound > 14500)) {
                    // 2) fq ~= 2.5 --> instanceof is moved here
                    for (int i = 0; GraalDirectives.injectIterationCount(100000, i < bound); i++) {
                        if (GraalDirectives.injectBranchProbability(0.01, i > 12312)) {
                            if (a1 instanceof B) {
                                GraalDirectives.controlFlowAnchor();
                                GraalDirectives.blackhole(a1);
                            }
                        }
                    }
                }
            }
        }
    }

    public static void snippetInstanceOfHoistedLoopNest(int bound, A a) {
        if (a == null) {
            return;
        }
        // 3) instanceof should be moved here (fq ~= 1)
        for (int j = 0; j < 50; j++) {
            // 2) ignores moving here (fq ~= 50)
            for (int k = 0; k < 50; k++) {
                // fq ~= 2500
                if (GraalDirectives.injectBranchProbability(0.01, bound > 14500)) {
                    // 1) could move instanceof here (fq ~= 25)
                    for (int i = 0; GraalDirectives.injectIterationCount(100000, i < bound); i++) {
                        if (GraalDirectives.injectBranchProbability(0.01, i > 12312)) {
                            if (a instanceof B) {
                                GraalDirectives.controlFlowAnchor();
                                GraalDirectives.blackhole(a);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testInstanceOfLoopNest() {
        // >15k iterations needed to get java profiles
        for (int i = 0; i < 15000; i++) {
            snippetInstanceOfLoopNest(i, bArray());
            snippetInstanceOfHoistedLoopNest(i, new B1());
        }
        assertTrue(instanceOfGuardPresent("snippetInstanceOfLoopNest"), "Unexpected graph after parsing! InstanceOf guard expected.");
        assertTrue(instanceOfGuardPresent("snippetInstanceOfHoistedLoopNest"), "Unexpected graph after parsing! InstanceOf guard expected.");

        OptionValues opt = getInitialOptions();

        test(opt, "snippetInstanceOfLoopNest", 0, null);
        int deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.TypeCheckedInliningViolated, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 2, String.format("InstanceOf should have just be hoisted from innermost loop. Actual loop depth: %s", deoptLoopDepth));

        test(opt, "snippetInstanceOfHoistedLoopNest", 0, null);
        deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.TypeCheckedInliningViolated, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 0, String.format("InstanceOf should be hoisted above all loop. Actual loop depth: %s", deoptLoopDepth));
    }

    private static B1[] bArray() {
        B1[] a = new B1[100];
        for (int i = 0; i < 100; i++) {
            a[i] = new B1();
        }
        return a;
    }

    public static void snippetCompare(int bound, int[] a) {
        if (a == null) {
            return;
        }
        for (int i = 0; GraalDirectives.injectIterationCount(100000, i < bound); i++) {
            GraalDirectives.sideEffect();
            GraalDirectives.controlFlowAnchor();
            if (GraalDirectives.injectBranchProbability(0.0000000001, i > 12312)) {
                int[] o = optAway(a);
                if (a[i] == 42) {
                    GraalDirectives.blackhole(o);
                }
            }
        }
    }

    public static void snippetCompareHoisted(int bound, int[] a) {
        if (a == null) {
            return;
        }
        for (int i = 0; GraalDirectives.injectIterationCount(100000, i < bound); i++) {
            GraalDirectives.sideEffect();
            GraalDirectives.controlFlowAnchor();
            if (GraalDirectives.injectBranchProbability(0.01, i > 12312)) {
                int[] o = optAway(a);
                if (a[i] == 42) {
                    GraalDirectives.blackhole(o);
                }
            }
        }
    }

    @BytecodeParserNeverInline
    static int[] optAway(int[] o) {
        return o;
    }

    @Test
    public void testCompare() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.PartialUnroll, false, GraalOptions.LoopUnswitch, false);

        test(opt, "snippetCompare", 0, new int[0]);
        int deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.BoundsCheckException, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 1, String.format("Guard should still be inside loop. Actual loop depth: %s", deoptLoopDepth));

        test(opt, "snippetCompareHoisted", 0, new int[0]);
        deoptLoopDepth = findDeoptLoopDepth(DeoptimizationReason.BoundsCheckException, lastCompiledGraph);
        assertTrue(deoptLoopDepth == 0, String.format("Guard should be hoisted from loop. Actual loop depth: %s", deoptLoopDepth));
    }

    public static int unsignedCompareAcrossSignBoundarySnippet(int start, int count) {
        int sum = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < count); i++) {
            int value = start - i;
            if (GraalDirectives.injectBranchProbability(0.001, Integer.compareUnsigned(value, 5) < 0)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += value;
        }
        return sum;
    }

    @Test
    public void testUnsignedCompareAcrossSignBoundary() throws Exception {
        InstalledCode code = getGuardMovementCode("unsignedCompareAcrossSignBoundarySnippet", DeoptimizationReason.TransferToInterpreter);
        Object result0 = code.executeVarargs(10, 0);
        Assert.assertEquals("Zero-trip loop should not execute the guarded body", 0, result0);
        Assert.assertTrue("Zero-trip execution must leave the compiled code valid", code.isValid());
        Object result = code.executeVarargs(10, 20);
        Assert.assertEquals("Unsigned compare should have become true during the loop", -1, result);
    }

    public static int unsignedCompareWithinNegativeRangeSnippet(int start, int limit, int bound) {
        int sum = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i > limit); i--) {
            if (GraalDirectives.injectBranchProbability(0.001, Integer.compareUnsigned(i, bound) < 0)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += i;
        }
        return sum;
    }

    @Test
    public void testUnsignedCompareWithinNegativeRange() throws Exception {
        InstalledCode code = getGuardMovementCode("unsignedCompareWithinNegativeRangeSnippet", DeoptimizationReason.TransferToInterpreter);
        Assert.assertTrue(code.isValid());
        Object result = code.executeVarargs(-2, -11, -5);
        Assert.assertEquals("Unsigned ordering within the negative int range must be preserved", -1, result);
    }

    public static int mirroredNegatedUnsignedCompareWithinNegativeRangeSnippet(int start, int limit, int bound) {
        int sum = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i > limit); i--) {
            if (GraalDirectives.injectBranchProbability(0.001, Integer.compareUnsigned(bound, i) >= 0)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += i;
        }
        return sum;
    }

    @Test
    public void testMirroredNegatedUnsignedCompareWithinNegativeRange() throws Exception {
        InstalledCode code = getGuardMovementCode("mirroredNegatedUnsignedCompareWithinNegativeRangeSnippet", DeoptimizationReason.TransferToInterpreter);
        Object result = code.executeVarargs(-2, -11, -5);
        Assert.assertEquals("Mirrored and negated unsigned ordering within the negative int range must be preserved", -1, result);
    }

    public static int signedCompareAcrossUnsignedBoundarySnippet(int start, int count) {
        int sum = 0;
        int limit = start + count;
        for (int i = start; GraalDirectives.injectIterationCount(1000, Integer.compareUnsigned(i, limit) < 0); i++) {
            if (GraalDirectives.injectBranchProbability(0.001, i < Integer.MIN_VALUE + 8)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += i;
        }
        return sum;
    }

    @Test
    public void testSignedCompareAcrossUnsignedBoundary() throws Exception {
        InstalledCode code = getCode(getResolvedJavaMethod("signedCompareAcrossUnsignedBoundarySnippet"), null, false, false, guardMovementOptions());
        Object result = code.executeVarargs(Integer.MAX_VALUE - 10, 20);
        Assert.assertEquals("Signed compare should have become true during the unsigned loop", -1, result);
    }

    public static int derivedLimitCheckedIVInitialOverflowSnippet(int start, int offset, int loopLimit, int guardBound) {
        int sum = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i + offset < loopLimit); i++) {
            // i is the limit checked IV but its extremum can overflow
            int value = i + offset;
            if (GraalDirectives.injectBranchProbability(0.001, value >= guardBound)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += value;
        }
        return sum;
    }

    @Test
    public void testDerivedLimitCheckedIVInitialOverflow() throws Exception {
        InstalledCode code = getGuardMovementCode("derivedLimitCheckedIVInitialOverflowSnippet", DeoptimizationReason.TransferToInterpreter);
        /* The overflowing offset maps i = -10..-2 to value = MAX_VALUE-9..MAX_VALUE-1.
         * The inner comparison is initially false and becomes true at MAX_VALUE-5.
         */
        Object result = code.executeVarargs(-10, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE - 5);
        Assert.assertEquals("An overflowing derived limit-checked IV must not hide a later comparison failure", -1, result);
    }

    public static int signedOffsetOverflowAtInitialValueSnippet(int start, int limit) {
        int sum = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i < limit); i++) {
            int value = i - 1;
            if (GraalDirectives.injectBranchProbability(0.001, value < Integer.MIN_VALUE + 4)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += value;
        }
        return sum;
    }

    @Test
    public void testSignedOffsetOverflowAtInitialValue() throws Exception {
        InstalledCode code = getGuardMovementCode("signedOffsetOverflowAtInitialValueSnippet", DeoptimizationReason.TransferToInterpreter);
        Object result = code.executeVarargs(Integer.MIN_VALUE, Integer.MIN_VALUE + 10);
        Assert.assertEquals("Signed derived offset should overflow at the initial loop value", -1, result);
    }

    public static int runtimeSubtractedOffsetOverflowAtExtremumSnippet(int offset, int limit, int bound) {
        int sum = 0;
        for (int i = -2; GraalDirectives.injectIterationCount(1000, i < limit); i++) {
            int value = i - offset;
            if (GraalDirectives.injectBranchProbability(0.001, value >= bound)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += value;
        }
        return sum;
    }

    @Test
    public void testRuntimeSubtractedOffsetOverflowAtExtremum() throws Exception {
        InstalledCode code = getGuardMovementCode("runtimeSubtractedOffsetOverflowAtExtremumSnippet", DeoptimizationReason.TransferToInterpreter);
        Assert.assertEquals("Safe runtime offset must not trigger the overflow guard", -3, code.executeVarargs(0, 1, Integer.MAX_VALUE));
        Assert.assertTrue("Safe runtime offset must leave the compiled code valid", code.isValid());
        Object result = code.executeVarargs(Integer.MIN_VALUE, 1, Integer.MAX_VALUE);
        Assert.assertEquals("Overflow at the maximum base value must not hide the middle comparison failure", -1, result);
    }

    public static int hugeTripCountSnippet(long limit, long earlyExit, int bound) {
        int value = 0;
        for (long i = 0; GraalDirectives.injectIterationCount(1000, i < limit); i++, value++) {
            if (GraalDirectives.injectBranchProbability(0.001, Integer.compareUnsigned(value, bound) < 0)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            /* Keep execution short despite the large loop limit. */
            if (i == earlyExit) {
                return 7;
            }
        }
        return 0;
    }

    @Test
    public void testHugeTripCount() throws Exception {
        InstalledCode code = getGuardMovementCode("hugeTripCountSnippet", DeoptimizationReason.TransferToInterpreter);
        Assert.assertTrue(code.isValid());
        /* limit - 1 is 2^32. The original guard is false for bound == 0, and execution exits
         * during the first iteration. */
        Object result = code.executeVarargs(0x1_0000_0001L, 0L, 0);
        Assert.assertEquals("A >32bit trip count must not be wrapped", 7, result);
        Assert.assertFalse("The huge trip count guard must have deoptimized", code.isValid());
    }

    public static int hugeUnsignedTripCountSnippet(long start, long end, long stop) {
        int value = 0;
        for (long i = start; GraalDirectives.injectIterationCount(1000, i < end); i++) {
            if (GraalDirectives.injectBranchProbability(0.001, value > 0)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            if (i == stop) {
                return 42;
            }
            value++;
        }
        return 0;
    }

    @Test
    public void testHugeUnsignedTripCount() throws Exception {
        InstalledCode code = getGuardMovementCode("hugeUnsignedTripCountSnippet", DeoptimizationReason.TransferToInterpreter);
        Object result = code.executeVarargs(Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE + 2);
        Assert.assertEquals("An unsigned 2^64-1 trip count must not hide an int IV wrap", -1, result);
        Assert.assertFalse("The huge trip count guard must have deoptimized", code.isValid());
    }

    public static long wrapAfterNestedZeroExtendSnippet(int start, int limit, int bound) {
        long sum = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, i < limit); i++) {
            long value = Integer.toUnsignedLong(i) - 0x8000_0000L;
            // for i = [-3,-2,-1,0,1,2]
            // value = [Int.MAX-2, Int.MAX-1, Int.MAX, Int.MIN, Int.MIN+1, Int.MIN+2]
            if (GraalDirectives.injectBranchProbability(0.001, value < bound)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += value;
        }
        return sum;
    }

    @Test
    public void testWrapAfterNestedZeroExtend() throws Exception {
        InstalledCode code = getGuardMovementCode("wrapAfterNestedZeroExtendSnippet", DeoptimizationReason.TransferToInterpreter);
        Object result = code.executeVarargs(-3, 3, Integer.MIN_VALUE + 1);
        Assert.assertEquals("Nested zero extension leads to non-monotonic range", -1L, result);
        Assert.assertFalse("Should have deopt'd", code.isValid());
    }

    public static int scaledIVWrapsInternallySnippet(int start, int count, int stride) {
        int sum = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < count); i++) {
            int iv = start + i * stride;
            int value = iv * 4;
            if (GraalDirectives.injectBranchProbability(0.001, Integer.compareUnsigned(value, 0x01000000) < 0)) {
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimizeAndInvalidate();
                return -1;
            }
            sum += value;
        }
        return sum;
    }

    @Test
    public void testScaledIVWrapsInternallyBeforeEndpoint() throws Exception {
        InstalledCode code = getGuardMovementCode("scaledIVWrapsInternallySnippet", DeoptimizationReason.TransferToInterpreter);
        /* The affine IV increases from 0x01000000 to 0xe2000000 without unsigned wraparound.
         * Scaling by four wraps the derived IV repeatedly; at affineIV == 0x40000000 its value is zero,
         * while its initial and last values are both unsigned-above 0x01000000.
         */
        Object result = code.executeVarargs(0x01000000, 26, 0x09000000);
        Assert.assertEquals("Scaled IV must not miss an internal unsigned wrap", -1, result);
    }
}
