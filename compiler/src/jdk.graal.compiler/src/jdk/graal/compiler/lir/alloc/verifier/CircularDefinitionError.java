package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;

import java.util.List;

/**
 * Circular definition was created between
 * a label block and one of its predecessors,
 * meaning a variable defined in the first block
 * is being propagated back to it by one of its
 * predecessors, which is an invalid state.
 * <p>
 * Defining block's entry state would contain
 * the variable it is defining in its label.
 * </p>
 */
@SuppressWarnings("serial")
public class CircularDefinitionError extends RAVError {
    /**
     * Construct a CircularDefinitionError
     *
     * @param defBlock        Block where label variables are first defined
     * @param predecessor     The predecessor block creating the circular definition
     * @param label           Label defining variables
     * @param beingPropagated Which variables are being propagated to defBlock from the predecessor
     */
    public CircularDefinitionError(BasicBlock<?> defBlock, BasicBlock<?> predecessor, RAVInstruction.Op label, List<RAVariable> beingPropagated) {
        super(getErrorMessage(defBlock, predecessor, label, beingPropagated));
    }

    static String getErrorMessage(BasicBlock<?> defBlock, BasicBlock<?> predecessor, RAVInstruction.Op label, List<RAVariable> beingPropagated) {
        StringBuilder operandString = new StringBuilder("[");
        var values = label.dests;
        for (int i = 0; i < values.count; i++) {
            if (!values.orig[i].isVariable()) {
                continue; // Avoid fatal error
            }

            var variable = values.orig[i].asVariable();
            if (!beingPropagated.contains(variable)) {
                continue;
            }

            var location = values.curr[i];

            operandString.append(variable.toString());
            if (location != null) {
                operandString.append(" -> ").append(location);
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
