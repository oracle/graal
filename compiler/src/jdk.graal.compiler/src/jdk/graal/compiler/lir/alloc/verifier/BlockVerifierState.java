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
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.AliveConstraintViolationException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.CalleeSavedRegisterNotRetrievedException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.ConstantRematerializedToStackException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.JavaKindReferenceMismatchException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.KindsMismatchException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.MissingLocationException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.MissingReferenceException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.OperandFlagMismatchException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.RAVException;
import jdk.graal.compiler.lir.alloc.verifier.exceptions.ValueNotInRegisterException;
import jdk.graal.compiler.lir.dfa.LocationMarker;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Verification state a block is in, holds a mapping between locations and their allocation states,
 * which can be unknown, value (with symbol content) or conflicted; multiple values conflict with
 * each other.
 */
public class BlockVerifierState {
    /**
     * Map maintaining mapping between locations and their state.
     */
    public final AllocationStateMap values;

    /**
     * Register allocation config we use to check if only allocatable registers are the ones used.
     */
    protected final RegisterAllocationConfig registerAllocationConfig;

    /**
     * Block this state pertains to.
     */
    protected final BasicBlock<?> block;

    protected final CalleeSaveMap calleeSaveMap;

    public BlockVerifierState(BasicBlock<?> block, RegisterAllocationConfig registerAllocationConfig,
                    CalleeSaveMap calleeSaveMap) {
        this.values = new AllocationStateMap(block, registerAllocationConfig);
        this.registerAllocationConfig = registerAllocationConfig;
        this.calleeSaveMap = calleeSaveMap;
        this.block = block;
    }

    public BlockVerifierState(BasicBlock<?> block, BlockVerifierState other) {
        this.registerAllocationConfig = other.registerAllocationConfig;
        this.values = new AllocationStateMap(block, other.values);
        this.calleeSaveMap = other.calleeSaveMap;
        this.block = block;
    }

    public AllocationStateMap getValues() {
        return values;
    }

    /**
     * Merge states of block and it's predecessor. This process modifies the current state based on
     * the contents of the predecessor, creating conflicts where current locations do not match.
     *
     * @param other Predecessor of this block
     * @return Was this state changed?
     */
    public boolean meetWith(BlockVerifierState other) {
        return this.values.mergeWith(other.getValues());
    }

    /**
     * Check state values stored in LIRFrameState same way other operands, except we skip those
     * where the state values are incorrectly stored, after stack allocation, because some values
     * are no longer present.
     *
     * @param op Operation for which we are checking state values
     */
    protected void checkStateValues(RAVInstruction.Op op) {
        if (!op.hasCompleteState()) {
            /*
             * Some values are null after allocation because of stack slot allocator because it is
             * skipped when iteration (StackLockValue).
             */
            return;
        }

        checkInputs(op.stateValues, op);
    }

    /**
     * Verify the correspondence of original variables used in instructions are stored in the state
     * of current locations.
     *
     * @param valuePairs Array of pairs of current location and original variable.
     * @param op Operation this input array of values belongs to
     */
    protected void checkInputs(RAVInstruction.ValueArrayPair valuePairs, RAVInstruction.Op op) {
        // Check that incoming values are not unknown or conflicted - these only matter if used
        for (int idx = 0; idx < valuePairs.count; idx++) {
            checkOperand(valuePairs.orig[idx], valuePairs.curr[idx], op);
        }
    }

