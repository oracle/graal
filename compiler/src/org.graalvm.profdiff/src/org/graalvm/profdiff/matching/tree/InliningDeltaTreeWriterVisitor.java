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
public class InliningDeltaTreeWriterVisitor extends DeltaTreeWriterVisitor<InliningTreeNode> {
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
    public static final String REASONING_IN_EXPERIMENT = "|_ reasoning in experiment ";

    public InliningDeltaTreeWriterVisitor(Writer writer) {
        super(writer);
    }

    @Override
    public void visitRelabeling(DeltaTreeNode<InliningTreeNode> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.RELABEL_PREFIX);
        if (node.getLeft().isPositive() != node.getRight().isPositive()) {
            writer.write("(");
            writer.write(node.getLeft().isPositive() ? INLINED : NOT_INLINED);
            writer.write(" -> ");
            writer.write(node.getRight().isPositive() ? INLINED : NOT_INLINED);
            writer.write(") ");
        } else if (!node.getLeft().isPositive()) {
            writer.write(InliningTreeNode.NOT_INLINED_PREFIX);
        }
        if (node.getLeft().getName() == null) {
            writer.write(InliningTreeNode.UNKNOWN_NAME);
        } else {
            writer.write(node.getLeft().getName());
        }
        writer.write(InliningTreeNode.AT_BCI);
        if (node.getLeft().getBCI() != node.getRight().getBCI()) {
            writer.write("(");
            writer.write(Integer.toString(node.getLeft().getBCI()));
            writer.write(" -> ");
            writer.write(Integer.toString(node.getRight().getBCI()));
            writer.writeln(") ");
        } else {
            writer.writeln(Integer.toString(node.getLeft().getBCI()));
        }
        if (node.getLeft().isPositive() != node.getRight().isPositive()) {
            writeReasoning(node.getLeft(), ExperimentId.ONE);
            writeReasoning(node.getRight(), ExperimentId.TWO);
        }
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
