/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.*;
import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.PolymorphicTestFactory.Polymorphic1Factory;
import com.oracle.truffle.api.dsl.test.PolymorphicTestFactory.Polymorphic2Factory;
import com.oracle.truffle.api.dsl.test.PolymorphicTestFactory.Polymorphic3Factory;
import com.oracle.truffle.api.dsl.test.TestHelper.ExecutionListener;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.*;

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
                Assert.fail(String.format("Multiple occurences of the same class %s. %nTree: %s", current.getClass().getSimpleName(), NodeUtil.printCompactTreeToString(current.getRootNode())));
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
                        array(42, 43, true, false, "a", "b"),//
                        new ExecutionListener() {
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

        @Generic
        String add(Object left) {
            throw new AssertionError(left.toString());
        }

    }

    @Test
    public void testPolymorphic2() {
        assertRuns(Polymorphic2Factory.getInstance(), //
                        array(0, 1, 1, "1", "2", 2, 3), //
                        array(0, 1, 1, "1", "2", 2, 3),//
                        new ExecutionListener() {
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
                        array("0", "1", 1, 1, 2, 2, 3, 3),//
                        new ExecutionListener() {
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
