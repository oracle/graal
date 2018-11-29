/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
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
            return doReplace().executeIntf();
        }

        @CompilerDirectives.TruffleBoundary
        private TestLeaf2Node doReplace() {
            return this.replace(new TestLeaf2Node());
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
