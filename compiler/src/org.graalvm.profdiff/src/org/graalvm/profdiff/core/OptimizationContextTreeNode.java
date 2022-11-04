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

import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.util.Writer;

public class OptimizationContextTreeNode extends TreeNode<OptimizationContextTreeNode> implements Comparable<OptimizationContextTreeNode> {

    private final TreeNode<?> originalNode;

    public OptimizationContextTreeNode() {
        super("Optimization-context tree");
        originalNode = null;
    }

    public OptimizationContextTreeNode(Optimization optimization) {
        super(optimization.getName());
        originalNode = optimization;
    }

    public OptimizationContextTreeNode(InliningTreeNode inliningTreeNode) {
        super(inliningTreeNode.getName());
        originalNode = inliningTreeNode;
    }

    public InliningTreeNode getOriginalInliningTreeNode() {
        if (originalNode instanceof InliningTreeNode) {
            return (InliningTreeNode) originalNode;
        }
        return null;
    }

    public Optimization getOriginalOptimization() {
        if (originalNode instanceof Optimization) {
            return (Optimization) originalNode;
        }
        return null;
    }

    public boolean hasOriginalNode() {
        return originalNode != null;
    }

    @Override
    public void writeHead(Writer writer) {
        if (originalNode != null) {
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
}
