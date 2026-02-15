package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.StackSlot;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConstantMaterializationConflictResolver implements ConflictResolver {
    protected Map<RAVariable, ConstantValue> constantVariableMap;
    protected Set<RAVariable> canRematerializeToStack;

    public ConstantMaterializationConflictResolver() {
        this.constantVariableMap = new EconomicHashMap<>();
        this.canRematerializeToStack = new EconomicHashSet<>();
    }

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

    public void prepareFromInstr(RAVInstruction.Base instruction, BasicBlock<?> block) {
        if (instruction instanceof RAVInstruction.Op op && op.lirInstruction.isLoadConstantOp()) {
            var loadConstantOp = StandardOp.LoadConstantOp.asLoadConstantOp(op.lirInstruction);

            // TODO: Handle moves for canRematerializeToStack
            // MOVE vstack:1, const // v1
            // MOVE rax, vstack:1
            // USE v1

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

    @Override
    public ValueAllocationState resolveConflictedState(RAVariable target, ConflictedAllocationState conflictedState, RAValue location) {
        var confStates = conflictedState.getConflictedStates();

        RAVariable variable = null;
        RAValue constantValue = null;

        for (var states : confStates) {
            var value = states.getRAValue();
            if (value.isVariable()) {
                if (variable != null && !variable.equals(value)) {
                    return null;
                }

                variable = value.asVariable();
            } else if (value.getValue() instanceof ConstantValue) {
                if (constantValue != null && !constantValue.equals(value)) {
                    return null;
                }

                constantValue = value;
            }
        }

        if (!target.equals(variable) || constantValue == null) {
            return null;
        }

        if (!this.constantVariableMap.containsKey(variable)) {
            return null;
        }

        if (!this.constantVariableMap.get(variable).equals(constantValue)) {
            return null;
        }

        if (isRematerializedToWrongLocation(variable, location)) {
            throw new RAVException("Variable " + variable + " cannot be rematerialized to stack location " + location);
        }

        return new ValueAllocationState(variable, null);
    }

    @Override
    public ValueAllocationState resolveValueState(RAVariable variable, ValueAllocationState valueState, RAValue location) {
        if (!this.constantVariableMap.containsKey(variable)) {
            return null;
        }

        if (valueState.getRAValue().getValue() instanceof ConstantValue constant) {
            if (!this.constantVariableMap.get(variable).equals(constant)) {
                return null;
            }

            if (isRematerializedToWrongLocation(variable, location)) {
                throw new RAVException("Variable " + variable + " cannot be rematerialized to stack location " + location);
            }

            return new ValueAllocationState(variable, valueState.source);
        }

        return null;
    }

    protected boolean isRematerializedToWrongLocation(RAVariable variable, RAValue raLocation) {
        var location = raLocation.getValue();
        if (location instanceof StackSlot || location instanceof VirtualStackSlot) {
            return !canRematerializeToStack.contains(variable);
        }
        return false;
    }
}
