package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.Variable;

import java.util.List;

@SuppressWarnings("serial")
public class CircularDefinitionError extends RAVError {
    public CircularDefinitionError(BasicBlock<?> defBlock, BasicBlock<?> predecessor, RAVInstruction.Op label, List<Variable> beingPropagated) {
        super(getErrorMessage(defBlock, predecessor, label, beingPropagated));
    }

    static String getErrorMessage(BasicBlock<?> defBlock, BasicBlock<?> predecessor, RAVInstruction.Op label, List<Variable> beingPropagated) {
        StringBuilder operandString = new StringBuilder("[");
        var values = label.dests;
        for (int i = 0; i < values.count; i++) {
            if (!LIRValueUtil.isVariable(values.orig[i])) {
                continue; // Avoid fatal error
            }

            var variable = LIRValueUtil.asVariable(values.orig[i]);
            if (!beingPropagated.contains(variable)) {
                continue;
            }

            var location = values.curr[i];

            operandString.append(variable.toString());
            if (location != null) {
                operandString.append(" -> ").append(location.toString());
            } else {
                operandString.append(" -> ?");
            }

            operandString.append(", ");
        }

        operandString.setLength(operandString.length() - 2);
        operandString.append("]");

        return "Circular definition for variable detected " + predecessor + " -> " + defBlock + " with " + operandString;
    }
}
