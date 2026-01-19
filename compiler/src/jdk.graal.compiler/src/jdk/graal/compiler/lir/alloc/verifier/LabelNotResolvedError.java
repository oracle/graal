package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;

@SuppressWarnings("serial")
public class LabelNotResolvedError extends RAVError {
    public BasicBlock<?> block;
    public RAVInstruction.Op label;
    public PhiResolution resolution;

    public LabelNotResolvedError(BasicBlock<?> block, RAVInstruction.Op label, PhiResolution resolution) {
        super(LabelNotResolvedError.getErrorMessage(label));

        this.block = block;
        this.label = label;
        this.resolution = resolution;
    }

    static String getErrorMessage(RAVInstruction.Op label) {
        StringBuilder labelStringBuilder = new StringBuilder("[");
        StringBuilder unresolvedVariablesStringBuilder = new StringBuilder();
        for (int i = 0; i < label.dests.count; i++) {
            var variable = label.dests.orig[i];
            var location = label.dests.curr[i];

            labelStringBuilder.append(variable.toString());
            if (location != null) {
                labelStringBuilder.append(" -> ").append(location);
            } else {
                labelStringBuilder.append(" -> ?");

                unresolvedVariablesStringBuilder.append(variable);
                unresolvedVariablesStringBuilder.append(", ");
            }

            labelStringBuilder.append(", ");
        }

        int unresLen = unresolvedVariablesStringBuilder.length();
        unresolvedVariablesStringBuilder.delete(unresLen - 2, unresLen);

        return "Could not resolve " + unresolvedVariablesStringBuilder + ": LABEL " + labelStringBuilder + "]";
    }
}
