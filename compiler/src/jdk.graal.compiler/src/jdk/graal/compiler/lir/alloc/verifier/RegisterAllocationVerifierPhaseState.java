/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.EconomicHashMap;
import org.graalvm.collections.Equivalence;

import java.util.Map;

/**
 * Phase state shared by the preallocation and verification phases,
 * pertaining mostly to shared config and Verifier IR.
 */
public class RegisterAllocationVerifierPhaseState {
    public PhiResolution phiResolution;
    public String filterStr;

    /**
     * Mapping between LIRGenerationResult and Map of LIR instructions to Verifier instructions.
     */
    protected Map<LIRGenerationResult, Map<LIRInstruction, RAVInstruction.Base>> verifierInstructions;

    public RegisterAllocationVerifierPhaseState(OptionValues options) {
        this.verifierInstructions = new EconomicHashMap<>(Equivalence.IDENTITY);

        this.phiResolution = RegisterAllocationVerifierPhase.Options.RAPhiResolution.getValue(options);
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
        Map<LIRInstruction, RAVInstruction.Base> idMap = new EconomicHashMap<>(Equivalence.IDENTITY);
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
