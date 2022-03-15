package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.tracing.NodeTrace;

public abstract class OperationsNode extends RootNode {

    private static FrameDescriptor createFrameDescriptor(int maxStack, int maxLocals) {
        FrameDescriptor.Builder b = FrameDescriptor.newBuilder(maxStack + maxLocals);
        b.addSlots(maxLocals + maxStack, FrameSlotKind.Object);
        return b.build();
    }

    protected final int maxStack;
    protected final int maxLocals;

    protected OperationsNode(int maxStack, int maxLocals) {
        super(null, createFrameDescriptor(maxStack, maxLocals));
        System.out.printf(" new operations node %d %d\n", maxStack, maxLocals);
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
    }

    public abstract Object continueAt(VirtualFrame frame, OperationLabel index);

// public abstract OperationsNode copyUninitialized();

    public abstract String dump();

    public NodeTrace getNodeTrace() {
        throw new UnsupportedOperationException("Operations not built with tracing");
    }
}
