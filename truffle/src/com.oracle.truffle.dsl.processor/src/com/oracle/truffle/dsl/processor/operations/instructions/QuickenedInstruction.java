package com.oracle.truffle.dsl.processor.operations.instructions;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.operations.SingleOperationData;

public class QuickenedInstruction extends CustomInstruction {

    private final CustomInstruction orig;
    private final List<String> activeSpecNames;
    private final List<SpecializationData> activeSpecs;

    private static String makeName(CustomInstruction orig, List<String> activeSpecNames) {
        StringBuilder sb = new StringBuilder(orig.name);
        sb.append(".q");
        for (String activeSpec : activeSpecNames) {
            sb.append('.');
            sb.append(activeSpec);
        }

        return sb.toString();
    }

    public List<String> getActiveSpecNames() {
        return activeSpecNames;
    }

    public List<SpecializationData> getActiveSpecs() {
        return activeSpecs;
    }

    public CustomInstruction getOrig() {
        return orig;
    }

    @Override
    public String getUniqueName() {
        StringBuilder sb = new StringBuilder(getData().getName());
        sb.append("_q");
        for (String activeSpec : activeSpecNames) {
            sb.append("_");
            sb.append(activeSpec.replaceAll("[^a-zA-Z0-9_]", "_"));
        }
        return sb.toString();
    }

    public QuickenedInstruction(CustomInstruction orig, int id, SingleOperationData data, List<String> activeSpecNames) {
        super(makeName(orig, activeSpecNames), id, data);
        this.orig = orig;
        this.activeSpecNames = activeSpecNames;

        if (activeSpecNames.isEmpty()) {
            data.addError("Invalid quickened instruction: no specializations defined.");
        }

        activeSpecs = new ArrayList<>(data.getNodeData().getSpecializations());
        activeSpecs.removeIf(spec -> {
            for (String activeSpec : activeSpecNames) {
                if (spec.getId().equals(activeSpec)) {
                    return false;
                }
            }
            return true;
        });

        // TODO we should probably error if one of the active specializations is not found

        if (activeSpecs.isEmpty()) {
            data.addError("Invalid quickened instruction: no specializations matched defined.");
        }

        orig.addQuickenedVariant(this);
    }

    @Override
    public CodeTree createSetResultBoxed(ExecutionVariables vars, CodeVariableElement varBoxed, CodeVariableElement varTargetType) {
        return orig.createSetResultBoxed(vars, varBoxed, varTargetType);
    }

    @Override
    public CodeTree createPrepareAOT(ExecutionVariables vars, CodeTree language, CodeTree root) {
        return orig.createPrepareAOT(vars, language, root);
    }

    @Override
    public CodeTree[] createTracingArguments(ExecutionVariables vars) {
        return orig.createTracingArguments(vars);
    }

    @Override
    public void addQuickenedVariant(QuickenedInstruction quick) {
        throw new AssertionError("should not add quickened variants to quickened instructions");
    }
}
