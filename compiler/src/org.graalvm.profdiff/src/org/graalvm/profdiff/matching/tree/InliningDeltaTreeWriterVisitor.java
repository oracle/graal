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

import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.util.Writer;

/**
 * Writes a representation of an inlining delta tree (the diff of 2 inlining trees) to a writer.
 */
public class InliningDeltaTreeWriterVisitor implements DeltaTreeVisitor<InliningTreeNode> {
    /**
     * The prefix of an inlined method.
     */
    public static final String INLINED = "inlined";

    /**
     * The prefix of a not inlined method.
     */
    public static final String NOT_INLINED = "not inlined";

    /**
     * A phrase introducing the reasons for an inlining decision in an experiment.
     */
    public static final String REASONING_IN_EXPERIMENT = "  |_ reasoning in experiment ";

    /**
     * The destination writer.
     */
    private final Writer writer;

    /**
     * The base indentation level of the destination writer (before visit).
     */
    private int baseIndentLevel;

    public InliningDeltaTreeWriterVisitor(Writer writer) {
        this.writer = writer;
        this.baseIndentLevel = 0;
    }

    @Override
    public void beforeVisit() {
        baseIndentLevel = writer.getIndentLevel();
    }

    @Override
    public void afterVisit() {
        writer.setIndentLevel(baseIndentLevel);
    }

    @Override
    public void visitIdentity(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.IDENTITY_PREFIX);
        node.getLeft().writeHead(writer);
    }

    @Override
    public void visitRelabeling(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.RELABEL_PREFIX);
        writer.write("(");
        writer.write(node.getLeft().isPositive() ? INLINED : NOT_INLINED);
        writer.write(" -> ");
        writer.write(node.getRight().isPositive() ? INLINED : NOT_INLINED);
        writer.write(") ");
        writelnNameBCI(node.getLeft());
        writeReasoning(node.getLeft(), ExperimentId.ONE);
        writeReasoning(node.getRight(), ExperimentId.TWO);
    }

    @Override
    public void visitDeletion(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.setPrefixAfterIndent(EditScript.DELETE_PREFIX);
        node.getLeft().writeRecursive(writer);
        writer.clearPrefixAfterIndent();
    }

    @Override
    public void visitInsertion(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.setPrefixAfterIndent(EditScript.INSERT_PREFIX);
        node.getRight().writeRecursive(writer);
        writer.clearPrefixAfterIndent();
    }

    private void adjustIndentLevel(DeltaTreeNode<InliningTreeNode> node) {
        writer.setIndentLevel(node.getDepth() + baseIndentLevel);
    }

    private void writelnNameBCI(InliningTreeNode node) {
        if (node.getName() == null) {
            writer.write(InliningTreeNode.UNKNOWN_NAME);
        } else {
            writer.write(node.getName());
        }
        writer.writeln(InliningTreeNode.AT_BCI + node.getBCI());
    }

    private void writeReasoning(InliningTreeNode node, ExperimentId experimentId) {
        writer.increaseIndent();
        writer.writeln(REASONING_IN_EXPERIMENT + experimentId);
        writer.increaseIndent(2);
        for (String reason : node.getReason()) {
            writer.writeln(reason);
        }
        writer.decreaseIndent(3);
    }
}
