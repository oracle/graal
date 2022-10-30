package org.graalvm.profdiff.core;

import org.graalvm.profdiff.core.inlining.InliningTreeNode;
import org.graalvm.profdiff.core.optimization.Optimization;
import org.graalvm.profdiff.util.Writer;

public class OptimizationContextTreeNode extends TreeNode<OptimizationContextTreeNode> implements Comparable<OptimizationContextTreeNode> {

    private final TreeNode<?> originalNode;

    public OptimizationContextTreeNode() {
        super("Optimization context tree root");
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
