package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.VectorValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(nameTemplate = "VectorWrite#{p#location/s}")
public class VectorWriteNode extends AbstractWriteNode implements LIRLowerableAccess {

    public static final NodeClass<VectorWriteNode> TYPE = NodeClass.create(VectorWriteNode.class);

    public VectorWriteNode(AddressNode address, LocationIdentity location, VectorValueNode value, BarrierType barrierType) {
        super(TYPE, address, location, value, barrierType);
    }

    protected VectorWriteNode(NodeClass<? extends AbstractWriteNode> c, AddressNode address, LocationIdentity location, VectorValueNode value, BarrierType barrierType) {
        super(c, address, location, value, barrierType);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // TODO: IMPLEMENT GENERATE
        throw GraalError.unimplemented("VWN unimplemented");
    }

    @Override
    public Stamp getAccessStamp() {
        return value().stamp();
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }
}
