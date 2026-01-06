package jdk.graal.compiler.lir.alloc.verifier;

public class UnknownAllocationState extends AllocationState {
    public static UnknownAllocationState INSTANCE = new UnknownAllocationState();

    @Override
    public boolean isUnknown() {
        return true;
    }

    @Override
    public AllocationState meet(AllocationState other) {
        return other;
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
