package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.IterableNodeType;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.VectorAccess;
import org.graalvm.compiler.nodes.memory.address.AddressNode;

@NodeInfo
public abstract class VectorFixedAccessNode extends FixedWithNextNode implements VectorAccess, IterableNodeType  {
    public static final NodeClass<VectorFixedAccessNode> TYPE = NodeClass.create(VectorFixedAccessNode.class);

    @OptionalInput(InputType.Guard) protected GuardingNode guard;

    @Input(InputType.Association) AddressNode address;
    protected final LocationIdentity[] locations;

    protected boolean nullCheck;
    protected BarrierType barrierType;

    @Override
    public AddressNode getAddress() {
        return address;
    }

    public void setAddress(AddressNode address) {
        updateUsages(this.address, address);
        this.address = address;
    }

    @Override
    public LocationIdentity[] getLocationIdentities() {
        return locations;
    }

    public boolean getNullCheck() {
        return nullCheck;
    }

    @Override
    public boolean canNullCheck() {
        return nullCheck;
    }

    protected VectorFixedAccessNode(NodeClass<? extends VectorFixedAccessNode> c, AddressNode address, LocationIdentity[] locations, Stamp stamp) {
        this(c, address, locations, stamp, BarrierType.NONE);
    }

    protected VectorFixedAccessNode(NodeClass<? extends VectorFixedAccessNode> c, AddressNode address, LocationIdentity[] locations, Stamp stamp, BarrierType barrierType) {
        this(c, address, locations, stamp, null, barrierType, false);
    }

    protected VectorFixedAccessNode(NodeClass<? extends VectorFixedAccessNode> c, AddressNode address, LocationIdentity[] locations, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck) {
        super(c, stamp);
        this.address = address;
        this.locations = locations;
        this.guard = guard;
        this.barrierType = barrierType;
        this.nullCheck = nullCheck;
    }

    @Override
    public GuardingNode getGuard() {
        return guard;
    }

    @Override
    public void setGuard(GuardingNode guard) {
        updateUsagesInterface(this.guard, guard);
        this.guard = guard;
    }

    @Override
    public BarrierType getBarrierType() {
        return barrierType;
    }

    public int numElements() {
        return locations.length;
    }
}
