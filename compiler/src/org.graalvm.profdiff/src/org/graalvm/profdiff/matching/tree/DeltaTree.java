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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.profdiff.core.TreeNode;

/**
 * A tree representation of the result of a tree diff. This is a tree representation of the
 * {@link EditScript}, which is convertible to/from a delta tree.
 *
 * @param <T> the type of the node of the diffed tree
 */
public class DeltaTree<T extends TreeNode<T>> {
    /**
     * The root of the delta tree.
     */
    private DeltaTreeNode<T> root;

    public DeltaTree(DeltaTreeNode<T> root) {
        this.root = root;
    }

    /**
     * Gets the root node of this delta tree.
     */
    public DeltaTreeNode<T> getRoot() {
        return root;
    }

    /**
     * Performs an operation on each node of this tree. The tree is traversed depth-first and the
     * operation is performed when the node is first visited.
     *
     * @param consumer the operation to be performed on each node
     */
    public void forEach(Consumer<DeltaTreeNode<T>> consumer) {
        if (root != null) {
            root.forEach(consumer);
        }
    }

    /**
     * Performs operations on each node of the tree. The tree is traversed depth-first. An operation
     * is performed when the node is first visited and then another operation is performed after its
     * whole subtree has been visited.
     *
     * @param pre the operation that is performed on the first visit
     * @param post the operation that is performed after the subtree has been visited
     */
    public void forEach(Consumer<DeltaTreeNode<T>> pre, Consumer<DeltaTreeNode<T>> post) {
        if (root != null) {
            root.forEach(pre, post);
        }
    }

    /**
     * Traverses the tree depth-first and removes each subtree where the predicate returns
     * {@code true}.
     *
     * @param predicate remove the subtree when the predicate returns {@code true}
     */
    public void removeIf(Function<DeltaTreeNode<T>, Boolean> predicate) {
        if (root != null) {
            if (predicate.apply(root)) {
                root = null;
            } else {
                root.removeIf(predicate);
            }
        }
    }

    /**
     * Remove each node in the delta tree which does not contain a non-identity leaf node in its
     * subtree. The result of this operation is a delta tree with exactly all differences from the
     * original tree with their contexts (i.e. the ancestors in the original diffed trees).
     */
    public void pruneIdentities() {
        LinkedList<DeltaTreeNode<T>> stack = new LinkedList<>();
        EconomicSet<DeltaTreeNode<T>> keepAlive = EconomicSet.create(Equivalence.IDENTITY);
        forEach(deltaNode -> {
            if (!deltaNode.isIdentity()) {
                keepAlive.add(deltaNode);
            }
            stack.add(deltaNode);
        }, deltaNode -> {
            stack.removeLast();
            if (!stack.isEmpty() && keepAlive.contains(deltaNode)) {
                keepAlive.add(stack.getLast());
            }
        });
        removeIf(deltaNode -> !keepAlive.contains(deltaNode));
    }

    /**
     * Builds a delta tree corresponding to an edit script.
     *
     * @param editScript a source edit script
     * @return a delta tree corresponding to the edit script
     * @param <T> the type of the node in the diffed tree
     */
    public static <T extends TreeNode<T>> DeltaTree<T> fromEditScript(EditScript<T> editScript) {
        LinkedList<DeltaTreeNode<T>> stack = new LinkedList<>();
        for (EditScript.DeltaNode<T> deltaNode : editScript.getDeltaNodes()) {
            boolean isIdentity = deltaNode instanceof EditScript.Identity;
            if (stack.isEmpty()) {
                stack.add(new DeltaTreeNode<>(0, isIdentity, deltaNode.left, deltaNode.right));
                continue;
            }
            if (deltaNode.depth == stack.getLast().getDepth() + 1) {
                DeltaTreeNode<T> child = stack.getLast().addChild(isIdentity, deltaNode.left, deltaNode.right);
                stack.add(child);
            } else if (deltaNode.depth <= stack.getLast().getDepth()) {
                int toPop = stack.getLast().getDepth() - deltaNode.depth + 1;
                for (int i = 0; i < toPop; i++) {
                    stack.removeLast();
                }
                DeltaTreeNode<T> child = stack.getLast().addChild(isIdentity, deltaNode.left, deltaNode.right);
                stack.add(child);
            } else {
                throw new RuntimeException("The edit script is not in dfs preorder.");
            }
        }
        if (stack.isEmpty()) {
            return new DeltaTree<>(null);
        }
        return new DeltaTree<>(stack.getFirst());
    }

    /**
     * Builds and returns an edit script corresponding to this delta tree.
     *
     * @return an edit script corresponding to this delta tree
     */
    public EditScript<T> asEditScript() {
        EditScript<T> script = new EditScript<>();
        List<DeltaTreeNode<T>> deltaNodes = new ArrayList<>();
        forEach(deltaNodes::add);
        for (int i = deltaNodes.size() - 1; i >= 0; i--) {
            DeltaTreeNode<T> deltaNode = deltaNodes.get(i);
            if (deltaNode.isIdentity()) {
                script.identity(deltaNode.getLeft(), deltaNode.getRight(), deltaNode.getDepth());
            } else if (deltaNode.isDeletion()) {
                script.delete(deltaNode.getLeft(), deltaNode.getDepth());
            } else if (deltaNode.isInsertion()) {
                script.insert(deltaNode.getRight(), deltaNode.getDepth());
            } else if (deltaNode.isRelabeling()) {
                script.relabel(deltaNode.getLeft(), deltaNode.getRight(), deltaNode.getDepth());
            } else {
                throw new RuntimeException("Inconsistent state of a delta node.");
            }
        }
        return script;
    }

    /**
     * Visits this delta tree in dfs preorder using the provided visitor.
     *
     * @param visitor the visitor that will visit this delta tree
     */
    public void accept(DeltaTreeVisitor<T> visitor) {
        visitor.beforeVisit();
        forEach(node -> {
            if (node.isIdentity()) {
                visitor.visitIdentity(node);
            } else if (node.isDeletion()) {
                visitor.visitDeletion(node);
            } else if (node.isInsertion()) {
                visitor.visitInsertion(node);
            } else {
                assert node.isRelabeling();
                visitor.visitRelabeling(node);
            }
        });
        visitor.afterVisit();
    }
}
