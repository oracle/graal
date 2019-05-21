package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;

/**
 * This class represents a vector value within the graph.
 */
@NodeInfo
public abstract class VectorValueNode extends ValueNode implements VectorValueNodeInterface {
    public VectorValueNode(NodeClass<? extends ValueNode> c, Stamp stamp) {
        super(c, stamp);
    }

    @Override
    public VectorValueNode asNode() {
        return this;
    }
}
