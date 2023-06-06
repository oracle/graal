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
package org.graalvm.profdiff.diff;

import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.Writer;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;

/**
 * Writes a representation of an inlining delta tree (the diff of 2 inlining trees) to a writer.
 */
public class InliningDeltaTreeWriterVisitor extends DeltaTreeWriterVisitor<InliningTreeNode> {
    public InliningDeltaTreeWriterVisitor(Writer writer) {
        super(writer);
    }

    @Override
    public void visitDeletion(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.DELETE_PREFIX);
        node.getLeft().writeHead(writer);
        node.getLeft().writeReasoningIfEnabled(writer, null);
        node.getLeft().writeReceiverTypeProfile(writer, null);
    }

    @Override
    public void visitInsertion(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.INSERT_PREFIX);
        node.getRight().writeHead(writer);
        node.getRight().writeReasoningIfEnabled(writer, null);
        node.getRight().writeReceiverTypeProfile(writer, null);
    }

    @Override
    public void visitIdentity(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.IDENTITY_PREFIX);
        node.getLeft().writeHead(writer);
        node.getLeft().writeReasoningIfEnabled(writer, ExperimentId.ONE);
        node.getRight().writeReasoningIfEnabled(writer, ExperimentId.TWO);
        node.getLeft().writeReceiverTypeProfile(writer, ExperimentId.ONE);
        node.getRight().writeReceiverTypeProfile(writer, ExperimentId.TWO);
    }

    @Override
    public void visitRelabeling(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.RELABEL_PREFIX);
        writer.write(InliningTreeNode.CallsiteKind.change(node.getLeft().getCallsiteKind(), node.getRight().getCallsiteKind()));
        if (node.getLeft().getName() == null) {
            writer.write(InliningTreeNode.UNKNOWN_NAME);
        } else {
            writer.write(node.getLeft().getName());
        }
        writer.write(InliningTreeNode.AT_BCI);
        writer.writeln(Integer.toString(node.getLeft().getBCI()));
        if (writer.getOptionValues().shouldAlwaysPrintInlinerReasoning() ||
                        node.getLeft().isPositive() != node.getRight().isPositive()) {
            node.getLeft().writeReasoning(writer, ExperimentId.ONE);
            node.getRight().writeReasoning(writer, ExperimentId.TWO);
        }
        node.getLeft().writeReceiverTypeProfile(writer, ExperimentId.ONE);
        node.getRight().writeReceiverTypeProfile(writer, ExperimentId.TWO);
    }
}
