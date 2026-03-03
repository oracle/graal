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

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.InstructionStateProcedure;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;
import org.graalvm.collections.Equivalence;

import java.util.ArrayList;
import java.util.Map;

/**
 * Pre-register allocation phase that needs to save information
 * about variables/constants used in LIR instructions.
 */
public class PreRegisterAllocationPhase extends AllocationPhase {
    /**
     * Shared state with the verification phase.
     */
    protected RegisterAllocationVerifierPhaseState state;

    public PreRegisterAllocationPhase(RegisterAllocationVerifierPhaseState state) {
        this.state = state;
    }

    /**
     * Process every block and every instruction to save variables used in them for the verification procedure.
     *
     * @param target    Machine architecture
     * @param lirGenRes LIR generaration result of a method
     * @param context   Allocation context, used for RegisterAllocationConfig
     */
    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        if (!state.shouldBeVerified(lirGenRes)) {
            return;
        }

        var preallocMap = state.createInstructionMap(lirGenRes);
        Map<LIRInstruction, RAVInstruction.Base> preallocMap = new EconomicHashMap<>(Equivalence.IDENTITY);
        LIR lir = lirGenRes.getLIR();

        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);

            RAVInstruction.Base previousInstr = null;
            for (var instruction : instructions) {
                if (this.isVirtualMove(instruction)) {
                    // Virtual moves (variable = MOV real register) are going to be removed by the allocator,
                    // but we still need the information about which variables are associated to which real
                    // registers, and so we store them. They are generally associated to other instructions
                    // that's why we append them here to the previous instruction (for example Label or Foreign Call)
                    // use these, if this instruction was deleted in the allocator, then they will be missing too.
                    assert previousInstr != null;

                    var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
                    var location = valueMov.getInput();
                    var variable = LIRValueUtil.asVariable(valueMov.getResult());

                    var virtualMove = new RAVInstruction.ValueMove(instruction, variable, location);
                    previousInstr.addVirtualMove(virtualMove);
                    continue; // No need to store virtual move here, it is stored into previous instruction.
                }

                boolean speculative = false;
                if (this.isSpeculativeMove(instruction)) {
                    speculative = true;
                    // Speculative moves are in form ry = MOVE vx, which could be removed if variable
                    // ends up being allocated to the same register as ry. If it was removed
                    // we need to re-add it because it holds important information about where value of
                    // this variable is placed - for label resolution after the label.
                    assert previousInstr != null;

                    var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
                    var variable = LIRValueUtil.asVariable(valueMov.getInput());
                    var register = valueMov.getResult();

                    var virtualMove = new RAVInstruction.ValueMove(instruction, variable, register);
                    previousInstr.addSpeculativeMove(virtualMove);
                }

                var opRAVInstr = new RAVInstruction.Op(instruction);

                instruction.forEachInput(opRAVInstr.uses.copyOriginalProc);
                instruction.forEachOutput(opRAVInstr.dests.copyOriginalProc);
                instruction.forEachTemp(opRAVInstr.temp.copyOriginalProc);
                instruction.forEachAlive(opRAVInstr.alive.copyOriginalProc);
                instruction.forEachState(opRAVInstr.stateValues.copyOriginalProc);
                instruction.forEachState(new InstructionStateProcedure() {
                    @Override
                    public void doState(LIRInstruction instruction, LIRFrameState state) {
                        if (state.topFrame == null) {
                            return;
                        }

                        var frameSlotKinds = state.topFrame.getSlotKinds();
                        if (state.topFrame.values.length != frameSlotKinds.length) {
                            // Test: JVMCIInfopointErrorTest
                            // has defined slotKinds [boolean]
                            // but no values
                            return;
                        }

                        // Haven't found a case where there is multiple frame states on an instruction
                        // so this will work, otherwise appending them would do the job in that case
                        // if we could also get this information about VirtualObjects.
                        opRAVInstr.origFrameSlots = state.topFrame.values;
                        opRAVInstr.frameSlotKinds = state.topFrame.getSlotKinds();
                    }
                });

                preallocMap.put(instruction, opRAVInstr);

                if (!speculative) {
                    previousInstr = opRAVInstr;
                }
            }
        }
    }

    /**
     * Determines if instruction is a virtual move, a virtual move is
     * a move instruction that moves a real register value into a variable,
     * which is something that will always get removed from the final allocated
     * IR.
     * <p>
     * This information is important to the verification process and needs to
     * be part of the Verifier IR.
     * </p>
     *
     * @param instruction LIR instruction we are looking at
     * @return true, if instruction is a virtual move, otherwise false
     */
    protected boolean isVirtualMove(LIRInstruction instruction) {
        if (!instruction.isValueMoveOp()) {
            return false;
        }

        var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
        var input = valueMov.getInput();
        return (ValueUtil.isRegister(input) || LIRValueUtil.isStackSlotValue(input)) && LIRValueUtil.isVariable(valueMov.getResult());
    }

    /**
     * Determines if a move is speculative - it could potentially be
     * removed, but hold important information to the verification process.
     * <p>
     * For example, this happens for a move between two variables and after
     * allocation locations are equal, making the move redundant.
     * </p>
     *
     * @param instruction LIR instruction we are looking at
     * @return true, if instruction is a speculative move, otherwise false
     */
    protected boolean isSpeculativeMove(LIRInstruction instruction) {
        if (!instruction.isValueMoveOp()) {
            return false;
        }

        var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
        var result = valueMov.getResult(); // Result could be variable or register
        return (ValueUtil.isRegister(result) || LIRValueUtil.isVariable(result)) && LIRValueUtil.isVariable(valueMov.getInput());
    }
}
