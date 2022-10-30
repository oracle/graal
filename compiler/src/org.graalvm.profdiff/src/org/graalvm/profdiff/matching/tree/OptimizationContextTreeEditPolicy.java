package org.graalvm.profdiff.matching.tree;

import org.graalvm.profdiff.core.OptimizationContextTreeNode;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;

import java.util.Objects;

public class OptimizationContextTreeEditPolicy extends TreeEditPolicy<OptimizationContextTreeNode> {
    @Override
    public boolean nodesEqual(OptimizationContextTreeNode node1, OptimizationContextTreeNode node2) {
        if (node1.getOriginalOptimization() != null && node2.getOriginalOptimization() != null) {
            return node1.getOriginalOptimization().equals(node2.getOriginalOptimization());
        } else if (node1.getOriginalInliningTreeNode() != null && node2.getOriginalInliningTreeNode() != null) {
            InliningTreeNode inliningTreeNode1 = node1.getOriginalInliningTreeNode();
            InliningTreeNode inliningTreeNode2 = node2.getOriginalInliningTreeNode();
            return inliningTreeNode1.getBCI() == inliningTreeNode2.getBCI() && inliningTreeNode1.isPositive() == inliningTreeNode2.isPositive() &&
                            Objects.equals(inliningTreeNode1.getName(), inliningTreeNode2.getName());
        }
        return !node1.hasOriginalNode() && !node2.hasOriginalNode();
    }

    @Override
    public long relabelCost(OptimizationContextTreeNode node1, OptimizationContextTreeNode node2) {
        assert node1 != node2;
        if (node1.getOriginalInliningTreeNode() != null && node2.getOriginalInliningTreeNode() != null) {
            InliningTreeNode inliningTreeNode1 = node1.getOriginalInliningTreeNode();
            InliningTreeNode inliningTreeNode2 = node2.getOriginalInliningTreeNode();
            if (inliningTreeNode1.getBCI() == inliningTreeNode2.getBCI() && Objects.equals(inliningTreeNode1.getName(), inliningTreeNode2.getName())) {
                return UNIT_COST;
            }
        }
        return INFINITE_COST;
    }
}
