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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AbsNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.CompressBitsNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerMulHighNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MaxNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.NotNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SaturatingAddNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.XorNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.vm.ci.meta.JavaKind;

public class ConditionalConstantFoldingTest extends GraalCompilerTest {

    public static int zeroExtend(boolean condition) {
        return (char) (condition ? 0x80 : 0x7f);
    }

    public static int signExtend(boolean condition) {
        return (byte) (condition ? 0x80 : 0x7f);
    }

    public static int narrow(boolean condition) {
        return (short) (condition ? 0x8000 : 0x7fff);
    }

    @Test
    public void testConversions() {
        assertConditionalResult("zeroExtend", ZeroExtendNode.class, 128, 127);
        assertConditionalResult("signExtend", SignExtendNode.class, -128, 127);
        assertConditionalResult("narrow", NarrowNode.class, -32768, 32767);
    }

    public static int leftShift(boolean condition) {
        return (condition ? 3 : 5) << 4;
    }

    public static int rightShift(boolean condition) {
        return (condition ? -16 : 8) >> 2;
    }

    public static int unsignedRightShift(boolean condition) {
        return (condition ? -16 : 8) >>> 2;
    }

    @Test
    public void testShifts() {
        assertConditionalResult("leftShift", LeftShiftNode.class, 48, 80);
        assertConditionalResult("rightShift", RightShiftNode.class, -4, 2);
        assertConditionalResult("unsignedRightShift", UnsignedRightShiftNode.class, 1073741820, 2);
    }

    public static int reusedConditional(boolean condition) {
        int value = condition ? 3 : 5;
        return (value << 4) + value;
    }

    @Test
    public void testPartlyFoldableMultiUseConditional() {
        test("reusedConditional", true);
        test("reusedConditional", false);

        StructuredGraph graph = earlyCanonicalGraph("reusedConditional");
        Assert.assertEquals(0, graph.getNodes().filter(LeftShiftNode.class).count());
        Assert.assertEquals(2, graph.getNodes().filter(ConditionalNode.class).count());
    }

    public static int booleanMaterialization(boolean condition) {
        return (condition ? 1 : 0) << 4;
    }

    public static int booleanMask(boolean condition) {
        return (condition ? -1 : 0) << 4;
    }

    @Test
    public void testBooleanMaterializationShifts() {
        assertConditionalResult("booleanMaterialization", LeftShiftNode.class, 16, 0);
        assertConditionalResult("booleanMask", LeftShiftNode.class, -16, 0);
    }

    public static int negateOneConstant(boolean condition, int value) {
        return -(condition ? 4 : value);
    }

    public static int notOneConstant(boolean condition, int value) {
        return ~(condition ? 4 : value);
    }

    public static int absOneConstant(boolean condition, int value) {
        return Math.abs(condition ? -4 : value);
    }

    @Test
    public void testUnaryOperations() {
        assertConditionalOperation("negateOneConstant", NegateNode.class, 4, -4, 11, (graph, conditional) -> new NegateNode(conditional));
        assertConditionalOperation("notOneConstant", NotNode.class, 4, -5, 11, (graph, conditional) -> NotNode.create(conditional));
        assertConditionalOperation("absOneConstant", AbsNode.class, -4, 4, -11, (graph, conditional) -> AbsNode.create(conditional, NodeView.DEFAULT));
    }

    public static int addOneConstant(boolean condition, int value) {
        return 10 + (condition ? 4 : value);
    }

    public static int subtractFromOneConstant(boolean condition, int value) {
        return 10 - (condition ? 4 : value);
    }

    public static int subtractOneConstant(boolean condition, int value) {
        return (condition ? 4 : value) - 10;
    }

    @Test
    public void testBinaryOperations() {
        assertConditionalOperation("addOneConstant", AddNode.class, 4, 14, 11,
                        (graph, conditional) -> new AddNode(ConstantNode.forInt(10, graph), conditional));
        assertConditionalOperation("subtractFromOneConstant", SubNode.class, 4, 6, 11,
                        (graph, conditional) -> new SubNode(ConstantNode.forInt(10, graph), conditional));
        assertConditionalOperation("subtractOneConstant", AddNode.class, 4, -6, 11,
                        (graph, conditional) -> new SubNode(conditional, ConstantNode.forInt(10, graph)));
    }

