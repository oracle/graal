/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.GraalCompiler;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.FinalCanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import org.junit.Test;

public class IntegerEqualsCanonicalizerTest extends GraalCompilerTest {

    @Test
    public void testSubtractEqualsZero() {
        test("testSubtractEqualsZeroSnippet", "testSubtractEqualsZeroReference");
    }

    public static int testSubtractEqualsZeroReference(int a, int b) {
        if (a == b) {
            return 1;
        }
        return 0;
    }

    public static int testSubtractEqualsZeroSnippet(int a, int b) {
        if (a - b == 0) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testSubtractEqualsZeroLong() {
        test("testSubtractEqualsZeroLongSnippet", "testSubtractEqualsZeroLongReference");
    }

    public static int testSubtractEqualsZeroLongReference(long a, long b) {
        if (a == b) {
            return 1;
        }
        return 0;
    }

    public static int testSubtractEqualsZeroLongSnippet(long a, long b) {
        if (a - b == 0) {
            return 1;
        }
        return 0;
    }

    /**
     * Tests the canonicalization of (x >>> const) == 0 to x |test| (-1 << const).
     */
    @Test
    public void testShiftEquals() {
        test("testShiftEqualsSnippet", "testShiftEqualsReference");
    }

    @SuppressWarnings("unused") private static int field;

    public static void testShiftEqualsSnippet(int x, int[] array, int y) {
        // optimize
        field = (x >>> 10) == 0 ? 1 : 0;
        field = (array.length >> 10) == 0 ? 1 : 0;
        field = (x << 10) == 0 ? 1 : 0;
        // don't optimize
        field = (x >> 10) == 0 ? 1 : 0;
        field = (x >>> y) == 0 ? 1 : 0;
        field = (x >> y) == 0 ? 1 : 0;
        field = (x << y) == 0 ? 1 : 0;
        field = (x >>> y) == 1 ? 1 : 0;
        field = (x >> y) == 1 ? 1 : 0;
        field = (x << y) == 1 ? 1 : 0;
    }

    public static void testShiftEqualsReference(int x, int[] array, int y) {
        field = (x & 0xfffffc00) == 0 ? 1 : 0;
        field = (array.length & 0xfffffc00) == 0 ? 1 : 0;
        field = (x & 0x3fffff) == 0 ? 1 : 0;
        // don't optimize signed right shifts
        field = (x >> 10) == 0 ? 1 : 0;
        // don't optimize no-constant shift amounts
        field = (x >>> y) == 0 ? 1 : 0;
        field = (x >> y) == 0 ? 1 : 0;
        field = (x << y) == 0 ? 1 : 0;
        // don't optimize non-zero comparisons
        field = (x >>> y) == 1 ? 1 : 0;
        field = (x >> y) == 1 ? 1 : 0;
        field = (x << y) == 1 ? 1 : 0;
    }

    @Test
    public void testCompare() {
        test("testCompareSnippet", "testCompareReference");
    }

    public static void testCompareSnippet(int x, int y, int[] array1, int[] array2) {
        int tempX = x;
        int array1Length = array1.length;
        int array2Length = array2.length;
        // optimize
        field = x == tempX ? 1 : 0;
        field = x != tempX ? 1 : 0;
        field = array1Length != (-1 - array2Length) ? 1 : 0;
        field = array1Length == (-1 - array2Length) ? 1 : 0;
        // don't optimize
        field = x == y ? 1 : 0;
        field = array1Length == array2Length ? 1 : 0;
        field = array1Length == (-array2Length) ? 1 : 0;
    }

    public static void testCompareReference(int x, int y, int[] array1, int[] array2) {
        int array1Length = array1.length;
        int array2Length = array2.length;
        // optimize
        field = 1;
        field = 0;
        field = 1;
        field = 0;
        // don't optimize (overlapping value ranges)
        field = x == y ? 1 : 0;
        field = array1Length == array2Length ? 1 : 0;
        field = array1Length == (-array2Length) ? 1 : 0;
    }

    public static boolean testNormalIntegerTest(int a) {
        return (a & 8) != 0;
    }

    public static boolean testAlternateIntegerTest(int a) {
        return (a & 8) == 8;
    }

    @Test
    public void testIntegerTest() {
        test("testNormalIntegerTest", "testAlternateIntegerTest");
    }

    public static boolean testEqualsNotLeftSnippet(int x) {
        return ~x == x;
    }

    public static boolean testEqualsNotRightSnippet(int x) {
        return x == ~x;
    }

    @SuppressWarnings("unused")
    public static boolean testEqualsNotReferenceSnippet(int x) {
        return false;
    }

    @Test
    public void testEqualsNot() {
        test("testEqualsNotLeftSnippet", "testEqualsNotReferenceSnippet");
        test("testEqualsNotRightSnippet", "testEqualsNotReferenceSnippet");
    }

    @Test
    public void testXorEquality() {
        // (x ^ y) == (x ^ z) is equivalent to y == z
        StructuredGraph g = parseEager("testXorEqualsSnippet", AllowAssumptions.NO);
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());
        assert g.getNodes().filter(n -> n instanceof XorNode).count() == 0 : "Unexpected node count!";
        assert g.getNodes().filter(n -> n instanceof ConditionalNode).count() == 1 : "Unexpected node count!";
        assert g.getNodes().filter(n -> n instanceof StoreFieldNode).count() == 4 : "Unexpected node count!";

        // test graph equivalence to reference snippet
        test("testXorEqualsSnippet", "testXorEqualsReference");
    }

