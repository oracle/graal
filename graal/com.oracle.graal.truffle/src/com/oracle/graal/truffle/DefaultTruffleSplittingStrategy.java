package com.oracle.graal.truffle;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeCountFilter;

public class DefaultTruffleSplittingStrategy implements TruffleSplittingStrategy {

    private final OptimizedDirectCallNode call;

    public DefaultTruffleSplittingStrategy(OptimizedDirectCallNode call) {
        this.call = call;
    }

    public void beforeCall(Object[] arguments) {
        if (call.getCallCount() == 2 && !call.isInlined()) {
            if (shouldSplit()) {
                forceSplitting();
            }
        }
    }

    public void forceSplitting() {
        if (call.isSplit()) {
            return;
        }
        call.installSplitCallTarget(call.getCallTarget().split());
    }

    public void afterCall(Object returnValue) {
    }

    private boolean shouldSplit() {
        if (call.getSplitCallTarget() != null) {
            return false;
        }
        if (!TruffleCompilerOptions.TruffleSplitting.getValue()) {
            return false;
        }
        if (!call.isSplittable()) {
            return false;
        }
        OptimizedCallTarget splitTarget = call.getCallTarget();
        int nodeCount = OptimizedCallUtils.countNonTrivialNodes(splitTarget, false);
        if (nodeCount > TruffleCompilerOptions.TruffleSplittingMaxCalleeSize.getValue()) {
            return false;
        }

        // disable recursive splitting for now
        OptimizedCallTarget root = (OptimizedCallTarget) call.getRootNode().getCallTarget();
        if (root == splitTarget || root.getSplitSource() == splitTarget) {
            // recursive call found
            return false;
        }

        // max one child call and callCount > 2 and kind of small number of nodes
        if (isMaxSingleCall(call)) {
            return true;
        }
        return countPolymorphic(call) >= 1;
    }

    private static boolean isMaxSingleCall(OptimizedDirectCallNode call) {
        return NodeUtil.countNodes(call.getCurrentCallTarget().getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node instanceof DirectCallNode;
            }
        }) <= 1;
    }

    private static int countPolymorphic(OptimizedDirectCallNode call) {
        return NodeUtil.countNodes(call.getCurrentCallTarget().getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                NodeCost cost = node.getCost();
                boolean polymorphic = cost == NodeCost.POLYMORPHIC || cost == NodeCost.MEGAMORPHIC;
                return polymorphic;
            }
        });
    }

}
