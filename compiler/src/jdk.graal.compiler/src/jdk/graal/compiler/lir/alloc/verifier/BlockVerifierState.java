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
import jdk.graal.compiler.lir.dfa.LocationMarker;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Verification state a block is in, holds a mapping between locations and their allocation states,
 * which can be unknown, value - it's contents or conflicted - multiple values conflict with each
 * other.
 */
public class BlockVerifierState {
    /**
     * Map maintaining mapping between locations and their state.
     */
    public AllocationStateMap values;

    /**
     * Register allocation config we use to check if only allocatable registers are the ones used.
     */
    protected RegisterAllocationConfig registerAllocationConfig;

    /**
     * Conflict resolver for constant materialization.
     */
    protected ConflictResolver conflictConstantResolver;

    /**
     * Block this state pertains to.
     */
    protected BasicBlock<?> block;

    protected VariableSynonymMap synonymMap;

    public BlockVerifierState(BasicBlock<?> block, RegisterAllocationConfig registerAllocationConfig, ConflictResolver constantConflictResolver, VariableSynonymMap synonymMap) {
        this.values = new AllocationStateMap(block, registerAllocationConfig);
        this.registerAllocationConfig = registerAllocationConfig;
        this.conflictConstantResolver = constantConflictResolver;
        this.synonymMap = synonymMap;
        this.block = block;
    }

    protected BlockVerifierState(BasicBlock<?> block, BlockVerifierState other) {
        this.registerAllocationConfig = other.registerAllocationConfig;
        this.conflictConstantResolver = other.conflictConstantResolver;
        this.values = new AllocationStateMap(block, other.values);
        this.synonymMap = other.synonymMap;
        this.block = block;
    }

    public AllocationStateMap getValues() {
        return values;
    }

    /**
     * Merge states of block and it's predecessor. This process creates a new state based on
     * contents of the predecessor, creating conflicts where current locations do not match.
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
            // Some values are null after allocation because of stack slot allocator
            // because it is skipped when iteration (StackLockValue).
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
                // This can happen if a variable without a usage is passed in
                // even when this variable acts as an alias to the next label
                // there's no usage, so no location.
                return;
            }

            throw new MissingLocationError(op.lirInstruction, block, orig);
        }

        ValueKind<?> currKind = curr.getValue().getValueKind();
        if (values.castMap.containsKey(curr)) {
            // The current location might have been cast by a previous move
            // see isMoveKindChange comment
            currKind = values.castMap.get(curr);
        }

        if (!kindsEqual(orig.getValue().getValueKind(), currKind)) {
            throw new KindsMismatchException(op.lirInstruction, block, orig, curr, true);
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

                if (orig.isVariable() && valAllocState.value.isVariable() && synonymMap.isSynonymOf(orig.asVariable(), valAllocState.value.asVariable())) {
                    return; // Resolved!
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

    protected boolean kindsEqual(RAValue orig, RAValue curr) {
        var origKind = orig.getValue().getValueKind();
        var currKind = curr.getValue().getValueKind();
        return kindsEqual(origKind, currKind);
    }

    /**
     * Are kinds equal even when {@link LIRKindWithCast casting} is present?
     *
     * @param origInputKind Original variable kind
     * @param currInputKind Current location kind
     * @return Are they equal?
     */
    protected boolean kindsEqual(ValueKind<?> origInputKind, ValueKind<?> currInputKind) {
        ValueKind<?> origKind;
        if (origInputKind instanceof LIRKindWithCast castKind) {
            origKind = castKind.getActualKind();
        } else {
            origKind = origInputKind;
        }

        ValueKind<?> currKind;
        if (currInputKind instanceof LIRKindWithCast castKind) {
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
            checkMoveKinds(move);
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
                    if (valAllocState.isIllegal()) {
                        continue; // Undefined in branch
                    }

                    if (!valAllocState.getRAValue().getLIRKind().isValue()) {
                        continue; // Is a reference
                    }

                    throw new MissingReferenceException(reference, state, op, block);
                }

                continue;
            }

