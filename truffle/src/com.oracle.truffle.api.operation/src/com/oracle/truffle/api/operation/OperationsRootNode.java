package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class OperationsRootNode extends RootNode implements InstrumentableNode {

    @Child private OperationsNode node;

    OperationsRootNode(OperationsNode node) {
        super(node.language, node.createFrameDescriptor());
        this.node = node;
    }

    @Override
    public String getName() {
        return node.nodeName;
    }

    @Override
    public boolean isInternal() {
        return node.isInternal;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return node.execute(frame);
    }

    @Override
    public boolean isCaptureFramesForTrace() {
        return true;
    }

    @Override
    protected Object translateStackTraceElement(TruffleStackTraceElement element) {
        int bci = element.getFrame().getInt(OperationsNode.BCI_SLOT);
        return new OperationsStackTraceElement(element.getTarget().getRootNode(), node.getSourceSectionAtBci(bci));
    }

    @Override
    public boolean isInstrumentable() {
        return true;
    }

    private class OperationsWrapperNode extends Node implements WrapperNode {
        private final ProbeNode probe;

        OperationsWrapperNode(ProbeNode probe) {
            this.probe = probe;
        }

        public Node getDelegateNode() {
            return OperationsRootNode.this;
        }

        public ProbeNode getProbeNode() {
            return probe;
        }
    }

    public WrapperNode createWrapper(final ProbeNode probe) {
        return new OperationsWrapperNode(probe);
    }

    @Override
    public String toString() {
        return "root " + getName();
    }

}
