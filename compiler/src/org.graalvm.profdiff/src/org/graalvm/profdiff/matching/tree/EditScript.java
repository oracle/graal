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
package org.graalvm.profdiff.matching.tree;

import java.util.Objects;

import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.util.ConcatList;
import org.graalvm.profdiff.util.Writer;

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
public class EditScript<T extends TreeNode<T>> implements TreeMatching {
    /**
     * The prefix of an identity operation.
     */
    public static final String IDENTITY_PREFIX = "    ";

    /**
     * The prefix of a delete operation.
     */
    public static final String DELETE_PREFIX = "  - ";

    /**
     * The prefix of an insert operation.
     */
    public static final String INSERT_PREFIX = "  + ";

    /**
     * The prefix of a relabeling operation.
     */
    public static final String RELABEL_PREFIX = "  * ";

    /**
     * An operation that modifies a rooted, ordered and labeled tree. It is a node of the delta
     * tree. Each operation works on at most one node from the first (left) tree and at most one
     * node from the second (right) tree. The nodes have always the same depth (the depth of root is
     * 0). Remembering the depth allows us to easily {@link DeltaNode#write write} the delta tree in
     * dfs preorder.
     */
    public abstract static class DeltaNode<T extends TreeNode<T>> {
        protected final T left;
        protected final T right;
        protected final int depth;

        protected DeltaNode(T left, T right, int depth) {
            this.left = left;
            this.right = right;
            this.depth = depth;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || !getClass().equals(obj.getClass())) {
                return false;
            }

            DeltaNode<?> deltaNode = (DeltaNode<?>) obj;

            if (!Objects.equals(left, deltaNode.left)) {
                return false;
            }
            return Objects.equals(right, deltaNode.right);
        }

        @Override
        public int hashCode() {
            int result = left != null ? left.hashCode() : 0;
            result = 31 * result + (right != null ? right.hashCode() : 0);
            return result;
        }

        /**
         * Writes the string representation either of this delta subtree or this delta node to the
         * destination writer. When an entire subtree is deleted/inserted, we create only one delta
         * node for the whole subtree - in that case, this method writes the entire subtree.
         * Otherwise, this object represents exactly one delta node and only prints itself.
         *
         * @param writer the destination writer
         */
        public abstract void write(Writer writer);
    }

    /**
     * An identity operation, which represents a pair of matched nodes.
     */
    public static class Identity<T extends TreeNode<T>> extends DeltaNode<T> {
        /**
         * Constructor of an identity operation.
         *
         * @param left the matched node from the first (left) tree
         * @param right the matched node from the second (right) tree
         * @param depth the depth of the nodes
         */
        public Identity(T left, T right, int depth) {
            super(left, right, depth);
        }

        @Override
        public void write(Writer writer) {
            writer.increaseIndent(depth);
            writer.setPrefixAfterIndent(IDENTITY_PREFIX);
            left.writeHead(writer);
            writer.clearPrefixAfterIndent();
            writer.decreaseIndent(depth);
        }
    }

    /**
     * Insertion of a subtree.
     */
    public static class Insert<T extends TreeNode<T>> extends DeltaNode<T> {
        /**
         * Constructor of a subtree insert operation.
         *
         * @param node the root of the subtree that is inserted
         * @param depth the depth of the root of the inserted subtree
         */
        public Insert(T node, int depth) {
            super(null, node, depth);
        }

        @Override
        public void write(Writer writer) {
            writer.increaseIndent(depth);
            writer.setPrefixAfterIndent(INSERT_PREFIX);
            right.writeRecursive(writer);
            writer.clearPrefixAfterIndent();
            writer.decreaseIndent(depth);
        }
    }

    /**
     * Deletion of a subtree.
     */
    public static class Delete<T extends TreeNode<T>> extends DeltaNode<T> {

        /**
         * Constructor of a subtree delete operation.
         *
         * @param node the root of the subtree that is deleted
         * @param depth the depth of the root of the deleted subtree
         */
        public Delete(T node, int depth) {
            super(node, null, depth);
        }

        @Override
        public void write(Writer writer) {
            writer.increaseIndent(depth);
            writer.setPrefixAfterIndent(DELETE_PREFIX);
            left.writeRecursive(writer);
            writer.clearPrefixAfterIndent();
            writer.decreaseIndent(depth);
        }
    }

    /**
     * An operation that changes the label - the {@link TreeNode#getName() name} of a node.
     */
    public static class Relabel<T extends TreeNode<T>> extends DeltaNode<T> {
        /**
         * Constructor of a relabelling operation.
         *
         * @param left the node which holds the original name from the first (left) tree
         * @param right the node which hold the new name from the second (right) tree
         * @param depth the depth of the nodes
         */
        public Relabel(T left, T right, int depth) {
            super(left, right, depth);
        }

        @Override
        public void write(Writer writer) {
            writer.increaseIndent(depth);
            writer.writeln(RELABEL_PREFIX + left.getNameOrNull() + " -> " + right.getNameOrNull());
            writer.decreaseIndent(depth);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            Relabel<?> relabel = (Relabel<?>) obj;
            return Objects.equals(left, relabel.left) && Objects.equals(right, relabel.right);
        }

        @Override
        public int hashCode() {
            int result = left.hashCode();
            result = 31 * result + right.hashCode();
            return result;
        }
    }

    private ConcatList<DeltaNode<T>> operations = new ConcatList<>();

    /**
     * Prepends a subtree insert operation to this edit script.
     *
     * @param node the root of the inserted subtree
     * @param depth the depth of the node
     */
    public void insert(T node, int depth) {
        operations.prepend(new Insert<>(node, depth));
    }

    /**
     * Prepends a subtree delete operation to this edit script.
     *
     * @param node the root of the deleted subtree
     * @param depth the depth of the node
     */
    public void delete(T node, int depth) {
        operations.prepend(new Delete<>(node, depth));
    }

    /**
     * Prepends a relabelling operation to this edit script.
     *
     * @param node1 the node with the original name from the first (left) tree
     * @param node2 the node with the new name from the second (right) tree
     * @param depth the depth of the nodes
     */
    public void relabel(T node1, T node2, int depth) {
        operations.prepend(new Relabel<>(node1, node2, depth));
    }

    /**
     * Prepends an identity operation to this edit script.
     *
     * @param node1 the matched node from the first (left) tree
     * @param node2 the matched node from the second (right) tree
     * @param depth the depth of the nodes
     */
    public void identity(T node1, T node2, int depth) {
        operations.prepend(new Identity<>(node1, node2, depth));
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
     * Writes the delta subtree represented by this edit script in dfs preorder to the destination
     * writer.
     *
     * @param writer the destination writer
     */
    @Override
    public void write(Writer writer) {
        for (DeltaNode<T> operation : operations) {
            operation.write(writer);
        }
    }

    /**
     * Gets the list of operations.
     *
     * @return the list of operations
     */
    public ConcatList<DeltaNode<T>> getDeltaNodes() {
        return operations;
    }
}
