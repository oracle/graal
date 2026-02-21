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
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verification phase for Register Allocation.
 */
public class RegisterAllocationVerifierPhase extends AllocationPhase {
    public static class Options {
        @Option(help = "Verify that register allocation is indeed, correct", type = OptionType.Debug)
        public static final OptionKey<Boolean> EnableRAVerifier = new OptionKey<>(false);

        @Option(help = "Select which way you want to resolve phi arguments.", type = OptionType.Debug)
        public static final EnumOptionKey<PhiResolution> RAPhiResolution = new EnumOptionKey<>(PhiResolution.FromUsage);

        @Option(help = "Should constants be moved to variables when needed", type = OptionType.Debug)
        public static final OptionKey<Boolean> MoveConstants = new OptionKey<>(true);

        @Option(help = "Substring necessary to be found for method to be verified", type = OptionType.Debug)
        public static final OptionKey<String> RAFilter = new OptionKey<>(null);
    }

    /**
     * Shared phase state.
     */
    private final RegisterAllocationVerifierPhaseState state;

    public RegisterAllocationVerifierPhase(RegisterAllocationVerifierPhaseState state) {
        this.state = state;
    }

    public RegisterAllocationVerifierPhaseState getState() {
        return state;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        if (!state.shouldBeVerified(lirGenRes)) {
            // Filter for compilation unit substring to run verification only on
            // certain methods, cannot use MethodFilter here because I cannot
            // access JavaMethod here.
            return;
        }

        var instructions = getVerifierInstructions(lirGenRes);
        var verifier = new RegisterAllocationVerifier(lirGenRes.getLIR(), instructions, this.state.phiResolution, context.registerAllocationConfig);

        // For timers for time spent in pre-alloc and verification phases look for these metric keys:
        // LIRPhaseTime_PreRegisterAllocationPhase & LIRPhaseTime_RegisterAllocationVerifierPhase
        try {
            verifier.run();
        } catch (RAVException | RAVError e) {
            var lir = lirGenRes.getLIR();
            var debugCtx = lir.getDebug();

            if (debugCtx.isDumpEnabled(DebugContext.VERBOSE_LEVEL)) {
                var debugPath = debugCtx.getDumpPath(".rav.txt", false);

                try {
                    PrintStream output = new PrintStream(debugPath);
                    output.println("Register Allocation Verification failure:");
                    output.println(e.getMessage());
                    output.println();
                    VerifierPrinter.print(output, lir, instructions);
                } catch (FileNotFoundException ignored) {
                }
            }

            throw e;
        }
    }

