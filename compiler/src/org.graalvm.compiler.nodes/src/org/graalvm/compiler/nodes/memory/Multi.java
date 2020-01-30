package org.graalvm.compiler.nodes.memory;

import org.graalvm.word.LocationIdentity;

public interface Multi extends MemoryCheckpoint {

    /**
     * This method is used to determine which set of memory locations is killed by this node.
     * Returning the special value {@link LocationIdentity#any()} will kill all memory
     * locations.
     *
     * @return the identities of all locations killed by this node.
     */
    LocationIdentity[] getKilledLocationIdentities();

}