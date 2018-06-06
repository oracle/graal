/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.Iterator;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * <h3>Creating a Child Node</h3>
 *
 * <p>
 * Child nodes are stored in the class of the parent node in fields that are marked with the
 * {@link Child} annotation. The {@link Node#getParent()} method allows access to this field. Every
 * node also provides the ability to iterate over its children using {@link Node#getChildren()}.
 * </p>
 *
 * <p>
 * A child node field must be declared private and non-final. It may only be assigned in the
 * constructor of the parent node. For changing the structure of the tree at run time, the method
 * {@link Node#replace(Node)} must be used (see {@link ReplaceTest}).
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.ChildrenNodesTest}.
 * </p>
 */
public class ChildNodeTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        TestChildNode leftChild = new TestChildNode();
        TestChildNode rightChild = new TestChildNode();
        TestRootNode rootNode = new TestRootNode(leftChild, rightChild);
        CallTarget target = runtime.createCallTarget(rootNode);
        assertEquals(rootNode, leftChild.getParent());
        assertEquals(rootNode, rightChild.getParent());
        Iterator<Node> iterator = rootNode.getChildren().iterator();
        assertEquals(leftChild, iterator.next());
        assertEquals(rightChild, iterator.next());
        assertFalse(iterator.hasNext());
        Object result = target.call();
        assertEquals(42, result);
    }

    class TestRootNode extends RootNode {

        @Child private TestChildNode left;
        @Child private TestChildNode right;

        TestRootNode(TestChildNode left, TestChildNode right) {
            super(null);
            this.left = left;
            this.right = right;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return left.execute() + right.execute();
        }
    }

    class TestChildNode extends Node {

        final int index;

        TestChildNode() {
            this.index = 21;
        }

        TestChildNode(int index) {
            this.index = index;
        }

        public int execute() {
            return index;
        }

        @Override
        public String toString() {
            return String.valueOf(index);
        }
    }

    @Test
    public void testChildTraversalOrder() {
        TestSubNode node = new TestSubNode();
        Iterator<Node> iterator = node.getChildren().iterator();

        assertSame(node.getChild(0), iterator.next());
        assertSame(node.getChild(1), iterator.next());
        assertSame(node.getChild(2), iterator.next());
        assertSame(node.getChild(3), iterator.next());

        assertFalse(iterator.hasNext());
    }

    class TestBaseNode extends Node {

        @Child private Node child = new TestChildNode(0);
        @Children private final Node[] children = new Node[]{new TestChildNode(1)};

        public Node getChild(int index) {
            if (index == 0) {
                return child;
            } else if (index == 1) {
                return children[0];
            }
            throw new AssertionError();
        }

    }

    class TestSubNode extends TestBaseNode {

        @Child private Node child = new TestChildNode(2);
        @Children private final Node[] children = new Node[]{new TestChildNode(3)};

        @Override
        public Node getChild(int index) {
            if (index == 2) {
                return child;
            } else if (index == 3) {
                return children[0];
            }
            return super.getChild(index);
        }

    }

}
