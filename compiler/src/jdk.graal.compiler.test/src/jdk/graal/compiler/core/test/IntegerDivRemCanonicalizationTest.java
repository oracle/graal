/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.calc.UnsignedDivNode;
import jdk.graal.compiler.nodes.calc.UnsignedRemNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class IntegerDivRemCanonicalizationTest extends GraalCompilerTest {

    public static int redundantRemNode(int a, int b) {
        int r = (a - a % b) / b;
        return r;
    }

    @Test
    public void testRedundantRemNode() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("redundantRemNode"));
        createCanonicalizerPhase().apply(graph, getProviders());
        // We expect the remainder to be canonicalized away.
        assertTrue(graph.getNodes().filter(SignedRemNode.class).count() == 0);
    }

    public static int unusedUnsignedDivNonZero(int a, int b) {
        int divisor = (b & 1023) + 1;
        @SuppressWarnings("unused")
        int unused = Integer.divideUnsigned(a, divisor);
        GraalDirectives.sideEffect();
        return a;
    }

    @Test
    public void testUnusedUnsignedDivNonZero() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unusedUnsignedDivNonZero"));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(UnsignedDivNode.class).count() == 0);
    }

    public static int unusedUnsignedDivNonZeroMaybeMinusOne(int a, int b) {
        int divisor = b | 1;
        @SuppressWarnings("unused")
        int unused = Integer.divideUnsigned(a, divisor);
        GraalDirectives.sideEffect();
        return a;
    }

    @Test
    public void testUnusedUnsignedDivNonZeroMaybeMinusOne() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unusedUnsignedDivNonZeroMaybeMinusOne"));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(UnsignedDivNode.class).count() == 0);
    }

    public static int unusedUnsignedDivMaybeZero(int a, int b) {
        @SuppressWarnings("unused")
        int unused = Integer.divideUnsigned(a, b);
        GraalDirectives.sideEffect();
        return a;
    }

    @Test
    public void testUnusedUnsignedDivMaybeZero() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unusedUnsignedDivMaybeZero"));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(UnsignedDivNode.class).count() == 1);
    }

    @Test
    public void testUnusedUnsignedDivMaybeZeroNonDeoptimizing() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unusedUnsignedDivMaybeZero"));
        UnsignedDivNode div = graph.getNodes().filter(UnsignedDivNode.class).first();
        div.setCanDeopt(false);
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(UnsignedDivNode.class).count() == 0);
    }

    public static int unusedUnsignedRemNonZero(int a, int b) {
        int divisor = (b & 1023) + 1;
        @SuppressWarnings("unused")
        int unused = Integer.remainderUnsigned(a, divisor);
        GraalDirectives.sideEffect();
        return a;
    }

    @Test
    public void testUnusedUnsignedRemNonZero() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unusedUnsignedRemNonZero"));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(UnsignedRemNode.class).count() == 0);
    }

    public static int unusedUnsignedRemNonZeroMaybeMinusOne(int a, int b) {
        int divisor = b | 1;
        @SuppressWarnings("unused")
        int unused = Integer.remainderUnsigned(a, divisor);
        GraalDirectives.sideEffect();
        return a;
    }

    @Test
    public void testUnusedUnsignedRemNonZeroMaybeMinusOne() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unusedUnsignedRemNonZeroMaybeMinusOne"));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(UnsignedRemNode.class).count() == 0);
    }

    public static int unusedUnsignedRemMaybeZero(int a, int b) {
        @SuppressWarnings("unused")
        int unused = Integer.remainderUnsigned(a, b);
        GraalDirectives.sideEffect();
        return a;
    }

    @Test
    public void testUnusedUnsignedRemMaybeZero() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unusedUnsignedRemMaybeZero"));
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(UnsignedRemNode.class).count() == 1);
    }

    @Test
    public void testUnusedUnsignedRemMaybeZeroNonDeoptimizing() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("unusedUnsignedRemMaybeZero"));
        UnsignedRemNode rem = graph.getNodes().filter(UnsignedRemNode.class).first();
        rem.setCanDeopt(false);
        createCanonicalizerPhase().apply(graph, getProviders());
        assertTrue(graph.getNodes().filter(UnsignedRemNode.class).count() == 0);
    }

    static Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
        if (stamp1.isEmpty()) {
            return stamp1;
        }
        if (stamp2.isEmpty()) {
            return stamp2;
        }
        IntegerStamp a = (IntegerStamp) stamp1;
        IntegerStamp b = (IntegerStamp) stamp2;
        assert a.getBits() == b.getBits();
        if (a.lowerBound() == a.upperBound() && b.lowerBound() == b.upperBound() && b.lowerBound() != 0) {
            long value = CodeUtil.convert(a.lowerBound() / b.lowerBound(), a.getBits(), false);
            return IntegerStamp.create(a.getBits(), value, value);
        } else if (b.isStrictlyPositive()) {
            long newLowerBound = a.lowerBound() < 0 ? a.lowerBound() / b.lowerBound() : a.lowerBound() / b.upperBound();
            long newUpperBound = a.upperBound() < 0 ? a.upperBound() / b.upperBound() : a.upperBound() / b.lowerBound();
            return IntegerStamp.create(a.getBits(), newLowerBound, newUpperBound);
        } else {
            return a.unrestricted();
        }
    }

    @Test
    public void testStamp() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.StressExplicitExceptionCode, true);
        test(opt, "foldStamp", IntegerStamp.create(32, Integer.MIN_VALUE, Integer.MAX_VALUE), IntegerStamp.create(32, 0, 0));
    }

    public static int narrowUnsignedDivisionSnippet(int value) {
        return value;
    }

    /** Compiles and executes unsigned division by two with an explicitly constructed narrow input. */
    private int executeNarrowUnsignedDivision(int bits, int value) throws InvalidInstalledCodeException {
        StructuredGraph graph = parseEager("narrowUnsignedDivisionSnippet", AllowAssumptions.YES);
        ValueNode input = bits == 32 ? graph.getParameter(0) : graph.addOrUnique(new NarrowNode(graph.getParameter(0), bits));
        ValueNode divisor = ConstantNode.forIntegerBits(bits, 2, graph);
        ValueNode result = graph.addOrUniqueWithInputs(UnsignedDivNode.create(input, divisor, null, NodeView.DEFAULT));
        Assert.assertFalse("Power-of-two division should use its rewrite", result instanceof UnsignedDivNode);
        if (bits != 32) {
            result = graph.addOrUnique(new ZeroExtendNode(result, 32));
        }
        ReturnNode ret = graph.getNodes().filter(ReturnNode.class).first();
        ret.replaceFirstInput(ret.result(), result);
        return ((Number) getCode(getResolvedJavaMethod("narrowUnsignedDivisionSnippet"), graph, true, false, getInitialOptions()).executeVarargs(value)).intValue();
    }

    @Test
    public void constantFactoryUsesNarrowUnsignedSemantics() {
        for (int bits : new int[]{8, 16}) {
            StructuredGraph graph = parseEager("narrowUnsignedDivisionSnippet", AllowAssumptions.YES);
            ValueNode input = ConstantNode.forIntegerBits(bits, -1, graph);
            ValueNode divisor = ConstantNode.forIntegerBits(bits, 2, graph);
            ValueNode folded = UnsignedDivNode.create(input, divisor, null, NodeView.DEFAULT);
            int actual = folded.asJavaConstant().asInt();
            int expected = (1 << (bits - 1)) - 1;
            Assert.assertEquals(bits + "-bit unsigned division constant folding", expected, actual);
        }
    }

    @Test
    public void unsignedPowerOfTwoDivision() throws InvalidInstalledCodeException {
        Assert.assertEquals(Integer.divideUnsigned(-1, 2), executeNarrowUnsignedDivision(32, -1));
        Assert.assertEquals(3, executeNarrowUnsignedDivision(8, 6));
        int actual8 = executeNarrowUnsignedDivision(8, -1);
        int actual16 = executeNarrowUnsignedDivision(16, -1);
        Assert.assertEquals("8-bit unsigned 255 / 2", 127, actual8);
        Assert.assertEquals("16-bit unsigned 65535 / 2", 32767, actual16);
    }

}
