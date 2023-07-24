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

import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.core.Writer;

/**
 * Formats a delta tree in pre-order using a destination writer. Each delta node is indented
 * depending on its depth in the tree.
 *
 * @param <T> the type of the original node
 */
public class DeltaTreeWriterVisitor<T extends TreeNode<T>> implements DeltaTreeVisitor<T> {
    /**
     * The destination writer.
     */
    protected final Writer writer;

    /**
     * The base indentation level of the destination writer (before visit).
     */
    private int baseIndentLevel;

    public DeltaTreeWriterVisitor(Writer writer) {
        this.writer = writer;
        baseIndentLevel = 0;
    }

    protected void adjustIndentLevel(DeltaTreeNode<T> node) {
        writer.setIndentLevel(node.getDepth() + baseIndentLevel);
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
    public void visitEmptyTree() {
        writer.writeln("There are no differences");
    }

    @Override
    public void visitIdentity(DeltaTreeNode<T> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.IDENTITY_PREFIX);
        node.getLeft().writeHead(writer);
    }

    @Override
    public void visitRelabeling(DeltaTreeNode<T> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.RELABEL_PREFIX);
        writer.write(node.getLeft().getNameOrNull());
        writer.write(" -> ");
        writer.writeln(node.getRight().getNameOrNull());
    }

    @Override
    public void visitDeletion(DeltaTreeNode<T> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.DELETE_PREFIX);
        node.getLeft().writeHead(writer);
    }

    @Override
    public void visitInsertion(DeltaTreeNode<T> node) {
        adjustIndentLevel(node);
        writer.write(EditScript.INSERT_PREFIX);
        node.getRight().writeHead(writer);
    }
}
