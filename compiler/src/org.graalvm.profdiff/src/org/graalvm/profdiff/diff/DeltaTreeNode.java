/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.diff;

import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.core.Writer;

/**
 * A node in the delta-tree representation of a tree diff. Each node is a pair of nodes, denote them
 * as {@code left} and {@code right}, from the original diffed tree. The {@code left} node comes
 * from the left diffed tree and the {@code right} comes from the right tree. Either of these nodes
 * can be {@code null}. The node represents one of the following operations:
 *
 * <ul>
 * <li>identity - both {@code left} and {@code right} are non-null and equal,</li>
 * <li>relabeling - both {@code left} and {@code right} are non-null and unequal,</li>
 * <li>deletion - {@code left} is not null and {@code right} is null,</li>
 * <li>insertion - {@code left} is null and {@code right} is not null</li>
 * </ul>
 *
 * @param <T> the type of the node of the diffed tree
 */
public class DeltaTreeNode<T extends TreeNode<T>> extends TreeNode<DeltaTreeNode<T>> {
    /**
     * The depth of this node in the delta tree, which is equal to the depths of the original nodes
     * {@link #left} and {@link #right}.
     */
    private final int depth;

    /**
     * The original node from the left diffed tree.
     */
    private final T left;

    /**
     * The original node from the right diffed tree.
     */
    private final T right;

    /**
     * {@code true} if this operation is an identity.
     */
    private final boolean identity;

    /**
     * Constructs a delta tree node.
     *
     * @param depth the distance from root
     * @param identity whether the node represents an identity operation
     * @param left the original node from the left diffed tree
     * @param right the original node from the right diffed tree
     */
    public DeltaTreeNode(int depth, boolean identity, T left, T right) {
        super(null);
        this.depth = depth;
        this.identity = identity;
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean isInfoNode() {
        return (left != null && left.isInfoNode()) || (right != null && right.isInfoNode());
    }

    /**
     * Add a child operation to this delta node.
     *
     * @param childIsIdentity {@code true} if the operation to be added is an identity
     * @param leftNode the original left node present in the operation
     * @param rightNode the original right node present in the operation
     * @return the added delta node
     */
    public DeltaTreeNode<T> addChild(boolean childIsIdentity, T leftNode, T rightNode) {
        if (childIsIdentity && (leftNode == null || rightNode == null)) {
            throw new IllegalArgumentException("The delta node cannot be an identity if any of the values is null.");
        }
        DeltaTreeNode<T> child = new DeltaTreeNode<>(depth + 1, childIsIdentity, leftNode, rightNode);
        addChild(child);
        return child;
    }

    /**
     * Returns the original node from the left diffed tree that is the operand of this operation.
     * The node is {@code null} when this operation is an insertion.
     *
     * @return the left operand of this operation
     */
    public T getLeft() {
        return left;
    }

    /**
     * Returns the original node from the right diffed tree that is the operand of this operation.
     * The node is {@code null} when this operation is a deletion.
     *
     * @return the right operand of this operation
     */
    public T getRight() {
        return right;
    }

    /**
     * Returns whether this node represents the operation identity.
     */
    public boolean isIdentity() {
        return identity;
    }

    /**
     * Returns whether this node represents the operation insert.
     */
    public boolean isInsertion() {
        return left == null && right != null;
    }

    /**
     * Returns whether this node represents the operation delete.
     */
    public boolean isDeletion() {
        return left != null && right == null;
    }

    /**
     * Returns whether this node represents the operation relabel.
     */
    public boolean isRelabeling() {
        return left != null && right != null && !identity;
    }

    /**
     * Returns the depth of this node (distance from the root) in its delta tree.
     *
     * @return the distance from the root
     */
    public int getDepth() {
        return depth;
    }

    @Override
    public void writeHead(Writer writer) {
        if (isIdentity()) {
            writer.write(EditScript.IDENTITY_PREFIX);
        } else if (isInsertion()) {
            writer.write(EditScript.INSERT_PREFIX);
        } else if (isDeletion()) {
            writer.write(EditScript.DELETE_PREFIX);
        } else {
            assert isRelabeling() : "the node represents one of the possible operations";
            writer.write(EditScript.RELABEL_PREFIX);
        }
        if (left != null) {
            left.writeHead(writer);
        } else {
            assert right != null : "either node must not be null";
            right.writeHead(writer);
        }
    }
}
