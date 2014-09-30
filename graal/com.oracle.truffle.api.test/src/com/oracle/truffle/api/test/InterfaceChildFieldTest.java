/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Test child fields declared with interface types instead of {@link Node} subclasses.
 */
public class InterfaceChildFieldTest {

    @Test
    public void testChild() {
        TruffleRuntime runtime = Truffle.getRuntime();
        TestChildInterface leftChild = new TestLeafNode();
        TestChildInterface rightChild = new TestLeafNode();
        TestChildNode parent = new TestChildNode(leftChild, rightChild);
        TestRootNode rootNode = new TestRootNode(parent);
        CallTarget target = runtime.createCallTarget(rootNode);
        Iterator<Node> iterator = parent.getChildren().iterator();
        Assert.assertEquals(leftChild, iterator.next());
        Assert.assertEquals(rightChild, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Object result = target.call();
        Assert.assertEquals(42, result);

        Assert.assertEquals(4, NodeUtil.countNodes(rootNode));
        Assert.assertEquals(4, NodeUtil.countNodes(NodeUtil.cloneNode(rootNode)));
    }

    @Test
    public void testChildren() {
        TruffleRuntime runtime = Truffle.getRuntime();
        TestChildInterface[] children = new TestChildInterface[5];
        for (int i = 0; i < children.length; i++) {
            children[i] = new TestLeafNode();
        }
        TestChildrenNode parent = new TestChildrenNode(children);
        TestRootNode rootNode = new TestRootNode(parent);
        CallTarget target = runtime.createCallTarget(rootNode);
        Iterator<Node> iterator = parent.getChildren().iterator();
        for (int i = 0; i < children.length; i++) {
            Assert.assertEquals(children[i], iterator.next());
        }
        Assert.assertFalse(iterator.hasNext());
        Object result = target.call();
        Assert.assertEquals(105, result);

        Assert.assertEquals(2 + children.length, NodeUtil.countNodes(rootNode));
        Assert.assertEquals(2 + children.length, NodeUtil.countNodes(NodeUtil.cloneNode(rootNode)));
    }

    class TestRootNode extends RootNode {

        @Child private TestChildInterface child;

        public TestRootNode(TestChildInterface child) {
            super(null);
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.executeIntf();
        }
    }

    interface TestChildInterface {
        public int executeIntf();
    }

    class TestLeafNode extends Node implements TestChildInterface {
        public TestLeafNode() {
            super(null);
        }

        public int executeIntf() {
            return this.replace(new TestLeaf2Node()).executeIntf();
        }
    }

    class TestLeaf2Node extends Node implements TestChildInterface {
        public TestLeaf2Node() {
            super(null);
        }

        public int executeIntf() {
            return 21;
        }
    }

    class TestChildNode extends Node implements TestChildInterface {

        @Child private TestChildInterface left;
        @Child private TestChildInterface right;

        public TestChildNode(TestChildInterface left, TestChildInterface right) {
            super(null);
            this.left = left;
            this.right = right;
        }

        @Override
        public int executeIntf() {
            return left.executeIntf() + right.executeIntf();
        }
    }

    class TestChildrenNode extends Node implements TestChildInterface {

        @Children private final TestChildInterface[] children;

        public TestChildrenNode(TestChildInterface[] children) {
            super(null);
            this.children = children;
        }

        @Override
        public int executeIntf() {
            int sum = 0;
            for (int i = 0; i < children.length; ++i) {
                sum += children[i].executeIntf();
            }
            return sum;
        }
    }
}
