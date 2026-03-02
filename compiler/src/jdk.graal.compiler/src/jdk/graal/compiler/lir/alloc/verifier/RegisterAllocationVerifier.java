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

import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * Class encapsulating the whole Register Allocation Verification.
 * Maintaining entry states for blocks, resolving label variable
 * locations and checking validity of every location to variable
 * correspondence.
 */
public class RegisterAllocationVerifier {
    /**
     * Verifier IR that abstracts LIR instructions
     * and marks moves inserted by the allocator.
     */
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;

    /**
     * Current state of said block during processing.
     */
    protected BlockMap<MergedBlockVerifierState> blockStates;

    /**
     * State of the block on entry, calculated from its predecessors.
     */
    protected BlockMap<MergedBlockVerifierState> blockEntryStates;

    /**
     * LIR necessary for to access the program graph.
     */
    protected LIR lir;

    /**
     * Register Allocator config used for validating
     * if valid register is used by the allocator.
     */
    protected RegisterAllocationConfig registerAllocationConfig;

    /**
     * Resolution method used for determining
     * label variable locations.
     */
    protected PhiResolution phiResolution;

    /**
     * Resolves locations for label variables by finding
     * their first usage and walking back to the defining
     * label.
     */
    protected FromUsageResolverGlobal fromUsageResolverGlobal;

    /**
     * Conflict resolver for re-materialized constants.
     */
    protected ConflictResolver constantMaterializationConflictResolver;

    public RegisterAllocationVerifier(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions, PhiResolution phiResolution, RegisterAllocationConfig registerAllocationConfig) {
        this.lir = lir;
        this.registerAllocationConfig = registerAllocationConfig;

        this.constantMaterializationConflictResolver = new ConstantMaterializationConflictResolver();

        var cfg = lir.getControlFlowGraph();
        this.blockInstructions = blockInstructions;
        this.blockEntryStates = new BlockMap<>(cfg);

        this.blockStates = new BlockMap<>(cfg);
        this.phiResolution = phiResolution;

        this.fromUsageResolverGlobal = new FromUsageResolverGlobal(lir, blockInstructions);
    }

    /**
     * For every block, we need to calculate its entry state
     * which is a combination of states of blocks that are its
     * predecessors, merged into a state we use to verify
     * that inputs to instructions are correct symbols based
     * on instructions before allocation.
     */
    public void calculateEntryBlocks() {
        Queue<BasicBlock<?>> worklist = new ArrayDeque<>();

        var startBlock = this.lir.getControlFlowGraph().getStartBlock();
        this.blockEntryStates.put(startBlock, new MergedBlockVerifierState(startBlock, registerAllocationConfig, phiResolution, constantMaterializationConflictResolver));
        worklist.add(this.lir.getControlFlowGraph().getStartBlock());

        while (!worklist.isEmpty()) {
            var block = worklist.poll();
            var instructions = this.blockInstructions.get(block);

            // Create new entry state for successor blocks out of current block state
            var state = new MergedBlockVerifierState(block, this.blockEntryStates.get(block));
            for (var instr : instructions) {
                state.update(instr, block);
            }
            this.blockStates.put(block, state);

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                var succ = block.getSuccessorAt(i);

                if (this.blockEntryStates.get(succ) == null) {
                    var succState = new MergedBlockVerifierState(succ, state);

                    this.blockEntryStates.put(succ, succState);
                    worklist.remove(succ);
                    worklist.add(succ);
                    continue;
                }

                var succState = this.blockEntryStates.get(succ);
                if (succState.meetWith(state)) {
                    // State changed or labels have not yet been determined, add to worklist
                    this.blockEntryStates.put(succ, succState);
                    worklist.remove(succ); // Always at the end, for predecessors to be processed first.
                    worklist.add(succ);
                }
            }
        }
    }

    /**
     * Verify every instruction input.
     */
    public void verifyInstructionInputs() {
        for (var blockId : this.lir.getBlocks()) {
            var block = this.lir.getBlockById(blockId);
            var state = this.blockEntryStates.get(block);
            var instructions = this.blockInstructions.get(block);

            RAVInstruction.Op labelInstrOfSucc = null;
            if (block.getSuccessorCount() == 1) {
                labelInstrOfSucc = (RAVInstruction.Op) this.blockInstructions.get(block.getSuccessorAt(0)).getFirst();
            }

            for (var instr : instructions) {
                state.check(instr, block, labelInstrOfSucc);
                state.update(instr, block);
            }
        }
    }

    /**
     * Verify every instruction and collect every exception that has occurred.
     *
     * @param compUnitName Name of this compilation unit, we are verifying
     */
    public void verifyInstructionsAndCollectErrors(String compUnitName) {
        List<RAVException> exceptions = new ArrayList<>();
        for (var blockId : this.lir.getBlocks()) {
            var block = this.lir.getBlockById(blockId);
            var state = this.blockEntryStates.get(block);
            var instructions = this.blockInstructions.get(block);
            var labelInstr = (RAVInstruction.Op) instructions.getFirst();

            for (var instr : instructions) {
                try {
                    state.check(instr, block, labelInstr);
                    state.update(instr, block);
                } catch (RAVException e) {
                    exceptions.add(e);
                }
            }
        }

        if (!exceptions.isEmpty()) {
            throw new RAVFailedVerificationException(compUnitName, exceptions);
        }
    }

    /**
     * Run the verification process, including label variable
     * resolution, handling of materialized constants, calculating
     * entry states for every block.
     */
    public void run() {
        this.constantMaterializationConflictResolver.prepare(lir, blockInstructions);
        if (this.phiResolution == PhiResolution.FromUsageGlobal) {
            this.fromUsageResolverGlobal.resolvePhiFromUsage();
        }

        this.calculateEntryBlocks();
        this.verifyInstructionInputs();
    }
}