            if (state.isUnknown()) {
                throw new MissingReferenceException(reference, state, op, block);
            }

            var valAllocState = (ValueAllocationState) state;
            if (!valAllocState.getRAValue().getLIRKind().isValue()) {
                continue; // Is a reference
            }

            throw new MissingReferenceException(reference, state, op, block);
        }
    }

    /**
     * Check that a move destination has the correct kind to store the {@link ValueAllocationState
     * value}.
     *
     * @param move Move between locations, inserted by register allocator
     */
    protected void checkMoveKinds(RAVInstruction.LocationMove move) {
        AllocationState state = this.values.get(move.from);
        if (state instanceof ValueAllocationState valueAllocationState) {
            RAValue movedValue = valueAllocationState.getRAValue();
            if (!kindsEqual(movedValue, move.to)) {
                if (isMoveKindChange(move, valueAllocationState)) {
                    // This move changes the kind for destination location
                    // see isMoveKindChange comment
                    return;
                }

                throw new KindsMismatchException(move.lirInstruction, block, move.to, movedValue, false);
            }
        }
    }

    /**
     * Does this move change the ValueKind? This happens for when derived reference gets changed to
     * a normal reference, we make sure that the underlying platform kind is equal, and then allow
     * change from derived ([.+]) to normal reference ([*]).
     *
     * <p>
     * Currently only allowed if the moved state is {@link ValueAllocationState}.
     * </p>
     *
     * <p>
     * The standard setup is as this:
     *
     * <pre>
     * (v8|QWORD[.+] -> rcx|QWORD[.+]) = ADD (x: rcx|QWORD, y: r8|QWORD[.]) size: QWORD
     * rdx|QWORD[*] = MOVE input: rcx|QWORD[.+] moveKind: QWORD // MoveResolver resolve mapping
     * JUMP ~outgoingValues: [v8|QWORD[.+] -> rdx|QWORD[*]] destination: B1 -> B3 isThreadedJump: false
     * </pre>
     *
     * Add calculates the derived reference address, move casts it to LIRKind ref and value is used
     * in the JUMP to the next block, where pre-allocation symbol (variable) has derived ref, but
     * location has reference.
     * </p>
     *
     * This is then changed in {@link BlockVerifierState#checkOperand} to verify that the kind is
     * correct.
     *
     * @param move Move that facilitates the change
     * @param state Current value state this happens for
     * @return if this move changes the kinds
     */
    protected boolean isMoveKindChange(RAVInstruction.LocationMove move, ValueAllocationState state) {
        var moveValueKind = state.getRAValue().getLIRKind();
        var toKind = move.to.getLIRKind();
        var fromKind = move.from.getLIRKind();

        if (!moveValueKind.getPlatformKind().equals(toKind.getPlatformKind())) {
            return false;
        }

        if (!moveValueKind.getPlatformKind().equals(fromKind.getPlatformKind())) {
            return false;
        }

        return moveValueKind.isDerivedReference() && !toKind.isValue() && fromKind.isDerivedReference();
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
                    // Skipping long(s) because it can be a numeric value
                    // or a derived reference / native pointer
                    continue;
                }

                var origLIRKind = orig.getValueKind(LIRKind.class);
                var currLIRKind = curr.getValueKind(LIRKind.class);
                if (JavaKind.Object.equals(kind)) {
                    if (!origLIRKind.isValue() && !currLIRKind.isValue()) {
                        continue;
                    }

                    throw new RAVException(orig + " -> " + curr + " not an object java kind when marked as a reference");
                } else {
                    if (origLIRKind.isValue() && currLIRKind.isValue()) {
                        // Either not a reference, or a derived one - which might not be marked as
                        // Object
                        continue;
                    }

                    throw new RAVException(orig + " -> " + curr + " is a reference when not marked as an object java kind");
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

            if (!kindsEqual(orig, curr)) {
                // Make sure the assigned register has the correct kind for temp.
                throw new KindsMismatchException(op.lirInstruction, block, orig, curr, true);
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
     * Make sure concrete current locations changed by the allocator are not violating set of
     * {@link jdk.graal.compiler.lir.LIRInstruction.OperandFlag flags}, which specify what type can
     * they be. This is done on every array of pairs (dest, uses, alive, temp).
     *
     * @param valuePairs Value array pair we are verifying
     * @param op Instruction which holds this array, for tracing in exceptions
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
            this.values.put(stackMove.backupSlot, UnknownAllocationState.INSTANCE);
        }

        var state = this.values.get(move.from);

        this.values.putClone(move.to, state);

        if (state instanceof ValueAllocationState valueAllocationState) {
            var movedValue = valueAllocationState.getRAValue();
            if (!kindsEqual(movedValue, move.to) && isMoveKindChange(move, valueAllocationState)) {
                this.values.castMap.put(move.to, move.from.getLIRKind()); // Add a new cast
            }
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
            // First we remove unknown references
            // then we define new values by the return value
            updateWithSafePoint(op);
        }

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
                // for example: rbp is used as input to start block and forbidden to be used by the
                // allocator
                this.values.putWithoutRegCheck(location, new ValueAllocationState(variable, op, block));
            } else {
                this.values.put(location, new ValueAllocationState(variable, op, block));
            }
        }

        // For calls, temp lists all registers that are supposed to be caller saved,
        // because sometimes there's a difference between RegisterConfig.getCallerSaveRegisters
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
     * Update block state with a safe point list of live references deemed by the GC, any other
     * references not included in said list are to be set as unknown so there's no freed pointer
     * use.
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
            if (Value.ILLEGAL.equals(valueAllocState.getValue()) || valueAllocState.getValue().getValueKind(LIRKind.class).isValue()) {
                continue; // Not a reference, continue
            }

            boolean referenceFound = false;
            for (RAValue reference : op.references) {
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
            entry.setValue(new ValueAllocationState(new RAValue(Value.ILLEGAL), op, block));
        }
    }

    /**
     * Take list of callee saved registers and add them to the start block state with their own
     * values as symbols in order to check that they were correctly retrieved at exit point.
     */
    protected void updateCalleeSavedRegisters() {
        var registers = this.registerAllocationConfig.getRegisterConfig().getCalleeSaveRegisters();
        if (registers == null) {
            return;
        }

        for (var reg : registers) {
            var regValue = RARegister.create(reg.asValue());

            // Save same registers as symbol, and later check if it was retrieved
            this.values.putWithoutRegCheck(regValue, new ValueAllocationState(regValue, null, block));
        }
    }

    /**
     * At exit point, check that all callee saved registers were indeed correctly saved, by checking
     * that the symbol stored in said registers is equal to the registers themselves.
     *
     * @throws RAVException when callee saved register was not recovered
     */
    protected void checkCalleeSavedRegisters() {
        var registers = this.registerAllocationConfig.getRegisterConfig().getCalleeSaveRegisters();
        if (registers == null) {
            return;
        }

        for (var reg : registers) {
            var regValue = RARegister.create(reg.asValue());
            var state = this.values.get(regValue);
            if (state instanceof ValueAllocationState valueAllocationState) {
                if (valueAllocationState.getRAValue().equals(regValue)) {
                    // Same symbol as register means the value was retrieved safely
                    continue;
                }
            }

            throw new RAVException("Callee saved register " + regValue + " not recovered.");
        }
    }

    /**
     * Update state with a {@link RAVInstruction.ValueMove}, if locations is concrete, we set it to
     * a variable/constant, if it's a variable to variable move, then all locations containing old
     * variable need to be changed to the new variable.
     *
     * @param valueMove move we update state from
     */
    protected void updateWithValueMove(RAVInstruction.ValueMove valueMove) {
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

            if (valueMove.variableOrConstant.isVariable()) {
                synonymMap.addSynonym(valueMove.variableOrConstant.asVariable(), valueMove.getLocation().asVariable());
            }
        } else {
            this.values.put(valueMove.getLocation(), new ValueAllocationState(valueMove.variableOrConstant, valueMove, block));
        }
    }
}
