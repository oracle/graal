package jdk.graal.compiler.lir.alloc.verifier;

/**
 * Interface for AllocationState stored in AllocationStateMap,
 * describing what state physical location is in.
 */
public abstract class AllocationState {
    /**
     * No location is ever just null, always at least Unknown.
     *
     * @return
     */
    public static AllocationState getDefault() {
        return UnknownAllocationState.INSTANCE;
    }

    /**
     * Shortcut to check if state is Unknown.
     *
     * @return Is unknown state
     */
    public boolean isUnknown() {
        return false;
    }

    /**
     * Shortcut to check if state is ConflictedState.
     *
     * @return Is ConflictedState
     */
    public boolean isConflicted() {
        return false;
    }

    /**
     * Create a copy of this state, necessary
     * for state copies made over program graph edges.
     *
     * @return Newly copied state
     */
    public abstract AllocationState clone();

    /**
     * Meet a state from different block coming from edge in
     * the program graph, decide what result of said two states
     * should be.
     *
     * @param other Other state coming from a predecessor edge
     * @return What is the new state the location is in.
     */
    public abstract AllocationState meet(AllocationState other);

    public abstract boolean equals(AllocationState other);
}
