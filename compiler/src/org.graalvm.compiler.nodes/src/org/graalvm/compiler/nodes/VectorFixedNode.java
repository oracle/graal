package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;

@NodeInfo
public abstract class VectorFixedNode extends VectorValueNode implements VectorFixedNodeInterface {
    public static final NodeClass<VectorFixedNode> TYPE = NodeClass.create(VectorFixedNode.class);

    protected VectorFixedNode(NodeClass<? extends VectorFixedNode> c, Stamp stamp) {
        super(c, stamp);
    }

    @Override
    public boolean verify() {
        assertTrue(this.successors().isNotEmpty() || this.predecessor() != null, "VectorFixedNode should not float");
        return super.verify();
    }

    @Override
    public VectorFixedNode asNode() {
        return this;
    }
}
