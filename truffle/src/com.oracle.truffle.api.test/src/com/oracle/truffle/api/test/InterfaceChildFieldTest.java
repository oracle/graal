/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

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

        TestRootNode(TestChildInterface child) {
            super(null);
            this.child = child;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child.executeIntf();
        }
    }

    interface TestChildInterface extends NodeInterface {
        int executeIntf();
    }

    class TestLeafNode extends Node implements TestChildInterface {
        TestLeafNode() {
        }

        public int executeIntf() {
            return this.replace(new TestLeaf2Node()).executeIntf();
        }
    }

    class TestLeaf2Node extends Node implements TestChildInterface {
        TestLeaf2Node() {
        }

        public int executeIntf() {
            return 21;
        }
    }

    class TestChildNode extends Node implements TestChildInterface {

        @Child private TestChildInterface left;
        @Child private TestChildInterface right;

        TestChildNode(TestChildInterface left, TestChildInterface right) {
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

        TestChildrenNode(TestChildInterface[] children) {
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
