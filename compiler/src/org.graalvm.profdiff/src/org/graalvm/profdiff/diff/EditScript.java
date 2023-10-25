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

import java.util.Objects;

import org.graalvm.profdiff.core.TreeNode;

/**
 * Represents a sequence of operations that modify a rooted, ordered and labeled tree.
 *
 * The allowed operations in an edit script are usually defined as subtree insertion, subtree
 * deletion and node relabelling. However, we want the edit script to be easily convertible to a
 * delta tree. Delta tree is a tree that contains the union of all nodes of the compared tree and
 * additional information for each node whether it is inserted/deleted/relabeled/unchanged.
 * Consequently, we have placed additional constraints on the edit script:
 * <ul>
 * <li>it must remember an identity operation, i.e., a node is not changed</li>
 * <li>the order of operations in the edit script is the dfs preorder of the delta tree</li>
 * </ul>
 */
public class EditScript<T extends TreeNode<T>> {
    /**
     * The prefix of an identity operation.
     */
    public static final String IDENTITY_PREFIX = ". ";

    /**
     * The prefix of a delete operation.
     */
    public static final String DELETE_PREFIX = "- ";

    /**
     * The prefix of an insert operation.
     */
    public static final String INSERT_PREFIX = "+ ";

    /**
     * The prefix of a relabeling operation.
     */
    public static final String RELABEL_PREFIX = "* ";

    /**
     * Type of subtree operation.
     */
    public enum OperationType {
        Identity,
        Delete,
        Insert,
        Relabel
    }

    /**
     * An operation that modifies a rooted, ordered and labeled tree. It is a node of the delta
     * tree. Each operation works on at most one node from the first (left) tree and at most one
     * node from the second (right) tree. The nodes have always the same depth (the depth of root is
     * 0).
     */
    public static final class Operation<T extends TreeNode<T>> {
        private final OperationType operationType;
        private final T left;
        private final T right;
        private final int depth;

        private Operation(OperationType operationType, T left, T right, int depth) {
            this.operationType = operationType;
            this.left = left;
            this.right = right;
            this.depth = depth;
        }

        public OperationType getOperationType() {
            return operationType;
        }

        public T getLeft() {
            return left;
        }

        public T getRight() {
            return right;
        }

        public int getDepth() {
            return depth;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || !getClass().equals(obj.getClass())) {
                return false;
            }

            Operation<?> operation = (Operation<?>) obj;
            return operationType.equals(operation.operationType) && depth == operation.depth &&
                            Objects.equals(left, operation.left) && Objects.equals(right, operation.right);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operationType, depth, left, right);
        }
    }

    private ConcatList<Operation<T>> operations = new ConcatList<>();

    /**
     * Prepends a subtree insert operation to this edit script.
     *
     * @param node the root of the inserted subtree
     * @param depth the depth of the node
     */
    public void insert(T node, int depth) {
        operations.prepend(new Operation<>(OperationType.Insert, null, node, depth));
    }

    /**
     * Prepends a subtree delete operation to this edit script.
     *
     * @param node the root of the deleted subtree
     * @param depth the depth of the node
     */
    public void delete(T node, int depth) {
        operations.prepend(new Operation<>(OperationType.Delete, node, null, depth));
    }

    /**
     * Prepends a relabelling operation to this edit script.
     *
     * @param node1 the node with the original name from the first (left) tree
     * @param node2 the node with the new name from the second (right) tree
     * @param depth the depth of the nodes
     */
    public void relabel(T node1, T node2, int depth) {
        operations.prepend(new Operation<>(OperationType.Relabel, node1, node2, depth));
    }

    /**
     * Prepends an identity operation to this edit script.
     *
     * @param node1 the matched node from the first (left) tree
     * @param node2 the matched node from the second (right) tree
     * @param depth the depth of the nodes
     */
    public void identity(T node1, T node2, int depth) {
        operations.prepend(new Operation<>(OperationType.Identity, node1, node2, depth));
    }

    /**
     * Prepends the operations of the other edit script to the operations of this edit script. The
     * other edit script will be empty after the concatenation.
     *
     * @param otherScript the other edit script which will be emptied
     */
    public void transferFrom(EditScript<T> otherScript) {
        operations = otherScript.operations.transferFrom(operations);
        otherScript.operations = null;
    }

    /**
     * Gets the list of operations.
     *
     * @return the list of operations
     */
    public ConcatList<Operation<T>> getOperations() {
        return operations;
    }
}
