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
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.StateProcedure;
import jdk.graal.compiler.lir.alloc.RegisterAllocationPhase;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;
import org.graalvm.collections.Equivalence;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Verification phase for Register Allocation, wraps around
 * the actual allocator and validates that order of spills, reloads
 * and moves is correct and that variables before allocation
 * are actually stored in current locations chosen by the allocator.
 *
 * Needs to extend RegisterAllocationPhase to not throw an exception.
 */
public class RegAllocVerifierPhase extends RegisterAllocationPhase {
    public static class Options {
        @Option(help = "Verify that register allocation is indeed, correct", type = OptionType.Debug)
        public static final OptionKey<Boolean> EnableRAVerifier = new OptionKey<>(false);

        @Option(help = "Verify output of stack allocator with register allocator", type = OptionType.Debug)
        public static final OptionKey<Boolean> VerifyStackAllocator = new OptionKey<>(true);

        @Option(help = "Collect reference map information to verify", type = OptionType.Debug)
        public static final OptionKey<Boolean> CollectReferences = new OptionKey<>(false);
    }

    /**
     * Register allocator we are verifying output of.
     */
    protected RegisterAllocationPhase allocator;

    /**
     * Stack allocator, if not null, being verified
     * simultaneously with reg allocator.
     */
    protected LIRPhase<AllocationContext> stackSlotAllocator;

    private static final TimerKey PreallocTimer = DebugContext.timer("RAV_PreAlloc");
    private static final TimerKey VerifierTimer = DebugContext.timer("RAV_Verification");

    public RegAllocVerifierPhase(RegisterAllocationPhase allocator, LIRPhase<AllocationContext> stackSlotAllocator) {
        this.allocator = allocator;
        this.stackSlotAllocator = stackSlotAllocator;
    }

    /**
     * Get allocator that is being verified.
     *
     * @return Register allocator
     */
    public RegisterAllocationPhase getAllocator() {
        return allocator;
    }

    /**
     * Set allocator to verify, used by tests.
     *
     * @param allocator Register allocator
     */
    public void setAllocator(RegisterAllocationPhase allocator) {
        this.allocator = allocator;
    }

    /**
     * Get stack allocator being verified with register allocator if not null.
     *
     * @return Stack allocator or null
     */
    public LIRPhase<AllocationContext> getStackSlotAllocator() {
        return stackSlotAllocator;
    }

    /**
     * Set stack allocator, used by tests.
     *
     * @param stackSlotAllocator Stack allocator
     */
    public void setStackSlotAllocator(LIRPhase<AllocationContext> stackSlotAllocator) {
        this.stackSlotAllocator = stackSlotAllocator;
    }

    @SuppressWarnings("try")
    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        assert allocator != null : "No register allocator present for verification";

        var lir = lirGenRes.getLIR();

        Map<LIRInstruction, RAVInstruction.Base> preAllocMap;
        try (DebugCloseable t = PreallocTimer.start(lir.getDebug())) {
            preAllocMap = saveInstructionsPreAlloc(lir);
        }

        boolean verifyStackAlloc = Options.VerifyStackAllocator.getValue(lir.getOptions());

        allocator.apply(target, lirGenRes, context);
        if (stackSlotAllocator != null && verifyStackAlloc) {
            stackSlotAllocator.apply(target, lirGenRes, context);

            if (Options.CollectReferences.getValue(lir.getOptions())) {
                // Frame map is only built after stack allocator has run
                new ReferencesBuilder().build(lir, lirGenRes.getFrameMap(), preAllocMap);
            }
        }

        try (DebugCloseable t = VerifierTimer.start(lir.getDebug())) {
            verifyAllocation(lir, preAllocMap, context);
        }

