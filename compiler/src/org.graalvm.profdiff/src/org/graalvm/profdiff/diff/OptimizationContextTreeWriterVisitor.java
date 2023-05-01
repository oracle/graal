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
package org.graalvm.profdiff.diff;

import org.graalvm.profdiff.core.OptimizationContextTreeNode;
import org.graalvm.profdiff.core.Writer;

/**
 * A pre-order writer for an {@link org.graalvm.profdiff.core.OptimizationContextTree} delegating to
 * {@link InliningDeltaTreeWriterVisitor}.
 */
public class OptimizationContextTreeWriterVisitor extends DeltaTreeWriterVisitor<OptimizationContextTreeNode> {

    private final InliningDeltaTreeWriterVisitor inliningDeltaTreeWriterVisitor;

    public OptimizationContextTreeWriterVisitor(Writer writer) {
        super(writer);
        inliningDeltaTreeWriterVisitor = new InliningDeltaTreeWriterVisitor(writer);
    }

    @Override
    public void beforeVisit() {
        super.beforeVisit();
        inliningDeltaTreeWriterVisitor.beforeVisit();
    }

    @Override
    public void afterVisit() {
        super.afterVisit();
        inliningDeltaTreeWriterVisitor.afterVisit();
    }

    @Override
    public void visitDeletion(DeltaTreeNode<OptimizationContextTreeNode> node) {
        if (node.getLeft().getOriginalInliningTreeNode() != null) {
            inliningDeltaTreeWriterVisitor.visitDeletion(new DeltaTreeNode<>(node.getDepth(), node.isIdentity(), node.getLeft().getOriginalInliningTreeNode(), null));
        } else {
            super.visitDeletion(node);
        }
    }

    @Override
    public void visitInsertion(DeltaTreeNode<OptimizationContextTreeNode> node) {
        if (node.getRight().getOriginalInliningTreeNode() != null) {
            inliningDeltaTreeWriterVisitor.visitInsertion(new DeltaTreeNode<>(node.getDepth(), node.isIdentity(), null, node.getRight().getOriginalInliningTreeNode()));
        } else {
            super.visitInsertion(node);
        }
    }

    @Override
    public void visitIdentity(DeltaTreeNode<OptimizationContextTreeNode> node) {
        if (node.getLeft().getOriginalInliningTreeNode() != null && node.getRight().getOriginalInliningTreeNode() != null) {
            inliningDeltaTreeWriterVisitor.visitIdentity(new DeltaTreeNode<>(node.getDepth(), node.isIdentity(),
                            node.getLeft().getOriginalInliningTreeNode(), node.getRight().getOriginalInliningTreeNode()));
        } else {
            super.visitIdentity(node);
        }
    }

    @Override
    public void visitRelabeling(DeltaTreeNode<OptimizationContextTreeNode> node) {
        if (node.getLeft().getOriginalInliningTreeNode() != null && node.getRight().getOriginalInliningTreeNode() != null) {
            inliningDeltaTreeWriterVisitor.visitRelabeling(new DeltaTreeNode<>(node.getDepth(), node.isIdentity(),
                            node.getLeft().getOriginalInliningTreeNode(), node.getRight().getOriginalInliningTreeNode()));
        } else {
            super.visitRelabeling(node);
        }
    }
}
