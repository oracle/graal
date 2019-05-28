package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.core.common.LocationIdentity;
import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;

public interface VectorAccess extends GuardedNode, HeapAccess {

    AddressNode getAddress();

    LocationIdentity[] getLocationIdentities();

    boolean canNullCheck();
}
