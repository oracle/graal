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
package org.graalvm.profdiff.core;

import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;

import java.util.Objects;

/**
 * A node of an optimization-context tree. A node might be an info node or backed either by an
 * {@link InliningTreeNode} or an {@link Optimization}.
 *
 * An info node uses its {@link TreeNode#getName() name} to convey some information, e.g. a warning.
 * The root is always an info node.
 *
 * It is ordered by {@link #compareTo(OptimizationContextTreeNode)} in a way such that info nodes
 * come before nodes backed by an optimization, and nodes backend by an optimization come before
 * nodes backed by an inlining-tree node. This is appropriate for writing in pre-order, because info
 * nodes and nodes backed by an optimization do not have children.
 */
public class OptimizationContextTreeNode extends TreeNode<OptimizationContextTreeNode> implements Comparable<OptimizationContextTreeNode> {
    /**
     * {@code null} for an info node (including root), or the original {@link InliningTreeNode}, or
     * an {@link Optimization} which backs this node.
     */
    private final TreeNode<?> originalNode;

    /**
     * Creates the root node.
     */
    public OptimizationContextTreeNode() {
        super("Optimization-context tree");
        originalNode = null;
    }

    /**
     * Creates an info node, which conveys some information in the tree. The
     * {@link TreeNode#getName() name} of the node is set to the given info message.
     *
     * @param infoMessage the info message used in place of the info node's name
     */
    public OptimizationContextTreeNode(String infoMessage) {
        super(infoMessage);
        originalNode = null;
    }

    /**
     * Creates an info node warning that there exists another branch with the same
     * {@link org.graalvm.profdiff.core.inlining.InliningPath} in this tree, therefore optimizations
     * cannot be unambiguously attributed.
     *
     * @return the warning node
     */
    public static OptimizationContextTreeNode duplicatePathWarning() {
        return new OptimizationContextTreeNode("Warning: Optimizations cannot be unambiguously attributed (duplicate path)");
    }

    /**
     * Creates a node backend by an optimization.
     *
     * @param optimization the optimization which backs this node
     */
    public OptimizationContextTreeNode(Optimization optimization) {
        super(optimization.getName());
        originalNode = optimization;
    }

    /**
     * Creates a node backed by an inlining-tree node.
     *
     * @param inliningTreeNode the inlining-tree node which backs this node
     */
    public OptimizationContextTreeNode(InliningTreeNode inliningTreeNode) {
        super(inliningTreeNode.getName());
        originalNode = inliningTreeNode;
    }

    /**
     * Gets the inlining-tree node which backs this node or {@code null}.
     */
    public InliningTreeNode getOriginalInliningTreeNode() {
        if (originalNode instanceof InliningTreeNode) {
            return (InliningTreeNode) originalNode;
        }
        return null;
    }

    /**
     * Gets the optimization which backs this node or {@code null}.
     */
    public Optimization getOriginalOptimization() {
        if (originalNode instanceof Optimization) {
            return (Optimization) originalNode;
        }
        return null;
    }

    /**
     * Returns the caller of this node or {@code null}. If this node has a caller, it is the parent
     * node.
     *
     * @return the caller of this node or {@code null}
     */
    private InliningTreeNode getCallerNode() {
        if (getParent() == null) {
            return null;
        }
        return getParent().getOriginalInliningTreeNode();
    }

    /**
     * Returns {@code true} iff the node is an info node, i.e., it is not backed by an original
     * node, but it only stores some information in its {@link TreeNode#getName()}.
     *
     * @return {@code true} iff the node is an info node
     */
    @Override
    public boolean isInfoNode() {
        return originalNode == null;
    }

    @Override
    public void writeHead(Writer writer) {
        if (originalNode instanceof Optimization) {
            ((Optimization) originalNode).writeHead(writer, getCallerNode());
        } else if (originalNode != null) {
            originalNode.writeHead(writer);
        } else {
            super.writeHead(writer);
        }
    }

    @Override
    public int compareTo(OptimizationContextTreeNode otherNode) {
        if (originalNode instanceof Optimization) {
            if (otherNode.originalNode instanceof Optimization) {
                return ((Optimization) originalNode).compareTo((Optimization) otherNode.originalNode);
            } else if (otherNode.originalNode instanceof InliningTreeNode) {
                return -1;
            } else {
                return 1;
            }
        } else if (originalNode instanceof InliningTreeNode) {
            if (otherNode.originalNode instanceof Optimization) {
                return 1;
            } else if (otherNode.originalNode instanceof InliningTreeNode) {
                return ((InliningTreeNode) originalNode).compareTo((InliningTreeNode) otherNode.originalNode);
            } else {
                return 1;
            }
        } else {
            return otherNode.originalNode == null ? 0 : -1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OptimizationContextTreeNode)) {
            return false;
        }
        OptimizationContextTreeNode that = (OptimizationContextTreeNode) o;
        return Objects.equals(getName(), that.getName()) && Objects.equals(originalNode, that.originalNode) &&
                        getChildren().equals(that.getChildren());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(), originalNode, getChildren());
    }
}
