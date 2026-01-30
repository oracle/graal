package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConstantMaterializationConflictResolver implements ConflictResolver {
    protected Map<Variable, ConstantValue> constantVariableMap;
    protected Set<Variable> canRematerializeToStack;

    public ConstantMaterializationConflictResolver() {
        this.constantVariableMap = new HashMap<>();
        this.canRematerializeToStack = new HashSet<>();
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

            if (!LIRValueUtil.isVariable(op.dests.orig[0])) {
                return;
            }

            var variable = LIRValueUtil.asVariable(op.dests.orig[0]);
            var constantValue = new ConstantValue(variable.getValueKind(), loadConstantOp.getConstant());

            constantVariableMap.put(variable, constantValue);
            if (loadConstantOp.canRematerializeToStack()) {
                canRematerializeToStack.add(variable);
            }
        }
    }

    @Override
    public ValueAllocationState resolveConflictedState(Variable target, ConflictedAllocationState conflictedState, Value location) {
        var confStates = conflictedState.getConflictedStates();

        Variable variable = null;
        ConstantValue constantValue = null;

        for (var states : confStates) {
            var value = states.getValue();
            if (LIRValueUtil.isVariable(value)) {
                if (variable != null && !variable.equals(value)) {
                    return null;
                }

                variable = LIRValueUtil.asVariable(value);
            } else if (value instanceof ConstantValue constValue) {
                if (constantValue != null && !constantValue.equals(constValue)) {
                    return null;
                }

                constantValue = constValue;
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
    public ValueAllocationState resolveValueState(Variable variable, ValueAllocationState valueState, Value location) {
        if (!this.constantVariableMap.containsKey(variable)) {
            return null;
        }

        if (valueState.getValue() instanceof ConstantValue constant) {
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

    protected boolean isRematerializedToWrongLocation(Variable variable, Value location) {
        if (location instanceof StackSlot || location instanceof VirtualStackSlot) {
            return !canRematerializeToStack.contains(variable);
        }
        return false;
    }
}
