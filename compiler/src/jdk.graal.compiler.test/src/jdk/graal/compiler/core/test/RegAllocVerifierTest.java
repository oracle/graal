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

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.ValueProcedure;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.alloc.RegisterAllocationPhase;
import jdk.graal.compiler.lir.alloc.verifier.AliveConstraintViolationException;
import jdk.graal.compiler.lir.alloc.verifier.CalleeSavedRegisterNotRetrievedException;
import jdk.graal.compiler.lir.alloc.verifier.ConflictedAllocationState;
import jdk.graal.compiler.lir.alloc.verifier.ConstantRematerializedToStackException;
import jdk.graal.compiler.lir.alloc.verifier.InvalidRegisterUsedException;
import jdk.graal.compiler.lir.alloc.verifier.KindsMismatchException;
import jdk.graal.compiler.lir.alloc.verifier.MissingLocationException;
import jdk.graal.compiler.lir.alloc.verifier.MissingReferenceException;
import jdk.graal.compiler.lir.alloc.verifier.OperandFlagMismatchException;
import jdk.graal.compiler.lir.alloc.verifier.RAVException;
import jdk.graal.compiler.lir.alloc.verifier.RAVInstruction;
import jdk.graal.compiler.lir.alloc.verifier.RAValue;
import jdk.graal.compiler.lir.alloc.verifier.RAVariable;
import jdk.graal.compiler.lir.alloc.verifier.RegAllocVerifierPhase;
import jdk.graal.compiler.lir.alloc.verifier.ValueAllocationState;
import jdk.graal.compiler.lir.alloc.verifier.ValueNotInRegisterException;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.framemap.SimpleVirtualStackSlot;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.AllocationPhase;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;

public class RegAllocVerifierTest extends GraalCompilerTest {
    /**
     * Should the valid set of compiler phase suites be used?
     */
    boolean validSuites = true;

    /**
     * Exception thrown during verification process.
     */
    RAVException exception;

    /**
     * Phase that causes RAVException to be thrown, by modifying LIR or Verifier State.
     */
    RAVPhaseWrapper phase;