    /**
     * Check that original variable matches symbol stored at the current location in the
     * {@link AllocationStateMap}.
     *
     * <p>
     * We also check that kinds match and possibly rematerialize variables at this point in the
     * state map.
     * </p>
     *
     * @param orig Original variable
     * @param curr Current location
     * @param op Operation where these are used
     */
    protected void checkOperand(RAValue orig, RAValue curr, RAVInstruction.Op op) {
        assert orig != null;

        if (curr == null) {
            if (op.isJump()) {
                /*
                 * This can happen if a variable without a usage is passed in even when this
                 * variable acts as an alias to the next label, there's no usage, so no location.
                 */
                return;
            }

            throw new MissingLocationException(op, block, orig);
        }

        ValueKind<?> currKind = curr.getValue().getValueKind();
        if (!kindsEqualBetweenPreAndPostAlloc(orig.getValue().getValueKind(), currKind)) {
            throw new KindsMismatchException(op, block, orig, curr, true);
        }

        AllocationState state = this.values.get(curr);
        if (orig.equals(curr)) {
            /*
             * Whenever both the original symbol and current location are equal, we do not do any
             * further checking, as there is no symbol to check. This happens for instructions like
             * function calls, which have their inputs set to concrete locations before allocation.
             *
             * The state here can be unknown, some value or in conflict.
             */
            return;
        }

        if (ValueUtil.isStackSlot(curr.getValue()) && LIRValueUtil.isVirtualStackSlot(orig.getValue())) {
            /*
             * For the same reason as a previous statement, if vstack slot was present before
             * allocation and stack allocator have run, then we need to check for a vstack and stack
             * combination. Both are considered locations, and so we do not have a symbol to check
             * for.
             *
             * Test case IntegerDivRemCanonicalizationTest, has instruction: r10|QWORD = STACKLEA
             * slot: stack:80|ILLEGAL[*] in B0, where vstack:0 was present before stack allocation.
             */
            return;
        }

        if (state.isUnknown()) {
            throw new ValueNotInRegisterException(op, block, orig, curr, state, this);
        }

        if (state.isConflicted()) {
            throw new ValueNotInRegisterException(op, block, orig, curr, state, this);
        }

        if (state instanceof ValueAllocationState valAllocState) {
            if (!valAllocState.value.equals(orig)) {
                throw new ValueNotInRegisterException(op, block, orig, curr, state, this);
            }

            if (!kindsEqualFromState(orig, valAllocState)) {
                throw new KindsMismatchException(op, block, orig, valAllocState.value, false);
            }

            if (orig.isConstant()) {
                var constant = orig.asConstant();
                checkMaterializationLocation(constant, valAllocState);
            }

            return;
        }

        throw GraalError.shouldNotReachHere("Invalid state " + state);
    }

    protected void checkMaterializationLocation(RAVConstant constant, ValueAllocationState state) {
        if (constant.canRematerializeToStack) {
            return;
        }

        var source = state.getSource();
        if (source instanceof RAVInstruction.ValueMove move) {
            var location = move.getLocation();
            if (LIRValueUtil.isStackSlotValue(location.getValue())) {
                throw new ConstantRematerializedToStackException(constant, move.getLocation(), state);
            }
        }
    }

    protected boolean kindsEqualBetweenPreAndPostAlloc(RAValue orig, RAValue curr) {
        var origKind = orig.getValue().getValueKind();
        var currKind = curr.getValue().getValueKind();
        return kindsEqualBetweenPreAndPostAlloc(origKind, currKind);
    }

    /**
     * Are kinds equal even when {@link LIRKindWithCast casting} is present?
     *
     * @param origInputKind Original variable kind
     * @param currInputKind Current location kind
     * @return Are they equal?
     */
    protected boolean kindsEqualBetweenPreAndPostAlloc(ValueKind<?> origInputKind, ValueKind<?> currInputKind) {
        ValueKind<?> origKind;
        if (origInputKind instanceof LIRKindWithCast castKind) {
            origKind = castKind.getActualKind();
        } else {
            origKind = origInputKind;
        }

        ValueKind<?> currKind;
        if (currInputKind instanceof LIRKindWithCast castKind) {
            // Original symbol was defined with the actual kind
            // that is being cast from with current location,
            // so we check kinds against that.
            currKind = castKind.getActualKind();
        } else {
            currKind = currInputKind;
        }

        return currKind.equals(origKind);
    }

