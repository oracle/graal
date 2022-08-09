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

import org.graalvm.bisect.core.CompilationUnit;
import org.graalvm.bisect.core.optimization.OptimizationPhase;
import org.graalvm.bisect.core.optimization.OptimizationTreeNode;
import org.graalvm.collections.EconomicMap;

/**
 * Creates a matching by computing an optimal edit script between two optimizations trees using
 * <a href="https://doi.org/10.1016/0020-0190(77)90064-3">Selkow's tree edit distance</a>. The
 * allowed set of operations is leaf insertion, leaf deletion and node relabelling. The original
 * algorithm is extended with the collection of performed operations.
 */
public class SelkowTreeMatcher implements TreeMatcher {
    /**
     * The cost of insertion or deletion per node.
     */
    public static final long INSERT_DELETE_COST = 1;

    /**
     * The cost to rename a phase node.
     */
    public static final long PHASE_RENAME_COST = 1000000000;

    /**
     * The cost to replace an optimization with a different optimization.
     */
    public static final long OPTIMIZATION_REPLACE_COST = 1000000000;

    /**
     * Maps a node to the size of its subtree.
     */
    private EconomicMap<OptimizationTreeNode, Integer> treeSize = null;

    @Override
    public EditScript match(CompilationUnit method1, CompilationUnit method2) {
        treeSize = EconomicMap.create();
        EditScript editScript = new EditScript();
        edit(method1.getRootPhase(), method2.getRootPhase(), editScript, 0);
        treeSize = null;
        return editScript;
    }

    /**
     * Computes the edit distance and the {@link EditScript edit script} between two optimization
     * trees using <a href="https://doi.org/10.1016/0020-0190(77)90064-3">Selkow's tree edit
     * distance</a>. The original algorithm is extended with the collection of performed operations.
     * The operations are collected in such an order that the {@link EditScript edit script} also
     * represents the delta tree in dfs preorder.
     *
     * @param node1 the root of the first (left) optimization subtree
     * @param node2 the root of the second (right) optimization subtree
     * @param editScript the resulting edit script
     * @param depth the depth of the nodes in the tree, i.e, the distances from the respective roots
     * @return the edit distance
     */
    private long edit(OptimizationTreeNode node1, OptimizationTreeNode node2, EditScript editScript, int depth) {
        int m = node1.getChildren().size();
        int n = node2.getChildren().size();
        boolean rootsEqual = nodesEqual(node1, node2);
        long[][] delta = new long[m + 1][n + 1];
        delta[0][0] = rootsEqual ? 0 : relabelCost(node1, node2);
        // scripts[i][j] is the edit script between the (i - 1)-th and (j - 1)-th child's subtree
        EditScript[][] scripts = new EditScript[m + 1][n + 1];
        for (int k = 1; k <= n; ++k) {
            delta[0][k] = delta[0][k - 1] + insertCost(node2.getChildren().get(k - 1));
        }
        for (int k = 1; k <= m; ++k) {
            delta[k][0] = delta[k - 1][0] + deleteCost(node1.getChildren().get(k - 1));
        }
        for (int i = 1; i <= m; ++i) {
            for (int j = 1; j <= n; ++j) {
                OptimizationTreeNode left = node1.getChildren().get(i - 1);
                OptimizationTreeNode right = node2.getChildren().get(j - 1);
                EditScript subtreeScript = new EditScript();
                long relabelDelta = delta[i - 1][j - 1] + edit(left, right, subtreeScript, depth + 1);
                long insertDelta = delta[i][j - 1] + insertCost(right);
                long deleteDelta = delta[i - 1][j] + deleteCost(left);
                delta[i][j] = Math.min(relabelDelta, Math.min(insertDelta, deleteDelta));
                scripts[i][j] = subtreeScript;
            }
        }
        // backtrack to collect the edit script
        int i = m;
        int j = n;
        while (i >= 0 && j >= 0 && i + j > 0) {
            OptimizationTreeNode left = (i > 0) ? node1.getChildren().get(i - 1) : null;
            OptimizationTreeNode right = (j > 0) ? node2.getChildren().get(j - 1) : null;
            if (j > 0 && delta[i][j - 1] + insertCost(right) == delta[i][j]) {
                editScript.insert(right, depth + 1);
                --j;
            } else if (i > 0 && delta[i - 1][j] + deleteCost(left) == delta[i][j]) {
                editScript.delete(left, depth + 1);
                --i;
            } else {
                editScript.concat(scripts[i][j]);
                --i;
                --j;
            }
        }
        if (rootsEqual) {
            editScript.identity(node1, node2, depth);
        } else {
            editScript.relabel(node1, node2, depth);
        }
        return delta[m][n];
    }

    /**
     * Gets the size of the node's subtree with memoization.
     *
     * @param node the root of the subtree
     * @return the size of the node's subtree
     */
    private int getTreeSize(OptimizationTreeNode node) {
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

    /**
     * Gets the cost of the insertion of the node's subtree.
     *
     * @param node the root of the subtree to be inserted
     * @return the cost to insert the node's subtree
     */
    private long insertCost(OptimizationTreeNode node) {
        return getTreeSize(node) * INSERT_DELETE_COST;
    }

    /**
     * Gets the cost of deletion of the node's subtree.
     *
     * @param node the root of the subtree to be deleted
     * @return the cost to delete the node's subtree
     */
    private long deleteCost(OptimizationTreeNode node) {
        return getTreeSize(node) * INSERT_DELETE_COST;
    }

    /**
     * Gets the cost of relabelling the first node to the second node assuming that they are not
     * {@link #nodesEqual equal}.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return the cost to relabel the first node to the second
     */
    private static long relabelCost(OptimizationTreeNode node1, OptimizationTreeNode node2) {
        assert !nodesEqual(node1, node2);
        if (node1 instanceof OptimizationPhase && node2 instanceof OptimizationPhase) {
            return PHASE_RENAME_COST;
        }
        return OPTIMIZATION_REPLACE_COST;
    }

    /**
     * Tests the equality of two nodes. Phases are compared by name, other types are compared by
     * content.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return true iff the nodes are equal
     */
    private static boolean nodesEqual(OptimizationTreeNode node1, OptimizationTreeNode node2) {
        if (node1 instanceof OptimizationPhase && node2 instanceof OptimizationPhase) {
            return node1.getName().equals(node2.getName());
        }
        return node1.equals(node2);
    }
}
