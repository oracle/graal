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
import jdk.graal.compiler.core.common.LIRKindWithCast;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

import java.util.List;

/**
 * Verification state a block is in.
 */
public class BlockVerifierState {
    /**
     * Map maintaining mapping between locations and their state.
     */
    public AllocationStateMap values;

    /**
     * Register allocation config we use to check if only
     * allocatable registers are the ones used.
     */
    protected RegisterAllocationConfig registerAllocationConfig;

    /**
     * Conflict resolver for constant materialization.
     */
    protected ConflictResolver conflictConstantResolver;

    public BlockVerifierState(BasicBlock<?> block, RegisterAllocationConfig registerAllocationConfig, ConflictResolver constantConflictResolver) {
        this.values = new AllocationStateMap(block, registerAllocationConfig);
        this.registerAllocationConfig = registerAllocationConfig;
        this.conflictConstantResolver = constantConflictResolver;
    }

    protected BlockVerifierState(BasicBlock<?> block, BlockVerifierState other) {
        this.registerAllocationConfig = other.registerAllocationConfig;
        this.conflictConstantResolver = other.conflictConstantResolver;
        this.values = new AllocationStateMap(block, other.values);
    }

    public AllocationStateMap getValues() {
        return values;
    }

    /**
     * Merge states of block and it's predecessor. This process
     * creates a new state based on contents of the predecessor,
     * creating conflicts where current locations do not match.
     *
     * @param other Predecessor of this block
     * @return Was this state changed?
     */
    public boolean meetWith(BlockVerifierState other) {
        return this.values.mergeWith(other.getValues());
    }

    /**
     * Check state values stored in LIRFrameState same way other operands,
     * except we skip those where the state values are incorrectly stored,
     * after stack allocation, because some values are no longer present.
     *
     * @param op    Operation for which we are checking state values
     * @param block Block where this operation is located
     */
    protected void checkStateValues(RAVInstruction.Op op, BasicBlock<?> block) {
        if (!op.hasCompleteState()) {
            // Some values are null after allocation because of stack slot allocator
            // because it is skipped when iteration (StackLockValue).
            return;
        }

        checkInputs(op.stateValues, op, block);
    }

    /**
     * Verify the correspondence of original variables used in instructions
     * are stored in the state of current locations.
     *
     * @param values Array of pairs of current location and original variable.
     * @param op     Operation this input array of values belongs to
     * @param block  Block this operation is in
     */
    protected void checkInputs(RAVInstruction.ValueArrayPair values, RAVInstruction.Op op, BasicBlock<?> block) {
        // Check that incoming values are not unknown or conflicted - these only matter if used
        for (int idx = 0; idx < values.count; idx++) {
            checkOperand(values.orig[idx], values.curr[idx], op, block);
        }
    }

