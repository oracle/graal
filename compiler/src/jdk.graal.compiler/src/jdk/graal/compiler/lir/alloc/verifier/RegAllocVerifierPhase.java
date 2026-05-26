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

import jdk.graal.compiler.core.common.LIRKind;
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
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.alloc.RegisterAllocationPhase;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.RAVException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.RAVFailedVerificationException;
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
 * Verification phase for Register Allocation, wraps around the actual allocator and validates that
 * the order of spills, reloads, and moves is correct and that variables before allocation are
 * actually stored in current locations chosen by the allocator.
 *
 * <p>
 * Needs to extend the RegisterAllocationPhase to not throw an exception.
 * </p>
 */
@SuppressWarnings("try")
public class RegAllocVerifierPhase extends RegisterAllocationPhase {
    public static class Options {
        // @formatter:off
        @Option(help = "Verify that register allocation is indeed, correct", type = OptionType.Debug)
        public static final OptionKey<Boolean> EnableRAVerifier = new OptionKey<>(true);

        @Option(help = "Verify output of stack allocator with register allocator", type = OptionType.Debug)
        public static final OptionKey<Boolean> VerifyStackAllocator = new OptionKey<>(true);

        @Option(help = "Collect reference map information to verify", type = OptionType.Debug)
        public static final OptionKey<Boolean> CollectReferences = new OptionKey<>(true);

        @Option(help = "Fail on first verification failure", type = OptionType.Debug)
        public static final OptionKey<Boolean> RAVFailOnFirst = new OptionKey<>(true);

        /**
         * Check for {@link RegisterAllocationPhase#getNeverSpillConstants() neverSpillConstant} setting
         */
        @Option(help = "Verify neverSpillConstants is respected", type = OptionType.Debug)
        public static final OptionKey<Boolean> CheckNeverSpillConstants = new OptionKey<>(false);
        // @formatter:on
    }

    /**
     * Register allocator we are verifying output of.
     */
    protected RegisterAllocationPhase allocator;

    /**
     * Stack allocator, if not null, being verified simultaneously with reg allocator.
     */
    protected LIRPhase<AllocationContext> stackSlotAllocator;

    private static final TimerKey PreallocTimer = DebugContext.timer("RAV_PreAlloc");
    private static final TimerKey VerifierTimer = DebugContext.timer("RAV_Verification");
    private static final TimerKey PhiResolverTimer = DebugContext.timer("RAV_PhiResolver");
    private static final TimerKey AllocationTimer = DebugContext.timer("RAV_Allocation");

    public RegAllocVerifierPhase(RegisterAllocationPhase allocator, LIRPhase<AllocationContext> stackSlotAllocator) {
        this.allocator = allocator;
        this.stackSlotAllocator = stackSlotAllocator;
    }

    @Override
    public void setNeverSpillConstants(boolean neverSpillConstants) {
        allocator.setNeverSpillConstants(neverSpillConstants);
    }

    @Override
    public boolean getNeverSpillConstants() {
        return allocator.getNeverSpillConstants();
    }

    /**
     * Get the allocator that is being verified.
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

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        assert allocator != null : "No register allocator present for verification";

        var lir = lirGenRes.getLIR();

        Map<LIRInstruction, RAVInstruction.Base> preAllocMap;
        try (DebugCloseable t = PreallocTimer.start(lir.getDebug())) {
            preAllocMap = saveInstructionsPreAlloc(lir);
        }

        boolean verifyStackAlloc = Options.VerifyStackAllocator.getValue(lir.getOptions());

        try (DebugCloseable t = AllocationTimer.start(lir.getDebug())) {
            allocator.apply(target, lirGenRes, context);
            if (stackSlotAllocator != null && verifyStackAlloc) {
                stackSlotAllocator.apply(target, lirGenRes, context);

                if (Options.CollectReferences.getValue(lir.getOptions())) {
                    // Frame map is only built after stack allocator has run
                    new ReferencesBuilder().build(lir, lirGenRes.getFrameMap(), preAllocMap);
                }
            }
        }

        verifyAllocation(lir, preAllocMap, context);

        if (stackSlotAllocator != null && !verifyStackAlloc) {
            stackSlotAllocator.apply(target, lirGenRes, context);
        }
    }

    /**
     * Which operation generated register as a symbol and the index of it, in the output array.
     */
    class OutValue {
        int idx;
        RAVInstruction.Op op;

