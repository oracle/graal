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
