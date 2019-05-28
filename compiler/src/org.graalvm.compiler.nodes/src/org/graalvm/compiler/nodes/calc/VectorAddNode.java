package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.VectorValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;

import java.util.List;

@NodeInfo(shortName = "[+]")
public class VectorAddNode extends VectorValueNode {

    public static final NodeClass<VectorAddNode> TYPE = NodeClass.create(VectorAddNode.class);

    @Input protected VectorValueNode x;
    @Input protected VectorValueNode y;

    // TODO: Improve -- make generic like Arithemetic nodes for scalar values, or make two compatible

    public VectorAddNode(VectorValueNode x, VectorValueNode y) {
        this(TYPE, x.stamp(), x, y);
    }

    protected VectorAddNode(NodeClass<? extends VectorAddNode> c, Stamp stamp, VectorValueNode x, VectorValueNode y) {
        super(c, stamp);
        this.x = x;
        this.y = y;
    }

}
