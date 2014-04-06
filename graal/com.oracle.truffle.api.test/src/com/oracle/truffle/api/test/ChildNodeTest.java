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

import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node.Child;

/**
 * <h3>Creating a Child Node</h3>
 *
 * <p>
 * Child nodes are stored in the class of the parent node in fields that are marked with the
 * {@link Child} annotation. Before such a field is assigned, {@link Node#adoptChild} must be
 * called. This method automatically establishes a link from the child to the parent. The
 * {@link Node#getParent()} method allows access to this field. Every node also provides the ability
 * to iterate over its children using {@link Node#getChildren()}.
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
        Assert.assertEquals(rootNode, leftChild.getParent());
        Assert.assertEquals(rootNode, rightChild.getParent());
        Iterator<Node> iterator = rootNode.getChildren().iterator();
        Assert.assertEquals(leftChild, iterator.next());
        Assert.assertEquals(rightChild, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        Object result = target.call();
        Assert.assertEquals(42, result);
    }

    class TestRootNode extends RootNode {

        @Child private TestChildNode left;
        @Child private TestChildNode right;

        public TestRootNode(TestChildNode left, TestChildNode right) {
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

        public TestChildNode() {
            super(null);
        }

        public int execute() {
            return 21;
        }
    }
}
