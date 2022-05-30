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
package org.graalvm.bisect.matching.tree;

import java.util.Objects;

import org.graalvm.bisect.core.optimization.OptimizationTreeNode;
import org.graalvm.bisect.util.ConcatList;
import org.graalvm.bisect.util.Writer;

/**
 * Represents a sequence of operations that modify a rooted, ordered and labeled tree.
 */
public class EditScript implements TreeMatching {
    /**
     * An operation that modifies a rooted, ordered and labeled tree.
     */
    public interface Operation {

    }

    /**
     * Insertion of a subtree.
     */
    public static class Insert implements Operation {
        private final OptimizationTreeNode node;

        /**
         * Constructor of a subtree insert operation.
         * @param node the root of the subtree that is inserted
         */
        public Insert(OptimizationTreeNode node) {
            this.node = node;
        }

        @Override
        public String toString() {
            return "INSERT " + node.getName();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }

            Insert insert = (Insert) object;
            return Objects.equals(node, insert.node);
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }
    }

    /**
     * Deletion of a subtree.
     */
    public static class Delete implements Operation {
        private final OptimizationTreeNode node;

        /**
         * Constructor of a subtree delete operation.
         * @param node the root of the subtree that is deletion
         */
        public Delete(OptimizationTreeNode node) {
            this.node = node;
        }

        @Override
        public String toString() {
            return "DELETE " + node.getName();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }

            Delete delete = (Delete) object;
            return Objects.equals(node, delete.node);
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }
    }

    /**
     * An operation that changes the label - the {@link OptimizationTreeNode#getName() name} of a node.
     */
    public static class Relabel implements Operation {
        private final OptimizationTreeNode node1;
        private final OptimizationTreeNode node2;

        /**
         * Constructor of a relabelling operation.
         * @param node1 the node which holds the original name
         * @param node2 the node which hold the new name
         */
        public Relabel(OptimizationTreeNode node1, OptimizationTreeNode node2) {
            this.node1 = node1;
            this.node2 = node2;
        }

        @Override
        public String toString() {
            return "RELABEL " + node1.getName() + " -> " + node2.getName();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }

            Relabel relabel = (Relabel) object;
            return Objects.equals(node1, relabel.node1) && Objects.equals(node2, relabel.node2);
        }

        @Override
        public int hashCode() {
            int result = node1.hashCode();
            result = 31 * result + node2.hashCode();
            return result;
        }
    }

    private final ConcatList<Operation> operations = new ConcatList<>();

    /**
     * Adds a subtree insert operation to this edit script.
     * @param node the root of the inserted subtree
     */
    public void insert(OptimizationTreeNode node) {
        operations.add(new Insert(node));
    }

    /**
     * Adds a subtree delete operations to this edit script.
     * @param node the root of the deleted subtree
     */
    public void delete(OptimizationTreeNode node) {
        operations.add(new Delete(node));
    }

    /**
     * Adds a relabelling operations to this edit script.
     * @param node1 the node with the original name
     * @param node2 the node with the new name
     */
    public void relabel(OptimizationTreeNode node1, OptimizationTreeNode node2) {
        operations.add(new Relabel(node1, node2));
    }

    /**
     * Concatenates the operations of the other edit script to the operations of this edit script. The other edit script
     * will be empty after the concatenation.
     * @param otherScript the other edit script which will be emptied
     */
    public void concat(EditScript otherScript) {
        operations.concat(otherScript.operations);
    }

    @Override
    public void writeSummary(Writer writer) {
        writer.increaseIndent();
        for (Operation operation : operations) {
            writer.writeln(operation.toString());
        }
        writer.decreaseIndent();
    }

    /**
     * Gets the list of operations.
     * @return the list of operations
     */
    public ConcatList<Operation> getOperations() {
        return operations;
    }
}