    /**
     * Check that original variable matches symbol stored at
     * the current location in the allocation state map.
     * <p>
     * We also check that kinds match and possibly
     * rematerialize variables at this point in the
     * state map.
     *
     * @param orig  Original variable
     * @param curr  Current location
     * @param op    Operation where these are used
     * @param block Block where this operation is
     */
    protected void checkOperand(RAValue orig, RAValue curr, RAVInstruction.Op op, BasicBlock<?> block) {
        assert orig != null;

        if (curr == null) {
            if (op.isJump()) {
                // This can happen if a variable without a usage is passed in
                // even when this variable acts as an alias to the next label
                // there's no usage, so no location.
                return;
            }

            throw new MissingLocationError(op.lirInstruction, block, orig);
        }

        if (!kindsEqual(orig, curr)) {
            if (!op.isJump()) {
                throw new KindsMismatchException(op.lirInstruction, block, orig, curr, true);
            }

            // Skip when jump due to this case:
            // "rdx|QWORD[*] = MOVE input: rdx|QWORD[.+] moveKind: QWORD"
            // this move is inserted by the allocator and changes type
            // of rdx from [.+] (compressed reference) (same as original variable) to [*] (invalid reference)
        }

        AllocationState state = this.values.get(curr);
        if (orig.equals(curr)) {
            // For these cases we do not consider checking state taking the original
            // register as a symbol, because there's too many cases when this does
            // not work, for example RETURN with rax tends to contain the actual
            // generated variable instead of rax symbol, or NEAR_FOREIGN_CALL
            // keeps its own registers before and after allocation, but those
            // can also contain different variable symbols.
            return;
        }

        if (ValueUtil.isStackSlot(curr.getValue()) && LIRValueUtil.isVirtualStackSlot(orig.getValue())) {
            // TestCase: IntegerDivRemCanonicalizationTest
            // instruction r10|QWORD = STACKLEA slot: stack:80|ILLEGAL[*] in B0
            // had vstack:0, which is not mentioned in first label or elsewhere
            // so symbol vstack:0 won't be found
            return;
        }

        if (state.isUnknown()) {
            throw new ValueNotInRegisterException(op.lirInstruction, block, orig, curr, state);
        }

        if (state.isConflicted()) {
            if (orig.isVariable()) {
                var variable = orig.asVariable();
                var resolvedState = this.conflictConstantResolver.resolveConflictedState(variable, (ConflictedAllocationState) state, curr);
                if (resolvedState != null && resolvedState.getValue().equals(orig.getValue())) {
                    this.values.put(curr, resolvedState);
                    return;
                }
            }

            throw new ValueNotInRegisterException(op.lirInstruction, block, orig, curr, state);
        }

        if (state instanceof ValueAllocationState valAllocState) {
            if (!valAllocState.value.equals(orig)) {
                if (LIRValueUtil.isConstantValue(valAllocState.value.getValue()) && orig.isVariable()) {
                    var variable = orig.asVariable();
                    var resolvedState = this.conflictConstantResolver.resolveValueState(variable, valAllocState, curr);
                    if (resolvedState != null && resolvedState.getValue().equals(orig.getValue())) {
                        this.values.put(curr, resolvedState);
                        return;
                    }
                }

                throw new ValueNotInRegisterException(op.lirInstruction, block, orig, curr, state);
            }

            if (!kindsEqualFromState(orig, valAllocState.value)) {
                throw new KindsMismatchException(op.lirInstruction, block, orig, valAllocState.value, false);
            }

            return;
        }

        throw GraalError.shouldNotReachHere("Invalid state " + state);
    }

    /**
     * Are kinds equal even when casting (LIRKindWithCast) is present?
     *
     * @param orig Original variable
     * @param curr Current location
     * @return Are they equal?
     */
    protected boolean kindsEqual(RAValue orig, RAValue curr) {
        var origKind = orig.getValue().getValueKind();
        var currKind = curr.getValue().getValueKind();

        if (origKind instanceof LIRKindWithCast castKind) {
            origKind = castKind.getActualKind();
        }

        if (currKind instanceof LIRKindWithCast castKind) {
            currKind = castKind.getActualKind();
        }

        return currKind.equals(origKind);
    }

    /**
     * Are kinds equal even when CastValue is present?
     * <p>
     * We need to ignore the cast value because the currently stored
     * value will not be cast.
     *
     * @param orig      Original variable
     * @param fromState Value stored in state of the current location
     * @return Are they equal?
     */
    protected boolean kindsEqualFromState(RAValue orig, RAValue fromState) {
        ValueKind<?> origKind = orig.getValue().getValueKind();
        ValueKind<?> currKind = fromState.getValue().getValueKind();
        if (LIRValueUtil.isCast(orig.getValue())) {
            origKind = LIRValueUtil.uncast(orig.getValue()).getValueKind();
        }

        return origKind.equals(currKind);
    }

    /**
     * Check that all instruction arrays of pairs of original variable and current location
     * check out to the state stored in for this block.
     *
     * @param instruction Instruction we are checking
     * @param block       Block where it is located
     */
    public void check(RAVInstruction.Base instruction, BasicBlock<?> block) {
        if (instruction instanceof RAVInstruction.Op op) {
            checkInputs(op.uses, op, block);
            checkInputs(op.alive, op, block);
            checkStateValues(op, block);

            checkTempKind(op, block);
            checkAliveConstraint(op, block);

            checkOperandFlags(op.dests, op, block);
            checkOperandFlags(op.uses, op, block);
            checkOperandFlags(op.alive, op, block);
            checkOperandFlags(op.temp, op, block);
        }
    }

