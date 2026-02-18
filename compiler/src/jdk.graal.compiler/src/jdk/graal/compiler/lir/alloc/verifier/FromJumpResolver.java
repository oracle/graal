package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;

import java.util.List;
import java.util.Set;

/**
 * Resolve label variable location before we jump to
 * said block from its predecessors by looking at
 * their state and finding said variables in their
 * JumpOp.
 */
public class FromJumpResolver {
    public LIR lir;
    public BlockMap<List<RAVInstruction.Base>> blockInstructions;
    public BlockMap<MergedBlockVerifierState> blockStates;

    public FromJumpResolver(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions, BlockMap<MergedBlockVerifierState> blockStates) {
        this.lir = lir;
        this.blockInstructions = blockInstructions;
        this.blockStates = blockStates;
    }

    /**
     * Resolve label variables to said block from their state
     * and their jump variables.
     * <p>
     * If multiple locations are chosen, we take the last one
     * </p>
     *
     * @param block Target block
     */
    public void resolvePhi(BasicBlock<?> block) {
        var labelInstr = (RAVInstruction.Op) this.blockInstructions.get(block).getFirst();
        for (int i = 0; i < labelInstr.dests.count; i++) {
            Set<RAValue> locations = null;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var state = this.blockStates.get(pred);
                if (state == null) {
                    continue;
                }

                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();
                var inputValue = jump.alive.orig[i];

                var varLoc = state.values.getValueLocations(inputValue);
                if (locations == null) {
                    locations = varLoc;
                    continue;
                }

                locations.retainAll(varLoc);
            }

            if (locations == null) {
                continue;
            }

            RAValue location = null;
            if (locations.size() != 1) {
                if (locations.isEmpty()) {
                    return;
                }

                // Selects the location that was used most recently,
                // but it has to be used recently by all the predecessors.
                for (int j = 0; j < block.getPredecessorCount(); j++) {
                    int time = -1;
                    RAValue blockReg = null;
                    for (var loc : locations) {
                        var pred = block.getPredecessorAt(j);
                        var state = this.blockStates.get(pred);
                        if (state == null) {
                            continue;
                        }

                        var regTime = state.values.getKeyTime(loc);
                        if (regTime > time) {
                            time = regTime; // Max time
                            blockReg = loc;
                        }
                    }

                    if (location == null) {
                        location = blockReg;
                    } else if (!location.equals(blockReg)) {
                        // Not same for all blocks, so none choosen.
                        return;
                    }
                }
            } else {
                location = locations.stream().findFirst().get();
            }

            labelInstr.dests.curr[i] = location;
            for (int j = 0; j < block.getPredecessorCount(); j++) {
                var pred = block.getPredecessorAt(j);
                var jump = (RAVInstruction.Op) blockInstructions.get(pred).getLast();
                jump.alive.curr[i] = location;
            }
        }
    }
}
