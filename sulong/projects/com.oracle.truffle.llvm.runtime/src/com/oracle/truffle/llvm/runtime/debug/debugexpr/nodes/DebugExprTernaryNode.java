package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class DebugExprTernaryNode extends LLVMExpressionNode {

    @Child protected LLVMExpressionNode condition, thenNode, elseNode;

    public DebugExprTernaryNode(LLVMExpressionNode condition, LLVMExpressionNode thenNode, LLVMExpressionNode elseNode) {
        this.condition = condition;
        this.thenNode = thenNode;
        this.elseNode = elseNode;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        boolean cond;
        try {
            cond = condition.executeI1(frame);
        } catch (UnexpectedResultException e) {
            System.out.println("CATCH " + e.getMessage());
            return DebugExprVarNode.noObj;
        }
        if (cond)
            return thenNode.executeGeneric(frame);
        else
            return elseNode.executeGeneric(frame);

    }

}
