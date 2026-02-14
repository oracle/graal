package jdk.graal.compiler.lir.alloc.verifier;

public abstract class AllocationState {
    public static AllocationState getDefault() {
        return UnknownAllocationState.INSTANCE;
    }

    public boolean isUnknown() {
        return false;
    }

    public boolean isConflicted() {
        return false;
    }

    public abstract AllocationState clone();

    public abstract AllocationState meet(AllocationState other);

    public abstract boolean equals(AllocationState other);
}
