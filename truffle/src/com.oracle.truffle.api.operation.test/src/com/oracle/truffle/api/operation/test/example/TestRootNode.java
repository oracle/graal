package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.OperationNode;

public class TestRootNode extends RootNode {

    @Child private OperationNode node;

    TestRootNode(OperationNode node) {
        super(null, node.createFrameDescriptor());
        this.node = node;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return node.execute(frame);
    }

}
