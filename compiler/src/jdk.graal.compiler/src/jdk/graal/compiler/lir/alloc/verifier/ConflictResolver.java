package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;

import java.util.List;

/**
 * Resolve ConflictedAllocationState occurrences based
 * on internal set of rules.
 * <p>
 * In-case comparison of ValueAllocationState fails, it
 * also might get resolved by this.
 * </p>
 */
public interface ConflictResolver {
    /**
     * ConflictResolver can prepare its own internal state here
     * so it can later resolve conflicts.
     *
     * @param lir               LIR
     * @param blockInstructions IR of the Verifier
     */
    void prepare(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions);

    /**
     * Resolve an issue stemming from ValueAllocationState not having the correct value
     * in verification phase.
     *
     * @param target     Variable we are looking to resolve to
     * @param valueState Current ValueAllocationState instance
     * @param location   Location where the valueState is stored
     * @return ValueAllocationState instance if conflict is resolved, otherwise null.
     */
    ValueAllocationState resolveValueState(RAVariable target, ValueAllocationState valueState, RAValue location);

    /**
     * Resolve a ConflictedAllocationState to ValueAllocationState based on the target variable.
     *
     * @param target          Variable we are looking to resolve to
     * @param conflictedState Set of conflicted states
     * @param location        Location where the valueState is stored
     * @return ValueAllocationState instance if conflict is resolved, otherwise null.
     */
    ValueAllocationState resolveConflictedState(RAVariable target, ConflictedAllocationState conflictedState, RAValue location);
}
