package jdk.graal.compiler.lir.alloc.verifier;

/**
 * Default allocation state for all locations,
 * nothing was yet inserted.
 */
public class UnknownAllocationState extends AllocationState {
    /**
     * Single instance used for all occurrences of Unknown state.
     */
    public static UnknownAllocationState INSTANCE = new UnknownAllocationState();

    @Override
    public boolean isUnknown() {
        return true;
    }

    /**
     * Meet state from predecessor, if both are unknown then unknown is returned,
     * otherwise conflict occurs.
     *
     * @param other Other state coming from a predecessor edge
     * @return Unknown if both are, otherwise a conflict
     */
    @Override
    public AllocationState meet(AllocationState other) {
        if (other.isUnknown()) {
            return this;
        }

        if (other instanceof ConflictedAllocationState conflictedState) {
            var newConfState = new ConflictedAllocationState(conflictedState.conflictedStates);
            newConfState.addConflictedValue(ValueAllocationState.createIllegal());
            return newConfState;
        }

        return new ConflictedAllocationState((ValueAllocationState) other, ValueAllocationState.createIllegal());
    }

    @Override
    public AllocationState clone() {
        return INSTANCE;
    }

    @Override
    public boolean equals(AllocationState other) {
        return other == INSTANCE;
    }

    @Override
    public String toString() {
        return "Unknown";
    }
}
