package jdk.graal.compiler.lir.alloc.verifier;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConflictedAllocationState extends AllocationState {
    protected Set<ValueAllocationState> conflictedStates;

    public ConflictedAllocationState() {
        this.conflictedStates = new HashSet<>();
    }

    public ConflictedAllocationState(ValueAllocationState state1, ValueAllocationState state2) {
        this();
        this.conflictedStates.add(state1); // Not using addConflictedValue because a warning is thrown
        this.conflictedStates.add(state2);
    }

    private ConflictedAllocationState(Set<ValueAllocationState> conflictedStates) {
        this.conflictedStates = new HashSet<>(conflictedStates);
    }

    public void addConflictedValue(ValueAllocationState state) {
        this.conflictedStates.add(state);
    }

    public Set<ValueAllocationState> getConflictedStates() {
        return this.conflictedStates;
    }

    @Override
    public boolean isConflicted() {
        return true;
    }

    @Override
    public AllocationState meet(AllocationState other) {
        var newlyConflictedState = new ConflictedAllocationState(this.getConflictedStates());
        if (other instanceof ValueAllocationState valueState) {
            newlyConflictedState.addConflictedValue(valueState);
        }

        if (other instanceof ConflictedAllocationState conflictedState) {
            newlyConflictedState.conflictedStates.addAll(conflictedState.conflictedStates);
        }

        return newlyConflictedState;
    }

    @Override
    public AllocationState clone() {
        return new ConflictedAllocationState(this.conflictedStates);
    }

    @Override
    public boolean equals(AllocationState other) {
        return other.isConflicted();
    }

    @Override
    public String toString() {
        return "Conflicted {" + Arrays.toString(this.conflictedStates.toArray()) + "}";
    }
}
