package com.oracle.truffle.api.operation;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.Node;

public class OperationsInstrumentTree extends Node implements InstrumentableNode {

    private static class Wrapper extends OperationsInstrumentTree implements WrapperNode {
        private final Node delegateNode;
        private final ProbeNode probeNode;

        public Wrapper(Node delegateNode, ProbeNode probeNode) {
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

    public boolean isInstrumentable() {
        return true;
    }

    public WrapperNode createWrapper(ProbeNode probe) {
        return new Wrapper(this, probe);
    }

    public ProbeNode getTreeProbeNode() {
        return null;
    }

}
