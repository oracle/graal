/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

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
        assertEquals(rootNode, leftChild.getParent());
        assertEquals(rootNode, rightChild.getParent());
        Iterator<Node> iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(leftChild, iterator.next());
        Assert.assertEquals(rightChild, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        CallTarget target = runtime.createCallTarget(rootNode);
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

        public TestRootNode(ValueNode[] children) {
            super(null);
            this.children = adoptChildren(children);
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

        public ValueNode() {
            super(null);
        }

        abstract int execute();
    }

    class UnresolvedNode extends ValueNode {

        private final String value;

        public UnresolvedNode(String value) {
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