    /**
     * Check if kinds in the temporary array match before allocation
     * original variables with after allocation concrete locations.
     *
     * @param op    Instruction we update state from
     * @param block Block where it is located
     * @throws KindsMismatchException if a pair does not match
     */
    protected void checkTempKind(RAVInstruction.Op op, BasicBlock<?> block) {
        for (int i = 0; i < op.temp.count; i++) {
            var curr = op.temp.curr[i];
            var orig = op.temp.orig[i];

            if (!kindsEqual(orig, curr)) {
                // Make sure the assigned register has the correct kind for temp.
                throw new KindsMismatchException(op.lirInstruction, block, orig, curr, true);
            }
        }
    }


    /**
     * Check if alive constraint is not being violated,
     * when one location is supposed to be alive after instruction
     * is complete, but is used either as an output or a generic input.
     *
     * @param instruction Instruction with alive inputs
     * @param block       Block this instruction is in
     */
    protected void checkAliveConstraint(RAVInstruction.Op instruction, BasicBlock<?> block) {
        for (int i = 0; i < instruction.alive.count; i++) {
            RAValue value = instruction.alive.curr[i];
            if (value == null) {
                continue;
            }

            if (value.isIllegal()) {
                continue;
            }

            for (int j = 0; j < instruction.temp.count; j++) {
                if (value.equals(instruction.temp.curr[j])) {
                    throw new AliveConstraintViolationException(instruction.lirInstruction, block, value, false);
                }
            }

            for (int j = 0; j < instruction.dests.count; j++) {
                if (value.equals(instruction.dests.curr[j])) {
                    throw new AliveConstraintViolationException(instruction.lirInstruction, block, value, true);
                }
            }
        }
    }

    /**
     * Make sure concrete current locations changed by the allocator
     * are not violating set of LIRInstruction.OperandFlag flags,
     * which specify what type can they be. This is done on every
     * array of pairs (dest, uses, alive, temp).
     *
     * @param values Value array pair we are verifying
     * @param op     Instruction which holds this array, for tracing in exceptions
     * @param block  Block this instruction is in, for tracing in exceptions
     * @throws OperandFlagMismatchException Operand is a wrong type based on OperandFlag set.
     */
    protected void checkOperandFlags(RAVInstruction.ValueArrayPair values, RAVInstruction.Op op, BasicBlock<?> block) {
        for (int i = 0; i < values.count; i++) {
            var curr = values.curr[i];
            if (curr == null) {
                continue;
            }

            var flags = values.operandFlags.get(i);
            var currValue = curr.getValue();
            if (LIRValueUtil.isStackSlotValue(currValue)) {
                if (flags.contains(LIRInstruction.OperandFlag.STACK)) {
                    continue;
                }
            } else if (ValueUtil.isRegister(currValue)) {
                if (flags.contains(LIRInstruction.OperandFlag.REG)) {
                    continue;
                }
            } else if (LIRValueUtil.isConstantValue(currValue)) {
                if (flags.contains(LIRInstruction.OperandFlag.CONST)) {
                    continue;
                }
            } else if (Value.ILLEGAL.equals(currValue)) {
                if (flags.contains(LIRInstruction.OperandFlag.ILLEGAL)) {
                    continue;
                }
            }

            throw new OperandFlagMismatchException(op, block, currValue, flags);
        }
    }

    /**
     * Update the current state based on outputs of this instruction.
     * Setting contents of current location in allocation state map to
     * the symbol that was present before allocation was completed.
     *
     * @param instruction Instruction we update state from
     * @param block       Block where it is located
     */
    public void update(RAVInstruction.Base instruction, BasicBlock<?> block) {
        switch (instruction) {
            case RAVInstruction.Op op -> this.updateWithOp(op, block);
            case RAVInstruction.ValueMove virtMove -> this.updateWithValueMove(virtMove, block);
            case RAVInstruction.LocationMove move -> this.values.putClone(move.to, this.values.get(move.from));
            default -> throw GraalError.shouldNotReachHere("Invalid RAV instruction " + instruction);
        }
    }

