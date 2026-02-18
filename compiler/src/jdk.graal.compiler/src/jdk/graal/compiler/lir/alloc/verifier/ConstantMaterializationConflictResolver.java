package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.StackSlot;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolve conflicts made by the constant materialization process.
 * <p>
 * Also checks if the constant can materialize to stack.
 * </p>
 */
public class ConstantMaterializationConflictResolver implements ConflictResolver {
    protected Map<RAVariable, ConstantValue> constantVariableMap;
    protected Set<RAVariable> canRematerializeToStack;

    public ConstantMaterializationConflictResolver() {
        this.constantVariableMap = new EconomicHashMap<>();
        this.canRematerializeToStack = new EconomicHashSet<>();
    }

    /**
     * Creates a variable to constant mapping.
     *
     * @param lir               LIR
     * @param blockInstructions IR of the Verifier
     */
    @Override
    public void prepare(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions) {
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);
            var instructions = blockInstructions.get(block);

            for (var instruction : instructions) {
                this.prepareFromInstr(instruction, block);
            }
        }
    }

    /**
     * Add variable to constant mapping from instruction contents.
     *
     * @param instruction Instruction we are looking at for LoadConstantOp
     * @param block       Block where instruction is from
     */
    public void prepareFromInstr(RAVInstruction.Base instruction, BasicBlock<?> block) {
        if (instruction instanceof RAVInstruction.Op op && op.lirInstruction.isLoadConstantOp()) {
            var loadConstantOp = StandardOp.LoadConstantOp.asLoadConstantOp(op.lirInstruction);

            if (!op.dests.orig[0].isVariable()) {
                return;
            }

            var variable = op.dests.orig[0].asVariable();
            var constantValue = new ConstantValue(variable.getValue().getValueKind(), loadConstantOp.getConstant());

            constantVariableMap.put(variable, constantValue);
            if (loadConstantOp.canRematerializeToStack()) {
                canRematerializeToStack.add(variable);
            }
        }
    }

    /**
     * Resolve conflict of target variable and the constant it represents
     * to the ValueAllocationState of the target variable.
     *
     * @param target          Variable we are looking to resolve to
     * @param conflictedState Set of conflicted states
     * @param location        Location where the valueState is stored
     * @return target variable stored in ValueAllocationState or null.
     */
    @Override
    public ValueAllocationState resolveConflictedState(RAVariable target, ConflictedAllocationState conflictedState, RAValue location) {
        var confStates = conflictedState.getConflictedStates();

        RAVariable variable = null;
        ValueAllocationState constantState = null;

        for (var valAllocState : confStates) {
            var value = valAllocState.getRAValue();
            if (value.isVariable()) {
                if (variable != null && !variable.equals(value)) {
                    return null;
                }

                variable = value.asVariable();
            } else if (value.getValue() instanceof ConstantValue) {
                if (constantState != null && !constantState.getRAValue().equals(value)) {
                    return null;
                }

                constantState = valAllocState;
            }
        }

        if (!target.equals(variable) || constantState == null) {
            return null;
        }

        if (!this.constantVariableMap.containsKey(variable)) {
            return null;
        }

        if (!this.constantVariableMap.get(variable).equals(constantState.getValue())) {
            return null;
        }

        if (isRematerializedToWrongLocation(variable, constantState)) {
            throw new RAVException("Variable " + variable + " cannot be rematerialized to stack location " + location);
        }

        return new ValueAllocationState(variable, constantState.getSource(), constantState.block);
    }

    /**
     * Resolve ValueAllocationState of a constant to the target variable.
     *
     * @param variable   Variable we are looking to resolve to
     * @param valueState Current ValueAllocationState instance
     * @param location   Location where the valueState is stored
     * @return target variable stored in ValueAllocationState or null.
     */
    @Override
    public ValueAllocationState resolveValueState(RAVariable variable, ValueAllocationState valueState, RAValue location) {
        if (!this.constantVariableMap.containsKey(variable)) {
            return null;
        }

        if (valueState.getRAValue().getValue() instanceof ConstantValue constant) {
            if (!this.constantVariableMap.get(variable).equals(constant)) {
                return null;
            }

            if (isRematerializedToWrongLocation(variable, valueState)) {
                throw new RAVException("Variable " + variable + " cannot be rematerialized to stack location " + location);
            }

            return new ValueAllocationState(variable, valueState.getSource(), valueState.getBlock());
        }

        return null;
    }

    /**
     * Check if variable can be rematerialized to said location based on the
     * original instruction source, stored in ValueAllocationState.
     *
     * <p>
     * Check if variable cannot rematerialize to stack and if it did so.
     * </p>
     *
     * @param variable Target variable
     * @param state    State it is in
     * @return Was it rematerialized to wrong location?
     */
    protected boolean isRematerializedToWrongLocation(RAVariable variable, ValueAllocationState state) {
        if (canRematerializeToStack.contains(variable)) {
            return false;
        }

        // Cannot be rematerialized to stack
        var source = state.getSource();
        if (source instanceof RAVInstruction.ValueMove move) {
            var location = move.location.getValue();
            return location instanceof StackSlot || location instanceof VirtualStackSlot;
        } else {
            throw new RematerializedConstantSourceMissingError(source, variable);
        }
    }
}
