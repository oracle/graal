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
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Verification state a block is in.
 */
public class MergedBlockVerifierState {
    /**
     * Map maintaining mapping between locations and their state.
     */
    public MergedAllocationStateMap values;

    /**
     * Register allocation config we use to check if only
     * allocatable registers are the ones used.
     */
    protected RegisterAllocationConfig registerAllocationConfig;

    /**
     * Conflict resolver for constant materialization.
     */
    protected ConflictResolver conflictConstantResolver;

    public MergedBlockVerifierState(BasicBlock<?> block, RegisterAllocationConfig registerAllocationConfig, ConflictResolver constantConflictResolver) {
        this.values = new MergedAllocationStateMap(block, registerAllocationConfig);
        this.registerAllocationConfig = registerAllocationConfig;
        this.conflictConstantResolver = constantConflictResolver;
    }

    protected MergedBlockVerifierState(BasicBlock<?> block, MergedBlockVerifierState other) {
        this.registerAllocationConfig = other.registerAllocationConfig;
        this.conflictConstantResolver = other.conflictConstantResolver;
        this.values = new MergedAllocationStateMap(block, other.values);
    }

    public MergedAllocationStateMap getValues() {
        return values;
    }

    /**
     * Merge states of block and it's predecessor.
     *
     * @param other Predecessor of this block
     * @return Was this state changed?
     */
    public boolean meetWith(MergedBlockVerifierState other) {
        return this.values.mergeWith(other.getValues());
    }

    /**
     * Verify the correspondence of original variables used in instructions
     * are stored in the state of current locations.
     *
     * @param values  Array of pairs of current location and original variable.
     * @param op      Operation this input array of values belongs to
     * @param block   Block this operation is in
     * @param labelOp Label of the successor block, in-case resolution was incomplete.
     */
    protected void checkInputs(RAVInstruction.ValueArrayPair values, RAVInstruction.Op op, BasicBlock<?> block, RAVInstruction.Op labelOp) {
        // Check that incoming values are not unknown or conflicted - these only matter if used
        for (int idx = 0; idx < values.count; idx++) {
            var orig = values.orig[idx];
            var curr = values.curr[idx];

            assert orig != null;

            if (curr == null) {
                if (op.isJump()) {
                    // This can happen if a variable without a usage is passed in
                    // even when this variable acts as an alias to the next label
                    // there's no usage, so no location.
                    continue;
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
                continue;
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
                        continue;
                    }
                }

                throw new ValueNotInRegisterException(op.lirInstruction, block, orig, curr, state);
            }

            if (state instanceof ValueAllocationState valAllocState) {
                if (!kindsEqualFromState(orig, valAllocState.value)) {
                    throw new KindsMismatchException(op.lirInstruction, block, orig, valAllocState.value, false);
                }

                if (!valAllocState.value.equals(orig)) {
                    if (LIRValueUtil.isCast(orig.getValue()) && valAllocState.value.getValue().equals(LIRValueUtil.uncast(orig.getValue()))) {
                        continue; // They aren't equal here because of the CastValue, so if they are equal afterwards, we skip next part.
                    }

                    if (LIRValueUtil.isConstantValue(valAllocState.value.getValue())) {
                        var variable = orig.asVariable();
                        var resolvedState = this.conflictConstantResolver.resolveValueState(variable, valAllocState, curr);
                        if (resolvedState != null && resolvedState.getValue().equals(orig.getValue())) {
                            this.values.put(curr, resolvedState);
                            continue;
                        }
                    }

                    throw new ValueNotInRegisterException(op.lirInstruction, block, orig, curr, state);
                }

                continue;
            }

            throw GraalError.shouldNotReachHere("Invalid state " + state);
        }
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
     * </p>
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
     * @param labelOp     Label of the successor block, in-case resolution failed
     */
    public void check(RAVInstruction.Base instruction, BasicBlock<?> block, RAVInstruction.Op labelOp) {
        if (instruction instanceof RAVInstruction.Op op) {
            checkInputs(op.uses, op, block, labelOp);
            checkInputs(op.alive, op, block, labelOp);
            checkInputs(op.stateValues, op, block, labelOp);

            checkTempKind(op, block);
            checkAliveConstraint(op, block);
            // checkStateJavaKinds(op, block);

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
     * Checks values from frame state to see if ones marked as Object JavaKind,
     * are a reference in LIRKind.
     *
     * @param op    Operation which we are checking state values for
     * @param block In which block is this operation
     */
    protected void checkStateJavaKinds(RAVInstruction.Op op, BasicBlock<?> block) {
        if (op.frameSlotKinds == null) {
            return;
        }

        for (int i = 0; i < op.frameSlotKinds.length; i++) {
            var kind = op.frameSlotKinds[i];
            var orig = op.origFrameSlots[i];
            var curr = op.currFrameSlots[i];

            if (!(orig instanceof AllocatableValue origAllocValue) || Value.ILLEGAL.equals(origAllocValue)) {
                continue;
            }

            var currAllocValue = (AllocatableValue) curr;

            var origLIRKind = origAllocValue.getValueKind(LIRKind.class);
            var currLIRKind = currAllocValue.getValueKind(LIRKind.class);
            if (JavaKind.Object.equals(kind)) {
                if (!origLIRKind.isValue() && !currLIRKind.isValue()) {
                    continue;
                }

                throw new RAVException(origAllocValue + " -> " + currAllocValue + " not an object java kind when marked as a reference");
            } else {
                if (origLIRKind.isValue() && currLIRKind.isValue()) {
                    continue;
                }

                // Test: PointerTrackingTest
                // has vstack marked as a reference, but long JavaKind.
                throw new RAVException(origAllocValue + " -> " + currAllocValue + " is a reference when not marked as an object java kind");
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
     * Update the state using a generic operation.
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

            if (op.dests.curr[i] == null) {
                continue;
            }

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
     * <p>
     * Not in use currently, because we do not have a reliable list of live references.
     * <p>
     * Reference test case: AOTTutorial
     *
     * @param references List of references deemed as live by the GC
     */
    protected void updateWithSafePoint(List<RAValue> references) {
        List<RAValue> toRemove = new ArrayList<>();
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
        if (valueMove.location.isVariable()) {
            // Whenever there is a move between two variables,
            // we need to change every location containing the old variable (rhs - source)
            // to the new variable (lhs - destination)
            // v4|QWORD[.] = MOVE input: v3|QWORD[.] moveKind: QWORD
            // Move before allocation
            // TestCase: BoxingTest.boxBoolean
            var locations = this.values.getValueLocations(valueMove.variableOrConstant);
            for (var location : locations) {
                this.values.put(location, new ValueAllocationState(valueMove.location, valueMove, block));
            }
        } else {
            this.values.put(valueMove.location, new ValueAllocationState(valueMove.variableOrConstant, valueMove, block));
        }
    }
}
