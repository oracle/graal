package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.options.OptionValues;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Phase state shared by the preallocation and verification phases,
 * pertaining mostly to shared config and Verifier IR.
 */
public class RegisterAllocationVerifierPhaseState {
    public PhiResolution phiResolution;
    public boolean moveConstants;
    public String filterStr;

    /**
     * Mapping between LIRGenerationResult and Map of LIR instructions to Verifier instructions.
     */
    protected Map<LIRGenerationResult, Map<LIRInstruction, RAVInstruction.Base>> verifierInstructions;

    public RegisterAllocationVerifierPhaseState(OptionValues options) {
        this.verifierInstructions = new IdentityHashMap<>();

        this.phiResolution = RegisterAllocationVerifierPhase.Options.RAPhiResolution.getValue(options);
        this.moveConstants = RegisterAllocationVerifierPhase.Options.MoveConstants.getValue(options);
        this.filterStr = RegisterAllocationVerifierPhase.Options.RAFilter.getValue(options);
    }

    /**
     * Should this method be verified? Filter when filterStr is set,
     * use ful debugging purposes.
     *
     * @param lirGenRes LIR generation result describing the method
     * @return true, if method should be verified, otherwise false
     */
    public boolean shouldBeVerified(LIRGenerationResult lirGenRes) {
        var compUnitName = lirGenRes.getCompilationUnitName();
        // Filter for compilation unit substring to run verification only on
        // certain methods, cannot use MethodFilter here because I cannot
        // access JavaMethod here.
        return filterStr == null || compUnitName.contains(filterStr);
    }

    /**
     * Create a new instruction map for this method.
     *
     * @param lirGenRes LIR generation result of this method
     * @return New instruction map
     */
    public Map<LIRInstruction, RAVInstruction.Base> createInstructionMap(LIRGenerationResult lirGenRes) {
        var idMap = new IdentityHashMap<LIRInstruction, RAVInstruction.Base>();
        this.verifierInstructions.put(lirGenRes, idMap);
        return idMap;
    }

    /**
     * Retrieve an existing instruction map for this method.
     *
     * @param lirGenRes LIR generation result of this method
     * @return Old instruction map
     */
    public Map<LIRInstruction, RAVInstruction.Base> getInstructionMap(LIRGenerationResult lirGenRes) {
        if (!this.verifierInstructions.containsKey(lirGenRes)) {
            GraalError.shouldNotReachHere("PreAlloc phase did not run for " + lirGenRes.getCompilationUnitName());
        }

        return this.verifierInstructions.get(lirGenRes);
    }

    /**
     * Delete existing instruction map for this method after
     * is it is no longer needed.
     *
     * @param lirGenRes LIR generation result of this method
     */
    public void deleteInstructionMap(LIRGenerationResult lirGenRes) {
        verifierInstructions.remove(lirGenRes);
    }
}
