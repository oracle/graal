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
package jdk.graal.compiler.replacements.test;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.LongToIntFunction;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.replacements.nodes.BitCountNode;
import jdk.graal.compiler.replacements.nodes.BitScanForwardNode;
import jdk.graal.compiler.replacements.nodes.BitScanReverseNode;
import jdk.graal.compiler.replacements.nodes.CountLeadingZerosNode;
import jdk.graal.compiler.replacements.nodes.CountTrailingZerosNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.InstalledCode;

/// Checks that bit operations reject subword inputs and preserve int/long results.
public class NarrowBitOpTest extends GraalCompilerTest {

    public static int input(long value) {
        return (int) value;
    }

    @Test
    public void testTrailingZeros() {
        checkBitOp(CountTrailingZerosNode::create, Integer::numberOfTrailingZeros, Long::numberOfTrailingZeros, 32);
    }

    @Test
    public void testLeadingZeros() {
        checkBitOp(CountLeadingZerosNode::create, Integer::numberOfLeadingZeros, Long::numberOfLeadingZeros, 32);
    }

    @Test
    public void testReverseScan() {
        checkBitOp(v -> new BitScanReverseNode(v).canonical(null, v), BitScanReverseNode::scan, BitScanReverseNode::scan, 31);
    }

    @Test
    public void testBitCount() {
        checkBitOp(v -> new BitCountNode(v).canonical(null, v), Integer::bitCount, Long::bitCount, 32);
    }

    @Test
    public void testInputWidths() {
        Assume.assumeTrue(Assertions.assertionsEnabled());
        List<Function<ValueNode, ValueNode>> operations = List.of(BitCountNode::new, BitScanForwardNode::new, BitScanReverseNode::new,
                        CountLeadingZerosNode::create, CountTrailingZerosNode::create);
        Assert.assertThrows(AssertionError.class, () -> StampTool.verifyIntOrLong(ConstantNode.forFloat(1.0f)));
        StructuredGraph graph = parseEager("input", AllowAssumptions.YES);
        for (Function<ValueNode, ValueNode> operation : operations) {
            for (int bits : new int[]{1, 8, 16}) {
                ValueNode narrow = graph.addOrUnique(new NarrowNode(graph.getParameter(0), bits));
                String message = Assert.assertThrows(AssertionError.class, () -> operation.apply(narrow)).getMessage();
                assertTrue(message, message.contains("32-bit int or 64-bit long"));
                assertTrue(message, message.contains(narrow.toString()));
                assertTrue(message, message.contains(narrow.stamp(NodeView.DEFAULT).toString()));
            }
            for (int bits : new int[]{32, 64}) {
                ValueNode value = graph.addOrUnique(new NarrowNode(graph.getParameter(0), bits));
                UnaryNode result = (UnaryNode) graph.addOrUniqueWithInputs(operation.apply(value));
                result.inferStamp();
                // Stamp folding must preserve the input width established at construction.
                for (int narrowBits : new int[]{1, 8, 16}) {
                    IntegerStamp narrowStamp = IntegerStamp.create(narrowBits);
                    Assert.assertThrows(AssertionError.class, () -> result.foldStamp(narrowStamp));
                }
            }
        }
    }

    /// Checks inferred stamps, constant folding, and compiled results for supported input widths.
    private void checkBitOp(Function<ValueNode, ValueNode> operation, IntUnaryOperator intOperation, LongToIntFunction longOperation, int comparison) {
        long[] inputs = {0, 1, -1, 0x80, 0x100, 0x8000, 0x10000, 0x80000000L, 0x100000000L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (int bits : new int[]{32, 64}) {
            for (boolean compare : new boolean[]{false, true}) {
                StructuredGraph graph = parseEager("input", AllowAssumptions.YES);
                ValueNode narrow = graph.addOrUnique(new NarrowNode(graph.getParameter(0), bits));
                UnaryNode result = (UnaryNode) graph.addOrUniqueWithInputs(operation.apply(narrow));
                result.inferStamp();
                for (long input : inputs) {
                    long value = CodeUtil.signExtend(input, bits);
                    int expected = bits <= 32 ? intOperation.applyAsInt((int) value) : longOperation.applyAsInt(value);
                    // Reverse scan has no defined result for zero.
                    if (expected < 0) {
                        continue;
                    }
                    assertTrue(result.stamp(NodeView.DEFAULT).toString(), ((IntegerStamp) result.stamp(NodeView.DEFAULT)).contains(expected));
                    IntegerStamp foldedStamp = (IntegerStamp) result.foldStamp(IntegerStamp.create(bits, value, value));
                    assertTrue(foldedStamp.toString(), foldedStamp.contains(expected));
                    ValueNode folded = operation.apply(ConstantNode.forIntegerBits(bits, value));
                    assertTrue(folded.isConstant());
                    Assert.assertEquals(expected, folded.asJavaConstant().asInt());
                }
                ValueNode returned = result;
                if (compare) {
                    LogicNode condition = graph.addOrUniqueWithInputs(IntegerEqualsNode.create(result, ConstantNode.forInt(comparison), NodeView.DEFAULT));
                    returned = graph.addOrUniqueWithInputs(new ConditionalNode(condition));
                }
                ReturnNode returnNode = graph.getNodes(ReturnNode.TYPE).first();
                returnNode.replaceFirstInput(returnNode.result(), returned);
                InstalledCode code = getCode(graph.method(), graph, true);
                for (long input : inputs) {
                    long value = CodeUtil.signExtend(input, bits);
                    int expected = bits <= 32 ? intOperation.applyAsInt((int) value) : longOperation.applyAsInt(value);
                    if (expected >= 0) {
                        Assert.assertEquals(compare ? (expected == comparison ? 1 : 0) : expected, (int) executeVarargsSafe(code, input));
                    }
                }
            }
        }
    }
}
