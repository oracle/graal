package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.VectorFixedAccessNode;
import org.graalvm.compiler.nodes.VectorValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import java.util.List;

@NodeInfo(nameTemplate = "VectorWrite#{p#locations/s}", allowedUsageTypes = {InputType.Memory, InputType.Guard})
public class VectorWriteNode extends VectorFixedAccessNode implements LIRLowerable, MemoryAccess {

    public static final NodeClass<VectorWriteNode> TYPE = NodeClass.create(VectorWriteNode.class);

    @Input VectorValueNode value;
    @OptionalInput(InputType.Memory) Node lastLocationAccess;

    public VectorValueNode value() {
        return value;
    }

    public VectorWriteNode(AddressNode address, LocationIdentity[] locations, VectorValueNode value, BarrierType barrierType) {
        this(TYPE, address, locations, value, barrierType);
    }

    protected VectorWriteNode(NodeClass<? extends VectorWriteNode> c, AddressNode address, LocationIdentity[] locations, VectorValueNode value, BarrierType barrierType) {
        super(c, address, locations, StampFactory.forVoid(), barrierType);
        this.value = value;
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return type == InputType.Guard && getNullCheck() || super.isAllowedUsageType(type);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        throw GraalError.shouldNotReachHere("VectorWriteNode does not have single LocationIdentity");
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return (MemoryNode) lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        Node newLla = ValueNodeUtil.asNode(lla);
        updateUsages(lastLocationAccess, newLla);
        lastLocationAccess = newLla;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // TODO: Implement
        throw GraalError.unimplemented();
    }

    @Override
    public boolean canNullCheck() {
        return false;
    }

    public static VectorWriteNode fromPackElements(List<WriteNode> nodes, VectorValueNode value) {
        assert nodes.size() != 0 : "pack empty";
        // Pre: nodes all have the same guard.
        // Pre: nodes are contiguous
        // Pre: nodes are from the same memory region
        // ???

        final WriteNode anchor = nodes.get(0);
        final AddressNode address = anchor.getAddress();
        final LocationIdentity[] locations = nodes.stream().map(WriteNode::getLocationIdentity).toArray(LocationIdentity[]::new);

        return new VectorWriteNode(TYPE, address, locations, value, anchor.getBarrierType());
    }
}
