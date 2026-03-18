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
 * Class encapsulating the whole Register Allocation Verification. Maintaining entry states for
 * blocks, resolving label variable locations and checking validity of every location to variable
 * correspondence.
 */
public class RegAllocVerifier {
    /**
     * Verifier IR that abstracts LIR instructions and marks moves inserted by the allocator.
     */
    protected BlockMap<List<RAVInstruction.Base>> blockInstructions;

    /**
     * State of the block on entry, calculated from its predecessors.
     */
    protected BlockMap<BlockVerifierState> blockEntryStates;

    /**
     * LIR necessary for to access the program graph.
     */
    protected LIR lir;

    /**
     * Register Allocator config used for validating if valid register is used by the allocator.
     */
    protected RegisterAllocationConfig registerAllocationConfig;

    /**
     * Resolves locations for label variables by finding their first usage and walking back to the
     * defining label.
     */
    protected FromUsageResolverGlobal fromUsageResolverGlobal;

    /**
     * Conflict resolver for re-materialized constants.
     */
    protected ConflictResolver constantMaterializationConflictResolver;

    protected RematerializationHandler rematerializationHandler;

    public RegAllocVerifier(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions, RegisterAllocationConfig registerAllocationConfig) {
        this.lir = lir;
        this.registerAllocationConfig = registerAllocationConfig;

        var cfg = lir.getControlFlowGraph();
        this.blockInstructions = blockInstructions;
        this.blockEntryStates = new BlockMap<>(cfg);

        this.fromUsageResolverGlobal = new FromUsageResolverGlobal(lir, blockInstructions);

        var constantMaterializationConflictResolver = new ConstantMaterializationConflictResolver();
        this.constantMaterializationConflictResolver = constantMaterializationConflictResolver;
        this.rematerializationHandler = new RematerializationHandler(constantMaterializationConflictResolver);
    }

    /**
     * For every block, we need to calculate its entry state which is a combination of states of
     * blocks that are its predecessors, we get after reached a fixed point state, where no entry
     * state is changed.
     *
     * This is necessary to verify instruction inputs correctly.
     */
    public void calculateEntryBlocks() {
        Queue<BasicBlock<?>> worklist = new ArrayDeque<>();

        var startBlock = this.lir.getControlFlowGraph().getStartBlock();
        var startBlockState = createNewBlockState(startBlock);
        startBlockState.updateCalleeSavedRegisters();
        this.blockEntryStates.put(startBlock, startBlockState);

        worklist.add(startBlock);
        while (!worklist.isEmpty()) {
            var block = worklist.poll();
            var instructions = this.blockInstructions.get(block);

            // Create new entry state for successor blocks out of current block state
            var state = new BlockVerifierState(block, this.blockEntryStates.get(block));
            for (var instr : instructions) {
                state.update(instr);
            }

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                var succ = block.getSuccessorAt(i);

                BlockVerifierState succState;
                if (this.blockEntryStates.get(succ) == null) {
                    succState = new BlockVerifierState(succ, state);
                } else {
                    succState = this.blockEntryStates.get(succ);
                    if (!succState.meetWith(state)) {
                        continue;
                    }
                }

                this.blockEntryStates.put(succ, succState);
                worklist.remove(succ); // Always at the end, for predecessors to be processed first.
                worklist.add(succ);
            }
        }
    }

    protected BlockVerifierState createNewBlockState(BasicBlock<?> block) {
        return new BlockVerifierState(block, registerAllocationConfig, constantMaterializationConflictResolver);
    }

    /**
     * By using the entry states calculated in step beforehand, we check input of every instruction
     * to see that it matches symbols before allocation, after wards we update the state so the next
     * instruction has correct state at said instruction input.
     */
    public void verifyInstructionInputs() {
        for (var blockId : this.lir.getBlocks()) {
            var block = this.lir.getBlockById(blockId);
            var state = this.blockEntryStates.get(block);
            var instructions = this.blockInstructions.get(block);

            for (var instr : instructions) {
                state.check(instr);
                state.update(instr);
            }

            if (block.getSuccessorCount() == 0) {
                state.checkCalleeSavedRegisters();
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

            for (var instr : instructions) {
                try {
                    state.check(instr);
                    state.update(instr);
                } catch (RAVException e) {
                    exceptions.add(e);
                }
            }

            try {
                if (block.getSuccessorCount() == 0) {
                    state.checkCalleeSavedRegisters();
                }
            } catch (RAVException e) {
                exceptions.add(e);
            }
        }

        if (!exceptions.isEmpty()) {
            throw new RAVFailedVerificationException(compUnitName, exceptions);
        }
    }

    /**
     * Run verification based on verifier IR created in phase beforehand, resolving stripped label
     * variable locations back, calculating entry state for every block so that at the end we can
     * verify inputs of instructions match variables present before allocation.
     *
     * The issues we are looking to catch are mostly about making sure that order of spills, reloads
     * and moves is correct and that used location after stores the symbol that is supposed to be
     * there.
     *
     * We also make sure that kinds are still matching, operand flags aren't violated, alive
     * location not being used as temp or output of same instruction.
     */
    public void run() {
        this.constantMaterializationConflictResolver.prepare(lir, blockInstructions);
        this.fromUsageResolverGlobal.resolvePhiFromUsage();

        this.calculateEntryBlocks();
        this.verifyInstructionInputs();
    }
}
