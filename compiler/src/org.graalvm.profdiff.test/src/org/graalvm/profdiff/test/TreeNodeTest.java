/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.test;

import org.graalvm.profdiff.core.TreeNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TreeNodeTest {
    private static class MockTreeNode extends TreeNode<MockTreeNode> {
        protected MockTreeNode(String name) {
            super(name);
        }
    }

    private static class MockTree {
        public final MockTreeNode root;

        public final MockTreeNode left;

        public final MockTreeNode right;

        public final MockTreeNode rightLeft;

        public final MockTreeNode rightRight;

        public final List<MockTreeNode> preorder;

        public final List<MockTreeNode> postorder;

        MockTree() {
            root = new MockTreeNode("root");
            left = new MockTreeNode("left");
            root.addChild(left);
            right = new MockTreeNode("right");
            root.addChild(right);
            rightLeft = new MockTreeNode("rightLeft");
            right.addChild(rightLeft);
            rightRight = new MockTreeNode("rightRight");
            right.addChild(rightRight);
            preorder = List.of(root, left, right, rightLeft, rightRight);
            postorder = List.of(left, rightLeft, rightRight, right, root);
        }
    }

    @Test
    public void forEachToCreatePreorder() {
        List<MockTreeNode> preorder = new ArrayList<>();
        MockTree mockTree = new MockTree();
        mockTree.root.forEach(preorder::add);
        Assert.assertEquals(mockTree.preorder, preorder);
    }

    @Test
    public void forEachToCreatePreorderAndPostorder() {
        List<MockTreeNode> preorder = new ArrayList<>();
        List<MockTreeNode> postorder = new ArrayList<>();
        MockTree mockTree = new MockTree();
        mockTree.root.forEach(preorder::add, postorder::add);
        Assert.assertEquals(mockTree.preorder, preorder);
        Assert.assertEquals(mockTree.postorder, postorder);
    }

    @Test
    public void removeRightSubtree() {
        MockTree mockTree = new MockTree();
        mockTree.root.removeIf(mockTreeNode -> mockTreeNode == mockTree.right);
        Assert.assertNull(mockTree.right.parent);
        List<MockTreeNode> expectedPreorderAfterRemoval = List.of(mockTree.root, mockTree.left);
        List<MockTreeNode> actualPreorder = new ArrayList<>();
        mockTree.root.forEach(actualPreorder::add);
        Assert.assertEquals(expectedPreorderAfterRemoval, actualPreorder);
    }
}
