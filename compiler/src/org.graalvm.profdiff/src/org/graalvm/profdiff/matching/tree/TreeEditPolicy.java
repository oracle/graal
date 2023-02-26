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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.profdiff.core.TreeNode;

/**
 * Provides an equality test of two {@link TreeNode tree nodes} and determines costs of edit
 * operations. It is assumed that there is a constant cost of node insertion and it is equal to the
 * cost of deletion. The cost to insert/delete a subtree is then calculated by multiplying the
 * constant by the size of the subtree. Sizes of the subtrees are calculated with memoization (the
 * identity of a node is mapped to its subtree size).
 *
 * @param <T> the concrete type of the {@link TreeNode}
 */
public abstract class TreeEditPolicy<T extends TreeNode<T>> {
    /**
     * Minimal non-zero cost of an operation.
     */
    public static final long UNIT_COST = 1;

    /**
     * Effectively infinite cost. Operations with this cost are discouraged.
     */
    public static final long INFINITE_COST = 1000000000;

    /**
     * Maps a node to the size of its subtree.
     */
    private final EconomicMap<T, Integer> treeSize = EconomicMap.create(Equivalence.IDENTITY);

    /**
     * Gets the cost of relabelling the first node to the second node assuming they are not
     * {@link #nodesEqual equal}.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return the cost to relabel the first node to the second
     */
    public long relabelCost(T node1, T node2) {
        assert node1 != node2;
        return INFINITE_COST;
    }

    /**
     * Tests two nodes for equality.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return {@code true} iff the nodes are equal
     */
    public abstract boolean nodesEqual(T node1, T node2);

    /**
     * Gets the cost of an insertion (deletion) of a single node.
     */
    protected long insertDeleteCostPerNode() {
        return UNIT_COST;
    }

    /**
     * Gets the cost of the insertion of the node's subtree.
     *
     * @param node the root of the subtree to be inserted
     * @return the cost to insert the node's subtree
     */
    public long insertCost(T node) {
        return getTreeSize(node) * insertDeleteCostPerNode();
    }

    /**
     * Gets the cost of deletion of the node's subtree.
     *
     * @param node the root of the subtree to be deleted
     * @return the cost to delete the node's subtree
     */
    public long deleteCost(T node) {
        return getTreeSize(node) * insertDeleteCostPerNode();
    }

    /**
     * Gets the size of the node's subtree with memoization.
     *
     * @param node the root of the subtree
     * @return the size of the node's subtree
     */
    private int getTreeSize(T node) {
        if (node.getChildren().isEmpty()) {
            return 1;
        }
        if (treeSize.containsKey(node)) {
            return treeSize.get(node);
        }
        int result = 1 + node.getChildren().stream().mapToInt(this::getTreeSize).sum();
        treeSize.put(node, result);
        return result;
    }
}
