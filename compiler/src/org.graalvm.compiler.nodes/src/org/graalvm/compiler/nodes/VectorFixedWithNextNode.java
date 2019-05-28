package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;

@NodeInfo
public abstract class VectorFixedWithNextNode extends VectorFixedNode {

    public static final NodeClass<VectorFixedWithNextNode> TYPE = NodeClass.create(VectorFixedWithNextNode.class);

    @Successor protected VectorFixedNode next;
    @Successor protected FixedNode nextScalar;

    public VectorFixedNode next() {
        return next;
    }

    public void setNext(VectorFixedNode x) {
        updatePredecessor(next, x);
        next = x;
    }

    public FixedNode nextScalar() {
        return nextScalar;
    }

    public void setNextScalar(FixedNode x) {
        updatePredecessor(nextScalar, x);
        nextScalar = x;
    }

    public VectorFixedWithNextNode(NodeClass<? extends VectorFixedWithNextNode> c, Stamp stamp) {
        super(c, stamp);
    }

    @Override
    public VectorFixedWithNextNode asNode() {
        return this;
    }
}
