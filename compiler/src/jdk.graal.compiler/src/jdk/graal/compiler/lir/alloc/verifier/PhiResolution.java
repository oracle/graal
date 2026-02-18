package jdk.graal.compiler.lir.alloc.verifier;

/**
 * Label variable location resolution method.
 */
public enum PhiResolution {
    /**
     * Before block is entered, locations are determined
     * by its predecessors with state defined and variables
     * used in their Jump instructions.
     */
    FromJump,
    /**
     * By looking up variables first usage,
     * and walking back to defining label -
     * per each variable.
     */
    FromUsage,
    /**
     * Resolve locations by looking at states of predecessors
     * and converging on a single location.
     */
    FromPredecessors,
    /**
     * By looking up variables first usage,
     * and walking back to defining label -
     * every variable at once.
     */
    FromUsageGlobal,
    /**
     * Resolve locations by resolving
     * conflicts created by numerous predecessors.
     */
    FromConflicts,
    /**
     * Modify the allocator to keep
     * label variable locations present
     * after the allocation.
     */
    ByAllocator
}