    public static int shiftValueOneConstant(boolean condition, int value) {
        return (condition ? 4 : value) << 1;
    }

    public static int shiftAmountOneConstant(boolean condition, int value) {
        return 8 << (condition ? 1 : value);
    }

    @Test
    public void testConditionalShiftOperands() {
        assertConditionalOperation("shiftValueOneConstant", LeftShiftNode.class, 4, 8, 11,
                        (graph, conditional) -> new LeftShiftNode(conditional, ConstantNode.forInt(1, graph)));
        assertConditionalOperation("shiftAmountOneConstant", LeftShiftNode.class, 1, 16, 3,
                        (graph, conditional) -> new LeftShiftNode(ConstantNode.forInt(8, graph), conditional));
    }

    public static int narrowOneConstant(boolean condition, int value) {
        return (byte) (condition ? 0x80 : value);
    }

    @Test
    public void testOneConstantConversion() {
        assertConditionalOperation("narrowOneConstant", NarrowNode.class, 0x80, -128, 0x1234,
                        (graph, conditional) -> new NarrowNode(conditional, 8));
    }

    public static long multiplyHighOneConstant(boolean condition, long value) {
        return Math.multiplyHigh(condition ? 4 : value, 7);
    }

    public static int compressOneConstant(boolean condition, int value) {
        return Integer.compress(condition ? 4 : value, 7);
    }

    public static int maxOneConstant(boolean condition, int value) {
        return Math.max(condition ? 4 : value, 10);
    }

    @Test
    public void testSpecializedOperations() {
        assertConditionalOperation("multiplyHighOneConstant", IntegerMulHighNode.class, 4, 0, 11L,
                        (graph, conditional) -> new IntegerMulHighNode(conditional, ConstantNode.forLong(7, graph)));
        assertConditionalOperation("compressOneConstant", CompressBitsNode.class, 4, 4, 11,
                        (graph, conditional) -> new CompressBitsNode(conditional, ConstantNode.forInt(7, graph)));
        assertConditionalOperation("maxOneConstant", MaxNode.class, 4, 10, 11,
                        (graph, conditional) -> MaxNode.create(conditional, ConstantNode.forInt(10, graph), NodeView.DEFAULT));
    }

    @Test
    public void testSaturatingOperation() {
        StructuredGraph graph = newGraph();
        ValueNode value = addIntParameter(graph, 1);
        ConditionalNode conditional = addConditional(graph, ConstantNode.forInt(4, graph), value);
        ValueNode add = graph.addOrUniqueWithInputs(SaturatingAddNode.create(conditional, ConstantNode.forInt(10, graph), NodeView.DEFAULT));
        finishAndCanonicalize(graph, add);

        ConditionalNode result = graph.getNodes().filter(ConditionalNode.class).first();
        assertTrue(hasConstantValue(result, 14));
        Assert.assertEquals(1, graph.getNodes().filter(SaturatingAddNode.class).count());
        assertTrue(graph.getNodes().filter(SaturatingAddNode.class).first().inputs().filter(ConditionalNode.class).isEmpty());
    }

    public static int multiUseAllFoldable(boolean condition, int value) {
        int selected = condition ? 4 : value;
        return (-selected) ^ (selected + 10);
    }

    public static int multiUsePartlyFoldable(boolean condition, int value) {
        int selected = condition ? 3 : value;
        return (selected << 4) + selected;
    }

    public static int multiUseConstants(boolean condition) {
        int selected = condition ? 3 : 5;
        return (selected << 4) ^ (selected + 1);
    }

