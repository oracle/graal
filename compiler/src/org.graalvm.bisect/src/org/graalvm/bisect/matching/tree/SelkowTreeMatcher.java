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

import java.util.HashMap;
import java.util.Map;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.optimization.OptimizationPhase;
import org.graalvm.bisect.core.optimization.OptimizationTreeNode;

/**
 * Creates a matching by computing an optimal edit script between two optimizations trees. The allowed set of operations
 * is leaf insertion, leaf deletion and node relabelling.
 */
public class SelkowTreeMatcher implements TreeMatcher {
    /**
     * The cost of insertion or deletion per node.
     */
    public static final int INSERT_DELETE_COST = 1;
    /**
     * The cost to rename a phase node.
     */
    public static final int PHASE_RENAME_COST = 2;
    /**
     * The cost to replace an optimization with a different optimization.
     */
    public static final int OPTIMIZATION_REPLACE_COST = 1000;

    /**
     * Maps a node to the size of its subtree.
     */
    private Map<OptimizationTreeNode, Integer> treeSize = null;

    @Override
    public EditScript match(ExecutedMethod method1, ExecutedMethod method2) {
        treeSize = new HashMap<>();
        EditScript editScript = new EditScript();
        edit(method1.getRootPhase(), method2.getRootPhase(), editScript);
        treeSize = null;
        return editScript;
    }

    /**
     * Computes the edit distance and the edit script between two optimization trees using
     * <a href="https://doi.org/10.1016/0020-0190(77)90064-3">Selkow's tree edit distance</a>.
     *
     * @param node1 the root of the first optimization tree
     * @param node2 the root of the second optimization tree
     * @param editScript the resulting edit script
     * @return the edit distance
     */
    private int edit(OptimizationTreeNode node1, OptimizationTreeNode node2, EditScript editScript) {
        int m = node1.getChildren().size();
        int n = node2.getChildren().size();
        int[][] delta = new int[m + 1][n +  1];
        delta[0][0] = relabelCost(node1, node2);
        // scripts[i][j] is the edit script between the (i - 1)-th and (j - 1)-th child's subtree
        EditScript[][] scripts = new EditScript[m + 1][n + 1];
        if (!node1.getName().equals(node2.getName())) {
            editScript.relabel(node1, node2);
        }
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
                delta[i][j] = Math.min(
                        delta[i - 1][j - 1] + edit(left, right, subtreeScript),
                        Math.min(
                                delta[i][j - 1] + insertCost(right),
                                delta[i - 1][j] + deleteCost(left)
                        )
                );
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
                editScript.insert(right);
                --j;
            } else if (i > 0 && delta[i - 1][j] + deleteCost(left) == delta[i][j]) {
                editScript.delete(left);
                --i;
            } else {
                editScript.concat(scripts[i][j]);
                --i;
                --j;
            }
        }
        return delta[m][n];
    }

    /**
     * Gets the size of node's subtree with memoization.
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
     * @param node the root of the subtree to be inserted
     * @return the cost to insert the node's subtree
     */
    private int insertCost(OptimizationTreeNode node) {
        return getTreeSize(node) * INSERT_DELETE_COST;
    }

    /**
     * Gets the cost of deletion of the node's subtree
     * @param node the root of the subtree to be deleted
     * @return the cost to delete the node's subtree
     */
    private int deleteCost(OptimizationTreeNode node) {
        return getTreeSize(node) * INSERT_DELETE_COST;
    }

    /**
     * Gets the cost of relabelling the first node to the second node. Returns 0 for equal nodes. If both node are
     * optimization phases, they are compared by names. Otherwise, they are compared by content.
     * @param node1 the first node
     * @param node2 the second node
     * @return the cost to relabel the first node to the second
     */
    private int relabelCost(OptimizationTreeNode node1, OptimizationTreeNode node2) {
        if (node1 instanceof OptimizationPhase && node2 instanceof OptimizationPhase) {
            return (node1.getName().equals(node2.getName())) ? 0 : PHASE_RENAME_COST;
        }
        return (node1.equals(node2)) ? 0 : OPTIMIZATION_REPLACE_COST;
    }
}
