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

import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.util.Writer;

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
            root.writeRecursive(writer);
            writer.decreaseIndent();
        }
    }
}
