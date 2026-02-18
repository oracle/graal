package jdk.graal.compiler.lir.alloc.verifier;

/**
 * Re-materialized constant has wrong source (not a Value Move),
 * but either undefined or something different.
 */
@SuppressWarnings("serial")
public class RematerializedConstantSourceMissingError extends RAVError {
    public RematerializedConstantSourceMissingError(RAVInstruction.Base instruction, RAVariable variable) {
        super(variable + " was materialized to " + instruction);
    }

    public static String getErrorMessage(RAVInstruction.Base instruction, RAVariable variable) {
        if (instruction == null) {
            return "Variable " + variable + " has no materialization source.";
        }

        return "Variable " + variable + " was materialized to " + instruction;
    }
}