    public static void testXorEqualsSnippet(int x, int y, int z) {
        field = (x ^ y) == (x ^ z) ? 1 : 0;
        field = (x ^ y) == (z ^ x) ? 1 : 0;
        field = (y ^ x) == (x ^ z) ? 1 : 0;
        field = (y ^ x) == (z ^ x) ? 1 : 0;
    }

    public static void testXorEqualsReference(@SuppressWarnings("unused") int x, int y, int z) {
        field = y == z ? 1 : 0;
        field = y == z ? 1 : 0;
        field = y == z ? 1 : 0;
        field = y == z ? 1 : 0;
    }

    @Test
    public void testXorNeutral() {
        // (x ^ y) == x is equivalent to y == 0
        StructuredGraph g = parseEager("testXorNeutralSnippet", AllowAssumptions.NO);
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());
        assert g.getNodes().filter(n -> n instanceof XorNode).count() == 0 : "Unexpected node count!";
        assert g.getNodes().filter(n -> n instanceof ConditionalNode).count() == 2 : "Unexpected node count!";
        assert g.getNodes().filter(n -> n instanceof StoreFieldNode).count() == 4 : "Unexpected node count!";

        // test graph equivalence to reference snippet
        test("testXorNeutralSnippet", "testXorNeutralReference");
    }

    public static void testXorNeutralSnippet(int x, int y) {
        field = (x ^ y) == x ? 1 : 0;
        field = x == (x ^ y) ? 1 : 0;
        field = (x ^ y) == y ? 1 : 0;
        field = y == (x ^ y) ? 1 : 0;
    }

    public static void testXorNeutralReference(int x, int y) {
        field = y == 0 ? 1 : 0;
        field = y == 0 ? 1 : 0;
        field = x == 0 ? 1 : 0;
        field = x == 0 ? 1 : 0;
    }

    @Test
    public void testXorEqualsZero() {
        // (x ^ y) == 0 is equivalent to x == y
        StructuredGraph g = parseEager("testXorEqualsZeroSnippet", AllowAssumptions.NO);
        CanonicalizerPhase.create().apply(g, getDefaultHighTierContext());
        assert g.getNodes().filter(n -> n instanceof XorNode).count() == 0 : "Unexpected node count!";
        assert g.getNodes().filter(n -> n instanceof ConditionalNode).count() == 1 : "Unexpected node count!";
        assert g.getNodes().filter(n -> n instanceof StoreFieldNode).count() == 1 : "Unexpected node count!";

        // test graph equivalence to reference snippet
        test("testXorEqualsZeroSnippet", "testXorEqualsZeroReference");
    }

    public static void testXorEqualsZeroSnippet(int x, int y) {
        field = (x ^ y) == 0 ? 1 : 0;
    }

    public static void testXorEqualsZeroReference(int x, int y) {
        field = x == y ? 1 : 0;
    }

    @Test
    public void testInsertIntegerEquals() {
        StructuredGraph graph = parseEager("testInsertIntegerEqualsSnippet", AllowAssumptions.NO);

        Suites suites = createSuites(graph.getOptions());
        int idx = suites.getLowTier().findPhase(FinalCanonicalizerPhase.class).previousIndex();

        suites.getLowTier().insertAtIndex(idx, new BasePhase<LowTierContext>() {

            @Override
            protected void run(StructuredGraph g, LowTierContext context) {
                // make "a == 0 ? 3 : b" to "(a == 0 ? 3 : b) == 3 ? 1 : 0"
                ValueNode cond = (ValueNode) g.getNodes().stream().filter(n -> n instanceof ConditionalNode).toList().get(0);
                ValueNode y = g.addOrUnique(ConstantNode.forInt(3, g));

                LogicNode iEq = g.addOrUnique(IntegerEqualsNode.create(cond, y, NodeView.DEFAULT));
                ValueNode cond2 = g.addOrUnique(ConditionalNode.create(iEq, ConstantNode.forInt(1, g), ConstantNode.forInt(0, g), NodeView.DEFAULT));

                cond.replaceAtUsages(cond2, n -> n instanceof ReturnNode);
            }

            @Override
            public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
                return ALWAYS_APPLICABLE;
            }
        });

        GraalCompiler.emitFrontEnd(getProviders(), getBackend(), graph, getDefaultGraphBuilderSuite(), getOptimisticOptimizations(), graph.getProfilingInfo(), suites);

        // create reference snippet
        StructuredGraph ref = parseEager("testInsertIntegerEqualsReference", AllowAssumptions.NO);
        GraalCompiler.emitFrontEnd(getProviders(), getBackend(), ref, getDefaultGraphBuilderSuite(), getOptimisticOptimizations(), ref.getProfilingInfo(), createSuites(ref.getOptions()));

        assertEquals(ref, graph);
    }

    public static int testInsertIntegerEqualsSnippet(int a, int b) {
        int result = a == 0 ? 3 : b;
        GraalDirectives.controlFlowAnchor();
        return result;
    }

    public static int testInsertIntegerEqualsReference(int a, int b) {
        int result = (a == 0 ? 3 : b) == 3 ? 1 : 0;
        GraalDirectives.controlFlowAnchor();
        return result;
    }

    private void test(String snippet, String referenceSnippet) {
        StructuredGraph graph = getCanonicalizedGraph(snippet);
        StructuredGraph referenceGraph = getCanonicalizedGraph(referenceSnippet);
        assertEquals(referenceGraph, graph);
    }

    private StructuredGraph getCanonicalizedGraph(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        createCanonicalizerPhase().apply(graph, getProviders());
        for (FrameState state : graph.getNodes(FrameState.TYPE).snapshot()) {
            state.replaceAtUsages(null);
            state.safeDelete();
        }
        return graph;
    }
}