    @Test
    public void testMultiUseConditionals() {
        test("multiUseAllFoldable", true, 11);
        test("multiUseAllFoldable", false, 11);
        StructuredGraph allFoldable = multiUseGraph(4, null,
                        (graph, conditional) -> new NegateNode(conditional),
                        (graph, conditional) -> new AddNode(conditional, ConstantNode.forInt(10, graph)));
        Assert.assertEquals(1, allFoldable.getNodes().filter(ConditionalNode.class).count());
        Assert.assertEquals(1, allFoldable.getNodes().filter(NegateNode.class).count());
        Assert.assertEquals(1, allFoldable.getNodes().filter(AddNode.class).count());

        test("multiUsePartlyFoldable", true, 11);
        test("multiUsePartlyFoldable", false, 11);
        StructuredGraph partlyFoldable = multiUseGraph(3, null,
                        (graph, conditional) -> new LeftShiftNode(conditional, ConstantNode.forInt(4, graph)),
                        (graph, conditional) -> new OpaqueValueNode(conditional));
        Assert.assertEquals(1, partlyFoldable.getNodes().filter(ConditionalNode.class).count());
        Assert.assertEquals(1, partlyFoldable.getNodes().filter(LeftShiftNode.class).count());

        test("multiUseConstants", true);
        test("multiUseConstants", false);
        StructuredGraph constants = multiUseGraph(3, 5,
                        (graph, conditional) -> new LeftShiftNode(conditional, ConstantNode.forInt(4, graph)),
                        (graph, conditional) -> new AddNode(conditional, ConstantNode.forInt(1, graph)));
        Assert.assertEquals(2, constants.getNodes().filter(ConditionalNode.class).count());
        Assert.assertEquals(0, constants.getNodes().filter(LeftShiftNode.class).count());
        Assert.assertEquals(0, constants.getNodes().filter(AddNode.class).count());
    }

    public static int nonConstantPeer(boolean condition, int value) {
        return (condition ? 4 : 5) + value;
    }

    public static int divideOneConstant(boolean condition, int value) {
        return (condition ? 4 : value) / 3;
    }

    public static int remainderOneConstant(boolean condition, int value) {
        return (condition ? 4 : value) % 3;
    }

    public static int addExactOneConstant(boolean condition, int value) {
        return Math.addExact(condition ? 4 : value, 10);
    }

    @Test
    public void testRejectedOperations() {
        test("nonConstantPeer", true, 11);
        test("nonConstantPeer", false, 11);
        StructuredGraph nonConstant = earlyCanonicalGraph("nonConstantPeer");
        Assert.assertEquals(1, nonConstant.getNodes().filter(ConditionalNode.class).count());
        Assert.assertEquals(1, nonConstant.getNodes().filter(AddNode.class).count());

        testRejectedOperationWithConstantPeer("divideOneConstant");
        testRejectedOperationWithConstantPeer("remainderOneConstant");
        testRejectedOperationWithConstantPeer("addExactOneConstant");
    }

    @Test
    public void testRejectedConditionalShapes() {
        StructuredGraph neitherConstant = newGraph();
        ValueNode first = addIntParameter(neitherConstant, 1);
        ValueNode second = addIntParameter(neitherConstant, 2);
        ConditionalNode nonConstantConditional = addConditional(neitherConstant, first, second);
        NegateNode negate = neitherConstant.addOrUnique(new NegateNode(nonConstantConditional));
        finishAndCanonicalize(neitherConstant, negate);
        Assert.assertEquals(1, neitherConstant.getNodes().filter(ConditionalNode.class).count());
        assertTrue(negate.isAlive());
        assertTrue(negate.inputs().contains(nonConstantConditional));

        StructuredGraph bothOperands = newGraph();
        ValueNode value = addIntParameter(bothOperands, 1);
        ConditionalNode sharedConditional = addConditional(bothOperands, ConstantNode.forInt(4, bothOperands), value);
        IntegerMulHighNode multiplyHigh = bothOperands.addOrUnique(new IntegerMulHighNode(sharedConditional, sharedConditional));
        finishAndCanonicalize(bothOperands, multiplyHigh);
        Assert.assertEquals(1, bothOperands.getNodes().filter(ConditionalNode.class).count());
        assertTrue(multiplyHigh.isAlive());
        assertTrue(multiplyHigh.getX() == sharedConditional && multiplyHigh.getY() == sharedConditional);
    }

