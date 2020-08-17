/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.array;
import static com.oracle.truffle.api.dsl.test.TestHelper.assertRuns;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.PolymorphicTestFactory.Polymorphic1Factory;
import com.oracle.truffle.api.dsl.test.PolymorphicTestFactory.Polymorphic2Factory;
import com.oracle.truffle.api.dsl.test.PolymorphicTestFactory.Polymorphic3Factory;
import com.oracle.truffle.api.dsl.test.TestHelper.TestExecutionListener;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;

public class PolymorphicTest {

    private static void assertParent(Node expectedParent, Node child) {
        Node parent = child.getParent();
        while (parent != null && parent != expectedParent) {
            parent = parent.getParent();
        }

        if (parent != expectedParent) {
            assertEquals(expectedParent, parent);
        }
    }

    public static void assertNoDuplicates(Node node, Node... ignored) {
        assertNoDuplicatesRec(new HashSet<>(Arrays.asList(ignored)), new HashSet<Class<?>>(), node);
    }

    private static void assertNoDuplicatesRec(Set<Node> ignored, Set<Class<?>> seenClasses, Node current) {
        if (!ignored.contains(current)) {
            if (seenClasses.contains(current.getClass())) {
                Assert.fail(String.format("Multiple occurrences of the same class %s. %nTree: %s", current.getClass().getSimpleName(), NodeUtil.printCompactTreeToString(current.getRootNode())));
            } else {
                seenClasses.add(current.getClass());
            }
        }

        for (Node child : current.getChildren()) {
            if (child != null) {
                assertNoDuplicatesRec(ignored, seenClasses, child);
            }
        }
    }

    @Test
    public void testPolymorphic1() {
        assertRuns(Polymorphic1Factory.getInstance(), //
                        array(42, 43, true, false, "a", "b"), //
                        array(42, 43, true, false, "a", "b"), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                Polymorphic1 polymorphic = ((Polymorphic1) node.getNode());
                                assertParent(node.getNode(), polymorphic.getA());
                                assertNoDuplicates(polymorphic, polymorphic.getA());
                                if (index == 0) {
                                    assertEquals(NodeCost.MONOMORPHIC, node.getNode().getCost());
                                }
                            }
                        });
    }

    @NodeChild("a")
    abstract static class Polymorphic1 extends ValueNode {

        public abstract ValueNode getA();

        @Specialization
        int add(int a) {
            return a;
        }

        @Specialization
        boolean add(boolean a) {
            return a;
        }

        @Specialization
        String add(String a) {
            return a;
        }

        @Fallback
        String add(Object left) {
            throw new AssertionError(left.toString());
        }

    }

    @Test
    public void testPolymorphic2() {
        assertRuns(Polymorphic2Factory.getInstance(), //
                        array(0, 1, 1, "1", "2", 2, 3), //
                        array(0, 1, 1, "1", "2", 2, 3), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                Polymorphic2 polymorphic = ((Polymorphic2) node.getNode());
                                assertParent(node.getNode(), polymorphic.getA());
                                assertNoDuplicates(polymorphic, polymorphic.getA());
                                if (index == 0) {
                                    assertEquals(NodeCost.MONOMORPHIC, node.getNode().getCost());
                                }
                            }
                        });
    }

    @NodeChild("a")
    abstract static class Polymorphic2 extends ValueNode {

        public abstract ValueNode getA();

        @Specialization
        String s2(String a) {
            return a;
        }

        @Specialization(rewriteOn = RuntimeException.class)
        int s0(int a) {
            if (a == 1) {
                throw new RuntimeException();
            }
            return a;
        }

        @Specialization
        int s1(int a) {
            return a;
        }

    }

    @Test
    public void testPolymorphic3() {
        assertRuns(Polymorphic3Factory.getInstance(), //
                        array("0", "1", 1, 1, 2, 2, 3, 3), //
                        array("0", "1", 1, 1, 2, 2, 3, 3), //
                        new TestExecutionListener() {
                            public void afterExecution(TestRootNode<? extends ValueNode> node, int index, Object value, Object expectedResult, Object actualResult, boolean last) {
                                Polymorphic3 polymorphic = ((Polymorphic3) node.getNode());
                                assertParent(node.getNode(), polymorphic.getA());
                                assertNoDuplicates(polymorphic, polymorphic.getA());
                            }
                        });
    }

    @NodeChild("a")
    abstract static class Polymorphic3 extends ValueNode {

        public abstract ValueNode getA();

        @Specialization
        String s2(String a) {
            return a;
        }

        @Specialization(rewriteOn = RuntimeException.class)
        int s0(int a) {
            if (a == 1) {
                throw new RuntimeException();
            }
            return a;
        }

        @Specialization(rewriteOn = RuntimeException.class)
        int s1(int a) {
            if (a == 1) {
                throw new RuntimeException();
            }
            return a;
        }

        @Specialization
        int s2(int a) {
            return a;
        }

    }

}
