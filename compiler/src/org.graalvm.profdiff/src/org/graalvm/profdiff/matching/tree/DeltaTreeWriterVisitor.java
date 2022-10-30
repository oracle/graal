package org.graalvm.profdiff.matching.tree;

import org.graalvm.profdiff.core.TreeNode;
import org.graalvm.profdiff.util.Writer;

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
        this.baseIndentLevel = 0;
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
