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
package jdk.graal.compiler.core.test;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.alloc.RegisterAllocationPhase;
import jdk.graal.compiler.lir.alloc.verifier.UnknownAllocationState;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.AliveConstraintViolationException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.CalleeSavedRegisterNotRetrievedException;
import jdk.graal.compiler.lir.alloc.verifier.ConflictedAllocationState;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.ConstantRematerializedToStackException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.InvalidRegisterUsedException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.KindsMismatchException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.MissingLocationException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.MissingReferenceException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.OperandFlagMismatchException;
import jdk.graal.compiler.lir.alloc.verifier.RAVConstant;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.RAVException;
import jdk.graal.compiler.lir.alloc.verifier.RAVInstruction;
import jdk.graal.compiler.lir.alloc.verifier.RAValue;
import jdk.graal.compiler.lir.alloc.verifier.RAVariable;
import jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifierPhase;
import jdk.graal.compiler.lir.alloc.verifier.ValueAllocationState;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.ValueNotInRegisterException;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.framemap.SimpleVirtualStackSlot;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ValueKind;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test cases for {@link RegAllocVerifierPhase}, these work by injecting errors into incoming
 * snippets and detecting that the exact exception is thrown.
 *
 * <p>
 * The test case first runs the {@link RegAllocVerifierPhase} on the same exact snippet, with the
 * original {@link RegisterAllocationPhase register allocator} to make sure there are no issues with
 * it. Then, the {@link RAVPhaseWrapper tainted verifier phase} is used, that injects a fault into
 * verifier's instructions. {@link RegisterAllocationPhase Verifier phase} runs, and we expect it to
 * throw an exception, this is then tested with assertions, and we look at its contents.
 * </p>
 *
 * <p>
 * Most of these tainted phases find the first candidate instruction and corrupt it. Some look for
 * control-flow patterns to corrupt. Few change the {@link RegisterAllocationConfig register
 * allocator config} to see if generated code adheres to it, while allocator runs with the correct
 * config. Sometimes a scenario is easier to simulate directly with overwritten instructions.
 * </p>
 */
public class RegAllocVerifierTest extends GraalCompilerTest {
    /**
     * Should the valid set of compiler phase suites be used?
     */
    boolean validSuites = true;

    /**
     * Exception thrown during the verification process.
     */
    RAVException exception;

    /**
     * Phase that causes RAVException to be thrown, by modifying LIR or Verifier State.
     */
    RAVPhaseWrapper phase;

    /**
     * Disable inlining to force function calls during verification, off by default.
     */
    boolean disableInlining = false;

    /**
     * Base for tainted verifier phases, with helper functions. The
     * {@link RAVPhaseWrapper#modifyVerifierInstructions} is where modifications are performed for
     * detection.
     */
    abstract class RAVPhaseWrapper extends RegAllocVerifierPhase {
        @FunctionalInterface
        interface InstructionScanFunction {
            boolean apply(BasicBlock<?> block, RAVInstruction.Base instruction);
        }

        @FunctionalInterface
        interface OpScanFunction {
            boolean apply(BasicBlock<?> block, RAVInstruction.Op op);
        }

        RAVPhaseWrapper() {
            super(null, null);
        }