        OutValue(int idx, RAVInstruction.Op op) {
            this.idx = idx;
            this.op = op;
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

            Map<RAValue, RAValue> inputMap = new EconomicHashMap<>();
            Map<RAValue, OutValue> outputMap = new EconomicHashMap<>();

            RAVInstruction.Base previousInstr = null;
            for (var instruction : instructions) {
                boolean outputSpeculative = false;
                boolean inputSpeculative = false;
                if (instruction.isValueMoveOp()) {
                    var valueMov = StandardOp.ValueMoveOp.asValueMoveOp(instruction);
                    var input = valueMov.getInput();
                    var result = valueMov.getResult();

                    if (LIRValueUtil.isVariable(result) && isLocation(input)) {
                        /*
                         * Speculative move that outputs a new variable from a concrete location. We
                         * assign the variable to the instruction that uses the same register as
                         * output (with no symbol). For example, [rsi] = LABEL followed by v0 = MOVE
                         * rsi, will get mapped to [v0 -> rsi] = LABEL, internally.
                         */
                        var locValue = RAValue.create(input);
                        var outputValue = outputMap.get(locValue);
                        if (outputValue != null) {
                            outputValue.op.dests.orig[outputValue.idx] = RAValue.create(result);
                            outputMap.remove(locValue);
                            continue;
                        }

                        outputSpeculative = true;
                    }

                    if (LIRValueUtil.isVariable(input)) {
                        /*
                         * Speculative move that has an existing variable as its input, output can
                         * be either a variable or a concrete location.
                         *
                         * If a concrete location is input, then it can be used to assign a symbol
                         * to an instruction that has none for this register - typically function
                         * calls.
                         *
                         * If it is a variable to variable move, then we save it, in case it was
                         * coalesced.
                         *
                         * For example, rsi = MOVE v1, followed by CALL [rsi], will get mapped to
                         * CALL [rsi -> v1].
                         */
                        inputSpeculative = true;

                        Variable variable = LIRValueUtil.asVariable(input);
                        if (isLocation(result)) {
                            var regKind = result.getValueKind();
                            var varKind = input.getValueKind();
                            if (!regKind.equals(varKind)) {
                                variable = new Variable(regKind, variable.index);
                            }

                            // What if type cast information missing?
                            inputMap.put(RAValue.create(result), RAValue.create(variable));
                        } else {
                            var virtualMove = new RAVInstruction.CoalescedMove(instruction, result, variable);

                            assert previousInstr != null;
                            previousInstr.addSpeculativeMove(virtualMove);
                        }
                    }
                }

                if (instruction.isLoadConstantOp()) {
                    var loadConstOp = StandardOp.LoadConstantOp.asLoadConstantOp(instruction);
                    var location = RAValue.create(loadConstOp.getResult());
                    if (location.isLocation()) {
                        // Speculative input move that sets variable to concrete location.
                        var constant = new ConstantValue(loadConstOp.getResult().getValueKind(LIRKind.class), loadConstOp.getConstant());
                        inputMap.put(location, RAValue.create(constant));
                    }
                }

                var op = new RAVInstruction.Op(instruction);

                instruction.forEachInput(op.uses.copyOriginalProc);
                instruction.forEachOutput(op.dests.copyOriginalProc);
                instruction.forEachTemp(op.temp.copyOriginalProc);
                instruction.forEachAlive(op.alive.copyOriginalProc);
                instruction.forEachState(op.stateValues.copyOriginalProc);
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
                            op.bcFrames.add(new RAVInstruction.StateValuePair(values, kinds));
                            frame = frame.caller();
                        }

                        if (!state.hasDebugInfo()) {
                            /*
                             * Debug info has to be initialized to get access to virtual objects.
                             */
                            state.initDebugInfo();
                        }

                        var virtObj = state.debugInfo().getVirtualObjectMapping();
                        if (virtObj != null) {
                            for (var obj : virtObj) {
                                var values = obj.getValues().clone();
                                op.virtualObj.add(new RAVInstruction.StateValuePair(values, obj.getSlotKinds()));
                            }
                        }
                    }
                });

                changeOriginalInputToVariable(inputMap, op.uses, outputSpeculative);
                changeOriginalInputToVariable(inputMap, op.alive, outputSpeculative);

                preallocMap.put(instruction, op);

                if (!inputSpeculative) {
                    for (int i = 0; i < op.dests.count; i++) {
                        var orig = op.dests.orig[i];
                        if (orig.isLocation()) {
                            outputMap.put(orig, new OutValue(i, op));
                        }
                    }

                    previousInstr = op;
                }
            }
        }

        return preallocMap;
    }

    /**
     * Changes input of an instruction that is a concrete location (before allocation) to
     * variable/constant from input mapping, that is created by speculative moves before this
     * instruction.
     *
     * @param inputMap Map of location to variable assigned from speculative input move
     * @param values Input values
     * @param outputSpeculative If this input is from a speculative instruction that creates a new
     *            variable
     */
    protected void changeOriginalInputToVariable(Map<RAValue, RAValue> inputMap, RAVInstruction.ValueArrayPair values, boolean outputSpeculative) {
        for (int i = 0; i < values.count; i++) {
            var orig = values.orig[i];
            if (!orig.isLocation()) {
                continue;
            }

            var inputVar = inputMap.get(orig);
            if (inputVar == null) {
                continue;
            }

            /*
             * When substituting a symbol, kind has to match the previous symbol for the
             * verification to pass. A move between the definition and usage is expected that
             * performs the cast, handled in BlockVerifierState.updateWithLocationMove.
             */
            if (!RAValue.kindsEqual(inputVar, orig)) {
                inputVar = RAValue.cast(orig, inputVar);
            }

            values.orig[i] = inputVar;

            if (!outputSpeculative) {
                // Do not remove from input map if this operation could be removed.
                // This value can be re-used.
                inputMap.remove(orig);
            }
        }
    }

    /**
     * Normalizes the values in this input array pair to remove any variables that can be
     * substituted for constants or variables that are aliased by different ones.
     *
     * <p>
     * We do this to make the internal verifier IR more consistent because sometimes a constant
     * value can be used as an input, while at other times it's substituted behind a variable, which
     * can also re-materialize later.
     * </p>
     *
     * <p>
     * As for variable aliasing, sometimes a move is coalesced but the register allocator, but the
     * mentioned of the alias variable still remain; we make sure to remove them.
     * </p>
     *
     * @param values Input array pair
     * @param synonymMap Variable synonym map, used to find the first concrete variable.
     * @param constantMap Constant map, map variables to their respective constant values.
     */
    protected void normalizeValues(RAVInstruction.ValueArrayPair values, VariableSynonymMap synonymMap, Map<RAVariable, RAValue> constantMap) {
        for (int i = 0; i < values.count; i++) {
            var value = values.orig[i];
            if (!value.isVariable()) {
                continue;
            }

            RAValue substitute;
            var variable = value.asVariable();
            if (constantMap.containsKey(variable)) {
                substitute = constantMap.get(variable);
            } else if (synonymMap.isAliased(variable)) {
                var synonym = synonymMap.find(variable);
                substitute = constantMap.getOrDefault(synonym, synonym);
            } else {
                continue;
            }

            if (!values.orig[i].getLIRKind().equals(substitute.getLIRKind())) {
                /*
                 * We cast the kind here, because we expect preceeding move to cast the value inside
                 * the state, for example, rax|QWORD = MOVE rax|QWORD[.] size: QWORD should cast the
                 * contents of rax to be QWORD, to be consistent with this, we need to change the
                 * kind here.
                 */
                substitute = RAValue.cast(substitute, values.orig[i]);
            }

            values.orig[i] = substitute;
        }
    }

    /**
     * Use information before allocation to verify the output of allocator(s).
     *
     * @param lir LIR
     * @param preallocMap Map of instructions before allocation
     * @param context Allocation context
     */
    protected void verifyAllocation(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext context) {
        var instructions = getVerifierInstructions(lir, preallocMap, context);
        var phiResolver = new FromUsageResolverGlobal(lir, instructions);
        var verifier = new RegAllocVerifier(lir, instructions, getRegisterAllocationConfig(context), allocator);

        boolean failOnFirst = Options.RAVFailOnFirst.getValue(lir.getOptions());

        try (DebugCloseable t = PhiResolverTimer.start(lir.getDebug())) {
            phiResolver.resolvePhiFromUsage();
        }

        try (DebugCloseable t = VerifierTimer.start(lir.getDebug())) {
            verifier.run(failOnFirst);
        } catch (RAVException e) {
            var debugCtx = lir.getDebug();

            if (debugCtx.isDumpEnabled(DebugContext.VERBOSE_LEVEL)) {
                var debugPath = debugCtx.getDumpPath(".rav.txt", false);

                try (PrintStream output = new PrintStream(debugPath)) {
                    verifier.getPrinter(output).printIRWithException(e);
                } catch (FileNotFoundException ignored) {
                }

                // Keep original message with class path prefix and add debug path info
                // to the end so it's easier to access.
                throw new RAVException(e + ", see debug file " + debugPath, e);
            }

            throw e;
        } catch (RAVFailedVerificationException e) {
            var debugCtx = lir.getDebug();

            if (debugCtx.isDumpEnabled(DebugContext.VERBOSE_LEVEL)) {
                var debugPath = debugCtx.getDumpPath(".rav.txt", false);

                try (PrintStream output = new PrintStream(debugPath)) {
                    verifier.getPrinter(output).printIRWithMultiExceptions(e);
                } catch (FileNotFoundException ignored) {
                }
            }

            throw e;
        }
    }

    /**
     * Retrieve RegisterAllocationConfig from context, this function is here, so it can be
     * overwritten by a test when it needs to change the config.
     *
     * @param context Current phase context
     * @return Instance of RegisterAllocationConfig
     */
    protected RegisterAllocationConfig getRegisterAllocationConfig(AllocationContext context) {
        return context.registerAllocationConfig;
    }

    /**
     * Process instructions after allocation and create the Verifier IR. Using previously stored
     * instructions from the PreAlloc phase.
     *
     * @param lir LIR
     * @param preallocMap Pre-allocation map to keep track of virtual values
     * @param ctx Context of the allocation, kept here so tests can access this value here
     * @return Verifier IR
     */
    protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext ctx) {
        VariableSynonymMap synonymMap = new VariableSynonymMap();
        Map<RAVariable, RAValue> constMap = new EconomicHashMap<>();

        var presentInstructions = getPresentInstructionSet(lir);
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

                        var virtObj = state.debugInfo().getVirtualObjectMapping();
                        if (virtObj != null) {
                            for (int j = 0; j < virtObj.length; j++) {
                                var obj = virtObj[j];
                                opRAVInstr.virtualObj.get(j).setCurr(obj.getValues());
                            }
                        }
                    }
                });

                normalizeValues(opRAVInstr.uses, synonymMap, constMap);
                normalizeValues(opRAVInstr.alive, synonymMap, constMap);

                if (instruction.isLoadConstantOp()) {
                    var loadConstOp = StandardOp.LoadConstantOp.asLoadConstantOp(instruction);
                    var location = loadConstOp.getResult();

                    ConstantValue constant = new ConstantValue(location.getValueKind(LIRKind.class), loadConstOp.getConstant());

                    var orig = opRAVInstr.dests.orig[0];
                    if (orig.isVariable()) {
                        var variable = orig.asVariable();
                        constMap.put(variable, new RAVConstant(constant, loadConstOp.canRematerializeToStack()));
                    }

                    var curr = opRAVInstr.dests.curr[0];
                    boolean validateRegs = !orig.equals(curr); // Only validate actual changes
                    var valMove = new RAVInstruction.ValueMove(instruction, constant, location, validateRegs);

                    instructionList.add(valMove);
                } else {
                    instructionList.add(opRAVInstr);
                }

                var speculativeMoves = opRAVInstr.getSpeculativeMoveList();
                for (var move : speculativeMoves) {
                    if (presentInstructions.contains(move.lirInstruction)) {
                        continue;
                    }

                    synonymMap.addSynonym(move.srcVariable, move.dstVariable);
                }
            }

            blockInstructions.put(block, instructionList);
        }

        return blockInstructions;
    }

    /**
     * Iterate over every instruction after the allocation, save it to a set to see if speculative
     * moves should be re-added or not and also track if a variable has been defined before.
     *
     * @param lir LIR
     * @return Set of instructions present after allocation
     */
    protected Set<LIRInstruction> getPresentInstructionSet(LIR lir) {
        Set<LIRInstruction> presentInstructions = new EconomicHashSet<>(Equivalence.IDENTITY);
        for (var blockId : lir.getBlocks()) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);

            presentInstructions.addAll(instructions);
        }
        return presentInstructions;
    }

    /**
     * Create Register Verifier Instruction that was created by the {@link RegisterAllocationPhase
     * register allocator}. Generally speaking, it's always a move instruction; other ones return
     * null.
     *
     * @param instruction LIR Instruction newly created by {@link RegisterAllocationPhase register
     *            allocator}
     * @return Spill, Reload, Move or null if instruction is not a move
     */
    protected RAVInstruction.Base getRAVMoveInstruction(LIRInstruction instruction) {
        if (!instruction.isValueMoveOp()) {
            if (instruction.isLoadConstantOp()) {
                var constatLoad = StandardOp.LoadConstantOp.asLoadConstantOp(instruction);
                var constant = constatLoad.getConstant();
                var result = constatLoad.getResult(); // Concrete location

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
                // Cannot access the isScratchAlwaysZero to see if a backup slot is used.
                // We use the backup slot to set the allocation state to unknown.
                return new RAVInstruction.StackMove(instruction, input, result, stackMove.getBackupSlot());
            }

            return new RAVInstruction.StackMove(instruction, input, result);
        }

        return null;
    }

    protected boolean isLocation(Value value) {
        return LIRValueUtil.isStackSlotValue(value) || ValueUtil.isRegister(value);
    }
}
