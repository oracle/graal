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

import org.junit.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.dsl.test.BinaryNodeTest.BinaryNode;
import com.oracle.truffle.api.dsl.test.PolymorphicTestFactory.Node1Factory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeInfo.Kind;

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

    @Test
    public void testJustSpecialize() {
        TestRootNode<Node1> node = TestHelper.createRoot(Node1Factory.getInstance());
        assertEquals("(int,int)", executeWith(node, 42, 42));
        assertEquals("(boolean,boolean)", executeWith(node, false, false));
        assertEquals("(int,boolean)", executeWith(node, 42, false));
        assertEquals("(boolean,int)", executeWith(node, false, 42));
        assertEquals(Kind.SPECIALIZED, node.getNode().getClass().getAnnotation(NodeInfo.class).kind());
        assertParent(node.getNode(), node.getNode().getLeft());
        assertParent(node.getNode(), node.getNode().getRight());
    }

    @Test
    public void testPolymorphic2() {
        TestRootNode<Node1> node = TestHelper.createRoot(Node1Factory.getInstance());
        assertEquals("(int,boolean)", executeWith(node, 42, false));
        assertEquals("(int,int)", executeWith(node, 42, 42));
        assertEquals(Kind.POLYMORPHIC, node.getNode().getClass().getAnnotation(NodeInfo.class).kind());
        assertParent(node.getNode(), node.getNode().getLeft());
        assertParent(node.getNode(), node.getNode().getRight());
    }

    @Test
    public void testPolymorphic3() {
        TestRootNode<Node1> node = TestHelper.createRoot(Node1Factory.getInstance());
        assertEquals("(int,boolean)", executeWith(node, 42, false));
        assertEquals("(boolean,boolean)", executeWith(node, true, false));
        assertEquals("(int,int)", executeWith(node, 42, 42));
        assertEquals(Kind.POLYMORPHIC, node.getNode().getClass().getAnnotation(NodeInfo.class).kind());
        assertParent(node.getNode(), node.getNode().getLeft());
        assertParent(node.getNode(), node.getNode().getRight());
    }

    @Test
    public void testGenericLimitReached() {
        TestRootNode<Node1> node = TestHelper.createRoot(Node1Factory.getInstance());
        assertEquals("(boolean,int)", executeWith(node, false, 42));
        assertEquals("(int,boolean)", executeWith(node, 42, false));
        assertEquals("(boolean,boolean)", executeWith(node, true, false));
        assertEquals("(int,int)", executeWith(node, 42, 42));
        assertEquals(Kind.GENERIC, node.getNode().getClass().getAnnotation(NodeInfo.class).kind());
        assertParent(node.getNode(), node.getNode().getLeft());
        assertParent(node.getNode(), node.getNode().getRight());
    }

    @Test
    public void testGenericInitial() {
        TestRootNode<Node1> node = TestHelper.createRoot(Node1Factory.getInstance());
        assertEquals("(generic,generic)", executeWith(node, "1", "1"));
        assertEquals(Kind.GENERIC, node.getNode().getClass().getAnnotation(NodeInfo.class).kind());
        assertParent(node.getNode(), node.getNode().getLeft());
        assertParent(node.getNode(), node.getNode().getRight());
    }

    @Test
    public void testGenericPolymorphic1() {
        TestRootNode<Node1> node = TestHelper.createRoot(Node1Factory.getInstance());
        assertEquals("(boolean,int)", executeWith(node, false, 42));
        assertEquals("(boolean,boolean)", executeWith(node, false, false));
        assertEquals("(generic,generic)", executeWith(node, "", ""));
        assertEquals(Kind.GENERIC, node.getNode().getClass().getAnnotation(NodeInfo.class).kind());
        /* Assertions for bug GRAAL-425 */
        assertParent(node.getNode(), node.getNode().getLeft());
        assertParent(node.getNode(), node.getNode().getRight());
    }

    @SuppressWarnings("unused")
    @PolymorphicLimit(3)
    abstract static class Node1 extends BinaryNode {

        public abstract ValueNode getLeft();

        public abstract ValueNode getRight();

        @Specialization(order = 1)
        String add(int left, int right) {
            return "(int,int)";
        }

        @Specialization(order = 2)
        String add(boolean left, boolean right) {
            return "(boolean,boolean)";
        }

        @Specialization(order = 3)
        String add(int left, boolean right) {
            return "(int,boolean)";
        }

        @Specialization(order = 4)
        String add(boolean left, int right) {
            return "(boolean,int)";
        }

        @Generic
        String add(Object left, Object right) {
            return "(generic,generic)";
        }

    }

}
