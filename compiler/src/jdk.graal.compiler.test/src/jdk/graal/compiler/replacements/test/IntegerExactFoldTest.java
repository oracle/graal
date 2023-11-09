/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.replacements.nodes.arithmetic.BinaryIntegerExactArithmeticSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactArithmeticSplitNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerNegExactSplitNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IntegerExactFoldTest extends GraalCompilerTest {
    private final long lowerBoundA;
    private final long upperBoundA;
    private final long lowerBoundB;
    private final long upperBoundB;
    private final int bits;
    private final Operation operation;

    public IntegerExactFoldTest(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, Operation operation) {
        this.lowerBoundA = lowerBoundA;
        this.upperBoundA = upperBoundA;
        this.lowerBoundB = lowerBoundB;
        this.upperBoundB = upperBoundB;
        this.bits = bits;
        this.operation = operation;

        assert bits == 32 || bits == 64;
        assert lowerBoundA <= upperBoundA;
        assert lowerBoundB <= upperBoundB;
        assert bits == 64 || isInteger(lowerBoundA);
        assert bits == 64 || isInteger(upperBoundA);
        assert bits == 64 || isInteger(lowerBoundB);
        assert bits == 64 || isInteger(upperBoundB);
    }

    @Test
    public void testFolding() {
        StructuredGraph graph = prepareGraph();
        IntegerStamp a = IntegerStamp.create(bits, lowerBoundA, upperBoundA);
        IntegerStamp b = IntegerStamp.create(bits, lowerBoundB, upperBoundB);

        List<ParameterNode> params = graph.getNodes(ParameterNode.TYPE).snapshot();
        params.get(0).replaceAtMatchingUsages(graph.addOrUnique(new PiNode(params.get(0), a)), x -> x instanceof IntegerExactArithmeticNode);
        if (!(operation instanceof NegOperation)) {
            params.get(1).replaceAtMatchingUsages(graph.addOrUnique(new PiNode(params.get(1), b)), x -> x instanceof IntegerExactArithmeticNode);
        }

        Node originalNode = graph.getNodes().filter(x -> x instanceof IntegerExactArithmeticNode).first();
        assertNotNull("original node must be in the graph", originalNode);

        createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());

        ValueNode node = findNode(graph);
        boolean overflowExpected = node instanceof IntegerExactArithmeticNode;

        IntegerStamp resultStamp = (IntegerStamp) node.stamp(NodeView.DEFAULT);
        operation.verifyOverflow(lowerBoundA, upperBoundA, lowerBoundB, upperBoundB, bits, overflowExpected, resultStamp);
    }

    @Test
    public void testFoldingAfterLowering() {
        StructuredGraph graph = prepareGraph();

        Node originalNode = graph.getNodes().filter(x -> x instanceof IntegerExactArithmeticNode).first();
        assertNotNull("original node must be in the graph", originalNode);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        HighTierContext highTierContext = getDefaultHighTierContext();
        new HighTierLoweringPhase(canonicalizer).apply(graph, highTierContext);
        MidTierContext midTierContext = getDefaultMidTierContext();
        new GuardLoweringPhase().apply(graph, midTierContext);
        createCanonicalizerPhase().apply(graph, midTierContext);

        IntegerExactArithmeticSplitNode loweredNode = graph.getNodes().filter(IntegerExactArithmeticSplitNode.class).first();
        assertNotNull("the lowered node must be in the graph", loweredNode);

        if (loweredNode instanceof BinaryIntegerExactArithmeticSplitNode) {
            BinaryIntegerExactArithmeticSplitNode binaryLoweredNode = (BinaryIntegerExactArithmeticSplitNode) loweredNode;
            binaryLoweredNode.getX().setStamp(IntegerStamp.create(bits, lowerBoundA, upperBoundA));
            binaryLoweredNode.getY().setStamp(IntegerStamp.create(bits, lowerBoundB, upperBoundB));
        } else if (loweredNode instanceof IntegerNegExactSplitNode) {
            IntegerNegExactSplitNode negExactSplitNode = (IntegerNegExactSplitNode) loweredNode;
            negExactSplitNode.getValue().setStamp(IntegerStamp.create(bits, lowerBoundA, upperBoundA));
        } else {
            fail("Unknown integer exact split node type: %s", loweredNode.getClass());
        }
        createCanonicalizerPhase().apply(graph, midTierContext);

        ValueNode node = findNode(graph);
        boolean overflowExpected = node instanceof IntegerExactArithmeticSplitNode;

        IntegerStamp resultStamp = (IntegerStamp) node.stamp(NodeView.DEFAULT);
        operation.verifyOverflow(lowerBoundA, upperBoundA, lowerBoundB, upperBoundB, bits, overflowExpected, resultStamp);
    }

    private static boolean isInteger(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private static ValueNode findNode(StructuredGraph graph) {
        ValueNode resultNode = graph.getNodes().filter(ReturnNode.class).first().result();
        assertNotNull("some node must be the returned value", resultNode);
        return resultNode;
    }

    protected StructuredGraph prepareGraph() {
        String snippet = "snippetInt" + bits;
        StructuredGraph graph = parseEager(getResolvedJavaMethod(operation.getClass(), snippet), AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        createCanonicalizerPhase().apply(graph, context);
        return graph;
    }

    private static void addTest(ArrayList<Object[]> tests, long lowerBound1, long upperBound1, long lowerBound2, long upperBound2, int bits, Operation operation) {
        tests.add(new Object[]{lowerBound1, upperBound1, lowerBound2, upperBound2, bits, operation});
    }

    @Parameters(name = "a[{0} / {1}], b[{2} / {3}], bits={4}, operation={5}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();

        Operation[] operations = new Operation[]{new AddOperation(), new SubOperation(), new MulOperation(), new NegOperation()};
        for (Operation operation : operations) {
            for (int bits : new int[]{32, 64}) {
                // zero related
                addTest(tests, 0, 0, 1, 1, bits, operation);
                addTest(tests, 1, 1, 0, 0, bits, operation);
                addTest(tests, -1, 1, 0, 1, bits, operation);

                // bounds
                addTest(tests, -2, 2, 3, 3, bits, operation);
                addTest(tests, -1, 1, 1, 1, bits, operation);
                addTest(tests, -1, 1, -1, 1, bits, operation);

                addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xF, Integer.MAX_VALUE - 0xF, Integer.MAX_VALUE, bits, operation);
                addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xF, -1, -1, bits, operation);
                addTest(tests, Integer.MAX_VALUE, Integer.MAX_VALUE, -1, -1, bits, operation);
                addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE, -1, -1, bits, operation);
                addTest(tests, Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 1, bits, operation);
                addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE, 1, 1, bits, operation);
            }

            // bit-specific test cases
            addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xF, Integer.MAX_VALUE - 0xF, Integer.MAX_VALUE, 64, operation);
            addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xF, -1, -1, 64, operation);
        }

        return tests;
    }

    private abstract static class Operation {
        abstract void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp);
    }

    private static final class AddOperation extends Operation {
        @Override
        public void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp) {
            try {
                long res = addExact(lowerBoundA, lowerBoundB, bits);
                resultStamp.contains(res);
                res = addExact(upperBoundA, upperBoundB, bits);
                resultStamp.contains(res);
                Assert.assertFalse(overflowExpected);
            } catch (ArithmeticException e) {
                Assert.assertTrue(overflowExpected);
            }
        }

        private static long addExact(long x, long y, int bits) {
            if (bits == 32) {
                return Math.addExact((int) x, (int) y);
            } else {
                return Math.addExact(x, y);
            }
        }

        @SuppressWarnings("unused")
        public static int snippetInt32(int a, int b) {
            return Math.addExact(a, b);
        }

        @SuppressWarnings("unused")
        public static long snippetInt64(long a, long b) {
            return Math.addExact(a, b);
        }
    }

    private static final class SubOperation extends Operation {
        @Override
        public void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp) {
            try {
                long res = subExact(lowerBoundA, upperBoundB, bits);
                Assert.assertTrue(resultStamp.contains(res));
                res = subExact(upperBoundA, lowerBoundB, bits);
                Assert.assertTrue(resultStamp.contains(res));
                Assert.assertFalse(overflowExpected);
            } catch (ArithmeticException e) {
                Assert.assertTrue(overflowExpected);
            }
        }

        private static long subExact(long x, long y, int bits) {
            if (bits == 32) {
                return Math.subtractExact((int) x, (int) y);
            } else {
                return Math.subtractExact(x, y);
            }
        }

        @SuppressWarnings("unused")
        public static int snippetInt32(int a, int b) {
            return Math.subtractExact(a, b);
        }

        @SuppressWarnings("unused")
        public static long snippetInt64(long a, long b) {
            return Math.subtractExact(a, b);
        }
    }

    private static final class MulOperation extends Operation {
        @Override
        public void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp) {
            // now check for all values in the stamp whether their products overflow overflow
            boolean overflowOccurred = false;

            for (long l1 = lowerBoundA; l1 <= upperBoundA; l1++) {
                for (long l2 = lowerBoundB; l2 <= upperBoundB; l2++) {
                    try {
                        long res = mulExact(l1, l2, bits);
                        Assert.assertTrue(resultStamp.contains(res));
                    } catch (ArithmeticException e) {
                        overflowOccurred = true;
                    }
                    if (l2 == Long.MAX_VALUE) {
                        // do not want to overflow the check loop
                        break;
                    }
                }
                if (l1 == Long.MAX_VALUE) {
                    // do not want to overflow the check loop
                    break;
                }
            }

            Assert.assertEquals(overflowExpected, overflowOccurred);
        }

        private static long mulExact(long x, long y, int bits) {
            if (bits == 32) {
                return Math.multiplyExact((int) x, (int) y);
            } else {
                return Math.multiplyExact(x, y);
            }
        }

        @SuppressWarnings("unused")
        public static int snippetInt32(int a, int b) {
            return Math.multiplyExact(a, b);
        }

        @SuppressWarnings("unused")
        public static long snippetInt64(long a, long b) {
            return Math.multiplyExact(a, b);
        }
    }

    private static final class NegOperation extends Operation {
        @Override
        public void verifyOverflow(long lowerBoundA, long upperBoundA, long lowerBoundB, long upperBoundB, int bits, boolean overflowExpected, IntegerStamp resultStamp) {
            try {
                long res = negExact(lowerBoundA, bits);
                Assert.assertTrue(resultStamp.contains(res));
                res = negExact(upperBoundA, bits);
                Assert.assertTrue(resultStamp.contains(res));
                Assert.assertFalse(overflowExpected);
            } catch (ArithmeticException e) {
                Assert.assertTrue(overflowExpected);
            }
        }

        private static long negExact(long x, int bits) {
            if (bits == 32) {
                return Math.negateExact((int) x);
            } else {
                return Math.negateExact(x);
            }
        }

        @SuppressWarnings("unused")
        public static int snippetInt32(int a) {
            return Math.negateExact(a);
        }

        @SuppressWarnings("unused")
        public static long snippetInt64(long a) {
            return Math.negateExact(a);
        }
    }

}