    @Test
    public void testOneConstantMultiUseConditionalIsNotDuplicated() {
        StructuredGraph graph = newGraph();
        ValueNode value = addIntParameter(graph, 1);
        ConditionalNode conditional = addConditional(graph, ConstantNode.forInt(4, graph), value);
        ValueNode negate = graph.addOrUnique(new NegateNode(conditional));
        ValueNode add = graph.addOrUnique(new AddNode(conditional, ConstantNode.forInt(10, graph)));
        ValueNode opaqueNegate = graph.addWithoutUnique(new OpaqueValueNode(negate));
        ValueNode opaqueAdd = graph.addWithoutUnique(new OpaqueValueNode(add));
        finishAndCanonicalize(graph, graph.addOrUnique(new XorNode(opaqueNegate, opaqueAdd)));

        Assert.assertEquals(1, graph.getNodes().filter(ConditionalNode.class).count());
        Assert.assertTrue(conditional.isAlive());
        Assert.assertEquals(1, graph.getNodes().filter(NegateNode.class).count());
        Assert.assertEquals(1, graph.getNodes().filter(AddNode.class).count());
        for (Node operation : graph.getNodes().filter(n -> n instanceof NegateNode || n instanceof AddNode)) {
            assertTrue(operation.inputs().contains(conditional));
        }
    }

    private void assertConditionalOperation(String snippet, Class<? extends ValueNode> operationClass, long constantValue, long foldedValue, Object nonConstantValue,
                    OperationFactory operationFactory) {
        test(snippet, true, nonConstantValue);
        test(snippet, false, nonConstantValue);

        JavaKind kind = nonConstantValue instanceof Long ? JavaKind.Long : JavaKind.Int;
        StructuredGraph graph = newGraph();
        ValueNode value = addParameter(graph, 1, kind);
        ConditionalNode inputConditional = addConditional(graph, ConstantNode.forIntegerKind(kind, constantValue, graph), value);
        ValueNode inputOperation = graph.addOrUniqueWithInputs(operationFactory.create(graph, inputConditional));
        finishAndCanonicalize(graph, inputOperation);
        Assert.assertEquals(1, graph.getNodes().filter(operationClass).count());
        ConditionalNode foldedConditional = null;
        for (ConditionalNode conditional : graph.getNodes().filter(ConditionalNode.class)) {
            if (hasConstantValue(conditional, foldedValue)) {
                foldedConditional = conditional;
                break;
            }
        }
        Assert.assertNotNull("nodes=" + graph.getNodes().snapshot(), foldedConditional);
        for (ValueNode operation : graph.getNodes().filter(operationClass)) {
            assertFalse("operation still consumes the original conditional: " + graph.getNodes().snapshot(), operation.inputs().filter(ConditionalNode.class).isNotEmpty());
        }
    }

    private void assertRejectedOperation(String snippet, Class<? extends ValueNode> operationClass) {
        test(snippet, true, 11);
        test(snippet, false, 11);
        StructuredGraph graph = earlyCanonicalGraph(snippet);
        Assert.assertEquals(snippet, 1, graph.getNodes().filter(ConditionalNode.class).count());
        assertTrue(graph.getNodes().filter(operationClass).isNotEmpty());
        assertTrue(graph.getNodes().filter(operationClass).filter(op -> op.inputs().filter(ConditionalNode.class).isNotEmpty()).isNotEmpty());
    }

    private void testRejectedOperationWithConstantPeer(String snippet) {
        /*
         * The parser lowers these trapping operations through control flow before the early
         * canonicalizer runs. The execution checks therefore isolate the constant-peer cases;
         * graph-level rejection is covered by assertRejectedOperation above.
         */
        test(snippet, true, 11);
        test(snippet, false, 11);
    }

