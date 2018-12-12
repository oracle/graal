package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.bytecode.OperandStack;

public abstract class InvokeNode extends Node {
    public abstract void invoke(OperandStack stack);
}
