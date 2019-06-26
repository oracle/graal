package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild(value = "leftNode", type = LLVMExpressionNode.class)
@NodeChild(value = "rightNode", type = LLVMExpressionNode.class)
public abstract class DebugExprShortCircuitEvaluationNode extends LLVMExpressionNode {
    public abstract Object executeWithTarget(Object left, Object right);

    public abstract static class DebugExprLogicalAndNode extends DebugExprShortCircuitEvaluationNode {
        @Specialization
        protected boolean and(boolean left, boolean right) {
            return left && right;
        }
    }

    public abstract static class DebugExprLogicalOrNode extends DebugExprShortCircuitEvaluationNode {
        @Specialization
        protected boolean or(boolean left, boolean right) {
            return left || right;
        }
    }
}
