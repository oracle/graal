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
 * <h3>Replacing Nodes at Run Time</h3>
 *
 * <p>
 * The structure of the Truffle tree can be changed at run time by replacing nodes using the
 * {@link Node#replace(Node)} method. This method will automatically change the child pointer in the
 * parent of the node and replace it with a pointer to the new node.
 * </p>
 *
 * <p>
 * Replacing nodes is a costly operation, so it should not happen too often. The convention is that
 * the implementation of the Truffle nodes should ensure that there are maximal a small (and
 * constant) number of node replacements per Truffle node.
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at {@link com.oracle.truffle.api.test.CallTest}.
 * </p>
 */
public class ReplaceTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        UnresolvedNode leftChild = new UnresolvedNode("20");
        UnresolvedNode rightChild = new UnresolvedNode("22");
        TestRootNode rootNode = new TestRootNode(new ValueNode[]{leftChild, rightChild});
        CallTarget target = runtime.createCallTarget(rootNode);
        assertEquals(rootNode, leftChild.getParent());
        assertEquals(rootNode, rightChild.getParent());
        Iterator<Node> iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(leftChild, iterator.next());
        Assert.assertEquals(rightChild, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Object result = target.call();
        assertEquals(42, result);
        assertEquals(42, target.call());
        iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(ResolvedNode.class, iterator.next().getClass());
        Assert.assertEquals(ResolvedNode.class, iterator.next().getClass());
        Assert.assertFalse(iterator.hasNext());
        iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(rootNode, iterator.next().getParent());
        Assert.assertEquals(rootNode, iterator.next().getParent());
        Assert.assertFalse(iterator.hasNext());
    }

    class TestRootNode extends RootNode {

        @Children private final ValueNode[] children;

        TestRootNode(ValueNode[] children) {
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

    abstract class ValueNode extends Node {

        ValueNode() {
        }

        abstract int execute();
    }

    class UnresolvedNode extends ValueNode {

        private final String value;

        UnresolvedNode(String value) {
            this.value = value;
        }

        @Override
        int execute() {
            int intValue = Integer.parseInt(value);
            ResolvedNode newNode = this.replace(new ResolvedNode(intValue));
            return newNode.execute();
        }
    }

    class ResolvedNode extends ValueNode {

        private final int value;

        ResolvedNode(int value) {
            this.value = value;
        }

        @Override
        int execute() {
            return value;
        }
    }
}