    private StructuredGraph earlyCanonicalGraph(String snippet) {
        StructuredGraph graph = parseEager(getResolvedJavaMethod(snippet), StructuredGraph.AllowAssumptions.NO);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
        return graph;
    }

    private StructuredGraph newGraph() {
        StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        addIntParameter(graph, 0);
        return graph;
    }

    private static ValueNode addIntParameter(StructuredGraph graph, int index) {
        return addParameter(graph, index, JavaKind.Int);
    }

    private static ValueNode addParameter(StructuredGraph graph, int index, JavaKind kind) {
        return graph.addWithoutUnique(new ParameterNode(index, StampPair.createSingle(StampFactory.forKind(kind))));
    }

    private static ConditionalNode addConditional(StructuredGraph graph, ValueNode trueValue, ValueNode falseValue) {
        ValueNode conditionValue = graph.getParameter(0);
        LogicNode condition = graph.addOrUniqueWithInputs(IntegerEqualsNode.create(conditionValue, ConstantNode.forInt(0, graph), NodeView.DEFAULT));
        return graph.addOrUnique(new ConditionalNode(condition, trueValue, falseValue));
    }

    private void finishAndCanonicalize(StructuredGraph graph, ValueNode result) {
        ReturnNode returnNode = graph.add(new ReturnNode(result));
        graph.start().setNext(returnNode);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());
    }

    private StructuredGraph multiUseGraph(long trueValue, Integer falseValue, OperationFactory firstFactory, OperationFactory secondFactory) {
        StructuredGraph graph = newGraph();
        ValueNode dynamicValue = addIntParameter(graph, 1);
        ValueNode falseInput = falseValue == null ? dynamicValue : ConstantNode.forInt(falseValue, graph);
        ConditionalNode conditional = addConditional(graph, ConstantNode.forInt((int) trueValue, graph), falseInput);
        ValueNode first = graph.addOrUniqueWithInputs(firstFactory.create(graph, conditional));
        ValueNode second = graph.addOrUniqueWithInputs(secondFactory.create(graph, conditional));
        ValueNode opaqueFirst = graph.addWithoutUnique(new OpaqueValueNode(first));
        ValueNode opaqueSecond = graph.addWithoutUnique(new OpaqueValueNode(second));
        finishAndCanonicalize(graph, graph.addOrUnique(new XorNode(opaqueFirst, opaqueSecond)));
        return graph;
    }

    private void assertConditionalResult(String snippet, Class<? extends ValueNode> foldedNodeClass, long trueValue, long falseValue) {
        test(snippet, true);
        test(snippet, false);

        StructuredGraph graph = parseEager(getResolvedJavaMethod(snippet), StructuredGraph.AllowAssumptions.NO);
        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());

        Assert.assertEquals(0, graph.getNodes().filter(foldedNodeClass).count());
        ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
        assertTrue(returnNode.result() instanceof ConditionalNode);
        ConditionalNode conditional = (ConditionalNode) returnNode.result();
        assertConstantValues(conditional, trueValue, falseValue);
    }

    private static void assertConstantValues(ConditionalNode conditional, long firstExpected, long secondExpected) {
        assertTrue(conditional.trueValue() instanceof ConstantNode);
        assertTrue(conditional.falseValue() instanceof ConstantNode);
        long trueValue = conditional.trueValue().asJavaConstant().asLong();
        long falseValue = conditional.falseValue().asJavaConstant().asLong();
        assertTrue((trueValue == firstExpected && falseValue == secondExpected) || (trueValue == secondExpected && falseValue == firstExpected));
    }

    private static boolean hasConstantValue(ConditionalNode conditional, long expected) {
        return conditional.trueValue().isConstant() && conditional.trueValue().asJavaConstant().asLong() == expected ||
                        conditional.falseValue().isConstant() && conditional.falseValue().asJavaConstant().asLong() == expected;
    }

    @FunctionalInterface
    private interface OperationFactory {
        ValueNode create(StructuredGraph graph, ConditionalNode conditional);
    }
}
