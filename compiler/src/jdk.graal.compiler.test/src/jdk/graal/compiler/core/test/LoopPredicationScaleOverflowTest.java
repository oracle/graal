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
package jdk.graal.compiler.core.test;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.DeoptimizationReason;

/**
 * Tests that loop predication composes scaled induction variables using the arithmetic width of
 * each operation.
 */
public class LoopPredicationScaleOverflowTest extends GraalCompilerTest {

    /**
     * Each multiplication and negation is an int operation. Their composed scale does not fit in a
     * long, but its effective int value does.
     */
    public static int overflowingNestedScaleSnippet(int n) {
        int[] arr = new int[10];
        int s = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(1000, i < n); i++) {
            int index = -(-(i * -21845) * 42009217) * 6700417;
            s += arr[index];
            GraalDirectives.neverStripMine();
        }
        return s;
    }

    private OptionValues opts() {
        return new OptionValues(getInitialOptions(),
                        GraalOptions.SpeculativeGuardMovement, false,
                        GraalOptions.LoopPredication, true,
                        GraalOptions.LoopPeeling, false,
                        GraalOptions.FullUnroll, false,
                        GraalOptions.PartialUnroll, false,
                        GraalOptions.LoopUnswitch, false);
    }

    @Test
    public void testOverflowingNestedScaleBoundsCheck() throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("overflowingNestedScaleSnippet"), opts());
        assertBoundsCheckHoisting(lastCompiledGraph, true);
        Assert.assertTrue(code.isValid());
        try {
            code.executeVarargs(4);
            Assert.fail("expected ArrayIndexOutOfBoundsException from the overflowing nested index");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // Expected: the corrected entry predicate does not permit the unsafe loop execution.
        }
    }

    /**
     * Negating Integer.MIN_VALUE in int arithmetic produces Integer.MIN_VALUE. With limit zero the
     * only index is zero, so a predicate using the effective int scale must pass without deopting.
     */
    public static int minValueNegatedScaleSnippet(int[] arr, int limit) {
        if (arr.length == 0) {
            return 0;
        }
        int s = 0;
        for (int i = -1; GraalDirectives.injectIterationCount(1000, i < limit); i++) {
            int scaled = -(i * Integer.MIN_VALUE);
            int index = scaled + Integer.MIN_VALUE;
            s += arr[index];
            GraalDirectives.neverStripMine();
        }
        return s;
    }

    @Test
    public void testMinValueNegatedScale() throws InvalidInstalledCodeException {
        assertIndexScale("minValueNegatedScaleSnippet", Integer.MIN_VALUE);
        InstalledCode code = getCode(getResolvedJavaMethod("minValueNegatedScaleSnippet"), opts());
        assertBoundsCheckHoisting(lastCompiledGraph, true);
        Assert.assertEquals(42, code.executeVarargs(new int[]{42}, 0));
        Assert.assertTrue("correctly predicated execution must not invalidate the code", code.isValid());
    }

    /**
     * The coefficient of {@code offset - base} is the negated base coefficient. Negating an
     * Integer.MIN_VALUE coefficient must retain Integer.MIN_VALUE in int arithmetic.
     */
    public static int minValueReverseSubtractionSnippet(int[] arr, int limit) {
        if (arr.length == 0) {
            return 0;
        }
        int s = 0;
        for (int i = -1; GraalDirectives.injectIterationCount(1000, i < limit); i++) {
            int scaled = i * Integer.MIN_VALUE;
            int index = Integer.MIN_VALUE - scaled;
            s += arr[index];
            GraalDirectives.neverStripMine();
        }
        return s;
    }

    @Test
    public void testMinValueReverseSubtraction() throws InvalidInstalledCodeException {
        assertIndexScale("minValueReverseSubtractionSnippet", Integer.MIN_VALUE);
        InstalledCode code = getCode(getResolvedJavaMethod("minValueReverseSubtractionSnippet"), opts());
        assertBoundsCheckHoisting(lastCompiledGraph, true);
        Assert.assertEquals(42, code.executeVarargs(new int[]{42}, 0));
        Assert.assertTrue("correctly predicated execution must not invalidate the code", code.isValid());
    }

    /**
     * A lossy conversion prevents this index from being represented as one constant-scale affine
     * IV. The in-loop bounds check must therefore be preserved.
     */
    public static int overflowingConvertedCounterSnippet(int[] arr, int start, int stop, int scale, long offset, long limit) {
        int s = 0;
        for (int i = start; GraalDirectives.injectIterationCount(1000, (long) (i * scale) < limit); i++) {
            long chk = (long) (i * scale) + offset;
            s += arr[(int) chk];
            GraalDirectives.neverStripMine();
            if (i == stop) {
                break;
            }
        }
        return s;
    }

    @Test
    public void testOverflowingConvertedCounterBoundsCheck() throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("overflowingConvertedCounterSnippet"), opts());
        assertBoundsCheckHoisting(lastCompiledGraph, false);
        Assert.assertTrue(code.isValid());
        int[] arr = new int[100];
        try {
            code.executeVarargs(arr, -2049, 2047, 1_048_576, -2_146_435_072L, 6_442_450_944L);
            Assert.fail("expected ArrayIndexOutOfBoundsException from the overflowing converted array index");
        } catch (ArrayIndexOutOfBoundsException expected) {
            // Expected while the in-loop array bounds check is preserved.
        }
    }

    private static void assertBoundsCheckHoisting(StructuredGraph graph, boolean expectedHoisted) {
        ControlFlowGraph cfg = ControlFlowGraph.computeForSchedule(graph);
        Assert.assertTrue("expected a loop", cfg.getLoops().size() > 0);

        List<GuardNode> boundsCheckGuards = graph.getNodes(GuardNode.TYPE).stream().filter(n -> n.getReason().equals(DeoptimizationReason.BoundsCheckException)).toList();
        List<DeoptimizeNode> boundsCheckDeopts = graph.getNodes(DeoptimizeNode.TYPE).stream().filter(n -> n.getReason().equals(DeoptimizationReason.BoundsCheckException)).toList();
        Assert.assertTrue("expected a bounds check", !boundsCheckGuards.isEmpty() || !boundsCheckDeopts.isEmpty());

        boolean inLoop = false;
        for (GuardNode guard : boundsCheckGuards) {
            CFGLoop<HIRBlock> loop = cfg.getNodeToBlock().get(guard.getAnchor().asNode()).getLoop();
            inLoop |= loop != null;
        }
        for (DeoptimizeNode deopt : boundsCheckDeopts) {
            CFGLoop<HIRBlock> loop = cfg.getNodeToBlock().get(deopt).getFirstPredecessor().getLoop();
            inLoop |= loop != null;
        }
        Assert.assertEquals("bounds check hoisting", expectedHoisted, !inLoop);
    }

    private void assertIndexScale(String snippet, long expectedScale) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        LoopsData loops = getDefaultHighTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals("expected one loop", 1, loops.loops().size());
        Loop loop = loops.loops().get(0);
        Assert.assertTrue("expected a counted loop", loop.detectCounted());

        LoadIndexedNode load = graph.getNodes().filter(LoadIndexedNode.class).first();
        Assert.assertNotNull("expected an indexed load", load);
        EconomicMap<Node, InductionVariable> inductionVariables = loop.getInductionVariables();
        InductionVariable indexIV = inductionVariables.get(load.index());
        Assert.assertNotNull("array index must be an induction variable", indexIV);

        InductionVariable counter = loop.counted().getLimitCheckedIV();
        Assert.assertTrue("array index must have a constant scale", indexIV.isConstantScale(counter));
        Assert.assertEquals("effective scale", expectedScale, indexIV.constantScale(counter));
    }
}
