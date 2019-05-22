/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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
package org.graalvm.compiler.nodes.test;

import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.calc.UnsignedRightShiftNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.OptimizeDivPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Test;

public class IntegerDivRemNodeCanonicalizationTest extends GraalCompilerTest {
    // Test positive remainder in increment counted loop.
    public static int remInCountedLoop1() {
        int sum = 0;
        for (int i = 0; i < 1000000; i++) {
            sum += i % 8;
        }
        return sum;
    }

    @Test
    public void testRemInCountedLoop1() {
        testRemainder("remInCountedLoop1", 7);
    }

    // Test positive remainder in decrement counted loop.
    public static int remInCountedLoop2() {
        int sum = 0;
        int i;
        for (i = 10000; i >= 0; i--) {
            sum += i % 4;
        }
        sum += i % 8;
        return sum;
    }

    @Test
    public void testRemInCountedLoop2() {
        test("remInCountedLoop2");
        StructuredGraph graph = buildGraph("remInCountedLoop2");

        // The remainder inside of the loop should be optimized to "i & 3".
        assertExist(graph, (node) -> {
            if (node instanceof AndNode) {
                ValueNode y = ((AndNode) node).getY();
                return y.isConstant() && y.asJavaConstant().asLong() == 3;
            }
            return false;
        });

        // The last remainder outside of the loop shouldn't be optimized to "i & 7".
        assertNotExist(graph, (node) -> {
            if (node instanceof AndNode) {
                ValueNode y = ((AndNode) node).getY();
                return y.isConstant() && y.asJavaConstant().asLong() == 7;
            }
            return false;
        });
    }

    // Test negative remainder in increment counted loop.
    public static int remInCountedLoop3() {
        int sum = 0;
        for (int i = -100000; i < 0; i++) {
            sum += i % 16;
        }
        return sum;
    }

    @Test
    public void testRemInCountedLoop3() {
        testRemainder("remInCountedLoop3", 15);
    }

    // Test negative remainder in decrement counted loop.
    public static int remInCountedLoop4() {
        int sum = 0;
        for (int i = 0; i > -1000000; i--) {
            sum += i % 8;
        }
        return sum;
    }

    @Test
    public void testRemInCountedLoop4() {
        testRemainder("remInCountedLoop4", 7);
    }

    // Test positive remainder in increment counted loop with NE condition.
    public static int remInCountedLoop5() {
        int sum = 0;
        for (int i = 0; i != 10000; i++) {
            sum += i % 32;
        }
        return sum;
    }

    @Test
    public void testRemInCountedLoop5() {
        testRemainder("remInCountedLoop5", 31);
    }

    // Test negative remainder in decrement counted loop with NE condition.
    public static int remInCountedLoop6() {
        int sum = 0;
        for (int i = -1; i != -10000; i--) {
            sum += i % 32;
        }
        return sum;
    }

    @Test
    public void testRemInCountedLoop6() {
        testRemainder("remInCountedLoop6", 31);
    }

    // Test positive division in increment counted loop.
    public static int divInCountedLoop() {
        int sum = 0;
        for (int i = 0; i < 1000000; i++) {
            sum += i / 8;
        }
        return sum;
    }

    @Test
    public void testDivInCountedLoop() {
        testDivision("divInCountedLoop", 3);
    }

    // Test function for integer remainder.
    private void testRemainder(String methodName, int num) {
        test(methodName);
        StructuredGraph graph = buildGraph(methodName);
        NodePredicate predicate = (node) -> {
            if (node instanceof AndNode) {
                ValueNode y = ((AndNode) node).getY();
                return y.isConstant() && y.asJavaConstant().asLong() == num;
            }
            return false;
        };
        assertExist(graph, predicate);
    }

    // Test function for integer division.
    private void testDivision(String methodName, int log2) {
        test(methodName);
        StructuredGraph graph = buildGraph(methodName);
        NodePredicate predicate = (node) -> {
            if (node instanceof UnsignedRightShiftNode) {
                ValueNode x = ((UnsignedRightShiftNode) node).getX();
                ValueNode y = ((UnsignedRightShiftNode) node).getY();
                int bits = PrimitiveStamp.getBits(x.stamp(NodeView.DEFAULT));
                if (y.isConstant() && y.asJavaConstant().asLong() == (bits - log2)) {
                    if (x instanceof RightShiftNode) {
                        ValueNode shift = ((RightShiftNode) x).getY();
                        return shift.isConstant() && shift.asJavaConstant().asLong() == bits - 1;
                    }
                }
            }
            return false;
        };
        assertNotExist(graph, predicate);
    }

    private StructuredGraph buildGraph(String methodName) {
        StructuredGraph graph = parseEager(methodName, StructuredGraph.AllowAssumptions.YES);
        HighTierContext context = getDefaultHighTierContext();
        OptimizeDivPhase optimizeDiv = new OptimizeDivPhase(true);
        optimizeDiv.apply(graph, context);
        new CanonicalizerPhase().apply(graph, context);
        return graph;
    }

    private static void assertExist(StructuredGraph graph, NodePredicate predicate) {
        Assert.assertNotEquals(0, graph.getNodes().filter(predicate).count());
    }

    private static void assertNotExist(StructuredGraph graph, NodePredicate predicate) {
        Assert.assertEquals(0, graph.getNodes().filter(predicate).count());
    }
}
