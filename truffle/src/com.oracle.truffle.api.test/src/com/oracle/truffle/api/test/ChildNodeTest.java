/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