        @Override
        protected final BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext context) {
            var instructions = super.getVerifierInstructions(lir, preallocMap, context);
            modifyVerifierInstructions(lir, instructions, context);
            return instructions;
        }

        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            // Overwritten when a modification is inserted into the resulting verifier instructions
            // Does not always have to be used.
        }

        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
            try {
                super.run(target, lirGenRes, context);
            } catch (RAVException e) {
                exception = e;
            }
        }

        /**
         * Last used variable id, incremented whenever {@link RAVPhaseWrapper#createNewVariable} is
         * called.
         */
        private int lastVariableId = -1;

        protected RAVariable createNewVariable(LIR lir, ValueKind<?> kind) {
            if (lastVariableId == -1) {
                lastVariableId = lir.numVariables();
            }

            var newVariable = new Variable(kind, ++lastVariableId);
            return (RAVariable) RAValue.create(newVariable);
        }

        protected RAVariable createNewVariable(LIR lir) {
            return createNewVariable(lir, ValueKind.Illegal);
        }

        protected RAVInstruction.Op createSymbolUsage(RAValue symbol, RAValue location) {
            var usage = new RAVInstruction.Op(new StandardOp.NoOp(null, 0));

            usage.uses = new RAVInstruction.ValueArrayPair(1);
            usage.uses.curr[0] = location;
            usage.uses.orig[0] = symbol;
            usage.uses.operandFlags = new ArrayList<>(List.of(EnumSet.of(REG, STACK)));

            return usage;
        }

        protected RAVInstruction.Base createSymbolSpawnOp(RAValue symbol, RAValue location) {
            var lirInstruction = new StandardOp.NoOp(null, 0);

            if (symbol.isConstant()) {
                return new RAVInstruction.ValueMove(lirInstruction, symbol.asConstant().getConstantValue(), location.getValue());
            }

            var op = new RAVInstruction.Op(lirInstruction);

            op.dests = new RAVInstruction.ValueArrayPair(1);
            op.dests.curr[0] = location;
            op.dests.orig[0] = symbol;
            op.dests.operandFlags = new ArrayList<>(List.of(EnumSet.of(REG, STACK)));

            return op;
        }

        /**
         * Helper class for finding a fresh new location.
         */
        static class UnusedValueTracker {
            int highestVstackId;
            Set<Register> allocatableRegs;

            UnusedValueTracker(RegisterAllocationConfig regAllocConfig) {
                highestVstackId = -1;
                allocatableRegs = new EconomicHashSet<>(regAllocConfig.getAllocatableRegisters());
            }

            void handleInstruction(RAVInstruction.Base instruction) {
                switch (instruction) {
                    case RAVInstruction.Op op -> {
                        for (int i = 0; i < op.dests.count; i++) {
                            var curr = op.dests.curr[i];
                            if (curr == null) {
                                continue;
                            }

                            handleValue(curr);
                        }
                    }
                    case RAVInstruction.LocationMove move -> {
                        handleValue(move.to);
                    }
                    case RAVInstruction.ValueMove move -> {
                        handleValue(move.getLocation());
                    }
                    default -> {
                    }
                }
            }

            void handleValue(RAValue value) {
                if (value.isRegister()) {
                    allocatableRegs.remove(value.asRegister().getRegister());
                } else if (LIRValueUtil.isVirtualStackSlot(value.getValue())) {
                    var vstack = LIRValueUtil.asVirtualStackSlot(value.getValue());
                    highestVstackId = Math.max(vstack.getId(), highestVstackId);
                }
            }

            RAValue getResult(ValueKind<?> kind) {
                if (allocatableRegs.isEmpty()) {
                    return RAValue.create(new SimpleVirtualStackSlot(highestVstackId + 1, kind));
                }

                return RAValue.create(allocatableRegs.stream().iterator().next().asValue(kind));
            }
        }

        /**
         * Get unused value by other instructions. If a {@link Register} from
         * {@link RegisterConfig#getAllocatableRegisters() allocatable registers} is not used, then
         * it's prefered. Otherwise, a new {@link jdk.graal.compiler.lir.VirtualStackSlot}, with the
         * highest id will be used.
         *
         * @param lir LIR
         * @param instructions Verifier instructions
         * @param context Allocation context
         * @return New, unused value
         */
        protected RAValue getUnusedValue(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            return getUnusedValue(lir, instructions, context, ValueKind.Illegal);
        }

        protected RAValue getUnusedValue(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context, ValueKind<?> kind) {
            var tracker = new UnusedValueTracker(context.registerAllocationConfig);
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructionsForBlock = instructions.get(block);
                for (var instruction : instructionsForBlock) {
                    tracker.handleInstruction(instruction);
                }
            }

            return tracker.getResult(kind);
        }

        /**
         * Go over instructions, run the scanFuntion, if true a modification occurred and stop.
         *
         * @param lir LIR
         * @param instructions Verifier instructions
         * @param scanFunction Modification function
         * @return true, if modification occurred, otherwise false
         */
        protected boolean scanOverInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, InstructionScanFunction scanFunction) {
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructionsForBlock = instructions.get(block);
                for (var instruction : instructionsForBlock) {
                    if (scanFunction.apply(block, instruction)) {
                        return true;
                    }
                }
            }

            return false;
        }

        /**
         * Same as {@link RAVPhaseWrapper#scanOverInstructions}, but only for
         * {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.Op}.
         *
         * @param lir LIR
         * @param instructions Verifier instructions
         * @param scanFunction Modification function
         * @return true, if modification occurred, otherwise false
         */
        protected boolean scanOps(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, OpScanFunction scanFunction) {
            return scanOverInstructions(lir, instructions, (block, instruction) -> {
                if (instruction instanceof RAVInstruction.Op op) {
                    return scanFunction.apply(block, op);
                }

                return false;
            });
        }
    }

    /**
     * Overwrite first seen variable with a fresh new one, to detect
     * {@link ValueNotInRegisterException} with @{link {@link ValueAllocationState}}.
     */
    class ChangeVariablePhase extends RAVPhaseWrapper {
        protected RAVariable originalVariable;
        protected RAVariable newVariable;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext ctx) {
            scanOps(lir, instructions, (block, op) -> {
                for (int i = 0; i < op.dests.count; i++) {
                    var curr = op.dests.curr[i];
                    var orig = op.dests.orig[i];
                    if (curr.equals(orig) || !orig.isVariable()) {
                        continue;
                    }

                    var variable = op.dests.orig[i].asVariable();
                    var newVariable = createNewVariable(lir, variable.getLIRKind());

                    op.dests.orig[i] = newVariable;

                    this.originalVariable = variable;
                    this.newVariable = newVariable;

                    return true;
                }

                return false;
            });
        }
    }

    /**
     * Change the register allocator config the verifier sees with one register being restricted
     * from allocation. This register is chosen simply by finding the first register the allocator
     * used.
     */
    class DisallowedRegisterPhase extends RAVPhaseWrapper {
        protected Register ignoredReg;
        protected RegisterAllocationConfig regAllocConfig;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            setFirstAllocatedRegister(lir, instructions);
            var restrictedTo = getAllocationRestrictedToArray(context.registerAllocationConfig);
            regAllocConfig = new RegisterAllocationConfig(context.registerAllocationConfig.getRegisterConfig(), restrictedTo);
        }

        @Override
        protected RegisterAllocationConfig getRegisterAllocationConfig(AllocationContext context) {
            return regAllocConfig;
        }

        /**
         * Create allocationRestrictedTo array for {@link RegisterAllocationConfig} without the
         * selected register.
         *
         * @param cfg Original config
         * @return New allocationRestrictedTo array without ignored register
         */
        protected String[] getAllocationRestrictedToArray(RegisterAllocationConfig cfg) {
            var allocatableRegs = cfg.getAllocatableRegisters();
            String[] restrictedTo = new String[allocatableRegs.size() - 1];

            int i = 0;
            for (var reg : allocatableRegs) {
                if (reg.equals(ignoredReg)) {
                    continue;
                }

                restrictedTo[i++] = reg.toString();
                if (i >= allocatableRegs.size() - 1) {
                    break;
                }
            }

            return restrictedTo;
        }

        /**
         * Find the first register the allocator used, set it and stop.
         *
         * @param lir LIR
         * @param instructions Verifier instructions
         */
        protected void setFirstAllocatedRegister(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions) {
            scanOverInstructions(lir, instructions, (block, instruction) -> {
                switch (instruction) {
                    case RAVInstruction.Op op -> {
                        for (int i = 0; i < op.dests.count; i++) {
                            var curr = op.dests.curr[i];
                            var orig = op.dests.orig[i];
                            if (curr.equals(orig)) {
                                continue;
                            }

                            if (curr.isRegister()) {
                                ignoredReg = curr.asRegister().getRegister();
                                return true;
                            }
                        }
                    }
                    case RAVInstruction.LocationMove move -> {
                        if (move.to.isRegister()) {
                            ignoredReg = move.to.asRegister().getRegister();
                            return true;
                        }
                    }
                    default -> {
                    }
                }

                return false;
            });
        }
    }

    /**
     * Change output of a first instruction to a different register, so that when it's output is
     * used somewhere, the state is {@link UnknownAllocationState}. This output needs to generate a
     * symbol if true, no symbol is generated.
     */
    class ForceUnknownStateInRegister extends RAVPhaseWrapper {
        protected RAValue symbol;
        protected RAValue oldLocation;
        protected RAValue newLocation;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            var regValue = getUnusedValue(lir, instructions, context);

            scanOps(lir, instructions, (block, op) -> {
                for (int i = 0; i < op.dests.count; i++) {
                    var curr = op.dests.curr[i];
                    var orig = op.dests.orig[i];
                    if (curr.equals(orig)) {
                        continue;
                    }

                    symbol = orig;
                    oldLocation = curr;
                    newLocation = regValue;
                    op.dests.curr[i] = regValue;

                    return true;
                }

                return false;
            });
        }
    }

    /**
     * Change the kind of operand to trigger a {@link KindsMismatchException}, very simply, find the
     * first instruction and look through its operand array to find the first variable and change
     * its type to Illegal.
     */
    abstract class ChangeKindPhase extends RAVPhaseWrapper {
        protected Variable variable;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext ctx) {
            scanOps(lir, instructions, (block, op) -> {
                var values = getTargetValueArrayPair(op);
                return changeVariableKind(values);
            });
        }

        protected abstract RAVInstruction.ValueArrayPair getTargetValueArrayPair(RAVInstruction.Op op);

        /**
         * Change the first-found variable and change its kind to {@link ValueKind#Illegal}.
         *
         * @param values Values we are changing kind of
         * @return true, if change occured, otherwise false.
         */
        protected boolean changeVariableKind(RAVInstruction.ValueArrayPair values) {
            for (int i = 0; i < values.count; i++) {
                var orig = values.orig[i];
                if (!orig.isVariable()) {
                    continue;
                }

                var raVar = orig.asVariable();
                var variable = raVar.getVariable();
                if (variable.getValueKind().equals(ValueKind.Illegal)) {
                    continue;
                }

                // Set the kind as illegal (should trigger an exception in most cases)
                values.orig[i] = RAValue.create(new Variable(ValueKind.Illegal, variable.index));
                this.variable = variable;
                return true;
            }

            return false;
        }
    }

    /**
     * Change the kind for a variable that is being used as an input.
     */
    class ChangeInputKindPhase extends ChangeKindPhase {
        @Override
        protected RAVInstruction.ValueArrayPair getTargetValueArrayPair(RAVInstruction.Op op) {
            return op.uses;
        }
    }

    /**
     * Change the kind for a variable that is being used as an output.
     */
    class ChangeOutputKindPhase extends ChangeKindPhase {
        @Override
        protected RAVInstruction.ValueArrayPair getTargetValueArrayPair(RAVInstruction.Op op) {
            return op.dests;
        }
    }

    /**
     * First the first instruction that has alive inputs, as well as an output. Change the output
     * and alive operand to use the same location so that the input operand does not survive.
     */
    abstract class ViolateAliveConstraint extends RAVPhaseWrapper {
        RAValue location;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            boolean found = scanOps(lir, instructions, (block, op) -> {
                if (op.alive.count == 0) {
                    return false;
                }

                var values = getValues(op);
                if (values.count == 0) {
                    return false;
                }

                if (values.curr[0].isIllegal()) {
                    return false;
                }

                location = op.alive.curr[0];
                values.curr[0] = location;

                return true;
            });

            if (found) {
                return;
            }

            /*
             * Create an operation that has alive and output with same location to see that it can
             * be detected, if it was not found in the input snippet.
             */
            var lirInstruction = new StandardOp.NoOp(null, 0);
            var op = new RAVInstruction.Op(lirInstruction);
            op.temp = new RAVInstruction.ValueArrayPair(1);
            op.alive = new RAVInstruction.ValueArrayPair(1);

            location = getUnusedValue(lir, instructions, context);
            op.temp.curr[0] = location;
            op.temp.orig[0] = location;
            op.temp.operandFlags = new ArrayList<>(List.of(EnumSet.of(REG, STACK)));
            op.alive.curr[0] = location;
            op.alive.orig[0] = location;
            op.alive.operandFlags = new ArrayList<>(List.of(EnumSet.of(REG, STACK)));

            var b0Instructions = instructions.get(0);
            b0Instructions.add(b0Instructions.size() - 1, op);
        }

        protected abstract RAVInstruction.ValueArrayPair getValues(RAVInstruction.Op op);
    }

    /**
     * Changes LIR instruction to use the same register as alive and output.
     */
    class ViolateAliveConstraintInDstPhase extends ViolateAliveConstraint {
        @Override
        protected RAVInstruction.ValueArrayPair getValues(RAVInstruction.Op op) {
            return op.dests;
        }
    }

    /**
     * Changes LIR instruction to use the same register as alive and temporary output.
     */
    class ViolateAliveConstraintInTempPhase extends ViolateAliveConstraint {
        @Override
        protected RAVInstruction.ValueArrayPair getValues(RAVInstruction.Op op) {
            return op.temp;
        }
    }

    /**
     * Base for creating conflict in block merge points. Uses an unused location to create a
     * conflict from predecessor blocks of a conflict block. Conflict block is the merge block, this
     * is where a usage of a value is inserted. The location contains
     * {@link ConflictedAllocationState} from the predecessors.
     */
    abstract class ConflictPhase extends RAVPhaseWrapper {
        RAVariable targetVariable;
        Set<ValueAllocationState> conflictVariables;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext ctx) {
            var conflictLocation = getUnusedValue(lir, instructions, ctx);
            var conflictBlock = getFirstConflictBlock(lir);

            conflictVariables = new EconomicHashSet<>();
            for (int i = 0; i < conflictBlock.getPredecessorCount(); i++) {
                var pred = conflictBlock.getPredecessorAt(i);
                var instructionsForPred = instructions.get(pred);

                var idx = instructionsForPred.size() - 2;
                var variable = createNewVariable(lir);
                var op = createSymbolSpawnOp(variable, conflictLocation);

                // New symbol inserted into same conflicting location.
                conflictVariables.add(new ValueAllocationState(variable, null, null));
                instructionsForPred.add(idx, op);
            }

            targetVariable = createNewVariable(lir);
            // Here the target varible will not be read, instead it will be conflicted
            var usage = createSymbolUsage(targetVariable, conflictLocation);
            var usageIdx = 1;
            instructions.get(conflictBlock).add(usageIdx, usage);
        }

        protected BasicBlock<?> getFirstConflictBlock(LIR lir) {
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                if (block.getPredecessorCount() > 1 && isConflictBlock(block)) {
                    return block;
                }
            }
            return null;
        }

        protected abstract boolean isConflictBlock(BasicBlock<?> block);
    }

    /**
     * Cause a conflict in an if-statement merge block.
     */
    class DiamondConflictPhase extends ConflictPhase {
        @Override
        protected boolean isConflictBlock(BasicBlock<?> block) {
            return !block.isLoopHeader();
        }
    }

    /**
     * Cause a conflict in a loop header.
     */
    class LoopConflictPhase extends ConflictPhase {
        @Override
        protected boolean isConflictBlock(BasicBlock<?> block) {
            return block.isLoopHeader();
        }
    }

    /**
     * Trigger {@link CalleeSavedRegisterNotRetrievedException}, by creating a new
     * {@link RegisterAllocationConfig} with a new callee-saved register. This register will be
     * overwritten by the body of the method and will not be retrieved.
     *
     * <p>
     * The selected register is dependent on the architecture, using the first argument register,
     * that will be used.
     * </p>
     */
    class CalleeSavePhase extends RAVPhaseWrapper {
        Register register;

        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
            var name = target.arch.toString();

            // Select first paramter register for this
            switch (name) {
                case "amd64" -> register = AMD64.rsi;
                case "aarch64" -> register = AArch64.r1;
            }

            super.run(target, lirGenRes, context);
        }

        @Override
        protected RegisterAllocationConfig getRegisterAllocationConfig(AllocationContext context) {
            assert register != null : "Callee save register not selected";

            var regCfg = context.registerAllocationConfig.getRegisterConfig();
            var newRegCfg = new RegisterConfig() {

                @Override
                public Register getReturnRegister(JavaKind kind) {
                    return regCfg.getReturnRegister(kind);
                }

                @Override
                public Register getFrameRegister() {
                    return regCfg.getFrameRegister();
                }

                @Override
                public CallingConvention getCallingConvention(CallingConvention.Type type, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
                    return regCfg.getCallingConvention(type, returnType, parameterTypes, valueKindFactory);
                }

                @Override
                public List<Register> getCallingConventionRegisters(CallingConvention.Type type, JavaKind kind) {
                    return regCfg.getCallingConventionRegisters(type, kind);
                }

                @Override
                public List<Register> getAllocatableRegisters() {
                    return regCfg.getAllocatableRegisters();
                }

                @Override
                public List<Register> filterAllocatableRegisters(PlatformKind kind, List<Register> registers) {
                    return regCfg.filterAllocatableRegisters(kind, registers);
                }

                @Override
                public List<Register> getCallerSaveRegisters() {
                    return regCfg.getCallerSaveRegisters();
                }

                @Override
                public List<Register> getCalleeSaveRegisters() {
                    return List.of(register);
                }

                @Override
                public List<RegisterAttributes> getAttributesMap() {
                    return regCfg.getAttributesMap();
                }

                @Override
                public boolean areAllAllocatableRegistersCallerSaved() {
                    return regCfg.areAllAllocatableRegistersCallerSaved();
                }
            };

            return new RegisterAllocationConfig(newRegCfg, null);
        }
    }

    /**
     * Trigger a rematerialization of a constant to a stack slot, where its forbidden via
     * {@link LoadConstOp#canRematerializeToStack()}. Triggered by inserting the scenario of a
     * constant that is used in this way.
     */
    class PhantomConstRematerializationPhase extends RAVPhaseWrapper {
        RAVariable variableValue;
        RAValue stackSlotValue;
        ConstantValue constant;

        static class LoadConstOp extends LIRInstruction implements StandardOp.LoadConstantOp {
            public static final LIRInstructionClass<LoadConstOp> TYPE = LIRInstructionClass.create(LoadConstOp.class);

            @Def({REG, STACK}) protected AllocatableValue result;
            JavaConstant input;

            LoadConstOp(AllocatableValue result, JavaConstant input) {
                super(TYPE);

                this.result = result;
                this.input = input;
            }

            @Override
            public Constant getConstant() {
                return input;
            }

            @Override
            public boolean canRematerializeToStack() {
                return false;
            }

            @Override
            public AllocatableValue getResult() {
                return result;
            }

            @Override
            public void emitCode(CompilationResultBuilder crb) {
            }
        }

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext ctx) {
            var kind = LIRKind.value(AMD64Kind.V32_BYTE);

            constant = new ConstantValue(kind, getConstant());
            var stackSlot = new SimpleVirtualStackSlot(1, kind);

            stackSlotValue = RAValue.create(stackSlot);
            var constantValue = new RAVConstant(constant, false);

            var remMove = new RAVInstruction.ValueMove(new LoadConstOp(stackSlot, constant.getJavaConstant()), constant, stackSlot);

            var usage = createSymbolUsage(constantValue, stackSlotValue);

            var blockInstructions = instructions.get(0);
            blockInstructions.add(blockInstructions.size() - 1, remMove);
            blockInstructions.add(blockInstructions.size() - 1, usage);
        }

        protected JavaConstant getConstant() {
            return new JavaConstant() {
                @Override
                public JavaKind getJavaKind() {
                    return JavaKind.Int;
                }

                @Override
                public boolean isNull() {
                    return false;
                }

                @Override
                public boolean isDefaultForKind() {
                    return false;
                }

                @Override
                public Object asBoxedPrimitive() {
                    return null;
                }

                @Override
                public int asInt() {
                    return 0;
                }

                @Override
                public boolean asBoolean() {
                    return false;
                }

                @Override
                public long asLong() {
                    return 0;
                }

                @Override
                public float asFloat() {
                    return 0;
                }

                @Override
                public double asDouble() {
                    return 0;
                }
            };
        }
    }

    /**
     * Detect a violation of {@link LIRInstruction.OperandFlag} being missing/different. Done by
     * finding the first instruction that has an input that is changed by the allocator.
     */
    class ForceOperandFlagPhase extends RAVPhaseWrapper {
        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext ctx) {
            scanOps(lir, instructions, (block, op) -> {
                if (op.isLabel()) {
                    return false;
                }

                for (int i = 0; i < op.uses.count; i++) {
                    var curr = op.uses.curr[i];
                    var orig = op.uses.orig[i];

                    if (orig.equals(curr)) {
                        continue;
                    }

                    EnumSet<LIRInstruction.OperandFlag> opFlags = EnumSet.noneOf(LIRInstruction.OperandFlag.class);
                    opFlags.addAll(op.uses.operandFlags.get(i));

                    if (curr.isRegister()) {
                        opFlags.remove(REG);
                    } else if (LIRValueUtil.isStackSlotValue(curr.getValue())) {
                        opFlags.remove(STACK);
                    } else if (LIRValueUtil.isConstantValue(curr.getValue())) {
                        opFlags.remove(LIRInstruction.OperandFlag.CONST);
                    } else {
                        continue;
                    }

                    op.uses.operandFlags.set(i, opFlags);
                    return true;
                }

                return false;
            });
        }
    }

    /**
     * Force a missing location (the case where the allocation location is null). Again, the first
     * instruction touched by the allocator is chosen.
     */
    class ForceMissingLocationExceptionPhase extends RAVPhaseWrapper {
        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext ctx) {
            scanOps(lir, instructions, (block, op) -> {
                if (op.isLabel()) {
                    return false;
                }

                for (int i = 0; i < op.uses.count; i++) {
                    var curr = op.uses.curr[i];
                    var orig = op.uses.orig[i];

                    if (orig.equals(curr)) {
                        continue;
                    }

                    op.uses.curr[i] = null;
                    return true;
                }

                return false;
            });
        }
    }

    /**
     * Add a new reference to a list of references. This reference will be at a location that a does
     * not have a reference, this triggers {@link MissingReferenceException}.
     */
    class ExcessReferencePhase extends RAVPhaseWrapper {
        BasicBlock<?> currentBlock;
        RAVInstruction.Op prev;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext ctx) {
            currentBlock = null;
            prev = null;

            scanOps(lir, instructions, (block, op) -> {
                if (block != currentBlock) {
                    currentBlock = block;
                    prev = null;
                }

                if (prev != null) {
                    var curr = prev.dests.curr[0];
                    var kind = curr.getLIRKind().makeUnknownReference();
                    var refLocation = RAValue.create(curr.asRegister().getRegister().asValue(kind));

                    op.references = new EconomicHashSet<>();
                    op.references.add(refLocation);
                    return true;
                }

                if (op.isLabel()) {
                    return false;
                }

                if (op.dests.count == 0) {
                    return false;
                }

                var curr = op.dests.curr[0];
                if (!curr.isRegister() || !curr.getLIRKind().isValue()) {
                    return false;
                }

                // This instruction will define dest location
                // next instruction will verify the reference list
                // -> no reference there!
                prev = op;
                return false;
            });
        }
    }

    /**
     * Test if a synthetically inserted variable gets its location chosen correctly by
     * {@link jdk.graal.compiler.lir.alloc.verifier.FromUsageResolverGlobal}.
     */
    class PhiResolver extends RAVPhaseWrapper {
        RAVInstruction.Op label;
        RAValue location;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            RAValue target = getUnusedValue(lir, instructions, context);

            var labelOp = (RAVInstruction.Op) instructions.get(0).getFirst();

            var newDst = new RAVInstruction.ValueArrayPair(labelOp.dests.count + 1);
            for (int i = 0; i < labelOp.dests.count; i++) {
                newDst.curr[i] = labelOp.dests.curr[i];
                newDst.orig[i] = labelOp.dests.orig[i];
                newDst.operandFlags.add(labelOp.dests.operandFlags.get(i));
            }

            var variable = createNewVariable(lir);
            location = target;

            newDst.orig[labelOp.dests.count] = variable;
            newDst.curr[labelOp.dests.count] = null;
            newDst.operandFlags.add(EnumSet.of(REG));

            labelOp.dests = newDst;
            label = labelOp;

            var usage = createSymbolUsage(variable, location);
            instructions.get(0).add(instructions.get(0).size() - 1, usage);
        }
    }

    /**
     * Test if a reference gets deleted, if it's not tracked in the reference list. When
     * {@link jdk.graal.compiler.lir.alloc.verifier.RAVInstruction.Op#references} is set (even
     * empty), then only these references can survive, so an old one will get deleted. Triggers
     * {@link ValueNotInRegisterException}, because it was purged.
     */
    class DeleteReferencePhase extends RAVPhaseWrapper {
        RAValue location;
        RAValue variable;
        BasicBlock<?> currentBlock;
        RAVInstruction.Op prev;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            currentBlock = null;
            prev = null;

            scanOps(lir, instructions, (block, op) -> {
                if (block != currentBlock) {
                    currentBlock = block;
                    prev = null;
                }

                if (prev != null && (setReferences(op.uses) || setReferences(op.alive))) {
                    prev.references = new EconomicHashSet<>();
                    return true;
                }

                if (!op.isLabel()) {
                    // Check fix, the previous instruction cannot define
                    // the reference being discarded, because then this phase
                    // won't work
                    prev = op;
                }

                return false;
            });
        }

        protected boolean setReferences(RAVInstruction.ValueArrayPair values) {
            for (int i = 0; i < values.count; i++) {
                var orig = values.orig[i];
                var curr = values.curr[i];

                if (orig.isIllegal() || curr == null || curr.isIllegal()) {
                    continue;
                }

                if (!orig.getLIRKind().isValue()) {
                    location = curr;
                    variable = orig;
                    return true;
                }
            }
            return false;
        }
    }

    class ForgottenReloadPhase extends RAVPhaseWrapper {
        RAValue spillSlot;
        RAValue expectedReloadLocation;
        RAValue redirectedReloadLocation;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            scanOverInstructions(lir, instructions, (block, instruction) -> {
                if (!(instruction instanceof RAVInstruction.Reload reload)) {
                    return false;
                }

                if (!isLocationUsedLater(instructions, block, instruction, reload.to)) {
                    return false;
                }

                spillSlot = reload.from;
                expectedReloadLocation = reload.to;
                redirectedReloadLocation = getUnusedValue(lir, instructions, context, reload.to.getLIRKind());

                reload.to = redirectedReloadLocation;

                // LocationMove is generally used instead of specialized Reload,
                // so the destination needs to be overwritten in both cases.
                // This should probably be changed.
                ((RAVInstruction.LocationMove) reload).to = redirectedReloadLocation;
                return true;
            });
        }

        /**
         * Check if the location is used later.
         *
         * @param instructions Verifier instructions
         * @param currentBlock Block where it is supposed to be used
         * @param currentInstruction Instruction from which we are looking for usage
         * @param location The location in question
         * @return true, if it was used in this block, otherwise false
         */
        protected boolean isLocationUsedLater(BlockMap<List<RAVInstruction.Base>> instructions, BasicBlock<?> currentBlock, RAVInstruction.Base currentInstruction, RAValue location) {
            boolean foundCurrentInstruction = false;
            var instructionsForBlock = instructions.get(currentBlock);
            for (var instruction : instructionsForBlock) {
                if (!foundCurrentInstruction) {
                    if (instruction == currentInstruction) {
                        foundCurrentInstruction = true;
                    }
                    continue;
                }

                if (usesLocation(instruction, location)) {
                    return true;
                }
            }

            return false;
        }

        protected boolean usesLocation(RAVInstruction.Base instruction, RAValue location) {
            if (instruction instanceof RAVInstruction.Op op) {
                return isLocationInValues(op.uses, location) || isLocationInValues(op.alive, location);
            }
            return false;
        }

        protected boolean isLocationInValues(RAVInstruction.ValueArrayPair values, RAValue location) {
            for (int i = 0; i < values.count; i++) {
                if (location.equals(values.curr[i])) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Complete overwrite the incoming snippet code with early reuse violation.
     *
     * <p>
     * Simulate a variable being created, immediately overwritten, and then used. This throws a
     * {@link ValueNotInRegisterException}, because the variable is not present, and instead a
     * different value is present.
     * </p>
     */
    class EarlyReusePhase extends RAVPhaseWrapper {
        RAVariable liveVariable;
        RAVariable overwrittenVariable;
        RAValue location;

        @Override
        protected void modifyVerifierInstructions(LIR lir, BlockMap<List<RAVInstruction.Base>> instructions, AllocationContext context) {
            location = getUnusedValue(lir, instructions, context, LIRKind.Illegal);

            liveVariable = createNewVariable(lir, location.getLIRKind());
            overwrittenVariable = createNewVariable(lir, location.getLIRKind());

            var changedInstructions = new ArrayList<RAVInstruction.Base>();
            changedInstructions.add(instructions.get(0).getFirst());
            changedInstructions.add(createSymbolSpawnOp(liveVariable, location));
            changedInstructions.add(createSymbolSpawnOp(overwrittenVariable, location));
            changedInstructions.add(createSymbolUsage(overwrittenVariable, location));
            changedInstructions.add(createSymbolUsage(liveVariable, location));

            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                instructions.put(block, new ArrayList<>());
            }

            var startBlock = lir.getControlFlowGraph().getStartBlock();
            instructions.put(startBlock, changedInstructions);
        }
    }

    @Override
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        OptionValues newOptions;
        if (disableInlining) {
            // Disable any inlining to allow for function calls
            newOptions = new OptionValues(options,
                            RegAllocVerifierPhase.Options.RAVFailOnFirst, true,
                            HighTier.Options.Inline, false,
                            BytecodeParserOptions.InlineDuringParsing, false);
        } else {
            newOptions = new OptionValues(options, RegAllocVerifierPhase.Options.RAVFailOnFirst, true);
        }

        return super.compile(installedCodeOwner, graph, compilationResult, compilationId, newOptions);
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        if (validSuites) {
            return createLIRSuitesWithVerifier(options);
        }

        return createModifiedVerifierLIRSuites(options);
    }

    /**
     * Create LIR suites with verification enabled, but no tainting. This is done preemptively to
     * check that the verification is passing.
     */
    protected LIRSuites createLIRSuitesWithVerifier(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        var stage = suites.getAllocationStage();

        if (RegAllocVerifierPhase.Options.EnableRAVerifier.getValue(options)) {
            var verifier = (RegAllocVerifierPhase) stage.findPhaseInstance(RegisterAllocationPhase.class);
            assert verifier != null;

            var stackAllocator = verifier.getStackSlotAllocator();
            if (stackAllocator != null) {
                // Do not use stack allocator
                verifier.setStackSlotAllocator(null);
                stage.appendPhase(stackAllocator);
            }

            return suites;
        }

        var it = stage.findPhase(RegisterAllocationPhase.class);
        assert it != null;

        var allocator = (RegisterAllocationPhase) it.previous();
        it.set(new RegAllocVerifierPhase(allocator, null));

        return suites;
    }

    /**
     * Create LIR suites with tainted verifier.
     */
    protected LIRSuites createModifiedVerifierLIRSuites(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        var stage = suites.getAllocationStage();

        var it = stage.findPhase(RegisterAllocationPhase.class);
        Assert.assertNotNull(it);

        LIRPhase<AllocationPhase.AllocationContext> stackAllocator = null;
        var allocator = (RegisterAllocationPhase) it.previous();
        if (allocator instanceof RegAllocVerifierPhase rav) {
            stackAllocator = rav.getStackSlotAllocator();
            if (stackAllocator != null) {
                rav.setStackSlotAllocator(null);
            }

            phase.setAllocator(rav.getAllocator());
        } else {
            phase.setAllocator(allocator);
        }

        it.set(phase);
        if (stackAllocator != null) {
            stage.appendPhase(stackAllocator);
        }

        return suites;
    }

    protected void compileModified(String name) {
        validSuites = false;
        compile(getResolvedJavaMethod(name), null);
        validSuites = true;
    }

    private <T extends RAVException> T runVerifierExpectingException(String methodName, RAVPhaseWrapper testPhase, Class<T> expectedException) {
        phase = testPhase;

        compile(getResolvedJavaMethod(methodName), null);
        Assert.assertNull(exception);

        compileModified(methodName);

        return assertException(expectedException);
    }

    private <T extends RAVException> T assertException(Class<T> expectedException) {
        Assert.assertNotNull("No exception was thrown", exception);

        RAVException actualException = exception;
        if (actualException.getCause() != null) {
            actualException = actualException.getCause();
        }

        Assert.assertTrue("Unexpected exception: " + actualException, expectedException.isInstance(actualException));
        return expectedException.cast(actualException);
    }

    public static int simple(int a) {
        return a + 1;
    }

    public static int keep(int a) {
        int b = 5 * a; // Make sure b is in different reg than a
        return b + a;
    }

    public static int diamond(int a, int b, int c, int d, int e) {
        int x;
        if (a > 0) {
            x = b * e;
        } else {
            x = c + d + 2 * b;
        }

        return x + a;
    }

    public static int sum(int a, int b) {
        int sum = 0;
        for (int i = 0; i < a; i++) {
            sum += i * b;
        }
        return sum;
    }

    // toArray and arrayLengthProviderSnippet taken from ArrayLengthProviderTest
    // to force dest and alive in an instruction
    public static Object[] toArray(List<?> list) {
        return new Object[list.size()];
    }

    public static Object arrayLengthProviderSnippet(ArrayList<?> list, boolean a) {
        while (true) {
            Object[] array = toArray(list);
            if (array.length < 1) {
                return null;
            }
            if (array[0] instanceof String || a) {
                /*
                 * This code is outside of the loop. Accessing the array requires a ValueProxyNode.
                 * When the simplification of the ArrayLengthNode replaces the length access with
                 * the ArrayList.size used to create the array, then the value needs to have a
                 * ValueProxyNode too. In addition, the two parts of the if-condition actually lead
                 * to two separate loop exits, with two separate proxy nodes. A ValuePhiNode is
                 * present originally for the array, and the array length simplification needs to
                 * create a new ValuePhiNode for the two newly introduced ValueProxyNode.
                 */
                if (array.length < 1) {
                    return null;
                }
                return array[0];
            }
        }
    }

    // Taken from EnumSwitchTest
    // to find an instruction with both temp and alive
    public static int aliveConstraintSnippet(Ex e) {
        switch (e) {
            case E0:
                return 0;
            case E1:
                return 1;
            case E2:
                return 2;
            case E3:
                return 3;
            default:
                return -1;
        }
    }

    public static void loop(int a, int b) {
        while (true) {
            if (a > 3) {
                System.out.println("a = " + a);
            }

            a += b;
        }
    }

    class A {
        protected int a;

        A(int a) {
            this.a = a;
        }

        public void increment() {
            this.a++;
        }

        public int getA() {
            return a;
        }
    }

    A obj;

    public int referenceSnippet(int a, int b) {
        obj = new A(a);
        while (obj.getA() < b) {
            obj.increment();
        }
        return obj.getA();
    }

    public static int consumeLiveValues(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j) {
        return a + b + c + d + e + f + g + h + i + j;
    }

    public static int consumeReloadedValue(int value) {
        return 3 * value + 1;
    }

    /**
     * Increase register pressure so that some variable is spilled and reloaded.
     */
    public static int spillAndReload(int a, int b, int c, int d, int e, int f, int g, int h, int i, int j, int k, int l, int m, int n) {
        int v0 = a + n;
        int v1 = b + m;
        int v2 = c + l;
        int v3 = d + k;
        int v4 = e + j;
        int v5 = f + i;
        int v6 = g + h;
        int v7 = a + c + e;
        int v8 = b + d + f;
        int v9 = m + n + k;
        int v10 = a + g + m;
        int v11 = b + h + n;
        int kept = v10 ^ v11;
        int call = consumeLiveValues(v0, v1, v2, v3, v4, v5, v6, v7, v8, v9);
        int reloaded = consumeReloadedValue(kept);
        return call + reloaded + kept + v0 + v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8 + v9 + v10 + v11;
    }

    @Before
    public void prepareTest() {
        exception = null;
        validSuites = true;
        phase = null;
        disableInlining = false;
    }

    @Test
    public void testInvalidRegisterUsed() {
        var disallowedRegPhase = new DisallowedRegisterPhase();
        var iruException = runVerifierExpectingException("simple", disallowedRegPhase, InvalidRegisterUsedException.class);
        Assert.assertEquals(iruException.register, disallowedRegPhase.ignoredReg);
    }

    @Test
    public void testWrongVariableInState() {
        var changeVariablePhase = new ChangeVariablePhase();
        var vnrException = runVerifierExpectingException("simple", changeVariablePhase, ValueNotInRegisterException.class);

        // Expected original variable
        Assert.assertEquals(changeVariablePhase.originalVariable, vnrException.variable);
        Assert.assertTrue(vnrException.state instanceof ValueAllocationState);
        // But new variable is there instead
        Assert.assertEquals(changeVariablePhase.newVariable, ((ValueAllocationState) vnrException.state).getRAValue());
    }

    @Test
    public void testUnknownLocation() {
        var changeLocationPhase = new ForceUnknownStateInRegister();
        var vnrException = runVerifierExpectingException("keep", changeLocationPhase, ValueNotInRegisterException.class);
        Assert.assertTrue(vnrException.state.isUnknown());
        Assert.assertEquals(vnrException.variable, changeLocationPhase.symbol);
        Assert.assertEquals(vnrException.location, changeLocationPhase.oldLocation);
    }

    @Test
    public void testConflictedLoc() {
        var diamondConflictPhase = new DiamondConflictPhase();
        var vnrException = runVerifierExpectingException("diamond", diamondConflictPhase, ValueNotInRegisterException.class);
        Assert.assertTrue(vnrException.state.isConflicted());

        var confState = (ConflictedAllocationState) vnrException.state;
        var conflictedStates = confState.getConflictedStates();
        Assert.assertEquals(conflictedStates, diamondConflictPhase.conflictVariables);
    }

    @Test
    public void testConflictInLoop() {
        var loopConflictPhase = new LoopConflictPhase();
        var vnrException = runVerifierExpectingException("sum", loopConflictPhase, ValueNotInRegisterException.class);
        Assert.assertTrue(vnrException.state.isConflicted());

        var confState = (ConflictedAllocationState) vnrException.state;
        Assert.assertEquals(confState.getConflictedStates(), loopConflictPhase.conflictVariables);
    }

    @Test
    public void testConflictInInfiniteLoop() {
        var loopConflictPhase = new LoopConflictPhase();
        var vnrException = runVerifierExpectingException("loop", loopConflictPhase, ValueNotInRegisterException.class);
        Assert.assertTrue(vnrException.state.isConflicted());

        var confState = (ConflictedAllocationState) vnrException.state;
        Assert.assertEquals(confState.getConflictedStates(), loopConflictPhase.conflictVariables);
    }

    @Test
    public void testAliveConstraintInDest() {
        var violateAliveConstraintPhase = new ViolateAliveConstraintInDstPhase();
        runVerifierExpectingException("arrayLengthProviderSnippet", violateAliveConstraintPhase, AliveConstraintViolationException.class);
    }

    @Test
    public void testAliveConstraintInTemp() {
        var violateAliveConstraintPhase = new ViolateAliveConstraintInTempPhase();
        runVerifierExpectingException("aliveConstraintSnippet", violateAliveConstraintPhase, AliveConstraintViolationException.class);
    }

    @Test
    public void testKindMatchAfterAlloc() {
        var changeInputKindPhase = new ChangeInputKindPhase();
        var kmException = runVerifierExpectingException("keep", changeInputKindPhase, KindsMismatchException.class);
        Assert.assertEquals(changeInputKindPhase.variable.index, kmException.value1.asVariable().getVariable().index);
        Assert.assertEquals(changeInputKindPhase.variable.getValueKind(), kmException.value2.getValue().getValueKind());
        Assert.assertEquals(ValueKind.Illegal, kmException.value1.getValue().getValueKind());
        Assert.assertTrue(kmException.origVsCurr);
    }

    @Test
    public void testKindMatchInState() {
        var changeInputKindPhase = new ChangeOutputKindPhase();
        var kmException = runVerifierExpectingException("keep", changeInputKindPhase, KindsMismatchException.class);
        Assert.assertEquals(changeInputKindPhase.variable.index, kmException.value1.asVariable().getVariable().index);
        Assert.assertEquals(changeInputKindPhase.variable.index, kmException.value2.asVariable().getVariable().index);
        Assert.assertEquals(changeInputKindPhase.variable.getValueKind(), kmException.value1.getValue().getValueKind());
        Assert.assertEquals(ValueKind.Illegal, kmException.value2.getValue().getValueKind());
        Assert.assertFalse(kmException.origVsCurr);
    }

    @Test
    public void testCalleeSaveRetrieval() {
        var calleeSavePhase = new CalleeSavePhase();
        var csException = runVerifierExpectingException("simple", calleeSavePhase, CalleeSavedRegisterNotRetrievedException.class);
        Assert.assertEquals(csException.register.getRegister(), calleeSavePhase.register);
    }

    @Test
    public void testOperandFlags() {
        var forceOperandFlagPhase = new ForceOperandFlagPhase();
        runVerifierExpectingException("simple", forceOperandFlagPhase, OperandFlagMismatchException.class);
    }

    @Test
    public void testMissingLocation() {
        var forceMissingLocationException = new ForceMissingLocationExceptionPhase();
        runVerifierExpectingException("simple", forceMissingLocationException, MissingLocationException.class);
    }

    @Test
    public void testExcessReference() {
        var forceExcessReference = new ExcessReferencePhase();
        runVerifierExpectingException("simple", forceExcessReference, MissingReferenceException.class);
    }

    @Test
    public void testEarlyReuse() {
        var earlyReusePhase = new EarlyReusePhase();
        // The phase overwrites the method here
        var vnrException = runVerifierExpectingException("simple", earlyReusePhase, ValueNotInRegisterException.class);

        Assert.assertEquals(earlyReusePhase.liveVariable, vnrException.variable);
        Assert.assertEquals(earlyReusePhase.location, vnrException.location);
        Assert.assertTrue(vnrException.state instanceof ValueAllocationState);
        Assert.assertEquals(earlyReusePhase.overwrittenVariable, ((ValueAllocationState) vnrException.state).getRAValue());
    }

    @Test
    public void testForgottenReload() {
        disableInlining = true;
        var forgottenReloadPhase = new ForgottenReloadPhase();
        var vnrException = runVerifierExpectingException("spillAndReload", forgottenReloadPhase, ValueNotInRegisterException.class);

        Assert.assertEquals(vnrException.location, forgottenReloadPhase.expectedReloadLocation);
    }

    @Test
    public void testPhiResolver() {
        var phiResolver = new PhiResolver();
        phase = phiResolver;

        var methodName = "simple";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        Assert.assertNull(exception);
        Assert.assertEquals(phiResolver.label.dests.curr[phiResolver.label.dests.count - 1], phiResolver.location);
    }

    @Test
    public void testConstRematerializer() {
        var constRemPhase = new PhantomConstRematerializationPhase();
        var crException = runVerifierExpectingException("simple", constRemPhase, ConstantRematerializedToStackException.class);
        Assert.assertEquals(constRemPhase.stackSlotValue, crException.location);
        Assert.assertEquals(constRemPhase.constant, crException.state.getValue());
    }

    @Test
    public void testDeleteReference() {
        var deleteReferencePhase = new DeleteReferencePhase();
        var vnrException = runVerifierExpectingException("referenceSnippet", deleteReferencePhase, ValueNotInRegisterException.class);
        Assert.assertEquals(vnrException.variable, deleteReferencePhase.variable);
        Assert.assertEquals(vnrException.location, deleteReferencePhase.location);
    }
}

// Taken from EnumSwitchTest, shortened
enum Ex {
    E0,
    E1,
    E2,
    E3,
}
