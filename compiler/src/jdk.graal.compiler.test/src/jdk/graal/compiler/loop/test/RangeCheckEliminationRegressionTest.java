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

import java.util.Objects;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.phases.MidTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.core.test.TestBasePhase;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.loop.phases.CountedStripMiningPhase;
import jdk.graal.compiler.loop.phases.RangeCheckEliminationPhase;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedOffsetInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedScaledInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.vector.replacements.VectorIntrinsics;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class RangeCheckEliminationRegressionTest extends GraalCompilerTest {

    static final long veryLargeScale = 1 << 29;

    private static void test1Snippet(long range, long j) {
        Objects.checkIndex(0, range);
        for (long i = 0; i < 100; i++) {
            if (i == j) {
                Objects.checkIndex(veryLargeScale * i, range);
            }
        }
    }

    private static void test2Snippet(long range, long j, long stop) {
        Objects.checkIndex(0, range);
        for (long i = 0; i < stop; i++) {
            if (i == j) {
                Objects.checkIndex(veryLargeScale * i, range);
            }
        }
    }

    private static void runAndExpectException(InstalledCode code, Class<?> exceptionClass, Object... args) {
        boolean exception = false;
        try {
            Object result = code.executeVarargs(args);
            TTY.printf("Execution result = %s%n", result);
        } catch (Throwable t) {
            if (t.getClass().equals(exceptionClass)) {
                exception = true;
            } else {
                throw GraalError.shouldNotReachHere(t);
            }
        }
        if (!exception) {
            throw new RuntimeException("Expected exception not thrown");
        }
    }

    @BeforeClass
    public static void before() {
        for (int i = 0; i < 20_000; i++) {
            test1Snippet(Integer.MAX_VALUE, 0);
            test2Snippet(Integer.MAX_VALUE, 0, 100);
        }
    }

    @Test
    public void test01() {
        InstalledCode code = getCode(getResolvedJavaMethod("test1Snippet"), getInitialOptions());
        runAndExpectException(code, IndexOutOfBoundsException.class, (long) Integer.MAX_VALUE, 10L);
        assert !code.isValid();
        code = getCode(getResolvedJavaMethod("test1Snippet"), getInitialOptions());
        runAndExpectException(code, IndexOutOfBoundsException.class, (long) Integer.MAX_VALUE, 10L);
        assert !code.isValid();
    }

    @Test
    public void test02() {
        InstalledCode code = getCode(getResolvedJavaMethod("test2Snippet"), getInitialOptions());
        runAndExpectException(code, IndexOutOfBoundsException.class, (long) Integer.MAX_VALUE, 10L, 100L);
        assert !code.isValid();
        code = getCode(getResolvedJavaMethod("test2Snippet"), getInitialOptions());
        runAndExpectException(code, IndexOutOfBoundsException.class, (long) Integer.MAX_VALUE, 10L, 100L);
        assert !code.isValid();
    }

    private static long sameStrideIndependentIVSnippet(int independentStart, int stop, long range) {
        long result = 0;
        int independentIV = independentStart;
        for (int i = 0; GraalDirectives.injectIterationCount(10_000, i < stop); i++, independentIV++) {
            long index = independentIV;
            if (Long.compareUnsigned(index, range) >= 0) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    private boolean builderOmitBytecodeExceptions;
    private boolean verifyObservedRCE;
    private boolean verifyScaledOffsetIVSubtraction;
    private ScaledOffsetIVSubtraction expectedScaledOffsetIVSubtraction;
    private boolean observedRCE;
    private GuardNode rangeCheckGuard;
    private CompareNode rangeCheckCondition;
    private long originalIVScale;

    private enum ScaledOffsetIVSubtraction {
        SCALED_IV_MINUS_OFFSET,
        OFFSET_MINUS_SCALED_IV
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration configuration) {
        GraphBuilderConfiguration edited = super.editGraphBuilderConfiguration(configuration);
        if (builderOmitBytecodeExceptions) {
            /*
             * Used to keep Objects.checkIndex intrinsified regardless of exception profiles collected by
             * other tests. This makes speculative guard movement deterministic for the offset-scaled-IV
             * subtraction tests.
             */
            return edited.withBytecodeExceptionMode(GraphBuilderConfiguration.BytecodeExceptionMode.OmitAll);
        }
        return edited;
    }

    private void assertExpectedScale(CompareNode replacement) {
        long expectedScale = expectedScaledOffsetIVSubtraction == ScaledOffsetIVSubtraction.SCALED_IV_MINUS_OFFSET ? originalIVScale : -originalIVScale;
        Assert.assertEquals("Replacement range check must use the normalized scale", Long.valueOf(expectedScale), findRewrittenScale(replacement));
    }

    private final class RCECheckPhase extends TestBasePhase<MidTierContext> {
        private final boolean afterRCE;

        RCECheckPhase(boolean afterRCE) {
            this.afterRCE = afterRCE;
        }

        @Override
        protected void run(StructuredGraph graph, MidTierContext context) {
            if (afterRCE) {
                verifyAfterRCE();
            } else {
                verifyBeforeRCE(graph, context);
            }
        }
    }

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        if (verifyObservedRCE) {
            if (verifyScaledOffsetIVSubtraction) {
                Assert.assertNotNull(expectedScaledOffsetIVSubtraction);
            }

            suites.getMidTier().insertBeforePhase(RangeCheckEliminationPhase.class, new RCECheckPhase(false));
            suites.getMidTier().insertAfterPhase(RangeCheckEliminationPhase.class, new RCECheckPhase(true));
        }
        return suites;
    }

    private static DerivedInductionVariable findDerivedIV(CompareNode compare, Loop loop) {
        for (ValueNode input : compare.inputs().filter(ValueNode.class)) {
            InductionVariable iv = loop.getInductionVariables().get(input);
            if (iv instanceof DerivedInductionVariable derivedIV) {
                return derivedIV;
            }
        }
        return null;
    }

    private static Long findRewrittenScale(CompareNode compare) {
        for (ValueNode cmpInput : compare.inputs().filter(ValueNode.class)) {
            if (cmpInput instanceof AddNode add) {
                for (ValueNode addInput : add.inputs().filter(ValueNode.class)) {
                    if (addInput instanceof MulNode mul && IntegerStamp.getBits(mul.stamp(NodeView.DEFAULT)) == Integer.SIZE) {
                        if (mul.getY().isConstant()) {
                            // Canonicalized mul has constant in the RHS
                            return mul.getY().asJavaConstant().asLong();
                        }
                    }
                }
            }
        }
        return null;
    }

    private void verifyBeforeRCE(StructuredGraph graph, MidTierContext context) {
        LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
        loopsData.detectCountedLoops();
        for (Loop loop : loopsData.countedLoops()) {
            for (GuardNode guard : loop.inside().nodes().filter(GuardNode.class)) {
                if (!guard.isNegated() && guard.getCondition() instanceof CompareNode compare &&
                                (!verifyScaledOffsetIVSubtraction || guard.getReason() == DeoptimizationReason.BoundsCheckException)) {
                    DerivedInductionVariable derivedIV = findDerivedIV(compare, loop);
                    if (derivedIV != null) {
                        verifyRangeCheckBeforeRCE(guard, compare, derivedIV);
                    }
                }
            }
        }
        Assert.assertNotNull("Expected a range check guard", rangeCheckGuard);
    }

    private void verifyRangeCheckBeforeRCE(GuardNode guard, CompareNode compare, DerivedInductionVariable derivedIV) {
        Assert.assertNull("Found more than one range check", rangeCheckGuard);
        DerivedOffsetInductionVariable offsetIV = null;
        if (derivedIV instanceof DerivedOffsetInductionVariable) {
            offsetIV = (DerivedOffsetInductionVariable) derivedIV;
            Assert.assertTrue("Offset IV must be an addition or subtraction", derivedIV.valueNode() instanceof AddNode || derivedIV.valueNode() instanceof SubNode);
        }
        if (derivedIV.getBase() instanceof DerivedScaledInductionVariable scaledIV) {
            Assert.assertTrue("Scale must be constant", scaledIV.getScale().isConstant());
            originalIVScale = scaledIV.getScale().asJavaConstant().asLong();
            if (verifyScaledOffsetIVSubtraction) {
                Assert.assertNotNull("Not a scaled offset IV", offsetIV);
                Assert.assertTrue("Subtraction white-box test must find a subtraction IV", derivedIV.valueNode() instanceof SubNode);
                SubNode sub = (SubNode) derivedIV.valueNode();
                if (expectedScaledOffsetIVSubtraction == ScaledOffsetIVSubtraction.SCALED_IV_MINUS_OFFSET) {
                    Assert.assertSame("Scaled IV must be subtraction LHS", derivedIV.getBase().valueNode(), sub.getX());
                    Assert.assertSame("Offset must be subtraction RHS", offsetIV.getOffset(), sub.getY());
                } else {
                    Assert.assertSame("Offset must be subtraction LHS", offsetIV.getOffset(), sub.getX());
                    Assert.assertSame("Scaled IV must be subtraction RHS", derivedIV.getBase().valueNode(), sub.getY());
                }
            }
            Assert.assertEquals("Original range check must use long values", Long.SIZE,
                            IntegerStamp.getBits(derivedIV.valueNode().stamp(NodeView.DEFAULT)));
        } else {
            Assert.assertFalse("Not a scaled offset IV", verifyScaledOffsetIVSubtraction);
        }
        rangeCheckGuard = guard;
        rangeCheckCondition = compare;
    }

    private void verifyAfterRCE() {
        Assert.assertTrue("Range check guard must remain alive", rangeCheckGuard.isAlive());
        Assert.assertNotSame("RCE must replace the range check condition", rangeCheckCondition, rangeCheckGuard.getCondition());
        Assert.assertTrue("Range check must be a comparison", rangeCheckGuard.getCondition() instanceof CompareNode);
        CompareNode replacement = (CompareNode) rangeCheckGuard.getCondition();
        Assert.assertEquals("Replacement range check must use int values", Integer.SIZE, IntegerStamp.getBits(replacement.getX().stamp(NodeView.DEFAULT)));
        Assert.assertEquals("Replacement range check must use int values", Integer.SIZE, IntegerStamp.getBits(replacement.getY().stamp(NodeView.DEFAULT)));
        if (verifyScaledOffsetIVSubtraction) {
            assertExpectedScale(replacement);
        }
        observedRCE = true;
    }

    private static final int[] array = new int[3000];

    private static int testScaledIVMinusOffsetSnippet(int target, long offset) {
        int result = 0;
        for (int i = 0; i < 100; i++) {
            if (i == target) {
                result += i;
                long index = (50L * i) - offset;
                result += array[(int) Objects.checkIndex(index, array.length)];
            }
        }
        return result;
    }

    private static int testOffsetMinusScaledIVSnippet(int target, long offset) {
        int result = 0;
        for (int i = 0; i < 100; i++) {
            if (i == target) {
                result += i;
                long index = offset - (50L * i);
                result += array[(int) Objects.checkIndex(index, array.length)];
            }
        }
        return result;
    }

    @Test
    public void testSameStrideIndependentIV() throws Throwable {
        OptionValues options = new OptionValues(getInitialOptions(),
                        CountedStripMiningPhase.Options.StripMineALot, true,
                        CountedStripMiningPhase.Options.CountedStripMiningInnerLoopTrips, 16,
                        RangeCheckEliminationPhase.Options.ForceRCE, true,
                        GraalOptions.SpeculativeGuardMovement, false,
                        VectorIntrinsics.Options.Vectorization, false);
        InstalledCode code = getCode(getResolvedJavaMethod("sameStrideIndependentIVSnippet"), options);
        Assert.assertEquals(3L, code.executeVarargs(Integer.MAX_VALUE - 1, 3, 3_000_000_000L));
        Assert.assertFalse("RCE must not use the limit IV bounds for an independently wrapping IV", code.isValid());
    }

    @Test
    public void test03ScaledIVMinusOffset() throws Exception {
        builderOmitBytecodeExceptions = true;
        ResolvedJavaMethod method = getResolvedJavaMethod("testScaledIVMinusOffsetSnippet");

        // The first compilation speculatively moves the range-check guard and causes execution to deopt.
        InstalledCode code = getCode(method);
        Assert.assertEquals(10, code.executeVarargs(10, 100L));
        Assert.assertFalse("The conservative speculative guard should deoptimize", code.isValid());

        // Recompilation exercises RCE with that speculation disabled.
        verifyObservedRCE = true;
        code = getCode(method);
        runAndExpectException(code, IndexOutOfBoundsException.class, 1, 100L);
    }

    private void verifyScaledOffsetIVSubtraction(String methodName, ScaledOffsetIVSubtraction expectedSubtraction) {
        builderOmitBytecodeExceptions = true;
        verifyObservedRCE = true;
        expectedScaledOffsetIVSubtraction = expectedSubtraction;
        verifyScaledOffsetIVSubtraction = true;
        // Disable speculative guard movement so the target guard reaches RCE
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.SpeculativeGuardMovement, false,
                        RangeCheckEliminationPhase.Options.ForceRCE, true);
        getCode(getResolvedJavaMethod(methodName), options);
    }

    @Test
    public void test04ScaledIVMinusOffsetWhiteBox() {
        verifyScaledOffsetIVSubtraction("testScaledIVMinusOffsetSnippet",
                        ScaledOffsetIVSubtraction.SCALED_IV_MINUS_OFFSET);
    }

    @Test
    public void test05OffsetMinusScaledIVWhiteBox() {
        verifyScaledOffsetIVSubtraction("testOffsetMinusScaledIVSnippet",
                        ScaledOffsetIVSubtraction.OFFSET_MINUS_SCALED_IV);
    }

    private static int testNegatedScaleOverflowSnippet(int target) {
        int result = 0;
        long offset = GraalDirectives.opaque(-(long) Integer.MIN_VALUE);
        for (int i = 0; i < 100; i++) {
            if (i == target) {
                result += i;
                /* at i == 1, the product is Integer.MIN_VALUE -> actual index is 2^32,
                 * which must fail the range check.
                 * negating the scale first when extracting the derived IV overflows and
                 * the later RC models index as offset + Integer.MIN_VALUE === 0
                 * which would incorrectly pass
                 */
                long index = offset - (long) (Integer.MIN_VALUE * i);
                Objects.checkIndex(index, array.length);
            }
        }
        return result;
    }

    @Test
    public void test06NegatedScaleOverflow() {
        builderOmitBytecodeExceptions = true;
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.SpeculativeGuardMovement, false,
                        RangeCheckEliminationPhase.Options.ForceRCE, true);
        InstalledCode code = getCode(getResolvedJavaMethod("testNegatedScaleOverflowSnippet"), options);

        runAndExpectException(code, IndexOutOfBoundsException.class, 1);
        Assert.assertFalse("The out-of-bounds index must invalidate compiled code", code.isValid());
    }

    private static long testNegatedIntScaleOverflowSnippet(int start, int stop) {
        long sum = 0;
        long offset = -(long) Integer.MIN_VALUE;
        long range = 42;
        for (int i = start; i < stop; i++) {
            // at i == 2^29, i * 4 == Integer.MIN_VALUE in int arithmetic
            // so index == -(long)Int.MIN - (long)Int.MIN == +2^32
            // but if negation is done in int first then the range check would use index == 0
            long index = offset - (long) (i * 4);
            sum += Objects.checkIndex(index, range);
        }
        return sum;
    }

    @Test
    public void testNegatedIntScaleOverflow() throws Throwable {
        builderOmitBytecodeExceptions = true;
        ResolvedJavaMethod method = getResolvedJavaMethod("testNegatedIntScaleOverflowSnippet");
        int start = (1 << 29);
        int stop = (1 << 29) + 1;
        runAndExpectExceptionNoRCE(method, start, stop);
        method.reprofile();
        resetSpeculationLog();

        OptionValues options = new OptionValues(getC2InvariantReproducerOptions(), GraalOptions.LoopPredication, false);
        InstalledCode code = getCode(method, null, true, true, options);
        runAndExpectException(code, IndexOutOfBoundsException.class, start, stop);
        Assert.assertFalse("The out-of-bounds index must invalidate compiled code", code.isValid());
    }

    private static long signedLongRangeCheckSnippet(long start, long stop, long range) {
        long result = 0;
        for (long i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            long index = -50L * i + 87L;
            // The signed comparison must not be changed by narrowing a negative long index to int.
            // the index can be below Integer.MIN_VALUE, so still less than range
            if (index < range) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result += i;
        }
        return result;
    }

    @Test
    public void testSignedLongRangeCheckOutsideIntRange() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("signedLongRangeCheckSnippet");
        // Produces negative checked indices far below Integer.MIN_VALUE.
        Object[] args = {195_834_104_283L, 195_834_104_383L, 1_743_716_353_846_474_685L};
        assertExpectedDeoptNoRCE(method, 19_583_410_433_250L, args);

        InstalledCode code = getCode(method);
        code.executeVarargs(args);
        Assert.assertFalse("The signed range check or its int-range speculation must deoptimize", code.isValid());

        method.reprofile();
        code = getCode(method);
        code.executeVarargs(args);
        Assert.assertFalse("The original signed range check must deoptimize after speculation fails", code.isValid());
    }

    private static long mirroredSignedSnippet(long start, long stop, long range) {
        long result = 0;
        for (long i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            long index = -50L * i + 2;
            if (range < index) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testMirroredSignedOneIteration() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("mirroredSignedSnippet");
        Object[] args = {0L, 1L, 1L};
        assertExpectedDeoptNoRCE(method, 1L, args);

        InstalledCode code = getCode(method);
        code.executeVarargs(args);
        assertFalse("Must deoptimize", code.isValid());
    }

    private static long mirroredUnsignedTruncationSnippet(long start, long stop, long range) {
        long result = 0;
        for (long i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            /* at i == 2^32, index is -2^32. the original unsigned comparison 1 <u index
             * is true, but RCE clamps the range to 0 and truncates the index to 0, producing
             * the false comparison 0 <u 0.
             */
            long index = -i;
            if (Long.compareUnsigned(range, index) < 0) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testMirroredUnsignedTruncation() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("mirroredUnsignedTruncationSnippet");
        long start = 1L << 32;
        Object[] args = {start, start + 1, 1L};
        assertExpectedDeoptNoRCE(method, 1L, args);

        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        Assert.assertEquals(1L, code.executeVarargs(args));
        Assert.assertFalse("The mirrored unsigned comparison must deoptimize before its negative long index is truncated",
                        code.isValid());
    }

    private static OptionValues getC2InvariantReproducerOptions(boolean enableRCE) {
        return new OptionValues(getInitialOptions(),
                        MidTier.Options.StripMineCountedLoops, true,
                        CountedStripMiningPhase.Options.StripMineALot, true,
                        MidTier.Options.StripMineNonCountedLoops, false,
                        RangeCheckEliminationPhase.Options.RangeCheckElimination, enableRCE,
                        RangeCheckEliminationPhase.Options.ForceRCE, enableRCE,
                        GraalOptions.SpeculativeGuardMovement, false);
    }

    private static OptionValues getC2InvariantReproducerOptions() {
        return getC2InvariantReproducerOptions(true);
    }

    private void assertExpectedDeoptNoRCE(ResolvedJavaMethod method, Object expected, Object... args) throws Throwable {
        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions(false));
        Assert.assertEquals(expected, code.executeVarargs(args));
        Assert.assertFalse("The original guard must deoptimize with RCE disabled", code.isValid());
        method.reprofile();
        resetSpeculationLog();
    }

    private void runAndExpectExceptionNoRCE(ResolvedJavaMethod method, Object... args) {
        builderOmitBytecodeExceptions = true;
        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions(false));
        runAndExpectException(code, IndexOutOfBoundsException.class, args);
    }

    private static long shortScaleOuterIVOverflowSnippet(int start, int stop) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            // if the int product wraps and becomes negative, the check should fail
            long index = i * 500_000;
            result += Objects.checkIndex(index, Long.MAX_VALUE);
        }
        return result;
    }

    @Test
    public void testShortScaleOuterIVOverflow() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("shortScaleOuterIVOverflowSnippet");
        Object[] args = {4_300, 8_397};
        runAndExpectExceptionNoRCE(method, args);

        verifyObservedRCE = true;
        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        runAndExpectException(code, IndexOutOfBoundsException.class, args);
    }

    private static long shortScaleSignedSnippet(int start, int stop, int offset) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            // product wraps in int before being sign extended to long
            long index = (long) (i * 3_000_000) + offset;
            if (index < -3_000_000_000L) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testShortScaleSignedComparison() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("shortScaleSignedSnippet");
        OptionValues options = getC2InvariantReproducerOptions();
        Object[] args = {1, 4_097, Integer.MIN_VALUE};
        assertExpectedDeoptNoRCE(method, 4_096L, args);

        InstalledCode code = getCode(method, null, true, true, options);
        Assert.assertEquals(4_096L, code.executeVarargs(args));
        Assert.assertFalse("The non-negative-range speculation must deoptimize", code.isValid());

        method.reprofile();
        code = getCode(method, null, true, true, options);
        Assert.assertEquals(4_096L, code.executeVarargs(args));
        Assert.assertFalse("The preliminary short-scale rewrite must not remove deoptimization from a negated signed guard", code.isValid());
    }

    private static long shortScaleNegatedUnsignedSnippet(int start, int stop) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            // int product wraps before the offset is added in long arithmetic
            long index = (long) (i * 1_000_000) + Integer.MAX_VALUE;
            if (Long.compareUnsigned(index, 1_000_000_000L) < 0) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testShortScaleNegatedUnsignedGuard() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("shortScaleNegatedUnsignedSnippet");
        Object[] args = {0, 2_149};
        assertExpectedDeoptNoRCE(method, 2_149L, args);

        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        Assert.assertEquals(2_149L, code.executeVarargs(args));
        Assert.assertFalse("A short-scale rewrite must not miss deoptimization on a true unsigned comparison", code.isValid());
    }

    private static long zeroExtendedIVSnippet(int start, int stop) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            // when i < 0, Integer.toUnsignedLong(i) is very big, negating it is a very large negative long
            // ignoring zero extension would treat i < 0 as -i and pass the ckeck if i was in [-99, -1]
            long index = -Integer.toUnsignedLong(i);
            result += Objects.checkIndex(index, 100L);
        }
        return result;
    }

    @Test
    public void testZeroExtendedIV() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("zeroExtendedIVSnippet");
        Object[] args = {-10, -9};
        runAndExpectExceptionNoRCE(method, args);

        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        runAndExpectException(code, IndexOutOfBoundsException.class, args);
    }

    private static long intDomainOffsetOverflowSnippet(int start, int stop, int offset) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            // if the int add overflows and becomes negative, the check should fail
            long index = (long) (i + offset);
            result += Objects.checkIndex(index, 3_000_000_000L);
        }
        return result;
    }

    @Test
    public void testIntDomainOffsetOverflow() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("intDomainOffsetOverflowSnippet");
        Object[] args = {Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 2};
        runAndExpectExceptionNoRCE(method, args);

        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        runAndExpectException(code, IndexOutOfBoundsException.class, args);
    }

    private static long shortScaleIntMinShiftSnippet(int start, int stop, int offset, long range) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            // shifting int by 31 is like multiplying by Integer.MIN_VALUE
            // widening the input before the shift instead acts as scaling by +2^31.
            long index = (long) (i << 31) + offset;
            if (Long.compareUnsigned(index, range) < 0) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testShortScaleIntMinShift() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("shortScaleIntMinShiftSnippet");
        Object[] args = {2, 4, 0, 1L};
        assertExpectedDeoptNoRCE(method, 2L, args);

        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        Assert.assertEquals(2L, code.executeVarargs(args));
        Assert.assertFalse("Widening an int shift by 31 must preserve its Integer.MIN_VALUE scale", code.isValid());
    }

    private static long shortScaleDynamicRangeClampSnippet(int start, int stop, long range) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            long index = (long) (i * 500_000);
            // force the nonnegative-range speculation to produce a refined R node, ensure
            // the short_scale rewrite computes the clamp from that
            if (Long.compareUnsigned(index, range) >= 0) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testShortScaleDynamicRangeClamp() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("shortScaleDynamicRangeClampSnippet");
        Object[] args = {4_300, 8_397, Long.MAX_VALUE};
        assertExpectedDeoptNoRCE(method, 4_097L, args);

        verifyObservedRCE = true;
        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        Assert.assertEquals(4_097L, code.executeVarargs(args));
        Assert.assertFalse("The short-scale clamp must use the proven non-negative range", code.isValid());
    }

    private static long shortScaleOffsetCancellationSnippet(int start, int stop, long offset, long range) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            // The int multiplication wraps before widening, the offset can move the widened product
            // into the valid range while the wrapped value remains invalid.
            long index = (long) (i * 1_000_000) + offset;
            if (Long.compareUnsigned(index, range) >= 0) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testShortScaleOffsetCancellation() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("shortScaleOffsetCancellationSnippet");
        Object[] args = {2_150, 2_151, -2_149_999_995L, 100L};
        assertExpectedDeoptNoRCE(method, 1L, args);

        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        Assert.assertEquals(1L, code.executeVarargs(args));
        Assert.assertFalse("Widening the overflowing int product must not remove the unsigned guard", code.isValid());
    }

    @Test
    public void testShortScaleOffsetCancellationNegativeOverflow() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("shortScaleOffsetCancellationSnippet");
        Object[] args = {-2_150, -2_149, 2_150_000_005L, 100L};
        assertExpectedDeoptNoRCE(method, 1L, args);

        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        Assert.assertEquals(1L, code.executeVarargs(args));
        Assert.assertFalse("Widening the underflowing int product must not remove the unsigned guard", code.isValid());
    }

    private static long shortScaleIntNegateSnippet(int start, int stop, int offset, long range) {
        long result = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++) {
            long index = (long) (-i) + offset;
            if (Long.compareUnsigned(index, range) >= 0) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testShortScaleIntNegate() throws Throwable {
        builderOmitBytecodeExceptions = true;
        verifyObservedRCE = true;
        ResolvedJavaMethod method = getResolvedJavaMethod("shortScaleIntNegateSnippet");
        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        Assert.assertEquals(100L, code.executeVarargs(-100, 0, 0, 1_000L));
    }

    private static long unrelatedIVStrideSnippet(int start, int stop, long range) {
        long result = 0;
        long unrelatedIV = 0;
        for (int i = start; GraalDirectives.injectIterationCount(10_000, i < stop); i++, unrelatedIV += 1_100) {
            long index = unrelatedIV * 1_000;
            if (Long.compareUnsigned(index, range) >= 0) {
                GraalDirectives.deoptimizeAndInvalidate();
            }
            result++;
        }
        return result;
    }

    @Test
    public void testAuxiliaryIVStride() throws Throwable {
        ResolvedJavaMethod method = getResolvedJavaMethod("unrelatedIVStrideSnippet");
        Object[] args = {0, 3_906, 4_294_800_000L};
        assertExpectedDeoptNoRCE(method, 3_906L, args);

        InstalledCode code = getCode(method, null, true, true, getC2InvariantReproducerOptions());
        Assert.assertEquals(3_906L, code.executeVarargs(args));
        Assert.assertFalse("RCE must bound the unrelated IV rather than the limit IV", code.isValid());
    }

}
