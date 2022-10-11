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

import org.graalvm.profdiff.core.TreeNode;

import java.lang.reflect.Array;

/**
 * Creates a matching by computing an optimal edit script between two trees using
 * <a href="https://doi.org/10.1016/0020-0190(77)90064-3">Selkow's tree edit distance</a>. The
 * allowed set of operations is leaf insertion, leaf deletion and node relabelling. The original
 * algorithm is extended with the collection of performed operations.
 */
public class SelkowTreeMatcher<T extends TreeNode<T>> implements TreeMatcher<T> {
    /**
     * The policy that performs equality tests and determines costs of edit operations.
     */
    private final TreeEditPolicy<T> editPolicy;

    public SelkowTreeMatcher(TreeEditPolicy<T> editPolicy) {
        this.editPolicy = editPolicy;
    }

    @Override
    public EditScript<T> match(T root1, T root2) {
        EditScript<T> editScript = new EditScript<>();
        edit(root1, root2, editScript, 0);
        return editScript;
    }

    /**
     * Computes the edit distance and the {@link EditScript edit script} between two trees using
     * <a href="https://doi.org/10.1016/0020-0190(77)90064-3">Selkow's tree edit distance</a>. The
     * original algorithm is extended with the collection of performed operations. The operations
     * are collected in such an order that the {@link EditScript edit script} also represents the
     * delta tree in dfs preorder.
     *
     * @param node1 the root of the first (left) subtree
     * @param node2 the root of the second (right) subtree
     * @param editScript the resulting edit script
     * @param depth the depth of the nodes in the tree, i.e, the distances from the respective roots
     * @return the edit distance
     */
    @SuppressWarnings("unchecked")
    private long edit(T node1, T node2, EditScript<T> editScript, int depth) {
        int m = node1.getChildren().size();
        int n = node2.getChildren().size();
        boolean rootsEqual = editPolicy.nodesEqual(node1, node2);
        long[][] delta = new long[m + 1][n + 1];
        delta[0][0] = rootsEqual ? 0 : editPolicy.relabelCost(node1, node2);
        // scripts[i][j] is the edit script between the (i - 1)-th and (j - 1)-th child's subtree
        EditScript<T>[][] scripts = (EditScript<T>[][]) Array.newInstance(EditScript.class, m + 1, n + 1);
        for (int k = 1; k <= n; ++k) {
            delta[0][k] = delta[0][k - 1] + editPolicy.insertCost(node2.getChildren().get(k - 1));
        }
        for (int k = 1; k <= m; ++k) {
            delta[k][0] = delta[k - 1][0] + editPolicy.deleteCost(node1.getChildren().get(k - 1));
        }
        for (int i = 1; i <= m; ++i) {
            for (int j = 1; j <= n; ++j) {
                T left = node1.getChildren().get(i - 1);
                T right = node2.getChildren().get(j - 1);
                EditScript<T> subtreeScript = new EditScript<>();
                long relabelDelta = delta[i - 1][j - 1] + edit(left, right, subtreeScript, depth + 1);
                long insertDelta = delta[i][j - 1] + editPolicy.insertCost(right);
                long deleteDelta = delta[i - 1][j] + editPolicy.deleteCost(left);
                delta[i][j] = Math.min(relabelDelta, Math.min(insertDelta, deleteDelta));
                scripts[i][j] = subtreeScript;
            }
        }
        // backtrack to collect the edit script
        int i = m;
        int j = n;
        while (i >= 0 && j >= 0 && i + j > 0) {
            T left = (i > 0) ? node1.getChildren().get(i - 1) : null;
            T right = (j > 0) ? node2.getChildren().get(j - 1) : null;
            if (j > 0 && delta[i][j - 1] + editPolicy.insertCost(right) == delta[i][j]) {
                editScript.insert(right, depth + 1);
                --j;
            } else if (i > 0 && delta[i - 1][j] + editPolicy.deleteCost(left) == delta[i][j]) {
                editScript.delete(left, depth + 1);
                --i;
            } else {
                editScript.transferFrom(scripts[i][j]);
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
}