    /**
     * Process instructions after allocation and create the Verifier IR.
     * Using previously stored instructions from the PreAlloc phase.
     *
     * @param lirGenRes LIR generation result of this method
     * @return Verifier IR
     */
    protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIRGenerationResult lirGenRes) {
        var lir = lirGenRes.getLIR();
        var preallocMap = state.getInstructionMap(lirGenRes);

        Map<RAVariable, RAVInstruction.Op> definedVariables = new EconomicHashMap<>();
        Set<LIRInstruction> presentInstructions = new EconomicHashSet<>();
        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);

            for (var instruction : instructions) {
                presentInstructions.add(instruction);

                var rAVInstr = preallocMap.get(instruction);
                if (rAVInstr instanceof RAVInstruction.Op op) {
                    for (int i = 0; i < op.dests.count; i++) {
                        if (op.dests.orig[i].isVariable()) {
                            var variable = op.dests.orig[i].asVariable();
                            definedVariables.put(variable, op);
                        }
                    }
                }
            }
        }

        BlockMap<List<RAVInstruction.Base>> blockInstructions = new BlockMap<>(lir.getControlFlowGraph());
        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            var instructionList = new LinkedList<RAVInstruction.Base>();

            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            for (var instruction : instructions) {
                var rAVInstr = preallocMap.get(instruction);
                if (rAVInstr == null) {
                    var movOp = this.getRAVMoveInstruction(instruction);
                    if (movOp != null) {
                        instructionList.add(movOp);
                        continue;
                    }

                    // TestCase: TruffleHotSpotCompilation-75393[TruffleSafepointTest.TestRootNode@f25a8e].cfg
                    throw new UnknownInstructionError(instruction, block);
                }

                var opRAVInstr = (RAVInstruction.Op) rAVInstr;

                instruction.forEachInput(opRAVInstr.uses.copyCurrentProc);
                instruction.forEachOutput(opRAVInstr.dests.copyCurrentProc);
                instruction.forEachTemp(opRAVInstr.temp.copyCurrentProc);
                instruction.forEachAlive(opRAVInstr.alive.copyCurrentProc);
                instruction.forEachState(opRAVInstr.stateValues.copyCurrentProc);

                instructionList.add(opRAVInstr);
                var speculativeMoves = opRAVInstr.getSpeculativeMoveList();

                if (!speculativeMoves.isEmpty()) {
                    for (var speculativeMove : speculativeMoves) {
                        if (presentInstructions.contains(speculativeMove.getLIRInstruction())) {
                            continue;
                        }

                        if (!speculativeMove.location.isVariable() && speculativeMove.variableOrConstant.isVariable()) {
                            var variable = speculativeMove.variableOrConstant.asVariable();
                            if (definedVariables.containsKey(variable)) {
                                continue;
                            }
                        }

                        instructionList.add(speculativeMove);
                    }
                }

                var virtualMoves = opRAVInstr.getVirtualMoveList();
                instructionList.addAll(virtualMoves);

                preallocMap.remove(instruction);
            }

            blockInstructions.put(block, instructionList);
        }

        state.deleteInstructionMap(lirGenRes);
        return blockInstructions;
    }

    /**
     * Create Register Verifier Instruction that was created by the Register Allocator.
     * Generally speaking, it's always a move instruction, other ones return null.
     *
     * @param instruction LIR Instruction newly created by Register Allocator
     * @return Spill, Reload, Move or null if instruction is not a move
     */
    protected RAVInstruction.Base getRAVMoveInstruction(LIRInstruction instruction) {
        if (!instruction.isValueMoveOp()) {
            if (instruction.isLoadConstantOp()) {
                var constatLoad = StandardOp.LoadConstantOp.asLoadConstantOp(instruction);
                var constant = constatLoad.getConstant();
                var result = constatLoad.getResult(); // Can be RegisterValue or VirtualStackSlot

                // Constant materialization result
                return new RAVInstruction.ValueMove(instruction, new ConstantValue(result.getValueKind(), constant), result);
            }

            return null;
        }
        var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);

        var input = valueMov.getInput();
        var result = valueMov.getResult();

        if (input instanceof VirtualStackSlot stackSlot && result instanceof RegisterValue reg) {
            return new RAVInstruction.Reload(instruction, reg, stackSlot);
        } else if (result instanceof VirtualStackSlot stackSlot && input instanceof RegisterValue reg) {
            return new RAVInstruction.Spill(instruction, stackSlot, reg);
        } else if (input instanceof RegisterValue reg1 && result instanceof RegisterValue reg2) {
            return new RAVInstruction.RegMove(instruction, reg1, reg2);
        } else if (input instanceof StackSlot stackSlot && result instanceof RegisterValue reg) {
            return new RAVInstruction.Reload(instruction, reg, stackSlot);
        } else if (input instanceof RegisterValue reg && result instanceof StackSlot stackSlot) {
            return new RAVInstruction.Spill(instruction, stackSlot, reg);
        }

        if (input instanceof StackSlot stackSlot1 && result instanceof StackSlot stackSlot2) {
            return new RAVInstruction.StackMove(instruction, stackSlot1, stackSlot2);
        } else if (input instanceof VirtualStackSlot stackSlot1 && result instanceof VirtualStackSlot stackSlot2) {
            return new RAVInstruction.StackMove(instruction, stackSlot1, stackSlot2);
        } else if (input instanceof StackSlot stackSlot1 && result instanceof VirtualStackSlot stackSlot2) {
            return new RAVInstruction.StackMove(instruction, stackSlot1, stackSlot2);
        } else if (input instanceof VirtualStackSlot stackSlot1 && result instanceof StackSlot stackSlot2) {
            return new RAVInstruction.StackMove(instruction, stackSlot1, stackSlot2);
        }

        return null;
    }
}
