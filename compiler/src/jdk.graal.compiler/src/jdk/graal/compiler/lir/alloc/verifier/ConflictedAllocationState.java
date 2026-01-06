package jdk.graal.compiler.lir.alloc.verifier;

import jdk.vm.ci.meta.Value;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConflictedAllocationState extends AllocationState {
    protected Set<Value> conflictedValues;

    public ConflictedAllocationState(Value value1, Value value2) {
        this.conflictedValues = new HashSet<>();
        this.conflictedValues.add(value1);
        this.conflictedValues.add(value2);
    }

    private ConflictedAllocationState(Set<Value> conflictedValues) {
        this.conflictedValues = new HashSet<>(conflictedValues);
    }

    public void addConflictedValue(Value value) {
        this.conflictedValues.add(value);
    }

    public Set<Value> getConflictedValues() {
        return this.conflictedValues;
    }

    @Override
    public boolean isConflicted() {
        return true;
    }

    @Override
    public AllocationState meet(AllocationState other) {
        if (other instanceof ValueAllocationState valueState) {
            this.addConflictedValue(valueState.getValue());
        }

        if (other instanceof ConflictedAllocationState conflictedState) {
            this.conflictedValues.addAll(conflictedState.conflictedValues);
        }

        return this;
    }

    @Override
    public AllocationState clone() {
        return new ConflictedAllocationState(this.conflictedValues);
    }

    @Override
    public boolean equals(AllocationState other) {
        return other.isConflicted(); // TODO: handle contents
    }

    @Override
    public String toString() {
        return "Conflicted {" + Arrays.toString(this.conflictedValues.toArray()) + "}";
    }
}
