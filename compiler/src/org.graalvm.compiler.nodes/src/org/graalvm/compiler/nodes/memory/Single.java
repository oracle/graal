package org.graalvm.compiler.nodes.memory;

import org.graalvm.word.LocationIdentity;

public interface Single extends MemoryKill {

    /**
     * This method is used to determine which memory location is killed by this node. Returning
     * the special value {@link LocationIdentity#any()} will kill all memory locations.
     *
     * @return the identity of the location killed by this node.
     */
    LocationIdentity getKilledLocationIdentity();
}