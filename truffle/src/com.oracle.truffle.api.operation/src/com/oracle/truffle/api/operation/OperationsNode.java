package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class OperationsNode extends Node {

    public abstract OperationPointer createPointer();

    public abstract Object execute(VirtualFrame frame);

    public abstract Object continueAt(VirtualFrame frame, OperationPointer index);

    public abstract OperationsNode copyUninitialized();

}
