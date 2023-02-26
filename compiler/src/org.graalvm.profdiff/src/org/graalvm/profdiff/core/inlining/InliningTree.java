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
package org.graalvm.profdiff.core.inlining;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.profdiff.core.OptionValues;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.optimization.Optimization;

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
 * @formatter:on
 *
 * After inlining everything in {@code a()}, we obtain the inlining tree below:
 *
 * <pre>
 *             (root) a()
 *              at bci -1
 *          ______/  \_____
 *        /                \
 * (inlined) b()       (inlined) c()
 *   at bci 0            at bci 1
 *                   ______/  \______
 *                 /                 \
 *          (inlined) d()        (inlined) e()
 *             at bci 0            at bci 1
 * </pre>
 *
 * In preorder, the tree corresponds to the following:
 *
 * <pre>
 * Inlining tree
 *     (root) a() at bci -1
 *         (inlined) b() at bci 0
 *         (inlined) c() at bci 1
 *             (inlined) d() at bci 0
 *             (inlined) e() at bci 1
 * </pre>
 *
 * Note that the root uses bci -1 as a placeholder, because it represents the root method rather
 * than a callsite.
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
     * Preprocesses the inlining tree according to the provided option values.
     *
     * @param optionValues the option values
     */
    public void preprocess(OptionValues optionValues) {
        if (optionValues.shouldSortInliningTree()) {
            sortInliningTree();
        }
    }

    /**
     * Writes the inlining tree with a header in preorder using the destination writer.
     *
     * @param writer the destination writer
     */
    public void write(Writer writer) {
        writer.writeln("Inlining tree");
        if (root != null) {
            writer.increaseIndent();
            root.forEach(node -> {
                node.writeHead(writer);
                node.writeReasoningIfEnabled(writer, null);
                node.writeReceiverTypeProfile(writer, null);
                writer.increaseIndent();
            }, node -> writer.decreaseIndent());
            writer.decreaseIndent();
        }
    }

    /**
     * Finds all nodes that match the given path.
     *
     * Consider the following inlining tree:
     *
     * <pre>
     * Inlining tree
     *     a() at bci -1
     *         b() at bci 1
     *             d() at bci 3
     *         b() at bci 1
     *             d() at bci 3
     *         c() at bci 2
     * </pre>
     *
     * There are two nodes matching the path {@code a() at bci -1, b() at bci 1, d() at bci 3}.
     * There is only one node matching the path {@code a() at bci -1, c() at bci 2}.
     *
     * Tree nodes corresponding to indirect calls ({@link InliningTreeNode#isIndirect()}) are
     * skipped as if they were removed from the tree and their children reattached to the closest
     * ancestor.
     *
     * @param path the path from root
     * @return the list of nodes matching the given path
     */
    public List<InliningTreeNode> findNodesAt(InliningPath path) {
        List<InliningTreeNode> found = new ArrayList<>();
        findNodesAt(null, path, 0, found);
        return found;
    }

    /**
     * Recursively finds all nodes that match the given path. The nodes are stored in the
     * {@code found} list.
     *
     * @param node the last node on the path (initially {@code null})
     * @param path the path from root
     * @param pathIndex the index to the next path element in the path (initially 0)
     * @param found the list of nodes found on the path (initially empty)
     *
     * @see #findNodesAt(InliningPath)
     */
    private void findNodesAt(InliningTreeNode node, InliningPath path, int pathIndex, List<InliningTreeNode> found) {
        if (pathIndex >= path.size()) {
            if (node != null) {
                found.add(node);
            }
            return;
        }
        InliningPath.PathElement element = path.get(pathIndex);
        List<InliningTreeNode> children = node == null ? List.of(root) : node.getChildren();
        for (InliningTreeNode child : children) {
            if (child.isIndirect()) {
                findNodesAt(child, path, pathIndex, found);
            } else if (element.matches(child.pathElement())) {
                findNodesAt(child, path, pathIndex + 1, found);
            }
        }
    }

    /**
     * Clones a subtree given by an index. The cloned subtree is rooted in the node given at the
     * provided index. The bci of the cloned root is set to {@link Optimization#UNKNOWN_BCI} and the
     * inlining reason is reset.
     *
     * @param index the index of the root of the cloned subtree
     * @return a cloned subtree
     */
    public InliningTree cloneSubtreeAt(List<Integer> index) {
        InliningTreeNode rootNode = root.atIndex(index);
        InliningTreeNode clonedNode = new InliningTreeNode(rootNode.getName(), Optimization.UNKNOWN_BCI, rootNode.isPositive(), null, rootNode.isIndirect(),
                        rootNode.getReceiverTypeProfile(), rootNode.isAlive());
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
                            originalChild.isIndirect(), originalChild.getReceiverTypeProfile(), originalChild.isAlive());
            cloneSubtreeInto(originalChild, clonedChild);
            clonedNode.addChild(clonedChild);
        }
    }
}
