package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.vm.ci.meta.Value;

import java.util.List;

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
     * Resolve phi registers from this jump point, can only be done if
     * the phi variables/constants only have one location.
     *
     * <p>
     * This way we can handle phi arguments before we reach said block.
     * But cannot really be done for loops, if their initial values
     * are the same and there are multiple ones.
     * </p>
     *
     * @param block From whom we are jumping from
     */
    public void resolvePhiFromJump(BasicBlock<?> block) {
        var state = this.blockStates.get(block);
        var jumpInstr = (RAVInstruction.Op) this.blockInstructions.get(block).getLast();

        for (int i = 0; i < jumpInstr.alive.count; i++) {
            if (jumpInstr.alive.curr[i] != null) {
                continue;
            }

            var inputValue = jumpInstr.alive.orig[i];
            if (!LIRValueUtil.isVariable(inputValue) && !(inputValue instanceof ConstantValue)) {
                continue;
            }

            var registerValue = this.getRegisterBeforeJump(state, inputValue);
            if (registerValue == null) {
                return;
            }

            jumpInstr.alive.curr[i] = registerValue;
            for (int j = 0; j < block.getSuccessorCount(); j++) {
                var succ = block.getSuccessorAt(j);
                var labelInstr = (RAVInstruction.Op) this.blockInstructions.get(succ).getFirst();

                labelInstr.dests.curr[i] = jumpInstr.alive.curr[i];

                for (int k = 0; k < succ.getPredecessorCount(); k++) {
                    // Sibling jumps need to be updated as well in order to pass input checks.
                    var sibling = succ.getPredecessorAt(k);
                    if (sibling.equals(block)) {
                        continue;
                    }

                    var siblingJumpInstr = (RAVInstruction.Op) this.blockInstructions.get(sibling).getLast();
                    siblingJumpInstr.alive.curr[i] = jumpInstr.alive.curr[i];
                }
            }
        }
    }

    private Value getRegisterBeforeJump(MergedBlockVerifierState state, Value inputValue) {
        // Does not work for constants if there is more of them
        var locations = state.values.getValueLocations(inputValue);
        if (locations.isEmpty()) {
            return null;
        }

        var register = locations.stream().findFirst().get();
        if (locations.size() != 1) {
            int time = -1;
            for (var location : locations) {
                var locTime = state.values.getKeyTime(location);
                if (locTime > time) {
                    register = location;
                    time = locTime;
                }
            }
        }

        return register;
    }
}
