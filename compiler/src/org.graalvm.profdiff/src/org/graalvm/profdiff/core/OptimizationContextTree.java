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
package org.graalvm.profdiff.core;

import java.util.List;
import java.util.Optional;

import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationTree;

/**
 * An optimization context tree is an inlining tree extended with optimizations placed in their method context.
 * Optimization-context trees make it easier to attribute optimizations to inlining decisions.
 *
 * Consider the inlining and optimization tree below:
 *
 * @formatter:off
 * <pre>
 * A compilation unit of the method a()
 *     Inlining tree
 *         a() at bci -1
 *             b() at bci 1
 *                 d() at bci 3
 *             c() at bci 2
 *     Optimization tree
 *         RootPhase
 *             SomeOptimizationPhase
 *                 SomeOptimization OptimizationA at bci {a(): 3}
 *                 SomeOptimization OptimizationB at bci {b(): 4, a(): 1}
 *                 SomeOptimization OptimizationC at bci {c(): 5, a(): 2}
 *                 SomeOptimization OptimizationD at bci {d(): 6, b(): 3, a(): 1}
 * </pre>
 * @formatter:on
 *
 * By combining the trees, we obtain the following optimization-context tree:
 *
 * @formatter:off
 * <pre>
 * A compilation unit of the method a()
 *     Optimization-context tree
 *         a() at bci -1
 *             SomeOptimization OptimizationA at bci 3
 *             b() at bci 1
 *                 SomeOptimization OptimizationB at bci 4
 *                 d() at bci 3
 *                     SomeOptimization OptimizationD at bci 6
 *             c() at bci 2
 *               SomeOptimization OptimizationC at bci 5
 * </pre>
 * @formatter:on
 */
public final class OptimizationContextTree {
    /**
     * The root node of the tree.
     */
    private final OptimizationContextTreeNode root;

    private OptimizationContextTree(OptimizationContextTreeNode root) {
        this.root = root;
    }

    /**
     * Gets the root node of the tree.
     */
    public OptimizationContextTreeNode getRoot() {
        return root;
    }

    /**
     * Recursively finds the last inlining node on a path. Returns {@code null} if there is no such
     * node. Only tree nodes corresponding to inlining tree nodes are considered. Inlining tree
     * nodes corresponding to abstract methods ({@link InliningTreeNode#isAbstract()}) are skipped
     * as if they were removed from the tree and their children reattached to the closest ancestor.
     *
     * If several nodes match the path, then the first match is returned. Note that backtracking is
     * necessary, because a path element may match 2 different edges, and one of the edges may lead
     * to a dead-end.
     *
     * @param node the last node on the path (initially {@code null})
     * @param path the path from root
     * @param pathIndex the index to the next path element in the path (initially 0)
     * @return the last node found on the path or {@code null}
     */
    private OptimizationContextTreeNode findLastNodeAt(OptimizationContextTreeNode node, InliningPath path, int pathIndex) {
        if (pathIndex >= path.size()) {
            return node;
        }
        InliningPath.PathElement element = path.get(pathIndex);
        List<OptimizationContextTreeNode> children = node == null ? List.of(root) : node.getChildren();
        for (OptimizationContextTreeNode child : children) {
            if (child.getOriginalInliningTreeNode() == null) {
                continue;
            }
            InliningTreeNode inliningTreeNode = child.getOriginalInliningTreeNode();
            OptimizationContextTreeNode result = null;
            if (inliningTreeNode.isAbstract()) {
                result = findLastNodeAt(child, path, pathIndex);
            } else if (element.matches(inliningTreeNode.pathElement())) {
                result = findLastNodeAt(child, path, pathIndex + 1);
            }
            if (result != null) {
                return result;
            }
        }
        return node;
    }

    /**
     * Creates an optimization-context tree by cloning an inlining tree.
     *
     * @param inliningTree the inlining tree to be cloned
     * @return an optimization-context tree corresponding to a cloned inlining tree
     */
    private static OptimizationContextTree fromInliningTree(InliningTree inliningTree) {
        OptimizationContextTreeNode root = new OptimizationContextTreeNode();
        fromInliningNode(root, inliningTree.getRoot());
        return new OptimizationContextTree(root);
    }

    /**
     * Clones an inlining subtree into an optimization-context tree node.
     *
     * @param parent the optimization-context node (initially without children)
     * @param inliningTreeNode the inlining subtree to be cloned into the optimization-context tree
     */
    private static void fromInliningNode(OptimizationContextTreeNode parent, InliningTreeNode inliningTreeNode) {
        OptimizationContextTreeNode optimizationContextTreeNode = new OptimizationContextTreeNode(inliningTreeNode);
        parent.addChild(optimizationContextTreeNode);
        for (InliningTreeNode child : inliningTreeNode.getChildren()) {
            fromInliningNode(optimizationContextTreeNode, child);
        }
    }

    /**
     * Inserts all optimizations from the given optimization tree to the appropriate context in the
     * optimization-context tree. The appropriate context is stated by the optimization's
     * {@link InliningPath#ofEnclosingMethod(Optimization) enclosing methods}. The path to the
     * enclosing method is followed in the optimization-context tree (by only considering node
     * origination in inlining tree nodes), and the optimization is placed in the last node on the
     * path.
     *
     * @param optimizationTree the source optimization tree with optimizations
     */
    private void insertAllOptimizationNodes(OptimizationTree optimizationTree) {
        optimizationTree.getRoot().forEach(node -> {
            if (!(node instanceof Optimization)) {
                return;
            }
            Optimization optimization = (Optimization) node;
            InliningPath optimizationPath = InliningPath.ofEnclosingMethod(optimization);
            OptimizationContextTreeNode lastNode = findLastNodeAt(null, optimizationPath, 0);
            if (lastNode != null) {
                lastNode.addChild(new OptimizationContextTreeNode(optimization));
            }
        });
    }

    /**
     * Creates an optimization-context tree from an inlining and optimization tree. It is assumed
     * that the inlining tree is {@link #checkUnsuitability(InliningTree) suitable}
     *
     * @param inliningTree the inlining tree
     * @param optimizationTree the optimization tree
     * @return the created optimization-context tree
     */
    public static OptimizationContextTree createFrom(InliningTree inliningTree, OptimizationTree optimizationTree) {
        OptimizationContextTree optimizationContextTree = fromInliningTree(inliningTree);
        optimizationContextTree.insertAllOptimizationNodes(optimizationTree);
        optimizationContextTree.root.forEach(node -> node.getChildren().sort(OptimizationContextTreeNode::compareTo));
        return optimizationContextTree;
    }

    /**
     * Checks whether it is possible to turn the given inlining tree into an optimization-context
     * tree. In particular, the inlining tree must not be empty and
     * {@link InliningTree#allInliningPathsAreDistinct() all inlining paths must be distinct}. If
     * the tree is unsuitable, the method returns the reason.
     *
     * @param inliningTree the inlining tree to be tested
     * @return the reason for unsuitability or {@link Optional#empty()} if suitable
     */
    public static Optional<String> checkUnsuitability(InliningTree inliningTree) {
        if (inliningTree.getRoot() == null) {
            return Optional.of("Inlining tree is missing");
        }
        if (!inliningTree.allInliningPathsAreDistinct()) {
            return Optional.of("Optimization cannot be attributed because there is a duplicate path in the inlining tree");
        }
        return Optional.empty();
    }
}
