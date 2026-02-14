package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.Variable;
import jdk.vm.ci.meta.Value;

import java.util.List;

public interface ConflictResolver {
    void prepare(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions);
    ValueAllocationState resolveValueState(Variable target, ValueAllocationState valueState, Value location);
    ValueAllocationState resolveConflictedState(Variable target, ConflictedAllocationState conflictedState, Value location);
}
