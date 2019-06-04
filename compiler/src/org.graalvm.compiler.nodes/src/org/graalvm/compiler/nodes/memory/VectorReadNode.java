package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import java.util.List;

@NodeInfo(nameTemplate = "VectorRead#{p#locations/s}")
public class VectorReadNode extends VectorFixedAccessNode implements LIRLowerable {

    public static final NodeClass<VectorReadNode> TYPE = NodeClass.create(VectorReadNode.class);

    public VectorReadNode(AddressNode address, LocationIdentity[] locations, Stamp stamp, BarrierType barrierType) {
        this(TYPE, address, locations, stamp, null, barrierType, false);
    }

    public VectorReadNode(NodeClass<? extends VectorReadNode> c, AddressNode address, LocationIdentity[] locations, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck) {
        super(c, address, locations, stamp, guard, barrierType, nullCheck);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        final LIRKind scalarReadKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitVectorLoad(scalarReadKind, locations.length, gen.operand(address), null));
        // TODO(nvangerow): Implement
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return getNullCheck() && type == InputType.Guard || super.isAllowedUsageType(type);
    }

    @Override
    public boolean isVector() {
        return true;
    }

    public static VectorReadNode fromPackElements(List<ReadNode> nodes) {
        assert nodes.size() != 0 : "pack empty";
        // Pre: nodes all have the same guard.
        // Pre: nodes are contiguous
        // Pre: nodes are from the same memory region
        // ???

        final ReadNode anchor = nodes.get(0);
        final AddressNode address = anchor.getAddress();
        final LocationIdentity[] locations = nodes.stream().map(ReadNode::getLocationIdentity).toArray(LocationIdentity[]::new);

        return new VectorReadNode(TYPE, address, locations, anchor.getAccessStamp(), anchor.getGuard(), anchor.getBarrierType(), anchor.getNullCheck());
    }
}