    /**
     * Are kinds equal even when {@link CastValue cast value} is present?
     *
     * <p>
     * We need to ignore the cast value because the currently stored value will not be cast.
     * </p>
     *
     * @param orig Original variable
     * @param valAllocState State we are checking against
     * @return Are they equal?
     */
    protected boolean kindsEqualFromState(RAValue orig, ValueAllocationState valAllocState) {
        LIRKind stateKind = valAllocState.getKind();
        ValueKind<?> origKind = orig.getLIRKind();
        if (LIRValueUtil.isCast(orig.getValue())) {
            origKind = LIRValueUtil.uncast(orig.getValue()).getValueKind();
        }

        return origKind.equals(stateKind);
    }

    /**
     * Check that all instruction arrays of pairs of original variable and current location check
     * out to the state stored in for this block.
     *
     * @param instruction Instruction we are checking
     */
    public void check(RAVInstruction.Base instruction) {
        if (instruction instanceof RAVInstruction.Op op) {
            checkInputs(op.uses, op);
            checkInputs(op.alive, op);
            checkStateValues(op);

            checkTempKind(op);
            checkAliveConstraint(op);

            checkOperandFlags(op.dests, op);
            checkOperandFlags(op.uses, op);
            checkOperandFlags(op.alive, op);
            checkOperandFlags(op.temp, op);

            checkBytecodeFrames(op);

            if (op.references != null) {
                checkReferences(op);
            }
        } else if (instruction instanceof RAVInstruction.LocationMove move) {
            checkLocationMoveKinds(move);
        }
    }

    /**
     * Iterate over references collected by ReferenceBuilder and check if said locations actually
     * contain a reference in the state map.
     *
     * @param op Operation containing references for GC
     */
    protected void checkReferences(RAVInstruction.Op op) {
        // ReferenceSet makes sure that contents are references only
        for (RAValue reference : op.references) {
            var state = values.get(reference);
            if (state.isConflicted()) {
                var confState = (ConflictedAllocationState) state;
                for (var valAllocState : confState.getConflictedStates()) {
                    if (valAllocState.isUndefinedFromBlock()) {
                        continue; // Undefined in branch
                    }

                    if (valAllocState.isReference()) {
                        continue; // State holds a reference
                    }

                    throw new MissingReferenceException(op, block, reference, state, this);
                }

                continue;
            }

            if (state.isUnknown()) {
                throw new MissingReferenceException(op, block, reference, state, this);
            }

            var valAllocState = (ValueAllocationState) state;
            if (valAllocState.isReference()) {
                continue; // State holds a reference
            }

            throw new MissingReferenceException(op, block, reference, state, this);
        }
    }

    /**
     * Check that a move destination has the correct kind to store the {@link ValueAllocationState
     * value}.
     *
     * @param move Move between locations, inserted by register allocator
     */
    protected void checkLocationMoveKinds(RAVInstruction.LocationMove move) {
        AllocationState state = this.values.get(move.from);
        if (state instanceof ValueAllocationState valueAllocationState) {
            RAValue movedValue = valueAllocationState.getRAValue();
            if (!movedValue.getLIRKind().getPlatformKind().equals(move.to.getLIRKind().getPlatformKind())) {
                throw new KindsMismatchException(move, block, move.to, movedValue, false);
            }
        }
    }

    /**
     * Check {@link BytecodeFrame frames}, before and after allocation, mainly checking that
     * {@link LIRKind} is a reference when {@link JavaKind} is an Object and wise-versa, checking
     * that {@link LIRKind} is not a reference when {@link JavaKind} is not an object.
     *
     * @param op Operation holding said frames
     * @throws RAVException when a violation occurs
     */
    public void checkBytecodeFrames(RAVInstruction.Op op) {
        for (var frame : op.bcFrames) {
            for (int i = 0; i < frame.kinds.length; i++) {
                var origJV = frame.orig[i];
                if (!(origJV instanceof AllocatableValue orig) || Value.ILLEGAL.equals(orig)) {
                    continue;
                }

                var currJV = frame.curr[i];
                if (!(currJV instanceof AllocatableValue curr) || Value.ILLEGAL.equals(curr)) {
                    continue;
                }

                var kind = frame.kinds[i];
                if (JavaKind.Long.equals(kind)) {
                    /*
                     * Skipping long(s) because it can be a numeric value or a derived reference /
                     * native pointer
                     */
                    continue;
                }

                var origLIRKind = orig.getValueKind(LIRKind.class);
                var currLIRKind = curr.getValueKind(LIRKind.class);
                if (JavaKind.Object.equals(kind)) {
                    if (!origLIRKind.isValue() && !currLIRKind.isValue()) {
                        continue;
                    }

                    throw new JavaKindReferenceMismatchException(orig, curr, kind, op, block);
                } else {
                    if (origLIRKind.isValue() && currLIRKind.isValue()) {
                        // Either not a reference or a derived one - which might not be marked as
                        // Object
                        continue;
                    }

                    throw new JavaKindReferenceMismatchException(orig, curr, kind, op, block);
                }
            }
        }
    }

