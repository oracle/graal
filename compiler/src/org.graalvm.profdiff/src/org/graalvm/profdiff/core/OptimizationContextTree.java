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

import org.graalvm.profdiff.core.inlining.InliningPath;
import org.graalvm.profdiff.core.inlining.InliningTree;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.core.optimization.OptimizationTree;

public class OptimizationContextTree {

    public OptimizationContextTreeNode getRoot() {
        return root;
    }

    private final OptimizationContextTreeNode root;

    private OptimizationContextTree(OptimizationContextTreeNode root) {
        this.root = root;
    }

    private OptimizationContextTreeNode getLastInliningTreeNodeOnPath(InliningPath path) {
        OptimizationContextTreeNode node = root;
        nextPathNode: for (InliningPath.PathElement element : path.elements()) {
            for (OptimizationContextTreeNode child : node.getChildren()) {
                if (child.getOriginalInliningTreeNode() == null) {
                    continue;
                }
                InliningTreeNode inliningTreeNode = child.getOriginalInliningTreeNode();
                if (inliningTreeNode.pathElement().matches(element)) {
                    node = child;
                    continue nextPathNode;
                }
            }
            break;
        }
        return node;
    }

    private static OptimizationContextTree fromInliningTree(InliningTree inliningTree) {
        OptimizationContextTreeNode root = new OptimizationContextTreeNode();
        fromInliningNode(root, inliningTree.getRoot());
        return new OptimizationContextTree(root);
    }

    private static void fromInliningNode(OptimizationContextTreeNode parent, InliningTreeNode inliningTreeNode) {
        OptimizationContextTreeNode optimizationContextTreeNode = new OptimizationContextTreeNode(inliningTreeNode);
        parent.addChild(optimizationContextTreeNode);
        for (InliningTreeNode child : inliningTreeNode.getChildren()) {
            fromInliningNode(optimizationContextTreeNode, child);
        }
    }

    private void insertAllOptimizationNodes(OptimizationTree optimizationTree) {
        optimizationTree.getRoot().forEach(node -> {
            if (!(node instanceof Optimization)) {
                return;
            }
            Optimization optimization = (Optimization) node;
            InliningPath optimizationPath = InliningPath.ofEnclosingMethod(optimization);
            getLastInliningTreeNodeOnPath(optimizationPath).addChild(new OptimizationContextTreeNode(optimization));
        });
    }

    public static OptimizationContextTree createFrom(InliningTree inliningTree, OptimizationTree optimizationTree) {
        OptimizationContextTree optimizationContextTree = fromInliningTree(inliningTree);
        optimizationContextTree.insertAllOptimizationNodes(optimizationTree);
        optimizationContextTree.root.forEach(node -> node.getChildren().sort(OptimizationContextTreeNode::compareTo));
        return optimizationContextTree;
    }
}
