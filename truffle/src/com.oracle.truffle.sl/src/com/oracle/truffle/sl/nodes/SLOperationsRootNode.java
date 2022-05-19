package com.oracle.truffle.sl.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.operation.OperationNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.operations.SLOperations;

public class SLOperationsRootNode extends SLRootNode {

    @Child private OperationNode operationsNode;

    public SLOperationsRootNode(SLLanguage language, OperationNode operationsNode) {
        super(language, operationsNode.createFrameDescriptor());
        this.operationsNode = insert(operationsNode);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return operationsNode.execute(frame);
    }

    @Override
    public SourceSection getSourceSection() {
        return operationsNode.getSourceSection();
    }

    @Override
    public SLExpressionNode getBodyNode() {
        return null;
    }

    @Override
    public TruffleString getTSName() {
        return operationsNode.getMetadata(SLOperations.MethodName);
    }

}