    /**
     * Update the state using a generic operation,
     * based on contents of its output array, storing
     * symbols pre-allocation to current locations after
     * allocation.
     *
     * @param op    Operation we update state from
     * @param block Block where it is located
     */
    protected void updateWithOp(RAVInstruction.Op op, BasicBlock<?> block) {
        for (int i = 0; i < op.dests.count; i++) {
            if (op.dests.orig[i].isIllegal()) {
                continue; // Safe to ignore, when destination is illegal value, not when used.
            }

            assert op.dests.orig[i] != null;

            if (op.dests.curr[i] == null && op.isLabel()) {
                continue; // Unused label variable
            }

            assert op.dests.curr[i] != null;

            RAValue location = op.dests.curr[i];
            RAValue variable = op.dests.orig[i];

            if (location.equals(variable)) {
                // Only check register validity if it was changed by the register allocator
                // for example: rbp is used as input to start block and forbidden to be used by the allocator
                this.values.putWithoutRegCheck(location, new ValueAllocationState(variable, op, block));
            } else {
                this.values.put(location, new ValueAllocationState(variable, op, block));
            }
        }

        // For calls, we hope that all saved registers are listed, even
        // though they are not as per checking RegisterConfig.getCallerSavedRegisters()
        // but using said list to clear them causes issues.
        for (int i = 0; i < op.temp.count; i++) {
            var value = op.temp.curr[i];
            if (value.isIllegal()) {
                continue;
            }

            // We cannot believe the contents of registers used as temp, thus we need to reset.
            RAValue location = op.temp.curr[i];
            this.values.put(location, UnknownAllocationState.INSTANCE);
        }
    }

    /**
     * Update block state with a safe point list of live references deemed by the GC,
     * any other references not included in said list are to be set as unknown so
     * there's no freed pointer use.
     *
     * Not in use currently, because we do not have a reliable list of live references.
     *
     * Reference test case: AOTTutorial
     *
     * @param references List of references deemed as live by the GC
     */
    protected void updateWithSafePoint(List<RAValue> references) {
        for (var entry : this.values.internalMap.entrySet()) {
            var state = entry.getValue();
            if (state.isUnknown() || state.isConflicted()) {
                continue; // Do not care, retain information
            }

            var valueAllocState = (ValueAllocationState) state;
            if (valueAllocState.getValue().getValueKind(LIRKind.class).isValue()) {
                continue; // Not a reference, continue
            }

            boolean referenceFound = false;
            for (var reference : references) {
                if (reference.equals(entry.getKey())) {
                    referenceFound = true;
                    break;
                }
            }

            if (referenceFound) {
                continue;
            }

            // Remove all references that are not present in the references list,
            // maybe it makes sense to keep registers that have live references,
            // that are same as the one in references list? Because said list
            // is expected to have stack slots and registers can retain same references.
            this.values.put(entry.getKey(), UnknownAllocationState.INSTANCE);
        }
    }

    /**
     * Update state with a ValueMove, if locations is concrete,
     * we set it to a variable/constant, if it's a variable to variable
     * move, then all locations containing old variable need to be changed
     * to the new variable.
     *
     * @param valueMove Value move we update state from
     * @param block     Block where it is located
     */
    protected void updateWithValueMove(RAVInstruction.ValueMove valueMove, BasicBlock<?> block) {
        if (valueMove.getLocation().isVariable()) {
            // Whenever there is a move between two variables,
            // we need to change every location containing the old variable (rhs - source)
            // to the new variable (lhs - destination)
            // v4|QWORD[.] = MOVE input: v3|QWORD[.] moveKind: QWORD
            // Move before allocation
            // TestCase: BoxingTest.boxBoolean
            var locations = this.values.getValueLocations(valueMove.variableOrConstant);
            for (var location : locations) {
                this.values.put(location, new ValueAllocationState(valueMove.getLocation(), valueMove, block));
            }
        } else {
            this.values.put(valueMove.getLocation(), new ValueAllocationState(valueMove.variableOrConstant, valueMove, block));
        }
    }
}
