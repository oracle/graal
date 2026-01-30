package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstantMaterializationConflictResolver implements ConflictResolver {
    protected Map<Variable, ConstantValue> constantVariableMap;

    public ConstantMaterializationConflictResolver() {
        this.constantVariableMap = new HashMap<>();
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
            // TODO: loadConstantOp.canRematerializeToStack?

            if (!LIRValueUtil.isVariable(op.dests.orig[0])) {
                return;
            }

            var variable = LIRValueUtil.asVariable(op.dests.orig[0]);
            var constantValue = new ConstantValue(variable.getValueKind(), loadConstantOp.getConstant());

            constantVariableMap.put(variable, constantValue);
        }
    }

    @Override
    public ValueAllocationState resolveConflictedState(Variable target, ConflictedAllocationState conflictedState) {
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

        return new ValueAllocationState(variable, null);
    }

    @Override
    public ValueAllocationState resolveValueState(Variable original, ValueAllocationState valueState) {
        if (!this.constantVariableMap.containsKey(original)) {
            return null;
        }

        if (valueState.getValue() instanceof ConstantValue constant) {
            if (!this.constantVariableMap.get(original).equals(constant)) {
                return null;
            }

            return new ValueAllocationState(original, valueState.source);
        }

        return null;
    }
}