    /**
     * Check if kinds in the {@link RAVInstruction.Op#temp temporary array} match before allocation
     * original variables with after allocation concrete locations.
     *
     * @param op Instruction we update state from
     * @throws KindsMismatchException if a pair does not match
     */
    protected void checkTempKind(RAVInstruction.Op op) {
        for (int i = 0; i < op.temp.count; i++) {
            var curr = op.temp.curr[i];
            var orig = op.temp.orig[i];

            if (!kindsEqualBetweenPreAndPostAlloc(orig, curr)) {
                // Make sure the assigned register has the correct kind for temp.
                throw new KindsMismatchException(op, block, orig, curr, true);
            }
        }
    }

    /**
     * Check if alive constraint is not being violated, when one location is supposed to be alive
     * after instruction is complete, but is used either as an output or a generic input.
     *
     * @param instruction Instruction with alive inputs
     * @throws AliveConstraintViolationException throw when violation occurs
     */
    protected void checkAliveConstraint(RAVInstruction.Op instruction) {
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
                    throw new AliveConstraintViolationException(instruction, block, value, false);
                }
            }

            for (int j = 0; j < instruction.dests.count; j++) {
                if (value.equals(instruction.dests.curr[j])) {
                    throw new AliveConstraintViolationException(instruction, block, value, true);
                }
            }
        }
    }

    /**
     * Make sure concrete current locations changed by the allocator are not violating a set of
     * {@link jdk.graal.compiler.lir.LIRInstruction.OperandFlag flags}, which specify what type can
     * they be. This is done on every array of pairs (dest, uses, alive, temp).
     *
     * @param valuePairs Value array pair we are verifying
     * @param op Instruction that holds this array, for tracing in exceptions
     * @throws OperandFlagMismatchException Operand is a wrong type based on OperandFlag set.
     */
    protected void checkOperandFlags(RAVInstruction.ValueArrayPair valuePairs, RAVInstruction.Op op) {
        for (int i = 0; i < valuePairs.count; i++) {
            var curr = valuePairs.curr[i];
            if (curr == null) {
                continue;
            }

            var flags = valuePairs.operandFlags.get(i);
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
     * Update the current state based on outputs of this instruction. Setting contents of current
     * location in {@link AllocationStateMap} to the symbol that was present before allocation was
     * completed.
     *
     * @param instruction Instruction we update state from
     */
    public void update(RAVInstruction.Base instruction) {
        switch (instruction) {
            case RAVInstruction.Op op -> this.updateWithOp(op);
            case RAVInstruction.ValueMove virtMove -> this.updateWithValueMove(virtMove);
            case RAVInstruction.LocationMove move -> this.updateWithLocationMove(move);
            default -> throw GraalError.shouldNotReachHere("Invalid RAV instruction " + instruction);
        }
    }

    public void updateWithLocationMove(RAVInstruction.LocationMove move) {
        if (move instanceof RAVInstruction.StackMove stackMove) {
            // Maybe the backup slot should hold what the scratch register holds?
            this.values.put(stackMove.backupSlot, UnknownAllocationState.INSTANCE, move);
        }

        var state = this.values.get(move.from);
        if (state instanceof ValueAllocationState valueAllocationState) {
            var movedValue = valueAllocationState.getRAValue();
            if (!movedValue.getLIRKind().equals(move.to.getLIRKind())) {
                state = new ValueAllocationState(valueAllocationState, move.to.getLIRKind());
            }
        }

        if (move.validateRegisters) {
            this.values.putClone(move.to, state, move);
        } else {
            this.values.putWithoutRegCheck(move.to, state.clone());
        }
    }

    /**
     * Update the state using a generic operation, based on contents of its
     * {@link RAVInstruction.Op#dests output array}, storing symbols pre-allocation to current
     * locations after allocation.
     *
     * @param op Operation we update state from
     */
    protected void updateWithOp(RAVInstruction.Op op) {
        if (op.references != null) {
            /*
             * First we remove unknown references, then we define new values by the return value
             */
            updateWithSafePoint(op);
        }

        if (canCastOpToMove(op)) {
            /*
             * Moves present before the allocation can also be treated same way the one inserted by
             * the allocator
             */
            RAVInstruction.LocationMove locMove = castMove(op);
            updateWithLocationMove(locMove);
            return;
        }

        /*
         * For calls, temp lists all registers that are supposed to be caller saved, because
         * sometimes there's a difference between RegisterConfig.getCallerSaveRegisters
         *
         * These registers need to be set to unknown before output registers, in case some of them
         * are also set as an output.
         */
        for (int i = 0; i < op.temp.count; i++) {
            var value = op.temp.curr[i];
            if (value.isIllegal()) {
                continue;
            }

            // We cannot believe the contents of registers used as temp, thus we need to reset.
            RAValue location = op.temp.curr[i];

            if (op.temp.orig[i].equals(location)) {
                this.values.putWithoutRegCheck(location, UnknownAllocationState.INSTANCE);
            } else {
                this.values.put(location, UnknownAllocationState.INSTANCE, op);
            }
        }

        for (int i = 0; i < op.dests.count; i++) {
            if (op.dests.orig[i].isIllegal()) {
                continue; // Safe to ignore when destination is illegal value, not when used.
            }

            assert op.dests.orig[i] != null;

            if (op.dests.curr[i] == null && op.isLabel()) {
                continue; // Unused label variable
            }

            assert op.dests.curr[i] != null;

            RAValue location = op.dests.curr[i];
            RAValue variable = op.dests.orig[i];

            if (location.equals(variable)) {
                /*
                 * Only check register validity if it was changed by the register allocator, for
                 * example, rbp is used as input to start block and forbidden to be used by the
                 * allocator
                 */
                this.values.putWithoutRegCheck(location, new ValueAllocationState(variable, op, block));
            } else {
                this.values.put(location, new ValueAllocationState(variable, op, block), op);
            }
        }

        if (op.isLabel() && block.getId() == 0) {
            updateCalleeSavedRegisters();
        }
    }

    /**
     * Moves not inserted by the register allocator were previously treated as generic Operations,
     * but some of them can be cast back to a move to increase the accuracy of the verification
     * process.
     *
     * <p>
     * All moves where the destination is the same location before and after the allocation, or
     * vstack/stack combination can be cast to a move.
     * </p>
     *
     * @param op the operation being cast
     * @return true, if op can be cast to a move
     */
    protected boolean canCastOpToMove(RAVInstruction.Op op) {
        if (!op.lirInstruction.isMoveOp() || op.dests.count != 1 || op.uses.count != 1) {
            return false;
        }

        return op.dests.curr[0].equals(op.dests.orig[0]) || (ValueUtil.isStackSlot(op.dests.curr[0].getValue()) && LIRValueUtil.isVirtualStackSlot(op.dests.orig[0].getValue()));
    }

    protected RAVInstruction.LocationMove castMove(RAVInstruction.Op op) {
        return new RAVInstruction.LocationMove(op.lirInstruction, op.uses.curr[0].getValue(), op.dests.curr[0].getValue(), false);
    }

    /**
     * Update the block state with a safe point list of live references deemed by the GC; any other
     * references not included in the said list are to be set as unknown, so there's no freed
     * pointer use.
     *
     * <p>
     * References need to be retrieved using {@link LocationMarker} classes.
     * </p>
     *
     * @param op SafePoint we are using to remove old references
     */
    protected void updateWithSafePoint(RAVInstruction.Op op) {
        for (var entry : this.values.internalMap.entrySet()) {
            var state = entry.getValue();
            if (state.isUnknown() || state.isConflicted()) {
                continue; // Retain information
            }

            var valueAllocState = (ValueAllocationState) state;
            if (Value.ILLEGAL.equals(valueAllocState.getValue()) || !valueAllocState.isReference()) {
                continue; // Not a reference, continue
            }

            if (op.references.contains(entry.getKey())) {
                continue;
            }

            /*
             * Remove all references that are not present in the reference list; maybe it makes
             * sense to keep registers that have live references, that are same as the one in the
             * reference list? Because the list is expected to have stack slots and registers can
             * retain the same references.
             */
            entry.setValue(new ValueAllocationState(new RAValue(Value.ILLEGAL), op, block));
        }
    }

    /**
     * Take the list of callee saved registers and add them to the start block state with their own
     * values as symbols in order to check that they were correctly retrieved at an exit point.
     */
    protected void updateCalleeSavedRegisters() {
        var registers = this.registerAllocationConfig.getRegisterConfig().getCalleeSaveRegisters();
        if (registers == null) {
            return;
        }

        for (var reg : registers) {
            var regValue = calleeSaveMap.createCalleeSavedRegister(reg.asValue());

            var presentState = values.get(regValue);
            if (presentState instanceof ValueAllocationState valueAllocationState) {
                // Keep the old value from the label but save it for check at exit point.
                if (!valueAllocationState.getRAValue().equals(regValue)) {
                    // If the symbol is the same register, then override it with CalleeSaveRegister
                    calleeSaveMap.addValue(regValue, valueAllocationState.getRAValue());
                    continue;
                }

                // Keep the kind here!
                var lirRegValue = ValueUtil.asRegisterValue(valueAllocationState.getValue());
                regValue = calleeSaveMap.createCalleeSavedRegister(lirRegValue);
            }

            // Save same registers as symbol, and later check if it was retrieved
            var state = new ValueAllocationState(regValue, null, block);
            this.values.putWithoutRegCheck(regValue, state);
        }
    }

    /**
     * At exit point, check that all callee saved registers were indeed correctly saved, by checking
     * that the symbol stored in said registers is equal to the registers themselves.
     *
     * @throws RAVException when callee saved register was not recovered
     */
    protected void checkCalleeSavedRegisters() {
        var registers = this.calleeSaveMap.getCalleeSaveRegisters();
        if (registers == null) {
            return;
        }

        for (var reg : registers) {
            var regValue = (RAVRegister) RAVRegister.create(reg.asValue());
            var state = this.values.get(regValue);
            if (state instanceof ValueAllocationState valueAllocationState) {
                var stateValue = valueAllocationState.getRAValue();
                var calleeSavedValue = calleeSaveMap.getCalleeSavedValue(regValue);
                if (stateValue.equals(calleeSavedValue) && stateValue.getLIRKind().equals(calleeSavedValue.getLIRKind())) {
                    /*
                     * The same symbol as register means the value was retrieved safely. Kinds also
                     * need to match
                     */
                    continue;
                }
            }

            throw new CalleeSavedRegisterNotRetrievedException(regValue, block, this);
        }
    }

    /**
     * Update state with a {@link RAVInstruction.ValueMove}, if location is concrete, we set it to a
     * variable/constant, if it's a variable to variable move, then all locations containing old
     * variable need to be changed to the new variable.
     *
     * @param valueMove move we update state from
     */
    protected void updateWithValueMove(RAVInstruction.ValueMove valueMove) {
        var state = new ValueAllocationState(valueMove.constant, valueMove, block);
        if (valueMove.validateRegisters) {
            this.values.put(valueMove.getLocation(), state, valueMove);
        } else {
            this.values.putWithoutRegCheck(valueMove.getLocation(), state);
        }
    }
}
