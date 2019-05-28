package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.extended.VectorGuardedNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;

public interface VectorAccess extends VectorGuardedNode, HeapAccess {

    AddressNode getAddress();

    LocationIdentity[] getLocationIdentities();

    boolean canNullCheck();
}
