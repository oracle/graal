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
package org.graalvm.profdiff.core.inlining;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.util.Writer;
import org.graalvm.util.CollectionsUtil;

/**
 * The inlining tree of a compilation unit. Each {@link InliningTreeNode node} in the tree
 * represents a method. The root of the inlining tree represents the compiled root method. Each node
 * was either inlined to its parent method or not inlined.
 *
 * Consider the following set of methods:
 *
 * @formatter:off
 * <pre>
 *     void a() { b(); c(); }
 *     void b() { }
 *     void c() { d(); e(); }
 *     void d() { }
 *     void e() { }
 * </pre>
 *
 * After inlining everything in {@code a()}, we obtain the inlining tree below:
 *
 * <pre>
 *          a()
 *      ___/  \_____
 *     /            \
 *    b()           c()
 * at bci 0       at bci 1
 *               __/  \__
 *             /         \
 *           d()         e()
 *        at bci 0     at bci 1
 * </pre>
 *
 * In preorder, the tree corresponds to the following:
 *
 * <pre>
 *     a() at bci -1
 *         b() at bci 0
 *         c() at bci 1
 *             d() at bci 0
 *             e() at bci 1
 * </pre>
 * @formatter:on
 */
public class InliningTree {
    /**
     * The root of the inlining tree if it is available. The root method in the inlining tree
     * corresponds to this method.
     */
    private final InliningTreeNode root;

    public InliningTree(InliningTreeNode root) {
        this.root = root;
    }

    /**
     * Gets the root of the inlining tree if it is available. The root method in the inlining tree
     * corresponds to the compiled root method.
     *
     * @return the root of the inlining tree if it is available
     */
    public InliningTreeNode getRoot() {
        return root;
    }

    /**
     * Sorts all children of each node in the inlining tree according to the
     * {@link InliningTreeNode#compareTo(InliningTreeNode) comparator}.
     */
    public void sortInliningTree() {
        if (root == null) {
            return;
        }
        root.forEach(node -> node.getChildren().sort(InliningTreeNode::compareTo));
    }

    /**
     * Preprocesses the inlining tree according to the provided verbosity level.
     *
     * @param verbosityLevel the verbosity level
     */
    public void preprocess(VerbosityLevel verbosityLevel) {
        if (verbosityLevel.shouldSortInliningTree()) {
            sortInliningTree();
        }
    }

    /**
     * Writes the inlining tree with a header in preorder using the destination writer.
     *
     * @param writer the destination writer
     */
    public void write(Writer writer) {
        if (root == null) {
            writer.writeln("Inlining tree not available");
        } else {
            writer.writeln("Inlining tree");
            writer.increaseIndent();
            root.forEach(node -> {
                node.writeHead(writer);
                node.writeReceiverTypeProfile(writer, null);
                writer.increaseIndent();
            }, node -> writer.decreaseIndent());
            writer.decreaseIndent();
        }
    }

    /**
     * Returns {@code true} iff all paths in the inlining tree are distinct. Formally, the method
     * returns {@code true} iff there do not exist two different nodes A and B, where
     * {@link InliningPath#fromRootToNode(InliningTreeNode) the path from root} to A
     * {@link InliningPath#matches(InliningPath) matches} the path from root to B.
     *
     * In other words, we are asking whether we can unambiguously navigate to each node in the tree
     * using {@link InliningPath inlining paths}. This is necessary to properly construct an
     * {@link org.graalvm.profdiff.core.OptimizationContextTree optimization-context tree}.
     *
     * @return {@code true} iff all paths in the inlining tree are distinct
     */
    public boolean allInliningPathsAreDistinct() {
        if (root == null) {
            return true;
        }
        List<InliningTreeNode> nodes = new ArrayList<>();
        root.forEach(nodes::add);
        List<InliningPath> paths = new ArrayList<>();
        for (InliningTreeNode node : nodes) {
            InliningPath path = InliningPath.fromRootToNode(node);
            if (CollectionsUtil.anyMatch(paths, otherPath -> otherPath.matches(path))) {
                return false;
            }
            paths.add(path);
        }
        return true;
    }

    /**
     * Recursively finds a node on a path. Returns {@code null} if there is no such node. Tree nodes
     * corresponding to abstract methods ({@link InliningTreeNode#isAbstract()}) are skipped as if
     * they were removed from the tree and their children reattached to the closest ancestor.
     *
     * If several nodes match the path, then the first match is returned. Note that backtracking is
     * necessary, because a path element may match 2 different edges, and one of the edges may lead
     * to a dead-end.
     *
     * @param node the last node on the path (initially {@code null})
     * @param path the path from root
     * @param pathIndex the index to the next path element in the path (initially 0)
     * @return the node found on the path or {@code null}
     */
    private InliningTreeNode findNodeAt(InliningTreeNode node, InliningPath path, int pathIndex) {
        if (pathIndex >= path.size()) {
            return node;
        }
        InliningPath.PathElement element = path.get(pathIndex);
        List<InliningTreeNode> children = node == null ? List.of(root) : node.getChildren();
        for (InliningTreeNode child : children) {
            InliningTreeNode result = null;
            if (child.isAbstract()) {
                result = findNodeAt(child, path, pathIndex);
            } else if (element.matches(child.pathElement())) {
                result = findNodeAt(child, path, pathIndex + 1);
            }
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Clones a subtree given by path. The cloned subtree is rooted in the node given by path. The
     * bci of the cloned root is set to {@link Optimization#UNKNOWN_BCI} and the inlining reason is
     * reset. If there is no such node on the given path, an inlining tree with {@code null} root is
     * returned.
     *
     * @param path the path to the root of the cloned subtree
     * @return a cloned subtree
     */
    public InliningTree cloneSubtreeAt(InliningPath path) {
        InliningTreeNode rootNode = findNodeAt(null, path, 0);
        if (rootNode == null) {
            return new InliningTree(null);
        }
        InliningTreeNode clonedNode = new InliningTreeNode(rootNode.getName(), Optimization.UNKNOWN_BCI, rootNode.isPositive(), null, rootNode.isAbstract(), rootNode.getReceiverTypeProfile());
        cloneSubtreeInto(rootNode, clonedNode);
        return new InliningTree(clonedNode);
    }

    /**
     * Recursively clones the children of the original node the cloned node.
     *
     * @param originalNode the original node
     * @param clonedNode the cloned node (initially without children)
     */
    private static void cloneSubtreeInto(InliningTreeNode originalNode, InliningTreeNode clonedNode) {
        for (InliningTreeNode originalChild : originalNode.getChildren()) {
            InliningTreeNode clonedChild = new InliningTreeNode(originalChild.getName(), originalChild.getBCI(), originalChild.isPositive(), originalChild.getReason(),
                            originalChild.isAbstract(), originalChild.getReceiverTypeProfile());
            cloneSubtreeInto(originalChild, clonedChild);
            clonedNode.addChild(clonedChild);
        }
    }
}