    class RAVPhaseWrapper extends RegAllocVerifierPhase {
        RAVPhaseWrapper() {
            super(null, null);
        }

        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationPhase.AllocationContext context) {
            try {
                super.run(target, lirGenRes, context);
            } catch (RAVException e) {
                exception = e;
            }
        }
    }

    /**
     * Overwrites a destination variable with a newly created one to cause a
     * ValueNotInLocationException with old variable being in said place.
     */
    class ChangeVariablePhase extends RAVPhaseWrapper {
        protected RAVariable originalVariable;
        protected RAVariable newVariable;

        @Override
        protected Map<LIRInstruction, RAVInstruction.Base> saveInstructionsPreAlloc(LIR lir) {
            var result = super.saveInstructionsPreAlloc(lir);
            modifyLIR(lir, result);
            return result;
        }

        protected void modifyLIR(LIR lir, Map<LIRInstruction, RAVInstruction.Base> instrMap) {
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructions = lir.getLIRforBlock(block);
                for (var instruction : instructions) {
                    if (instruction instanceof StandardOp.LabelOp) {
                        continue;
                    }

                    var virInstr = instrMap.get(instruction);
                    if (virInstr instanceof RAVInstruction.Op op && changeVariable(lir, op)) {
                        break;
                    }
                }
            }
        }

        protected boolean changeVariable(LIR lir, RAVInstruction.Op op) {
            for (int i = 0; i < op.dests.count; i++) {
                if (!op.dests.orig[i].isVariable()) {
                    continue;
                }

                var variable = op.dests.orig[i].asVariable();
                var newVariable = createNewVariable(lir, variable);

                op.dests.orig[i] = newVariable;

                this.originalVariable = variable;
                this.newVariable = newVariable;

                return true;
            }

            return false;
        }

        protected RAVariable createNewVariable(LIR lir, RAVariable oldVariable) {
            var newVariable = new Variable(oldVariable.getValue().getValueKind(), lir.numVariables() + 1);
            return (RAVariable) RAValue.create(newVariable);
        }
    }

    /**
     * This pass changes register allocation config to only allow certain registers to be used for
     * allocation, and we want the verifier to detect usage of said register.
     */
    class DisallowedRegisterPhase extends RAVPhaseWrapper {
        protected Register ignoredReg;
        protected RegisterAllocationConfig regAllocConfig;

        @Override
        protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext context) {
            var restrictedTo = getAllocationRestrictedToArray(lir, context.registerAllocationConfig);
            regAllocConfig = new RegisterAllocationConfig(context.registerAllocationConfig.getRegisterConfig(), restrictedTo);

            return super.getVerifierInstructions(lir, preallocMap, context);
        }

        @Override
        protected RegisterAllocationConfig getRegisterAllocationConfig(AllocationContext context) {
            return regAllocConfig;
        }

        protected void modifyAllocatableRegisterList(LIR lir, List<Register> registerList) {
            registerList.remove(findAllocatedRegister(lir));
        }

        protected String[] getAllocationRestrictedToArray(LIR lir, RegisterAllocationConfig cfg) {
            var regAllocatedTo = findAllocatedRegister(lir);
            var allocatableRegs = cfg.getAllocatableRegisters();
            String[] restrictedTo = new String[allocatableRegs.size() - 1];
            int i = 0;
            for (var reg : allocatableRegs) {
                if (i >= allocatableRegs.size() - 1) {
                    continue;
                }

                if (reg.equals(regAllocatedTo)) {
                    continue;
                }

                restrictedTo[i++] = reg.toString();
            }

            ignoredReg = regAllocatedTo;
            return restrictedTo;
        }

        protected Register findAllocatedRegister(LIR lir) {
            final Register[] allocatedTo = {null};
            for (var blockId : lir.getBlocks()) {
                if (allocatedTo[0] != null) {
                    break;
                }

                var block = lir.getBlockById(blockId);
                var instructions = lir.getLIRforBlock(block);
                for (var instruction : instructions) {
                    if (instruction instanceof StandardOp.LabelOp) {
                        continue;
                    }

                    instruction.forEachOutput(new ValueProcedure() {
                        @Override
                        public Value doValue(Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                            if (value instanceof RegisterValue regValue) {
                                allocatedTo[0] = regValue.getRegister();
                            }
                            return value;
                        }
                    });

                    if (allocatedTo[0] != null) {
                        break;
                    }
                }
            }
            return allocatedTo[0];
        }
    }

    /**
     * Change a register that is only used once as output, so that state of it is
     * {@link jdk.graal.compiler.lir.alloc.verifier.UnknownAllocationState unknown}.
     */
    class ForceUnknownStateInRegister extends RAVPhaseWrapper {
        protected RAVariable variable;
        protected RegisterValue oldRegister = null;
        protected RegisterValue newRegister;
        protected int idx = 0;

        protected Map<Register, Integer> regUsage;

        ForceUnknownStateInRegister() {
            super();
            this.regUsage = new EconomicHashMap<>();
        }

        @Override
        protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> instrMap, AllocationContext context) {
            modifyLIR(lir, instrMap, context);
            return super.getVerifierInstructions(lir, instrMap, context);
        }

        protected void modifyLIR(LIR lir, Map<LIRInstruction, RAVInstruction.Base> instrMap, AllocationContext context) {
            var replacementReg = getUnusedAllowedRegister(lir, context.registerAllocationConfig);
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructions = lir.getLIRforBlock(block);
                for (var instruction : instructions) {
                    if (!(instruction instanceof StandardOp.LabelOp) && instrMap.containsKey(instruction)) {
                        var op = (RAVInstruction.Op) instrMap.get(instruction);

                        if (handleOp(op, replacementReg)) {
                            return;
                        }

                        if (handleVirtualMoves(op, replacementReg)) {
                            return;
                        }
                    }

                    handleRegisterUsage(instruction);
                }
            }
        }

        protected boolean handleOp(RAVInstruction.Op op, Register replacementReg) {
            idx = 0;
            op.getLIRInstruction().forEachOutput(new ValueProcedure() {
                @Override
                public Value doValue(Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                    if (ValueUtil.isRegister(value) && op.dests.orig[idx].isVariable() && oldRegister == null && !regUsage.containsKey(ValueUtil.asRegister(value))) {
                        oldRegister = ValueUtil.asRegisterValue(value);
                        newRegister = replacementReg.asValue(oldRegister.getValueKind());
                        variable = op.dests.orig[idx].asVariable();
                        return newRegister;
                    }

                    idx++;
                    return value;
                }
            });

            return newRegister != null;
        }

        protected boolean handleVirtualMoves(RAVInstruction.Op op, Register replacementReg) {
            for (var move : op.getVirtualMoveList()) {
                var locValue = move.getLocation().getValue();
                if (ValueUtil.isRegister(locValue)) {
                    var register = ValueUtil.asRegister(locValue);

                    if (move.variableOrConstant.isVariable() && !regUsage.containsKey(register)) {
                        oldRegister = ValueUtil.asRegisterValue(locValue);
                        newRegister = replacementReg.asValue(locValue.getValueKind());
                        variable = move.variableOrConstant.asVariable();
                        move.setLocation(RAValue.create(newRegister));
                        return true;
                    }

                    regUsage.put(register, regUsage.getOrDefault(register, 1) + 1);
                }
            }
            return false;
        }

        protected void handleRegisterUsage(LIRInstruction instruction) {
            instruction.forEachOutput(new ValueProcedure() {
                @Override
                public Value doValue(Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                    if (ValueUtil.isRegister(value)) {
                        var register = ValueUtil.asRegister(value);
                        regUsage.put(register, regUsage.getOrDefault(register, 1) + 1);
                    }

                    return value;
                }
            });
        }

        protected Register getUnusedAllowedRegister(LIR lir, RegisterAllocationConfig cfg) {
            Map<Register, Integer> usedRegisters = new EconomicHashMap<>();
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructions = lir.getLIRforBlock(block);
                for (var instruction : instructions) {
                    instruction.forEachOutput(new ValueProcedure() {
                        @Override
                        public Value doValue(Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                            if (ValueUtil.isRegister(value)) {
                                var register = ValueUtil.asRegister(value);
                                usedRegisters.put(register, usedRegisters.getOrDefault(register, 1) + 1);
                            }

                            return value;
                        }
                    });
                }
            }

            for (var reg : cfg.getAllocatableRegisters()) {
                if (usedRegisters.containsKey(reg)) {
                    continue;
                }

                return reg;
            }

            return null;
        }

    }

    /**
     * Change kind of an operand to trigger a KindsMismatchException, very simply, find first
     * instruction that is not a label and look through its operand array to find first variable and
     * change its type to Illegal.
     */
    abstract class ChangeKindPhase extends RAVPhaseWrapper {
        protected Variable variable;

        @Override
        protected Map<LIRInstruction, RAVInstruction.Base> saveInstructionsPreAlloc(LIR lir) {
            var instrMap = super.saveInstructionsPreAlloc(lir);
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructions = lir.getLIRforBlock(block);
                for (var instruction : instructions) {
                    if (instruction instanceof StandardOp.LabelOp) {
                        continue;
                    }

                    var op = (RAVInstruction.Op) instrMap.get(instruction);
                    if (op == null) {
                        continue;
                    }

                    if (changeVariableKind(getTargetValueArrayPair(op))) {
                        return instrMap;
                    }
                }
            }

            return instrMap;
        }

        protected abstract RAVInstruction.ValueArrayPair getTargetValueArrayPair(RAVInstruction.Op op);

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
     * Change kind for a variable that is being used as an input.
     */
    class ChangeInputKindPhase extends ChangeKindPhase {
        @Override
        protected RAVInstruction.ValueArrayPair getTargetValueArrayPair(RAVInstruction.Op op) {
            return op.uses;
        }
    }

    /**
     * Change kind for a variable that is being used as an output.
     */
    class ChangeOutputKindPhase extends ChangeKindPhase {
        @Override
        protected RAVInstruction.ValueArrayPair getTargetValueArrayPair(RAVInstruction.Op op) {
            return op.dests;
        }
    }

    /**
     * Modifies LIR instruction location in a way where an alive operand and destination or
     * temporary use the same register.
     *
     * <p>
     * Finds the first instruction that satisfies having both alive operand and temp/output and
     * changes it so one location is the same.
     * </p>
     */
    abstract class ViolateAliveConstraint extends RAVPhaseWrapper {
        class SetAliveRegProc implements ValueProcedure {
            boolean first = true;
            Value aliveValue;

            SetAliveRegProc(Value aliveValue) {
                this.aliveValue = aliveValue;
            }

            @Override
            public Value doValue(Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                if (first) {
                    var registerValue = (RegisterValue) aliveValue;
                    var register = registerValue.getRegister();

                    first = false;

                    return register.asValue(value.getValueKind());
                }

                return value;
            }
        }

        @Override
        protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> instrMap, AllocationContext context) {
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructions = lir.getLIRforBlock(block);
                for (var instruction : instructions) {
                    if (instruction instanceof StandardOp.LabelOp) {
                        continue;
                    }

                    if (!instrMap.containsKey(instruction)) {
                        continue;
                    }

                    var op = (RAVInstruction.Op) instrMap.get(instruction);
                    if (op.alive.count == 0) {
                        continue;
                    }

                    var aliveValue = findAllocatedAliveValue(instruction);
                    var setAliveRegProc = new SetAliveRegProc(aliveValue);
                    changeLocationInTarget(instruction, setAliveRegProc);
                    if (!setAliveRegProc.first) {
                        return super.getVerifierInstructions(lir, instrMap, context);
                    }
                }
            }

            return super.getVerifierInstructions(lir, instrMap, context);
        }

        protected abstract void changeLocationInTarget(LIRInstruction instruction, ValueProcedure setAliveRegProc);

        protected Value findAllocatedAliveValue(LIRInstruction instruction) {
            final Value[] alive = {null};
            instruction.forEachAlive(new ValueProcedure() {
                @Override
                public Value doValue(Value value, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
                    alive[0] = value;
                    return value;
                }
            });
            return alive[0];
        }
    }

    /**
     * Changes LIR instruction to use same register as alive and output.
     */
    class ViolateAliveConstraintInDstPhase extends ViolateAliveConstraint {
        @Override
        protected void changeLocationInTarget(LIRInstruction instruction, ValueProcedure setAliveRegProc) {
            instruction.forEachOutput(setAliveRegProc);
        }
    }

    /**
     * Changes LIR instruction to use same register as alive and temporary.
     */
    class ViolateAliveConstraintInTempPhase extends ViolateAliveConstraint {
        @Override
        protected void changeLocationInTarget(LIRInstruction instruction, ValueProcedure setAliveRegProc) {
            instruction.forEachTemp(setAliveRegProc);
        }
    }

    abstract class ConflictPhase extends RAVPhaseWrapper {
        RAVariable targetVariable;
        RAVariable newVariable;

        @Override
        protected Map<LIRInstruction, RAVInstruction.Base> saveInstructionsPreAlloc(LIR lir) {
            var instrMap = super.saveInstructionsPreAlloc(lir);

            var startBlock = lir.getControlFlowGraph().getStartBlock();

            BasicBlock<?> conflictBlock = getConflictUseBlock(lir);
            assert conflictBlock != null;

            var variables = getVariablesFromVirtualMoves(lir, startBlock, instrMap);

            RAVariable targetVariable = getUsedVariableFromBlock(lir, conflictBlock, instrMap, variables);
            assert targetVariable != null;

            var branchBlock = getConflictSourceBlock(lir, conflictBlock);
            addVirtualMove(lir, branchBlock, instrMap, targetVariable, variables);

            return instrMap;
        }

        /**
         * Get block where a location with conflicted state will be used.
         *
         * @param lir LIR
         * @return Conflicted use block
         */
        protected abstract BasicBlock<?> getConflictUseBlock(LIR lir);

        /**
         * Get block where conflict will be created, by inserting a ValueMove instruction.
         *
         * @param lir LIR
         * @param conflictBlock Block where conflict will be used
         * @return Source of the conflict
         */
        protected abstract BasicBlock<?> getConflictSourceBlock(LIR lir, BasicBlock<?> conflictBlock);

        protected Map<RAVariable, RAValue> getVariablesFromVirtualMoves(LIR lir, BasicBlock<?> block, Map<LIRInstruction, RAVInstruction.Base> instrMap) {
            var variables = new EconomicHashMap<RAVariable, RAValue>();
            var startInstructions = lir.getLIRforBlock(block);
            for (var instruction : startInstructions) {
                var op = (RAVInstruction.Op) instrMap.get(instruction);
                if (op == null) {
                    continue;
                }

                for (var move : op.getVirtualMoveList()) {
                    if (move.variableOrConstant.isVariable()) {
                        variables.put(move.variableOrConstant.asVariable(), move.getLocation());
                    }
                }
            }
            return variables;
        }

        protected RAVariable getUsedVariableFromBlock(LIR lir, BasicBlock<?> block, Map<LIRInstruction, RAVInstruction.Base> instrMap, Map<RAVariable, RAValue> variables) {
            var endInstructions = lir.getLIRforBlock(block);
            for (var instruction : endInstructions) {
                var op = (RAVInstruction.Op) instrMap.get(instruction);
                if (op == null) {
                    continue;
                }

                for (int i = 0; i < op.uses.count; i++) {
                    var orig = op.uses.orig[i];
                    if (!orig.isVariable()) {
                        continue;
                    }

                    if (variables.containsKey(orig.asVariable())) {
                        return orig.asVariable();
                    }
                }
            }
            return null;
        }

        /**
         * Adds a conflict inducing value move to a block.
         *
         * @param lir LIR
         * @param block Block where we are putting move to
         * @param instrMap Pre allocation instruction map
         * @param targetVariable Target variable we are creating conflict with
         * @param variables Variables and their locations from previous steps
         */
        protected void addVirtualMove(LIR lir, BasicBlock<?> block, Map<LIRInstruction, RAVInstruction.Base> instrMap, RAVariable targetVariable, Map<RAVariable, RAValue> variables) {
            var instructions = lir.getLIRforBlock(block);

            boolean first = true;
            for (var instruction : instructions.reversed()) {
                if (first) {
                    first = false;
                    continue;
                }

                var op = (RAVInstruction.Op) instrMap.get(instruction);
                if (op == null) {
                    continue;
                }

                var newVar = new Variable(targetVariable.getVariable().getValueKind(), lir.numVariables() + 1);
                op.addVirtualMove(new RAVInstruction.ValueMove(null, newVar, variables.get(targetVariable).getValue()));

                this.newVariable = (RAVariable) RAValue.create(newVar);
                this.targetVariable = targetVariable;

                break;
            }
        }
    }

    class DiamondConflictPhase extends ConflictPhase {
        @Override
        protected BasicBlock<?> getConflictUseBlock(LIR lir) {
            BasicBlock<?> endBlock = null;
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                if (block.getSuccessorCount() == 0) {
                    endBlock = block;
                    break;
                }
            }
            return endBlock;
        }

        @Override
        protected BasicBlock<?> getConflictSourceBlock(LIR lir, BasicBlock<?> conflictBlock) {
            return lir.getControlFlowGraph().getStartBlock().getSuccessorAt(0);
        }
    }

    class LoopConflictPhase extends ConflictPhase {
        @Override
        protected BasicBlock<?> getConflictUseBlock(LIR lir) {
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);

                if (block.isLoopHeader()) {
                    return block;
                }
            }
            return null;
        }

        @Override
        protected BasicBlock<?> getConflictSourceBlock(LIR lir, BasicBlock<?> loopBlock) {
            for (int i = 0; i < loopBlock.getSuccessorCount(); i++) {
                var succ = loopBlock.getSuccessorAt(i);
                for (int j = 0; j < succ.getSuccessorCount(); j++) {
                    if (succ.getSuccessorAt(j).equals(loopBlock)) {
                        return succ;
                    }
                }
            }
            return null;
        }
    }

    class CalleeSavePhase extends RAVPhaseWrapper {
        Register register;

        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
            var name = target.arch.getName();

            switch (name) {
                case "AMD64" -> register = AMD64.rsi;
                case "ARM64" -> register = AArch64.r0;
            }

            super.run(target, lirGenRes, context);
        }

        @Override
        protected RegisterAllocationConfig getRegisterAllocationConfig(AllocationContext context) {
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

    class PhantomConstRematerializationPhase extends RAVPhaseWrapper {
        RAVariable variableValue;
        RAValue stackSlotValue;
        ConstantValue constant;

        class LoadConstOp extends LIRInstruction implements StandardOp.LoadConstantOp {
            public static final LIRInstructionClass<LoadConstOp> TYPE = LIRInstructionClass.create(LoadConstOp.class);

            @Def({REG, STACK}) protected AllocatableValue result;
            JavaConstant input;

            public LoadConstOp(AllocatableValue result, JavaConstant input) {
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
        protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext ctx) {
            var instructions = super.getVerifierInstructions(lir, preallocMap, ctx);

            var kind = LIRKind.value(AMD64Kind.V32_BYTE);
            var jvConst = new JavaConstant() {
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

            constant = new ConstantValue(kind, jvConst);
            var stackSlot = new SimpleVirtualStackSlot(1, kind);
            var variable = new Variable(kind, lir.numVariables() + 1);

            stackSlotValue = RAValue.create(stackSlot);
            variableValue = (RAVariable) RAValue.create(variable);

            var loadConstOp = new LoadConstOp(variable, constant.getJavaConstant());
            var constSpawnOp = new RAVInstruction.Op(loadConstOp);
            loadConstOp.forEachOutput(constSpawnOp.dests.copyOriginalProc);
            constSpawnOp.dests.curr[0] = stackSlotValue;

            var remMove = new RAVInstruction.ValueMove(new LoadConstOp(stackSlot, constant.getJavaConstant()), constant, stackSlot);

            var usage = new RAVInstruction.Op(new StandardOp.NoOp(null, 0));
            usage.uses = new RAVInstruction.ValueArrayPair(1);
            usage.uses.curr[0] = stackSlotValue;
            usage.uses.orig[0] = variableValue;

            var blockInstructions = instructions.get(0);
            blockInstructions.add(1, constSpawnOp);
            blockInstructions.add(blockInstructions.size() - 1, remMove);
            blockInstructions.add(blockInstructions.size() - 1, usage);

            return instructions;
        }
    }

    class ForceOperandFlagPhase extends RAVPhaseWrapper {
        @Override
        protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext ctx) {
            var instructions = super.getVerifierInstructions(lir, preallocMap, ctx);

            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructionsForBlock = instructions.get(block);
                for (var instruction : instructionsForBlock) {
                    if (instruction instanceof RAVInstruction.Op op) {
                        if (op.isLabel()) {
                            continue;
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
                                opFlags.remove(LIRInstruction.OperandFlag.REG);
                            } else if (LIRValueUtil.isStackSlotValue(curr.getValue())) {
                                opFlags.remove(LIRInstruction.OperandFlag.STACK);
                            } else if (LIRValueUtil.isConstantValue(curr.getValue())) {
                                opFlags.remove(LIRInstruction.OperandFlag.CONST);
                            } else {
                                continue;
                            }

                            op.uses.operandFlags.set(i, opFlags);
                            return instructions;
                        }
                    }
                }
            }

            return instructions;
        }
    }

    class ForceMissingLocationExceptionPhase extends RAVPhaseWrapper {
        @Override
        protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext ctx) {
            var instructions = super.getVerifierInstructions(lir, preallocMap, ctx);

            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructionsForBlock = instructions.get(block);
                for (var instruction : instructionsForBlock) {
                    if (instruction instanceof RAVInstruction.Op op) {
                        if (op.isLabel()) {
                            continue;
                        }

                        for (int i = 0; i < op.uses.count; i++) {
                            var curr = op.uses.curr[i];
                            var orig = op.uses.orig[i];

                            if (orig.equals(curr)) {
                                continue;
                            }

                            op.uses.curr[i] = null;
                            return instructions;
                        }
                    }
                }
            }

            return instructions;
        }
    }

    class ExcessReferencePhase extends RAVPhaseWrapper {
        @Override
        protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> preallocMap, AllocationContext ctx) {
            var instructions = super.getVerifierInstructions(lir, preallocMap, ctx);

            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructionsForBlock = instructions.get(block);

                RAVInstruction.Op prev = null;
                for (var instruction : instructionsForBlock) {
                    if (instruction instanceof RAVInstruction.Op op) {
                        if (prev != null) {
                            var curr = prev.dests.curr[0];
                            var kind = curr.getLIRKind().makeUnknownReference();
                            var refLocation = RAValue.create(curr.asRegister().getRegister().asValue(kind));

                            op.references = List.of(refLocation);
                            return instructions;
                        }

                        if (op.isLabel()) {
                            continue;
                        }

                        if (op.dests.count == 0) {
                            continue;
                        }

                        var curr = op.dests.curr[0];
                        if (!curr.isRegister() || !curr.getLIRKind().isValue()) {
                            continue;
                        }

                        // This instruction will define dest location
                        // next instruction will verify the references list
                        // -> no reference there!
                        prev = op;
                    }
                }
            }

            return instructions;
        }
    }

    class PhiResolver extends RAVPhaseWrapper {
        RAVInstruction.Op label;
        RAValue location;

        @Override
        protected BlockMap<List<RAVInstruction.Base>> getVerifierInstructions(LIR lir, Map<LIRInstruction, RAVInstruction.Base> instrMap, AllocationContext context) {
            var instructions = super.getVerifierInstructions(lir, instrMap, context);

            Set<Register> usedRegisters = new EconomicHashSet<>();
            for (var blockId : lir.getBlocks()) {
                var block = lir.getBlockById(blockId);
                var instructionsForBlock = instructions.get(block);
                for (var instruction : instructionsForBlock) {
                    switch (instruction) {
                        case RAVInstruction.Op op -> {
                            for (int i = 0; i < op.dests.count; i++) {
                                var dest = op.dests.curr[i];
                                if (dest.isRegister()) {
                                    usedRegisters.add(dest.asRegister().getRegister());
                                }
                            }
                        }
                        case RAVInstruction.ValueMove move -> {
                            var regValue = move.getLocation();
                            if (regValue.isRegister()) {
                                usedRegisters.add(regValue.asRegister().getRegister());
                            }
                        }
                        case RAVInstruction.LocationMove move -> {
                            if (move.to.isRegister()) {
                                usedRegisters.add(move.to.asRegister().getRegister());
                            }
                        }
                        default -> {
                        }
                    }
                }
            }

            Register targetRegister = null;
            var allocatableRegisters = context.registerAllocationConfig.getAllocatableRegisters();
            for (var register : allocatableRegisters) {
                if (!usedRegisters.contains(register)) {
                    targetRegister = register;
                    break;
                }
            }

            assert targetRegister != null : "No register available for phi resolver test";

            var labelOp = (RAVInstruction.Op) instructions.get(0).getFirst();

            var newDst = new RAVInstruction.ValueArrayPair(labelOp.dests.count + 1);
            for (int i = 0; i < labelOp.dests.count; i++) {
                newDst.curr[i] = labelOp.dests.curr[i];
                newDst.orig[i] = labelOp.dests.orig[i];
                newDst.operandFlags.add(labelOp.dests.operandFlags.get(i));
            }

            var variable = RAVariable.create(new Variable(ValueKind.Illegal, lir.numVariables() + 1));
            location = RAValue.create(targetRegister.asValue());

            newDst.orig[labelOp.dests.count] = variable;
            newDst.curr[labelOp.dests.count] = null;
            newDst.operandFlags.add(EnumSet.of(LIRInstruction.OperandFlag.REG));

            labelOp.dests = newDst;
            label = labelOp;

            var usage = new RAVInstruction.Op(new StandardOp.NoOp(null, 0));
            usage.uses = new RAVInstruction.ValueArrayPair(1);
            usage.uses.orig[0] = variable;
            usage.uses.curr[0] = location;
            usage.uses.operandFlags.add(EnumSet.of(LIRInstruction.OperandFlag.REG));

            instructions.get(0).add(instructions.get(0).size() - 1, usage);

            return instructions;
        }
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        if (validSuites) {
            return createLIRSuitesWithVerifier(options);
        }

        return createModifiedVerifierLIRSuites(options);
    }

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

    protected <T extends RAVException> void assertException(Class<T> expected) {
        if (exception.getCause() != null) {
            exception = exception.getCause();
        }

        Assert.assertNotNull("No exception was thrown", exception);
        Assert.assertTrue("Unexpected exception: " + exception, expected.isInstance(exception));
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
                 * This code is outside of the loop. Accessing the array reqires a ValueProxyNode.
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

    @Before
    public void prepareTest() {
        exception = null;
        validSuites = true;
        phase = null;
    }

    @Test
    public void testInvalidRegisterUsed() {
        var disallowedRegPhase = new DisallowedRegisterPhase();
        phase = disallowedRegPhase;

        var methodName = "simple";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(InvalidRegisterUsedException.class);
        var iruException = (InvalidRegisterUsedException) exception;
        // Used forbidden register
        Assert.assertEquals(iruException.register, disallowedRegPhase.ignoredReg);
    }

    @Test
    public void testWrongVariableInState() {
        var changeVariablePhase = new ChangeVariablePhase();
        phase = changeVariablePhase;

        var methodName = "simple";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(ValueNotInRegisterException.class);

        var vnrException = (ValueNotInRegisterException) exception;

        // Expected original variable
        Assert.assertEquals(changeVariablePhase.originalVariable, vnrException.variable);
        Assert.assertTrue(vnrException.state instanceof ValueAllocationState);
        // But new variable is there instead
        Assert.assertEquals(changeVariablePhase.newVariable, ((ValueAllocationState) vnrException.state).getRAValue());
    }

    @Test
    public void testUnknownLocation() {
        var changeLocationPhase = new ForceUnknownStateInRegister();
        phase = changeLocationPhase;

        var methodName = "keep";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(ValueNotInRegisterException.class);

        var vnrException = (ValueNotInRegisterException) exception;
        Assert.assertTrue(vnrException.state.isUnknown());
        Assert.assertEquals(vnrException.variable, changeLocationPhase.variable);
        Assert.assertEquals(vnrException.location.getValue(), changeLocationPhase.oldRegister);
    }

    @Test
    public void testConflictedLoc() {
        var diamondConflictPhase = new DiamondConflictPhase();
        phase = diamondConflictPhase;

        var methodName = "diamond";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(ValueNotInRegisterException.class);
        var vnrException = (ValueNotInRegisterException) exception;
        Assert.assertTrue(vnrException.state.isConflicted());

        var confState = (ConflictedAllocationState) vnrException.state;
        var conflitedStates = confState.getConflictedStates();
        Assert.assertEquals(2, conflitedStates.size());
        for (var state : conflitedStates) {
            Assert.assertTrue(state.getRAValue().equals(diamondConflictPhase.newVariable) || state.getRAValue().equals(diamondConflictPhase.targetVariable));
        }
    }

    @Test
    public void testConflictInLoop() {
        var loopConflictPhase = new LoopConflictPhase();
        phase = loopConflictPhase;

        var methodName = "sum";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(ValueNotInRegisterException.class);
        var vnrException = (ValueNotInRegisterException) exception;
        Assert.assertTrue(vnrException.state.isConflicted());

        var confState = (ConflictedAllocationState) vnrException.state;
        var conflitedStates = confState.getConflictedStates();
        Assert.assertEquals(2, conflitedStates.size());
        for (var state : conflitedStates) {
            Assert.assertTrue(state.getRAValue().equals(loopConflictPhase.newVariable) || state.getRAValue().equals(loopConflictPhase.targetVariable));
        }
    }

    @Test
    public void testConflictInInfiniteLoop() {
        var loopConflictPhase = new LoopConflictPhase();
        phase = loopConflictPhase;

        var methodName = "loop";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(ValueNotInRegisterException.class);
        var vnrException = (ValueNotInRegisterException) exception;
        Assert.assertTrue(vnrException.state.isConflicted());

        var confState = (ConflictedAllocationState) vnrException.state;
        var conflitedStates = confState.getConflictedStates();
        Assert.assertEquals(2, conflitedStates.size());
        for (var state : conflitedStates) {
            Assert.assertTrue(state.getRAValue().equals(loopConflictPhase.newVariable) || state.getRAValue().equals(loopConflictPhase.targetVariable));
        }
    }

    @Test
    public void testAliveConstraintInDest() {
        var violateAliveConstraintPhase = new ViolateAliveConstraintInDstPhase();
        phase = violateAliveConstraintPhase;

        var methodName = "arrayLengthProviderSnippet";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(AliveConstraintViolationException.class);
    }

    @Test
    public void testAliveConstraintInTemp() {
        var violateAliveConstraintPhase = new ViolateAliveConstraintInTempPhase();
        phase = violateAliveConstraintPhase;

        var methodName = "aliveConstraintSnippet";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(AliveConstraintViolationException.class);
    }

    @Test
    public void testKindMatchAfterAlloc() {
        var changeInputKindPhase = new ChangeInputKindPhase();
        phase = changeInputKindPhase;

        var methodName = "keep";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(KindsMismatchException.class);
        var kmException = (KindsMismatchException) exception;
        Assert.assertEquals(changeInputKindPhase.variable.index, kmException.value1.asVariable().getVariable().index);
        Assert.assertEquals(changeInputKindPhase.variable.getValueKind(), kmException.value2.getValue().getValueKind());
        Assert.assertEquals(ValueKind.Illegal, kmException.value1.getValue().getValueKind());
        Assert.assertTrue(kmException.origVsCurr);
    }

    @Test
    public void testKindMatchInState() {
        var changeInputKindPhase = new ChangeOutputKindPhase();
        phase = changeInputKindPhase;

        var methodName = "keep";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(KindsMismatchException.class);
        var kmException = (KindsMismatchException) exception;
        Assert.assertEquals(changeInputKindPhase.variable.index, kmException.value1.asVariable().getVariable().index);
        Assert.assertEquals(changeInputKindPhase.variable.index, kmException.value2.asVariable().getVariable().index);
        Assert.assertEquals(changeInputKindPhase.variable.getValueKind(), kmException.value1.getValue().getValueKind());
        Assert.assertEquals(ValueKind.Illegal, kmException.value2.getValue().getValueKind());
        Assert.assertFalse(kmException.origVsCurr);
    }

    @Test
    public void testCalleeSaveRetrieval() {
        var calleeSavePhase = new CalleeSavePhase();
        phase = calleeSavePhase;

        var methodName = "simple";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(CalleeSavedRegisterNotRetrievedException.class);
        var csException = (CalleeSavedRegisterNotRetrievedException) exception;
        Assert.assertEquals(csException.register.getRegister(), calleeSavePhase.register);
    }

    @Test
    public void testOperandFlags() {
        var forceOperandFlagPhase = new ForceOperandFlagPhase();
        phase = forceOperandFlagPhase;

        var methodName = "simple";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(OperandFlagMismatchException.class);
    }

    @Test
    public void testMissingLocation() {
        var forceMissingLocationException = new ForceMissingLocationExceptionPhase();
        phase = forceMissingLocationException;

        var methodName = "simple";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(MissingLocationException.class);
    }

    @Test
    public void testExcessReference() {
        var forceExcessReference = new ExcessReferencePhase();
        phase = forceExcessReference;

        var methodName = "simple";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(MissingReferenceException.class);
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
        phase = constRemPhase;

        var methodName = "simple";
        compile(getResolvedJavaMethod(methodName), null);

        Assert.assertNull(exception);

        compileModified(methodName);

        assertException(ConstantRematerializedToStackException.class);
        var crException = (ConstantRematerializedToStackException) exception;
        Assert.assertEquals(constRemPhase.stackSlotValue, crException.location);
        Assert.assertEquals(constRemPhase.variableValue, crException.variable);
        Assert.assertEquals(constRemPhase.constant, crException.state.getValue());
    }
}

// Taken from EnumSwitchTest, shortened
enum Ex {
    E0,
    E1,
    E2,
    E3,
}