        if (stackSlotAllocator != null && !verifyStackAlloc) {
            stackSlotAllocator.apply(target, lirGenRes, context);
        }
    }

    /**
     * Save instruction before allocation to keep track of symbols used in instructions.
     *
     * @param lir LIR
     * @return Map of LIRInstruction to RAVInstruction that keeps symbols
     */
    protected Map<LIRInstruction, RAVInstruction.Base> saveInstructionsPreAlloc(LIR lir) {
        Map<LIRInstruction, RAVInstruction.Base> preallocMap = new EconomicHashMap<>(Equivalence.IDENTITY);
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
                instruction.forEachState(new StateProcedure() {
                    @Override
                    public void doState(LIRFrameState state) {
                        if (state.topFrame == null) {
                            return;
                        }

                        BytecodeFrame frame = state.topFrame;
                        while (frame != null) {
                            var kinds = frame.getSlotKinds();
                            if (kinds.length != frame.numLocals + frame.numStack) {
                                frame = frame.caller();
                                continue;
                            }

                            var values = frame.values.clone();
                            opRAVInstr.bcFrames.add(new RAVInstruction.StateValuePair(values, kinds));
                            frame = frame.caller();
                        }
                    }
                });

                preallocMap.put(instruction, opRAVInstr);

                if (!speculative) {
                    previousInstr = opRAVInstr;
                }
            }
        }
        return preallocMap;
    }

    /**
     * Use information before allocation to verify output of allocator(s).
     *
     * @param lir         LIR
     * @param preallocMap Map of instructions before allocation
     * @param context     Allocation context
     */
    protected void verifyAllocation(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext context) {
        var instructions = getVerifierInstructions(lir, preallocMap, context);
        var verifier = new RegAllocVerifier(lir, instructions, getRegisterAllocationConfig(context));

        try {
            verifier.run();
        } catch (RAVException | RAVError e) {
            var debugCtx = lir.getDebug();

            if (debugCtx.isDumpEnabled(DebugContext.VERBOSE_LEVEL)) {
                var debugPath = debugCtx.getDumpPath(".rav.txt", false);

                try {
                    PrintStream output = new PrintStream(debugPath);
                    output.println("Register Allocation Verification failure:");
                    output.println(e.getMessage());
                    output.println();
                    VerifierPrinter.printAligned(output, lir, instructions);
                } catch (FileNotFoundException ignored) {
                }

                // Keep original message with class path prefix and add debug path info
                // to the end so it's easier to access.
                throw new RAVException(e + ", see debug file " + debugPath, e);
            }

            throw e;
        }
    }

    /**
     * Retrieve RegisterAllocationConfig from context, this function
     * is here so it can be overwritten by a test when it needs to
     * change the config.
     *
     * @param context Current phase context
     * @return Instance of RegisterAllocationConfig
     */
    protected RegisterAllocationConfig getRegisterAllocationConfig(AllocationContext context) {
        return context.registerAllocationConfig;
    }

    /**
     * Process instructions after allocation and create the Verifier IR.
     * Using previously stored instructions from the PreAlloc phase.
     *
     * @param lir         LIR
     * @param preallocMap Pre-allocation map to keep track of virtual values
     * @return Verifier IR
     */
    protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext context) {
        Map<RAVariable, RAVInstruction.Op> definedVariables = new EconomicHashMap<>();
        var presentInstructions = preprocessAllocatedInstructions(lir, preallocMap, definedVariables);

        BlockMap<List<RAVInstruction.Base>> blockInstructions = new BlockMap<>(lir.getControlFlowGraph());
        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            var instructionList = new ArrayList<RAVInstruction.Base>();

            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            for (var instruction : instructions) {
                var rAVInstr = preallocMap.get(instruction);
                if (rAVInstr == null) {
                    var movOp = this.getRAVMoveInstruction(instruction);
                    if (movOp != null) {
                        instructionList.add(movOp);
                        continue;
                    }

                    throw new UnknownInstructionError(instruction, block);
                }

                var opRAVInstr = (RAVInstruction.Op) rAVInstr;

                instruction.forEachInput(opRAVInstr.uses.copyCurrentProc);
                instruction.forEachOutput(opRAVInstr.dests.copyCurrentProc);
                instruction.forEachTemp(opRAVInstr.temp.copyCurrentProc);
                instruction.forEachAlive(opRAVInstr.alive.copyCurrentProc);
                instruction.forEachState(opRAVInstr.stateValues.copyCurrentProc);
                instruction.forEachState(new StateProcedure() {
                    @Override
                    public void doState(LIRFrameState state) {
                        if (state.topFrame == null) {
                            return;
                        }

                        int i = 0;
                        BytecodeFrame frame = state.topFrame;
                        while (frame != null) {
                            if (i >= opRAVInstr.bcFrames.size()) {
                                break;
                            }

                            opRAVInstr.bcFrames.get(i).setCurr(frame.values);
                            frame = frame.caller();
                            i++;
                        }
                    }
                });

                instructionList.add(opRAVInstr);
                var speculativeMoves = opRAVInstr.getSpeculativeMoveList();
                if (!speculativeMoves.isEmpty()) {
                    var readdedMoves = handleSpeculativeMoves(opRAVInstr, presentInstructions, definedVariables);
                    instructionList.addAll(readdedMoves);
                }

                var virtualMoves = opRAVInstr.getVirtualMoveList();
                for (var virtMove : virtualMoves) {
                    instructionList.add(fixOldValueMove(virtMove));
                }
            }

            blockInstructions.put(block, instructionList);
        }

        return blockInstructions;
    }

    /**
     * Fixes value move created before any allocation has happened,
     * we mainly care about stack allocator running - old value move
     * still keeps the virtual stack slot, but the underlying lir
     * instruction already has an allocated concrete stack slot, so
     * for verification to work correctly, it needs to be changed.
     *
     * @param valueMove Old value move created before any (stack) allocation
     * @return Fixed value move
     */
    protected RAVInstruction.ValueMove fixOldValueMove(RAVInstruction.ValueMove valueMove) {
        if (!LIRValueUtil.isVirtualStackSlot(valueMove.location.getValue())) {
            return valueMove;
        }

        Value moveLocation;
        if (StandardOp.LoadConstantOp.isLoadConstantOp(valueMove.lirInstruction)) {
            var loadConstOp = StandardOp.LoadConstantOp.asLoadConstantOp(valueMove.lirInstruction);
            moveLocation = loadConstOp.getResult();
        } else {
            var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(valueMove.lirInstruction);
            moveLocation = valueMov.getInput();
        }

        if (ValueUtil.isStackSlot(moveLocation)) {
            // Change vstack to allocated stack slot, because it was changed
            valueMove.location.value = moveLocation;
        }

        return valueMove;
    }

    /**
     * Iterate over every instruction after allocation save it to a set
     * to see if speculative moves should be re-added or not and also
     * track if variable has been defined before.
     *
     * @param lir              LIR
     * @param preallocMap      Map of instructions before allocation
     * @param definedVariables Output map, set defined variables here
     * @return Set of instructions present after allocation
     */
    protected Set<LIRInstruction> preprocessAllocatedInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, Map<RAVariable, RAVInstruction.Op> definedVariables) {
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
        return presentInstructions;
    }

    /**
     * Handle speculative moves that should be re-added back to the IR
     * to keep verification information in-tact, based on instructions
     * present after allocation and variables defined by them.
     *
     * @param op                  Op that holds these speculatives
     * @param presentInstructions Instructions present in the IR in form of a Map
     * @param definedVariables    Variables already defined
     * @return List of speculative moves that need to be added bakc
     */
    protected List<RAVInstruction.ValueMove> handleSpeculativeMoves(RAVInstruction.Op op, Set<LIRInstruction> presentInstructions, Map<RAVariable, RAVInstruction.Op> definedVariables) {
        List<RAVInstruction.ValueMove> toAdd = new ArrayList<>();
        for (var speculativeMove : op.getSpeculativeMoveList()) {
            if (presentInstructions.contains(speculativeMove.getLIRInstruction())) {
                continue;
            }

            if (!speculativeMove.getLocation().isVariable() && speculativeMove.variableOrConstant.isVariable()) {
                var variable = speculativeMove.variableOrConstant.asVariable();
                var variableDefInstr = definedVariables.get(variable);
                if (variableDefInstr == null) {
                    continue;
                }

                if (variableDefInstr.lirInstruction instanceof StandardOp.LabelOp && variableDefInstr.lirInstruction == op.lirInstruction) {
                    for (int i = 0; i < op.dests.count; i++) {
                        var orig = op.dests.orig[i];
                        if (!orig.isVariable() || op.dests.curr[i] != null) {
                            continue;
                        }

                        // Add speculative instruction location back to the label
                        // where it's missing, only when speculative move is part
                        // of said label.
                        op.dests.curr[i] = speculativeMove.getLocation();
                    }
                }

                continue;
            }

            toAdd.add(fixOldValueMove(speculativeMove));
        }
        return toAdd;
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

        if (LIRValueUtil.isStackSlotValue(input) && ValueUtil.isRegister(result)) {
            return new RAVInstruction.Reload(instruction, ValueUtil.asRegisterValue(result), input);
        } else if (LIRValueUtil.isStackSlotValue(result) && ValueUtil.isRegister(input)) {
            return new RAVInstruction.Spill(instruction, result, ValueUtil.asRegisterValue(input));
        } else if (ValueUtil.isRegister(input) && ValueUtil.isRegister(result)) {
            return new RAVInstruction.RegMove(instruction, ValueUtil.asRegisterValue(input), ValueUtil.asRegisterValue(result));
        } else if (LIRValueUtil.isStackSlotValue(input) && LIRValueUtil.isStackSlotValue(result)) {
            if (valueMov instanceof AMD64Move.AMD64StackMove stackMove) {
                // Cannot access the isScratchAlwaysZero to see if backup slot is used
                return new RAVInstruction.StackMove(instruction, input, result, stackMove.getBackupSlot());
            }

            return new RAVInstruction.StackMove(instruction, input, result);
        }

        return null;
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
