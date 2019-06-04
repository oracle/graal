package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.nodes.extended.GuardedNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.word.LocationIdentity;

public interface VectorAccess extends GuardedNode, HeapAccess {

    AddressNode getAddress();

    LocationIdentity[] getLocationIdentities();

    boolean canNullCheck();
}
