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

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * <h3>Creating an Array of Children Nodes</h3>
 *
 * <p>
 * An array of children nodes can be used as a field in a parent node. The field has to be annotated
 * with {@link com.oracle.truffle.api.nodes.Node.Children} and must be declared private and final.
 * Before assigning the field in the parent node constructor, {@link Node#adoptChildren} must be
 * called in order to update the parent pointers in the child nodes. After filling the array with
 * its first values, it must never be changed. It is only possible to call {@link Node#replace} on a
 * child node.
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.FinalFieldTest} .
 * </p>
 */
public class ChildrenNodesTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        TestChildNode firstChild = new TestChildNode();
        TestChildNode secondChild = new TestChildNode();
        TestRootNode rootNode = new TestRootNode(new TestChildNode[]{firstChild, secondChild});
        CallTarget target = runtime.createCallTarget(rootNode);
        Assert.assertEquals(rootNode, firstChild.getParent());
        Assert.assertEquals(rootNode, secondChild.getParent());
        Iterator<Node> iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(firstChild, iterator.next());
        Assert.assertEquals(secondChild, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Object result = target.call();
        Assert.assertEquals(42, result);
    }

    class TestRootNode extends RootNode {

        @Children private final TestChildNode[] children;

        TestRootNode(TestChildNode[] children) {
            super(null);
            this.children = children;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int sum = 0;
            for (int i = 0; i < children.length; ++i) {
                sum += children[i].execute();
            }
            return sum;
        }
    }

    class TestChildNode extends Node {

        TestChildNode() {
        }

        public int execute() {
            return 21;
        }
    }

    @Test
    public void testMultipleChildrenFields() {
        TruffleRuntime runtime = Truffle.getRuntime();
        TestChildNode firstChild = new TestChildNode();
        TestChildNode secondChild = new TestChildNode();
        TestChildNode thirdChild = new TestChildNode();
        TestChildNode forthChild = new TestChildNode();
        TestRootNode rootNode = new TestRoot2Node(new TestChildNode[]{firstChild, secondChild}, new TestChildNode[]{thirdChild, forthChild});
        CallTarget target = runtime.createCallTarget(rootNode);
        Assert.assertEquals(rootNode, firstChild.getParent());
        Assert.assertEquals(rootNode, secondChild.getParent());
        Assert.assertEquals(rootNode, thirdChild.getParent());
        Assert.assertEquals(rootNode, forthChild.getParent());
        Iterator<Node> iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(firstChild, iterator.next());
        Assert.assertEquals(secondChild, iterator.next());
        Assert.assertEquals(thirdChild, iterator.next());
        Assert.assertEquals(forthChild, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Object result = target.call();
        Assert.assertEquals(2 * 42, result);
    }

    class TestRoot2Node extends TestRootNode {
        @Children private final TestChildNode[] children1;
        @Children private final TestChildNode[] children2;

        TestRoot2Node(TestChildNode[] children1, TestChildNode[] children2) {
            super(new TestChildNode[0]);
            this.children1 = children1;
            this.children2 = children2;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            int sum = 0;
            for (int i = 0; i < children1.length; ++i) {
                sum += children1[i].execute();
            }
            for (int i = 0; i < children2.length; ++i) {
                sum += children2[i].execute();
            }
            return sum;
        }
    }
}
