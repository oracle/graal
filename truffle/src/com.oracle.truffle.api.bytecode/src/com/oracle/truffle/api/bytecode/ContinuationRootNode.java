package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.frame.Frame;

public interface ContinuationRootNode {
    OperationRootNode getSourceRootNode();

    Object[] getLocals(Frame frame);
}
