package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.options.OptionValues;

import java.util.IdentityHashMap;
import java.util.Map;

public class RegisterAllocationVerifierPhaseState {
    public PhiResolution phiResolution;
    public boolean moveConstants;
    public String filterStr;

    protected Map<LIRGenerationResult, Map<LIRInstruction, RAVInstruction.Base>> verifierInstructions;

    public RegisterAllocationVerifierPhaseState(OptionValues options) {
        this.verifierInstructions = new IdentityHashMap<>();

        this.phiResolution = RegisterAllocationVerifierPhase.Options.RAPhiResolution.getValue(options);
        this.moveConstants = RegisterAllocationVerifierPhase.Options.MoveConstants.getValue(options);
        this.filterStr = RegisterAllocationVerifierPhase.Options.RAFilter.getValue(options);
    }

    public boolean shouldBeVerified(LIRGenerationResult lirGenRes) {
        var compUnitName = lirGenRes.getCompilationUnitName();
        // Filter for compilation unit substring to run verification only on
        // certain methods, cannot use MethodFilter here because I cannot
        // access JavaMethod here.
        return filterStr == null || compUnitName.contains(filterStr);
    }

    public Map<LIRInstruction, RAVInstruction.Base> createInstructionMap(LIRGenerationResult lirGenRes) {
        var idMap = new IdentityHashMap<LIRInstruction, RAVInstruction.Base>();
        this.verifierInstructions.put(lirGenRes, idMap);
        return idMap;
    }

    public Map<LIRInstruction, RAVInstruction.Base> getInstructionMap(LIRGenerationResult lirGenRes) {
        if (!this.verifierInstructions.containsKey(lirGenRes)) {
            throw new IllegalStateException();
        }

        return this.verifierInstructions.get(lirGenRes);
    }

    public void deleteInstructionMap(LIRGenerationResult lirGenRes) {
        verifierInstructions.remove(lirGenRes);
    }
}
