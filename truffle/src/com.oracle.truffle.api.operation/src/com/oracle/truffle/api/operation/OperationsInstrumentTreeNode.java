package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;

public class OperationsInstrumentTreeNode extends Node implements InstrumentableNode {

    private static class Wrapper extends OperationsInstrumentTreeNode implements WrapperNode {
        private final Node delegateNode;
        private final ProbeNode probeNode;

        public Wrapper(OperationsInstrumentTreeNode delegateNode, ProbeNode probeNode) {
            super(delegateNode.tag);
            this.delegateNode = delegateNode;
            this.probeNode = probeNode;
        }

        public Node getDelegateNode() {
            return delegateNode;
        }

        public ProbeNode getProbeNode() {
            return probeNode;
        }

        @Override
        public ProbeNode getTreeProbeNode() {
            return probeNode;
        }
    }

    private final Class<? extends Tag> tag;

    public OperationsInstrumentTreeNode(Class<? extends Tag> tag) {
        this.tag = tag;
    }

    public boolean isInstrumentable() {
        return true;
    }

    public WrapperNode createWrapper(ProbeNode probe) {
        return new Wrapper(this, probe);
    }

    public ProbeNode getTreeProbeNode() {
        return null;
    }

    public boolean hasTag(Class<? extends Tag> other) {
        return tag == other;
    }

}
