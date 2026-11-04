/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.loop.phases.LoopInversionPhase;
import jdk.graal.compiler.loop.phases.SpeculativeGuardMovementPhase;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.nodes.loop.DerivedConvertedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedOffsetInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.vector.phases.VectorLoopUtility;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CountedLoopTest extends GraalCompilerTest {

    public static int negatedIntCounter(int start, int stride, int lowBit) {
        int result = 0;
        int step = stride | Integer.MIN_VALUE | lowBit;
        for (int i = start; 1 - i < 1; i += step) {
            result ^= i;
        }
        return result;
    }

    public static long negatedLongCounter(long start, long stride, int lowBit) {
        long result = 0;
        long step = stride | Long.MIN_VALUE | lowBit;
        for (long i = start; 1 - i < 1; i += step) {
            result ^= i;
        }
        return result;
    }

    @Test
    public void testGR77693NegatedIntStride() {
        checkNegatedStrideOverflow("negatedIntCounter");
    }

    @Test
    public void testGR77693NegatedLongStride() {
        checkNegatedStrideOverflow("negatedLongCounter");
    }

    /**
     * Checks the static proof and fallback guard without executing loops that can cycle forever.
     * Specializes guards built with an unbound stride to check both rejection and acceptance.
     */
    private void checkNegatedStrideOverflow(String method) {
        CountedLoopInfo nonWrapping = parseNegatedStrideLoop(method, 1, false);
        Assert.assertTrue(nonWrapping.counterNeverOverflows());
        Assert.assertTrue(nonWrapping.createOverflowGuardCondition().isContradiction());

        CountedLoopInfo concreteWrap = parseNegatedStrideLoop(method, 0, true);
        Assert.assertFalse(concreteWrap.counterNeverOverflows());
        Assert.assertTrue("The existing guard must reject a concrete MIN_VALUE stride", concreteWrap.createOverflowGuardCondition().isTautology());

        // The two bindings select MIN_VALUE and MIN_VALUE + 1 root strides.
        for (int lowStrideBit = 0; lowStrideBit <= 1; lowStrideBit++) {
            CountedLoopInfo possibleWrap = parseNegatedStrideLoop(method, 0, false);
            Assert.assertFalse("The negated stride stamp cannot bound the MIN_VALUE step", possibleWrap.counterNeverOverflows());
            LogicNode overflow = possibleWrap.createOverflowGuardCondition();
            Assert.assertFalse(overflow.isContradiction());
            Assert.assertFalse(overflow.isTautology());
            StructuredGraph graph = possibleWrap.getLimitCheckedIV().valueNode().graph();
            ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
            JavaKind kind = returnNode.result().getStackKind();
            // Keep this guard condition live across binding and canonicalization without executing it.
            returnNode.replaceFirstInput(returnNode.result(), graph.addOrUniqueWithInputs(ConditionalNode.create(overflow, ConstantNode.forIntegerKind(kind, 1, graph),
                            ConstantNode.forIntegerKind(kind, 0, graph), NodeView.DEFAULT)));
            Object stride = kind == JavaKind.Int ? (Object) lowStrideBit : (long) lowStrideBit;
            bindArguments(graph, new Object[]{NO_BIND, stride, NO_BIND});
            createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
            Assert.assertTrue(returnNode.result().isConstant());
            Assert.assertEquals("The dynamic guard must reject MIN_VALUE and accept MIN_VALUE + 1", 1 - lowStrideBit, returnNode.result().asJavaConstant().asLong());
            Assert.assertTrue(graph.verify());
        }
    }

    /**
     * Binds the stride domain before canonicalization and obtains fresh loop analysis afterwards.
     */
    private CountedLoopInfo parseNegatedStrideLoop(String method, int lowBit, boolean bindStride) {
        StructuredGraph graph = parseEager(method, AllowAssumptions.YES);
        Object stride = NO_BIND;
        if (bindStride) {
            // Keep the int argument boxed as Integer instead of promoting the conditional to long.
            stride = graph.getParameter(1).getStackKind() == JavaKind.Int ? (Object) 0 : 0L;
        }
        bindArguments(graph, new Object[]{NO_BIND, stride, lowBit});
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertTrue(counted.getLimitCheckedIV() instanceof DerivedOffsetInductionVariable);
        Assert.assertEquals(InductionVariable.Direction.Up, counted.getDirection());
        Assert.assertFalse(counted.isUnsignedCheck());
        Assert.assertTrue(graph.verify());
        return counted;
    }

    public static int signExtendedCounter(long limit) {
        int iterations = 0;
        for (int i = Integer.MAX_VALUE - 1; (long) i < limit; i++) {
            if (i < 0) {
                return 1;
            }
            if (++iterations == 3) {
                return 2;
            }
        }
        return 0;
    }

    @Test
    public void testGR77693SignExtendedCounter() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false);
        test(opt, "signExtendedCounter", (long) Integer.MAX_VALUE + 1);
    }

    public static int zeroExtendedCounter(long limit) {
        int iterations = 0;
        for (int i = Integer.MAX_VALUE - 1; Integer.toUnsignedLong(i) < limit; i++) {
            if (i < 0) {
                return 1;
            }
            if (++iterations == 3) {
                return 2;
            }
        }
        return 0;
    }

    @Test
    public void testGR77693ZeroExtendedCounter() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false);
        test(opt, "zeroExtendedCounter", (long) Integer.MAX_VALUE + 2);
    }

    // With limit = 2147483648L, the root IV i visits MAX_VALUE - 1023 through MAX_VALUE, then its int
    // increment overflows to MIN_VALUE. The checked IV (long) i still satisfies the condition. The 1024 array
    // stores make vectorization relevant; both scalar and vector-enabled compilation must reach the i < 0
    // deoptimization and return 1.
    public static int signExtendedVectorCounter(int[] array, long limit) {
        int start = Integer.MAX_VALUE - 1023;
        for (int i = start; (long) i < limit; i++) {
            if (i < 0) {
                GraalDirectives.deoptimize();
                return 1;
            }
            array[i - start] = i;
        }
        return 0;
    }

    @Test
    public void testGR77693SignExtendedVectorCounter() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false, VectorIntrinsics.Options.Vectorization, true);
        test(new OptionValues(opt, VectorIntrinsics.Options.Vectorization, false), "signExtendedVectorCounter", new int[1025], (long) Integer.MAX_VALUE + 1);
        test(opt, "signExtendedVectorCounter", new int[1025], (long) Integer.MAX_VALUE + 1);
    }

    // Here the checked IV is Integer.toUnsignedLong(i), while the root IV i is an int starting at -1024. With
    // limit = 4294967296L, the checked values reach 4294967295L and then become zero when i changes from -1
    // to 0. This is the unsigned 32-bit rollover, not signed int overflow. After 1024 stores, scalar and
    // vector-enabled compilation must reach i == 0, deoptimize and return 1.
    public static int zeroExtendedVectorCounter(int[] array, long limit) {
        int start = -1024;
        for (int i = start; Integer.toUnsignedLong(i) < limit; i++) {
            if (i == 0) {
                GraalDirectives.deoptimize();
                return 1;
            }
            array[i - start] = i;
        }
        return 0;
    }

    @Test
    public void testGR77693ZeroExtendedVectorCounter() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false, VectorIntrinsics.Options.Vectorization, true);
        test(new OptionValues(opt, VectorIntrinsics.Options.Vectorization, false), "zeroExtendedVectorCounter", new int[1025], 1L << 32);
        test(opt, "zeroExtendedVectorCounter", new int[1025], 1L << 32);
    }

    public static int guardedRootCounter(int[] array, long limit) {
        for (int i = 0; (long) i < limit; i++) {
            if (i == array.length) {
                GraalDirectives.deoptimize();
                return 1;
            }
            array[i] = i;
        }
        return 0;
    }

    @Test
    public void testGR77693GuardedRootCounter() throws InvalidInstalledCodeException {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false, VectorIntrinsics.Options.Vectorization, true);
        checkGuardedRootCounter(opt);
    }

    @Test
    public void testGR77693GuardedRootCounterWithoutVectorization() throws InvalidInstalledCodeException {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false, VectorIntrinsics.Options.Vectorization, false);
        checkGuardedRootCounter(opt);
    }

    private void checkGuardedRootCounter(OptionValues opt) throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("guardedRootCounter"), null, true, false, opt);
        Assert.assertEquals(0, code.executeVarargs(new int[1024], 128L));
        assertTrue("safe limit must keep the code valid", code.isValid());
        Assert.assertEquals(0, code.executeVarargs(new int[1024], -1L));
        assertTrue("an empty loop must not fail the overflow speculation", code.isValid());
        Assert.assertEquals(0, code.executeVarargs(new int[1024], Long.MIN_VALUE));
        assertTrue(code.isValid());
        Assert.assertEquals(1, code.executeVarargs(new int[1024], (long) Integer.MAX_VALUE + 1));
        assertFalse("overflowing limit must invalidate the guarded code", code.isValid());
        test(opt, "guardedRootCounter", new int[1024], (long) Integer.MAX_VALUE);
    }

    public static void overflowableIntCounter(int limit) {
        for (int i = 0; i <= limit; i += 2) {
            GraalDirectives.sideEffect();
        }
    }

    @Test
    public void testGR77693CombinedOverflowProof() {
        StructuredGraph graph = parseEager("overflowableIntCounter", AllowAssumptions.YES);
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertSame(counted.getLimitCheckedIV(), counted.getLimitCheckedIV().getRootIV());
        Assert.assertFalse(counted.counterNeverOverflows());
        Assert.assertNull(counted.getOverFlowGuard());
        Assert.assertFalse(counted.loopAndRootCanNeverOverflow());
        Assert.assertNotNull(counted.createOverFlowGuard());
        Assert.assertTrue(counted.loopAndRootCanNeverOverflow());
    }

    // These two snippets test the previous body value constructed for a tail-counted loop. The root IV is int
    // i; the checked IV is (long) i. In the upward failure case, limit = 2147483648L exceeds every int, so no
    // value of i can take the exit. The downward case uses a limit below every int. Parse these inputs only:
    // the tests inspect whether the synthetic previous value hides overflowing int arithmetic.
    public static void invertedPreviousUp(int start, long limit) {
        int i = start;
        while (true) {
            GraalDirectives.sideEffect(i);
            if ((long) i >= limit) {
                return;
            }
            i += 3;
        }
    }

    public static void invertedPreviousDown(int start, long limit) {
        int i = start;
        while (true) {
            GraalDirectives.sideEffect(i);
            if ((long) i <= limit) {
                return;
            }
            i -= 1_000_000_000;
        }
    }

    @Test
    public void testGR77693InvertedPreviousUpProof() {
        checkInvertedPreviousRoot("invertedPreviousUp", 6, 15, 4, true);
        checkInvertedPreviousRoot("invertedPreviousUp", Integer.MIN_VALUE + 1, (long) Integer.MAX_VALUE + 1, 1, false);
    }

    @Test
    public void testGR77693InvertedPreviousDownProof() {
        checkInvertedPreviousRoot("invertedPreviousDown", 9, 0, 2, true);
        checkInvertedPreviousRoot("invertedPreviousDown", 1_500_000_000, -2_794_967_297L, 2, false);
    }

    /**
     * Checks that a tail-counted loop's computed iteration count does not hide overflow of its int counter.
     * The upward snippet has this shape:
     *
     * <pre>
     * int i = start;
     * while (true) {
     *     body(i);
     *     if ((long) i &gt;= limit) {
     *         return;
     *     }
     *     i += 3;
     * }
     * </pre>
     *
     * The root IV is {@code i}; the checked IV is its long conversion. With {@code start = 6} and {@code
     * limit = 15}, the body sees {@code 6, 9, 12, 15}, so the count is four. Counted-loop analysis represents
     * the body start through a synthetic previous value. For {@code start = Integer.MIN_VALUE + 1},
     * subtracting the stride mathematically gives {@code -2147483650L}, outside the int range.
     *
     * <p>Assert the inverted shape, direct conversion, predicted count and checked-IV result before checking
     * the root and combined results against {@code safe}. An available count must not by itself make those
     * last checks pass. The downward snippet exercises the same issue in the opposite direction. All inputs
     * are parsed only; the failing long limits can make execution nonterminating.
     */
    private void checkInvertedPreviousRoot(String snippet, int start, long limit, long predictedTrips, boolean safe) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        graph.getParameter(0).replaceAndDelete(ConstantNode.forInt(start, graph));
        graph.getParameter(1).replaceAndDelete(ConstantNode.forLong(limit, graph));
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertTrue("The count must use a synthetic previous iteration", counted.isInverted());
        Assert.assertTrue(counted.getLimitCheckedIV() instanceof DerivedConvertedInductionVariable);
        Assert.assertSame(counted.getLimitCheckedIV().getRootIV(), ((DerivedInductionVariable) counted.getLimitCheckedIV()).getBase());
        Assert.assertEquals(predictedTrips, counted.constantMaxTripCount().asLong());
        Assert.assertTrue(counted.counterNeverOverflows());
        Assert.assertEquals("A synthetic previous value must not hide native-width wrap", safe, counted.ivCanNeverOverflow(counted.getLimitCheckedIV().getRootIV()));
        Assert.assertEquals(safe, counted.loopAndRootCanNeverOverflow());
    }

    public static int guardedRootUpdateUp(int start) {
        int i = start;
        if (i < 1000) {
            do {
                i++;
            } while (i < 1000);
        }
        return i;
    }

    public static int guardedRootUpdateDown(int start) {
        int i = start;
        if (i > -1000) {
            do {
                i--;
            } while (i > -1000);
        }
        return i;
    }

    public static int unguardedRootUpdateUp(int start) {
        int i = start;
        do {
            i++;
        } while (i < 1000);
        return i;
    }

    public static int unguardedRootUpdateDown(int start) {
        int i = start;
        do {
            i--;
        } while (i > -1000);
        return i;
    }

    public static int guardedVariableRootUpdate(int start, int stride) {
        int step = (stride & 1) + 1;
        int i = start;
        if (i < 1000) {
            do {
                i += step;
            } while (i < 1000);
        }
        return i;
    }

    @Test
    public void testGR77693VariableRootUpdateProof() {
        checkRootUpdateProof("guardedVariableRootUpdate", false);
    }

    @Test
    public void testGR77693GuardedRootUpdateUpProof() {
        checkRootUpdateProof("unguardedRootUpdateUp", false);
        checkRootUpdateProof("guardedRootUpdateUp", true);
    }

    @Test
    public void testGR77693GuardedRootUpdateDownProof() {
        checkRootUpdateProof("unguardedRootUpdateDown", false);
        checkRootUpdateProof("guardedRootUpdateDown", true);
    }

    private void checkRootUpdateProof(String snippet, boolean safe) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        BasicInductionVariable root = counted.getLimitCheckedIV().getRootIV();
        Assert.assertNotSame(counted.getLimitCheckedIV(), root);
        Assert.assertSame("The checked value must be the actual root update", root.getOp(), counted.getLimitCheckedIV().valueNode());
        Assert.assertFalse(counted.isUnsignedCheck());
        Assert.assertTrue(counted.counterNeverOverflows());
        Assert.assertEquals("The entry comparison must establish the first native update", safe, counted.ivCanNeverOverflow(root));
    }

    public static int guardedRootUpdateByFour(int start, int limit) {
        if (start < limit) {
            do {
                GraalDirectives.blackhole(start);
                start += 4;
            } while (start < limit);
        }
        return start;
    }

    public static int unguardedRootUpdateByFour(int start, int limit) {
        do {
            GraalDirectives.blackhole(start);
            start += 4;
        } while (start < limit);
        return start;
    }

    public static int guardedRootUpdateByMinusFour(int start, int limit) {
        if (start > limit) {
            do {
                GraalDirectives.blackhole(start);
                start -= 4;
            } while (start > limit);
        }
        return start;
    }

    public static int unguardedRootUpdateByMinusFour(int start, int limit) {
        do {
            GraalDirectives.blackhole(start);
            start -= 4;
        } while (start > limit);
        return start;
    }

    @Test
    public void testGR77693RootUpdateWithExistingOverflowGuard() {
        checkRootUpdateOverflowGuard("unguardedRootUpdateByFour", false);
        checkRootUpdateOverflowGuard("guardedRootUpdateByFour", true);
        checkRootUpdateOverflowGuard("unguardedRootUpdateByMinusFour", false);
        checkRootUpdateOverflowGuard("guardedRootUpdateByMinusFour", true);
    }

    private void checkRootUpdateOverflowGuard(String snippet, boolean rootSafe) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        // Detect the tail-counted shape even without entry protection so both guarded and unguarded snippets
        // reach the proof under test. For do { body(start); start += 4; } while (start < limit), the checked
        // IV is start + 4. Its overflow guard must prove the initial body update safe only when an entry
        // comparison also constrains start.
        Assert.assertTrue("Counted input required: " + snippet, loop.detectCounted(true));
        CountedLoopInfo counted = loop.counted();
        BasicInductionVariable root = counted.getLimitCheckedIV().getRootIV();
        Assert.assertTrue(counted.isInverted());
        Assert.assertSame(root, counted.getBodyIV());
        Assert.assertSame(root.getOp(), counted.getLimitCheckedIV().valueNode());
        Assert.assertFalse(counted.counterNeverOverflows());
        Assert.assertFalse(counted.ivCanNeverOverflow(root));
        Assert.assertNotNull(counted.createOverFlowGuard());
        Assert.assertTrue(counted.loopCanNeverOverflow());
        Assert.assertEquals("The checked overflow guard and entry comparison must prove the first update: " + snippet, rootSafe, counted.ivCanNeverOverflow(root));
    }

    public static void vectorGuardPreparation(int[] array, long limit) {
        for (int i = 0; (long) i < limit; i++) {
            array[i] = i;
        }
    }

    public static void guardPreparationFromStart(int start, long limit) {
        for (int i = start; (long) i < limit; i++) {
            GraalDirectives.sideEffect(i);
        }
    }

    @Test
    public void testGR77693GuardPreparationWithConstantStart() {
        for (int start : new int[]{0, 1, 1024, Integer.MAX_VALUE - 1, -1, Integer.MIN_VALUE}) {
            StructuredGraph graph = parseEager("guardPreparationFromStart", AllowAssumptions.YES);
            graph.getParameter(0).replaceAndDelete(ConstantNode.forInt(start, graph));
            LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
            Loop loop = loops.loops().get(0);
            Assert.assertTrue(loop.detectCounted());
            ValueNode originalCondition = loop.counted().getLimitTest().condition();
            CountedLoopInfo.prepareRootOverflowGuards(graph, getDefaultMidTierContext());
            if (start >= 0) {
                assertPreparedRoot(graph);
            } else {
                // With start = -1 and limit = -1, replacing the limit by zero would add an
                // iteration. Negative starts must retain the original comparison.
                Assert.assertSame(originalCondition, loop.counted().getLimitTest().condition());
                Assert.assertEquals(0, graph.getNodes(GuardNode.TYPE).count());
            }
        }
    }

    @Test
    public void testGR77693GuardPreparationWithoutScalarProtection() {
        OptionValues options = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, true, VectorLoopUtility.Options.RespectVectorization, false);
        StructuredGraph graph = parseEager("vectorGuardPreparation", AllowAssumptions.YES, options);
        CountedLoopInfo.prepareRootOverflowGuards(graph, getDefaultMidTierContext());
        assertPreparedRoot(graph);
    }

    private void assertPreparedRoot(StructuredGraph graph) {
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertTrue("Guard preparation must not depend on scalar-loop protection", counted.getLimit() instanceof PiNode);
        IntegerStamp limitStamp = (IntegerStamp) counted.getLimit().stamp(NodeView.DEFAULT);
        Assert.assertEquals(0, limitStamp.lowerBound());
        Assert.assertEquals(Integer.MAX_VALUE, limitStamp.upperBound());
        Assert.assertTrue(counted.ivCanNeverOverflow(counted.getLimitCheckedIV().getRootIV()));
        Assert.assertTrue(counted.loopAndRootCanNeverOverflow());
    }

    @Test
    public void testGR77693InversionPreparesRootWithoutDetection() {
        OptionValues options = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false, Loop.Options.DetectInvertedLoopsAsCounted, false);
        StructuredGraph graph = parseEager("vectorGuardPreparation", AllowAssumptions.YES, options);
        LoopInversionPhase phase = new LoopInversionPhase(new DefaultLoopPolicies(), createCanonicalizerPhase());
        new DisableOverflownCountedLoopsPhase().apply(graph);
        phase.apply(graph, getDefaultMidTierContext());
        assertPreparedRoot(graph);
        int guards = graph.getNodes(GuardNode.TYPE).count();
        int nodes = graph.getNodeCount();
        phase.apply(graph, getDefaultMidTierContext());
        assertPreparedRoot(graph);
        Assert.assertEquals("Repeated preparation must not add guards", guards, graph.getNodes(GuardNode.TYPE).count());
        Assert.assertEquals("Repeated preparation must not add nodes", nodes, graph.getNodeCount());
    }

    @Test
    public void testGR77693GuardMovementPreparesRootWithoutInversion() {
        OptionValues options = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false, LoopInversionPhase.Options.LoopInversion, false);
        StructuredGraph graph = parseEager("vectorGuardPreparation", AllowAssumptions.YES, options);
        new SpeculativeGuardMovementPhase(createCanonicalizerPhase()).apply(graph, getDefaultMidTierContext());
        assertPreparedRoot(graph);
    }

    @Test
    public void testGR77693FirstGuardLoweringPreparesRoot() {
        OptionValues options = new OptionValues(getInitialOptions(), VectorIntrinsics.Options.Vectorization, false);
        StructuredGraph graph = parseEager("vectorGuardPreparation", AllowAssumptions.YES, options);
        new GuardLoweringPhase().apply(graph, getDefaultMidTierContext());
        Assert.assertEquals(GraphState.GuardsStage.FIXED_DEOPTS, graph.getGuardsStage());
        Assert.assertEquals(0, graph.getNodes(GuardNode.TYPE).count());
        assertPreparedRoot(graph);
    }

    @Test
    public void testGR77693GuardPreparationInSubstitution() {
        StructuredGraph graph = parse(builder(getResolvedJavaMethod("vectorGuardPreparation"), AllowAssumptions.YES).setIsSubstitution(true), getEagerGraphBuilderSuite());
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertFalse(counted.loopAndRootCanNeverOverflow());
        int nodes = graph.getNodeCount();
        CountedLoopInfo.prepareRootOverflowGuards(graph, getDefaultMidTierContext());
        counted.prepareRootOverflowGuard();
        Assert.assertEquals("Snippet preparation must not introduce a deoptimization without a frame state", nodes, graph.getNodeCount());
        Assert.assertEquals(0, graph.getNodes(GuardNode.TYPE).count());
        Assert.assertSame(counted, loop.counted());
    }

    @Test
    public void testGR77693GuardLoweringInSubstitutionWithNullContext() {
        StructuredGraph graph = parse(builder(getResolvedJavaMethod("vectorGuardPreparation"), AllowAssumptions.YES).setIsSubstitution(true), getEagerGraphBuilderSuite());
        Assert.assertTrue(graph.hasLoops());
        Assert.assertEquals(0, graph.getNodes(GuardNode.TYPE).count());
        // MacroInvokable lowers replacement graphs without a compilation context.
        new GuardLoweringPhase().apply(graph, null);
        Assert.assertEquals(GraphState.GuardsStage.FIXED_DEOPTS, graph.getGuardsStage());
        Assert.assertEquals(0, graph.getNodes(GuardNode.TYPE).count());
    }

    @Test
    public void testGR77693GuardPreparationWithFixedGuards() {
        for (GraphState.GuardsStage stage : new GraphState.GuardsStage[]{GraphState.GuardsStage.FIXED_DEOPTS, GraphState.GuardsStage.AFTER_FSA}) {
            StructuredGraph graph = parseEager("vectorGuardPreparation", AllowAssumptions.YES);
            LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
            Loop loop = loops.loops().get(0);
            Assert.assertTrue(loop.detectCounted());
            CountedLoopInfo counted = loop.counted();
            Assert.assertFalse(counted.loopAndRootCanNeverOverflow());
            graph.getGraphState().setGuardsStage(stage);
            int nodes = graph.getNodeCount();
            CountedLoopInfo.prepareRootOverflowGuards(graph, getDefaultMidTierContext());
            counted.prepareRootOverflowGuard();
            Assert.assertEquals("Late preparation must not mutate the graph", nodes, graph.getNodeCount());
            Assert.assertEquals(0, graph.getNodes(GuardNode.TYPE).count());
            Assert.assertSame(counted, loop.counted());
            Assert.assertFalse("Skipping preparation must not manufacture a root proof", counted.loopAndRootCanNeverOverflow());
        }
    }

    @Test
    public void testGR77693DirectRootPreparationIsIdempotent() {
        StructuredGraph graph = parseEager("vectorGuardPreparation", AllowAssumptions.YES);
        CountedLoopInfo.prepareRootOverflowGuards(graph, getDefaultMidTierContext());
        assertPreparedRoot(graph);
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        int nodes = graph.getNodeCount();
        counted.prepareRootOverflowGuard();
        Assert.assertEquals("An existing proof must avoid another rewrite", nodes, graph.getNodeCount());
        Assert.assertSame(counted, loop.counted());
        Assert.assertTrue(counted.loopAndRootCanNeverOverflow());
    }

    public static int offsetCounterWithEntryCheck(int seed, int limit) {
        int start = -(seed & 1);
        if (limit < start + Integer.MIN_VALUE) {
            return -1;
        }
        int result = 0;
        for (int i = start; i + Integer.MIN_VALUE < limit; i++) {
            result ^= i;
        }
        return result;
    }

    @Test
    public void testGR77693UnsignedTripCountWithEntryPi() {
        StructuredGraph graph = parseEager("offsetCounterWithEntryCheck", AllowAssumptions.YES);
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertTrue(counted.getLimitCheckedIV() instanceof DerivedOffsetInductionVariable);
        InductionVariable root = counted.getLimitCheckedIV().getRootIV();
        Assert.assertFalse(root.isConstantInit());
        IntegerStamp initStamp = (IntegerStamp) root.initNode().stamp(NodeView.DEFAULT);
        Assert.assertEquals(-1, initStamp.lowerBound());
        Assert.assertEquals(0, initStamp.upperBound());
        Assert.assertFalse(counted.isConstantMaxTripCount());
        Assert.assertTrue(counted.counterNeverOverflows());
        Assert.assertTrue(counted.maxTripCountNode() instanceof PiNode);
        Assert.assertTrue(((IntegerStamp) counted.maxTripCountNode(true).stamp(NodeView.DEFAULT)).lowerBound() < 0);
        // For seed = 0 and limit = 1, root i starts at 0 and checked i + MIN_VALUE starts at MIN_VALUE. It
        // takes 2147483649 increments for the checked value to reach 1; i overflows along the way. The
        // positive int Pi produced for the count cannot justify replacing that unsigned distance by a
        // signed-int bound.
        Assert.assertFalse("Loop entry does not bound an unsigned count to signed int range", counted.ivCanNeverOverflow(root));
    }

    public static int descendingOffsetCounter(int seed, int limit) {
        int result = 0;
        for (int i = seed & 1; i - 1 >= limit; i--) {
            result ^= i;
        }
        return result;
    }

    public static long unsignedLongOffsetCounter(long seed, long limit) {
        long result = 0;
        for (long i = seed & 1; Long.compareUnsigned(i + 1, limit) < 0; i++) {
            result ^= i;
        }
        return result;
    }

    public static int variableScaledCounter(int seed, int limit) {
        int bit = seed & 1;
        int result = 0;
        for (int i = Integer.MAX_VALUE - bit; i * (bit + 1) < limit; i++) {
            result ^= i;
        }
        return result;
    }

    @Test
    public void testGR77693DynamicRootProofBoundaries() {
        checkDynamicRootProof("descendingOffsetCounter", -10, true);
        checkDynamicRootProof("unsignedLongOffsetCounter", -1, false);
        checkDynamicRootProof("variableScaledCounter", 100, false);
    }

    private void checkDynamicRootProof(String snippet, long limit, boolean safe) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        graph.getParameter(1).replaceAndDelete(ConstantNode.forIntegerStamp(graph.getParameter(1).stamp(NodeView.DEFAULT), limit, graph));
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertTrue(counted.getLimitCheckedIV() instanceof DerivedInductionVariable);
        Assert.assertFalse(counted.isConstantMaxTripCount());
        Assert.assertTrue(counted.counterNeverOverflows());
        InductionVariable root = counted.getLimitCheckedIV().getRootIV();
        Assert.assertFalse(root.isConstantInit());
        Assert.assertSame(root, ((DerivedInductionVariable) counted.getLimitCheckedIV()).getBase());
        Assert.assertEquals(safe, counted.ivCanNeverOverflow(root));
        Assert.assertEquals(safe, counted.loopAndRootCanNeverOverflow());
    }

    public static int intermediateWrappedCounter(int[] array, long limit) {
        long bound = Math.max((long) Integer.MAX_VALUE, Math.min(limit, (long) Integer.MAX_VALUE + 1));
        for (int i = 0; (long) (i + Integer.MAX_VALUE) < bound; i++) {
            if (i == 3) {
                GraalDirectives.deoptimize();
                return 1;
            }
            array[i] = i;
        }
        return 0;
    }

    @Test
    public void testGR77693IntermediateWrappedCounter() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false, VectorIntrinsics.Options.Vectorization, true);
        test(new OptionValues(opt, VectorIntrinsics.Options.Vectorization, false), "intermediateWrappedCounter", new int[4], (long) Integer.MAX_VALUE + 1);
        test(opt, "intermediateWrappedCounter", new int[4], (long) Integer.MAX_VALUE + 1);
    }

    public static int constantTripIntermediateWrappedCounter(long limit) {
        int secondary = 0;
        for (int i = 0; (long) (i + (Integer.MAX_VALUE - 1)) < limit; i += 3) {
            if (i == 9) {
                return secondary;
            }
            secondary++;
        }
        return -1;
    }

    public static int constantTripIndependentCounter(long limit) {
        int secondary = 0;
        for (int i = 0; (long) i < limit; i += 3) {
            secondary++;
        }
        return secondary;
    }

    @Test
    public void testGR77693ConstantTripIntermediateWrapProof() {
        checkConstantTripRootProof("constantTripIntermediateWrappedCounter", Integer.MAX_VALUE, 1, true, false);
        checkConstantTripRootProof("constantTripIndependentCounter", 9, 3, false, true);
        checkConstantTripRootProof("constantTripIndependentCounter", (long) Integer.MAX_VALUE + 1, 715827883, false, false);
    }

    private void checkConstantTripRootProof(String snippet, long limit, long tripCount, boolean intermediateWrap, boolean safe) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        // Bind limit after parsing so canonicalization does not erase the int-add-then-widen expression
        // (long) (i + (MAX_VALUE - 1)). With stride 3 and limit = MAX_VALUE, widened arithmetic predicts one
        // trip, but the int addition overflows on the next iteration. The graph must retain this chain for
        // the root and independent-counter assertions.
        graph.getParameter(0).replaceAndDelete(ConstantNode.forLong(limit, graph));
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue(loop.detectCounted());
        CountedLoopInfo counted = loop.counted();
        Assert.assertTrue(counted.getLimitCheckedIV() instanceof DerivedConvertedInductionVariable);
        DerivedConvertedInductionVariable converted = (DerivedConvertedInductionVariable) counted.getLimitCheckedIV();
        Assert.assertEquals(intermediateWrap, converted.getBase() instanceof DerivedOffsetInductionVariable);
        Assert.assertTrue(counted.isConstantMaxTripCount());
        Assert.assertEquals(tripCount, counted.constantMaxTripCount().asLong());
        Assert.assertTrue(counted.counterNeverOverflows());
        InductionVariable root = converted.getRootIV();
        Assert.assertEquals("A widened trip count must not hide intermediate wrap", safe, counted.ivCanNeverOverflow(root));
        int independentIVs = 0;
        for (InductionVariable iv : loop.getInductionVariables().getValues()) {
            if (iv instanceof BasicInductionVariable && iv != root) {
                Assert.assertEquals(0, iv.constantInit());
                Assert.assertEquals(1, iv.constantStride());
                Assert.assertTrue("The independent counter must fit even when the root wraps", tripCount <= Integer.MAX_VALUE);
                Assert.assertEquals("An independent IV must not inherit an invalid trip count", safe, counted.ivCanNeverOverflow(iv));
                independentIVs++;
            }
        }
        Assert.assertEquals(1, independentIVs);
    }

    @Test
    public void testGR77693ConstantTripIntermediateWrapExecution() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false, GraalOptions.FullUnroll, false);
        test(opt, "constantTripIntermediateWrappedCounter", (long) Integer.MAX_VALUE);
    }

    @FunctionalInterface
    protected interface IVProperty {
        ValueNode get(InductionVariable iv);
    }

    @FunctionalInterface
    protected interface StaticIVProperty {
        long get(InductionVariable iv);
    }

    @FunctionalInterface
    protected interface IVPredicate {
        boolean test(InductionVariable iv);
    }

    /**
     * Get a property of an induction variable.
     */
    @SuppressWarnings("unused")
    protected static int get(IVProperty property, StaticIVProperty staticProperty, IVPredicate constantCheck, int iv) {
        return iv;
    }

    @SuppressWarnings("unused")
    protected static int get(IVProperty property, int iv) {
        return iv;
    }

    @SuppressWarnings("unused")
    protected static long get(IVProperty property, StaticIVProperty staticProperty, IVPredicate constantCheck,
                    long iv) {
        return iv;
    }

    @SuppressWarnings("unused")
    protected static long get(IVProperty property, long iv) {
        return iv;
    }

    public static class Result {
        public long extremum;
        public long exitValue;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Long.hashCode(exitValue);
            result = prime * result + Long.hashCode(extremum);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Result)) {
                return false;
            }
            Result other = (Result) obj;
            return extremum == other.extremum && exitValue == other.exitValue;
        }

        @Override
        public String toString() {
            return String.format("extremum = %d, exitValue = %d", extremum, exitValue);
        }
    }

    public static Result incrementSnippet(int start, int limit, int step) {
        int i;
        int inc = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i < limit; i += inc) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void increment1() {
        testCounted("incrementSnippet", 0, 256, 1);
    }

    @Test
    public void increment2() {
        testCounted("incrementSnippet", 0, 256, 2);
    }

    @Test
    public void increment3() {
        testCounted("incrementSnippet", 0, 256, 3);
    }

    @Test
    public void increment4() {
        testCounted("incrementSnippet", -10, 1, Integer.MAX_VALUE);
    }

    @Test
    public void increment5() {
        testRemovableCounted("incrementSnippet", 256, 256, 1);
    }

    @Test
    public void increment6() {
        testRemovableCounted("incrementSnippet", 257, 256, 1);
    }

    @Test
    public void increment7() {
        testCounted("incrementSnippet", -10, Integer.MAX_VALUE, 1);
    }

    @Test
    public void increment8() {
        testCounted("incrementSnippet", -10, Integer.MAX_VALUE - 1, 2);
    }

    public static Result incrementEqSnippet(int start, int limit, int step) {
        int i;
        int inc = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i <= limit; i += inc) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementEq1() {
        testCounted("incrementEqSnippet", 0, 256, 1);
    }

    @Test
    public void incrementEq2() {
        testCounted("incrementEqSnippet", 0, 256, 2);
    }

    @Test
    public void incrementEq3() {
        testCounted("incrementEqSnippet", 0, 256, 3);
    }

    @Test
    public void incrementEq4() {
        testCounted("incrementEqSnippet", -10, 0, Integer.MAX_VALUE);
    }

    @Test
    public void incrementEq5() {
        testCounted("incrementEqSnippet", 256, 256, 1);
    }

    @Test
    public void incrementEq6() {
        testRemovableCounted("incrementEqSnippet", 257, 256, 1);
    }

    @Test
    public void incrementEq7() {
        testCounted("incrementEqSnippet", -10, Integer.MAX_VALUE - 1, 1);
    }

    @Test
    public void incrementEq8() {
        testCounted("incrementEqSnippet", -10, Integer.MAX_VALUE - 2, 2);
    }

    @Test
    public void incrementEq9() {
        testCounted("incrementEqSnippet", 0, 0, 1);
    }

    @Test
    public void incrementEq10() {
        testCounted("incrementEqSnippet", 0, 0, 3);
    }

    public static Result decrementSnippet(int start, int limit, int step) {
        int i;
        int dec = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i > limit; i -= dec) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrement1() {
        testCounted("decrementSnippet", 256, 0, 1);
    }

    @Test
    public void decrement2() {
        testCounted("decrementSnippet", 256, 0, 2);
    }

    @Test
    public void decrement3() {
        testCounted("decrementSnippet", 256, 0, 3);
    }

    @Test
    public void decrement4() {
        testCounted("decrementSnippet", Integer.MAX_VALUE, -10, 1);
    }

    @Test
    public void decrement5() {
        testCounted("decrementSnippet", Integer.MAX_VALUE, -10, 2);
    }

    public static Result decrementEqSnippet(int start, int limit, int step) {
        int i;
        int dec = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i >= limit; i -= dec) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrementEq1() {
        testCounted("decrementEqSnippet", 256, 0, 1);
    }

    @Test
    public void decrementEq2() {
        testCounted("decrementEqSnippet", 256, 0, 2);
    }

    @Test
    public void decrementEq3() {
        testCounted("decrementEqSnippet", 256, 0, 3);
    }

    @Test
    public void decrementEq4() {
        testRemovableCounted("decrementEqSnippet", -10, 0, Integer.MAX_VALUE);
    }

    @Test
    public void decrementEq5() {
        testCounted("decrementEqSnippet", Integer.MAX_VALUE, -10, 1);
    }

    @Test
    public void decrementEq6() {
        testCounted("decrementEqSnippet", Integer.MAX_VALUE, -10, 2);
    }

    @Test
    public void decrementEq7() {
        testCounted("decrementEqSnippet", 10, 10, 1);
    }

    @Test
    public void decrementEq8() {
        testCounted("decrementEqSnippet", 10, 10, 3);
    }

    public static Result twoVariablesSnippet() {
        Result ret = new Result();
        int j = 0;
        for (int i = 0; i < 1024; i++) {
            j += 5;
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, j);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, j);
        return ret;
    }

    @Test
    public void testTwoVariables() {
        testCounted("twoVariablesSnippet");
    }

    public static Result incrementNeqSnippet(int limit) {
        int i;
        int posLimit = ((limit - 1) & 0xFFFF) + 1; // make sure limit is always strictly positive
        Result ret = new Result();
        for (i = 0; i != posLimit; i++) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrementNeq() {
        testCounted("decrementNeqSnippet", 256);
    }

    public static Result decrementNeqSnippet(int limit) {
        int i;
        int posLimit = ((limit - 1) & 0xFFFF) + 1; // make sure limit is always strictly positive
        Result ret = new Result();
        for (i = posLimit; i != 0; i--) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementNeq() {
        testCounted("incrementNeqSnippet", 256);
    }

    public static Result incrementLongSnippet(long start, long limit, long step) {
        long i;
        long inc = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; i < limit; i += inc) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementLong1() {
        testCounted("incrementLongSnippet", 0L, 256L, 1L);
    }

    @Test
    public void incrementLong2() {
        testCounted("incrementLongSnippet", 0L, 256L, 2L);
    }

    @Test
    public void incrementLong3() {
        testCounted("incrementLongSnippet", 0L, 256L, 3L);
    }

    @Test
    public void incrementLong4() {
        testCounted("incrementLongSnippet", -10L, 1L, Long.MAX_VALUE);
    }

    @Test
    public void incrementLong5() {
        testRemovableCounted("incrementLongSnippet", 256L, 256L, 1L);
    }

    @Test
    public void incrementLong6() {
        testRemovableCounted("incrementLongSnippet", 257L, 256L, 1L);
    }

    public static Result incrementUnsignedSnippet(int start, int limit, int step) {
        int i;
        int inc = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        for (i = start; Integer.compareUnsigned(i, limit) < 0; i += inc) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void incrementUnsigned1() {
        testCounted("incrementUnsignedSnippet", 0, 256, 1);
    }

    @Test
    public void incrementUnsigned2() {
        testCounted("incrementUnsignedSnippet", 0, 256, 2);
    }

    @Test
    public void incrementUnsigned3() {
        testCounted("incrementUnsignedSnippet", 0, 256, 3);
    }

    @Test
    public void incrementUnsigned4() {
        testCounted("incrementUnsignedSnippet", 1, Integer.MAX_VALUE + 10, Integer.MAX_VALUE);
    }

    @Test
    public void incrementUnsigned5() {
        testRemovableCounted("incrementUnsignedSnippet", 256, 256, 1);
    }

    @Test
    public void incrementUnsigned6() {
        testRemovableCounted("incrementUnsignedSnippet", 257, 256, 1);
    }

    @Test
    public void incrementUnsigned7() {
        testCounted("incrementUnsignedSnippet", 0, Integer.MAX_VALUE + 10, 1);
    }

    @Test
    public void incrementUnsigned8a() {
        testCounted("incrementUnsignedSnippet", 0, Integer.MAX_VALUE + 11, 2);
    }

    @Test
    public void incrementUnsigned8b() {
        testCounted("incrementUnsignedSnippet", 0, Integer.MAX_VALUE + 10, 2);
    }

    @Test
    public void incrementUnsigned9() {
        testCounted("incrementUnsignedSnippet", Integer.MAX_VALUE - 1, Integer.MAX_VALUE + 10, 1);
    }

    @Test
    public void incrementUnsigned10() {
        testCounted("incrementUnsignedSnippet", Integer.MAX_VALUE - 1, Integer.MAX_VALUE + 10, 2);
    }

    public static Result decrementUnsignedSnippet(int start, int limit, int step) {
        if (!GraalDirectives.inCompiledCode()) {
            /*
             * C2 can spuriously deopt this method due to tiered compilation and profile update
             * issues when executing the expected code shape. This incurs tremendous test timeouts
             * because we exercise entire integer ranges on these texts. Thus, we have a fast path
             * for the interpreter and C2 only.
             */

            Result ret = new Result();

            final long lInit = Integer.toUnsignedLong(start);
            final long lLimit = Integer.toUnsignedLong(limit);
            final long absStride = Integer.toUnsignedLong(step);
            final long stride = Integer.toUnsignedLong(step) * -1L;
            final long range = lInit - lLimit;

            final long tripCount = lInit > lLimit ? (range + (absStride - 1)) / absStride : 0L;
            final long iterationsTimeStride = (tripCount - 1L) * stride;
            final long extremum = lInit + iterationsTimeStride;
            final long exitValue = extremum - absStride;

            // test below only sets extremum if loop is actually entered
            ret.extremum = tripCount > 0 ? extremum : 0;
            ret.exitValue = exitValue;
            return ret;
        }
        int dec = ((step - 1) & 0xFFFF) + 1; // make sure this value is always strictly positive
        Result ret = new Result();
        int i;
        for (i = start; Integer.compareUnsigned(i, limit) > 0; i -= dec) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void decrementUnsigned1() {
        testCounted("decrementUnsignedSnippet", 256, 0, 1);
    }

    @Test
    public void decrementUnsigned2() {
        testCounted("decrementUnsignedSnippet", 256, 0, 2);
    }

    @Test
    public void decrementUnsigned3() {
        testCounted("decrementUnsignedSnippet", 256, 2, 3);
    }

    @Test
    public void decrementUnsigned5() {
        testRemovableCounted("decrementUnsignedSnippet", 256, 256, 1);
    }

    @Test
    public void decrementUnsigned6() {
        testRemovableCounted("decrementUnsignedSnippet", 256, 257, 1);
    }

    @Test
    @Ignore("GR-54120: Loops requiring overflow to terminate must not be counted in graal, CountedLoopInfo API does not support that")
    public void decrementUnsigned7() {
        testCounted("decrementUnsignedSnippet", Integer.MAX_VALUE + 10, 0, 1);
    }

    @Test
    @Ignore("GR-54120: Loops requiring overflow to terminate must not be counted in graal, CountedLoopInfo API does not support that")
    public void decrementUnsigned8() {
        testCounted("decrementUnsignedSnippet", Integer.MAX_VALUE + 11, 0, 2);
    }

    @Test
    public void decrementUnsigned9() {
        testCounted("decrementUnsignedSnippet", Integer.MAX_VALUE + 10, Integer.MAX_VALUE - 1, 1);
    }

    @Test
    public void decrementUnsigned10() {
        testCounted("decrementUnsignedSnippet", Integer.MAX_VALUE + 10, Integer.MAX_VALUE - 1, 2);
    }

    public static Result shiftLargeLongSnippet() {
        Result ret = new Result();
        for (long i = 1; GraalDirectives.injectIterationCount(11, i <= 10); i++) {
            GraalDirectives.controlFlowAnchor();
            long derived = i << 48;
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, derived);
        }
        ret.exitValue = 0;
        return ret;
    }

    @Test
    public void shiftLargeLong() {
        testCounted("shiftLargeLongSnippet");
    }

    public static Result disjointOrToAddSnippet(int limit) {
        Result ret = new Result();
        int i;
        for (i = 0; GraalDirectives.injectIterationCount(11, (i | 4) < limit); i += 8) {
            GraalDirectives.controlFlowAnchor();
            ret.extremum = get(InductionVariable::extremumNode, InductionVariable::constantExtremum, InductionVariable::isConstantExtremum, i | 4);
        }
        ret.exitValue = get(InductionVariable::exitValueNode, i);
        return ret;
    }

    @Test
    public void disjointOrToAdd() {
        testCounted("disjointOrToAddSnippet", 256);
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static class IVPropertyNode extends FloatingNode implements LIRLowerable {
        public static final NodeClass<IVPropertyNode> TYPE = NodeClass.create(IVPropertyNode.class);

        private final IVProperty property;
        private final StaticIVProperty staticProperty;
        private final IVPredicate staticCheck;
        private final boolean loopCanBeRemoved;
        @Input private ValueNode iv;

        protected IVPropertyNode(IVProperty property, StaticIVProperty staticProperty, IVPredicate staticCheck, ValueNode iv, boolean loopCanBeRemoved) {
            super(TYPE, iv.stamp(NodeView.DEFAULT).unrestricted());
            this.property = property;
            this.staticProperty = staticProperty;
            this.staticCheck = staticCheck;
            this.iv = iv;
            this.loopCanBeRemoved = loopCanBeRemoved;
        }

        public void rewrite(LoopsData loops) {
            InductionVariable inductionVariable = loops.getInductionVariable(GraphUtil.unproxify(iv));
            ValueNode node = null;
            if (inductionVariable == null) {
                assert loopCanBeRemoved;
                assert loops.loops().isEmpty();
                node = iv;
            } else {
                assertTrue(inductionVariable.getLoop().isCounted(), "must be counted");
                if (staticCheck != null) {
                    assert staticProperty != null;
                    if (staticCheck.test(inductionVariable)) {
                        node = ConstantNode.forIntegerStamp(stamp(NodeView.DEFAULT), staticProperty.get(inductionVariable), graph());
                    }
                }
                if (node == null) {
                    node = property.get(inductionVariable);
                }
            }
            replaceAtUsagesAndDelete(node);
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            gen.setResult(this, gen.operand(iv));
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, CountedLoopTest.class);
        registerPlugins(r, JavaKind.Int);
        registerPlugins(r, JavaKind.Long);
        super.registerInvocationPlugins(invocationPlugins);
    }

    private void registerPlugins(Registration r, JavaKind ivKind) {
        r.register(new InvocationPlugin("get", IVProperty.class, ivKind.toJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2) {
                IVProperty property = null;
                if (arg1.isConstant()) {
                    property = getSnippetReflection().asObject(IVProperty.class, arg1.asJavaConstant());
                }
                if (property != null) {
                    b.addPush(ivKind, new IVPropertyNode(property, null, null, arg2, loopCanBeRemoved));
                    return true;
                } else {
                    return false;
                }
            }
        });
        r.register(new InvocationPlugin("get", IVProperty.class, StaticIVProperty.class, IVPredicate.class, ivKind.toJavaClass()) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4) {
                IVProperty property = null;
                StaticIVProperty staticProperty = null;
                IVPredicate staticCheck = null;
                if (arg1.isConstant()) {
                    property = getSnippetReflection().asObject(IVProperty.class, arg1.asJavaConstant());
                }
                if (arg2.isConstant()) {
                    staticProperty = getSnippetReflection().asObject(StaticIVProperty.class, arg2.asJavaConstant());
                }
                if (arg3.isConstant()) {
                    staticCheck = getSnippetReflection().asObject(IVPredicate.class, arg3.asJavaConstant());
                }
                if (property != null && staticProperty != null && staticCheck != null) {
                    b.addPush(ivKind, new IVPropertyNode(property, staticProperty, staticCheck, arg4, loopCanBeRemoved));
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        LoopsData loops = getDefaultMidTierContext().getLoopsDataProvider().getLoopsData(graph);
        loops.detectCountedLoops();
        for (Loop lex : loops.countedLoops()) {
            lex.counted().createOverFlowGuard();
        }
        for (IVPropertyNode node : graph.getNodes().filter(IVPropertyNode.class)) {
            node.rewrite(loops);
        }
        assert graph.getNodes().filter(IVPropertyNode.class).isEmpty();
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        // Disable profile based optimizations
        return OptimisticOptimizations.NONE;
    }

    private Object[] argsToBind;
    private boolean loopCanBeRemoved;

    @Override
    protected Object[] getArgumentToBind() {
        return argsToBind;
    }

    public void testCounted(String snippetName, Object... args) {
        this.loopCanBeRemoved = false;
        test(snippetName, args);
        this.argsToBind = args;
        test(snippetName, args);
        this.argsToBind = null;
    }

    public void testCounted(String snippetName, Object start, Object limit, Object step) {
        testCounted(false, snippetName, start, limit, step);
    }

    public void testRemovableCounted(String snippetName, Object start, Object limit, Object step) {
        testCounted(true, snippetName, start, limit, step);
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        installedCodeOwner.reprofile();
        return super.getCode(installedCodeOwner, graph, forceCompile, installAsDefault, options);
    }

    public void testCounted(boolean removable, String snippetName, Object start, Object limit, Object step) {
        this.loopCanBeRemoved = removable;
        Object[] args = {start, limit, step};
        resetSpeculationLog();
        test(snippetName, args);
        this.argsToBind = args;
        resetSpeculationLog();
        test(snippetName, args);
        this.argsToBind = new Object[]{NO_BIND, NO_BIND, step};
        resetSpeculationLog();
        test(snippetName, args);
        this.argsToBind = new Object[]{start, NO_BIND, step};
        resetSpeculationLog();
        test(snippetName, args);
        this.argsToBind = null;
        this.loopCanBeRemoved = false;
    }
}
