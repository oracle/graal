package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(nameTemplate = "VectorRead#{p#location/s}")
public class VectorReadNode extends FloatableAccessNode implements LIRLowerableAccess, GuardingNode {

    public static final NodeClass<VectorReadNode> TYPE = NodeClass.create(VectorReadNode.class);

    public VectorReadNode(AddressNode address, LocationIdentity location, Stamp stamp, BarrierType barrierType) {
        this(TYPE, address, location, stamp, null, barrierType, false, null);
    }

    protected VectorReadNode(NodeClass<? extends VectorReadNode> c, AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck, FrameState stateBefore) {
        super(c, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // TODO: IMPLEMENT GENERATE
        throw GraalError.unimplemented("VRN unimplemented");
    }

    @Override
    public FloatingAccessNode asFloatingNode(MemoryNode lastLocationAccess) {
        // TODO: IMPLEMENT FLOATING
        throw GraalError.shouldNotReachHere("VectorReadNode not actually floatable");
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return getNullCheck() && type == InputType.Guard || super.isAllowedUsageType(type);
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    @Override
    public Stamp getAccessStamp() {
        return stamp();
    }
}
