package com.oracle.truffle.sl.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.operation.OperationsNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;

public class SLOperationsRootNode extends SLRootNode {

    @Child private OperationsNode operationsNode;

    public SLOperationsRootNode(SLLanguage language, OperationsNode operationsNode, TruffleString name) {
        super(language, operationsNode.createFrameDescriptor(), null, null, name);
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

}
