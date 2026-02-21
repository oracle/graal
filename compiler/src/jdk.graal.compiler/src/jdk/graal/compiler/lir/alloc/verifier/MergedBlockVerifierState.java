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

import jdk.graal.compiler.core.common.LIRKindWithCast;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.StandardOp;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.ValueKind;

/**
 * Verification state a block is in.
 */
public class MergedBlockVerifierState {
    /**
     * Map maintaining mapping between locations and their state.
     */
    public MergedAllocationStateMap values;

    protected PhiResolution phiResolution;
    protected RegisterAllocationConfig registerAllocationConfig;

    /**
     * Conflict resolver for constant materialization.
     */
    protected ConflictResolver conflictConstantResolver;
    protected ConflictResolver labelConflictResolver;

    public MergedBlockVerifierState(RegisterAllocationConfig registerAllocationConfig, PhiResolution phiResolution, ConflictResolver constantConflictResolver, ConflictResolver labelConflictResolver) {
        this.values = new MergedAllocationStateMap(registerAllocationConfig);
        this.phiResolution = phiResolution;
        this.registerAllocationConfig = registerAllocationConfig;
        this.conflictConstantResolver = constantConflictResolver;
        this.labelConflictResolver = labelConflictResolver;
    }

    protected MergedBlockVerifierState(MergedBlockVerifierState other, RegisterAllocationConfig registerAllocationConfig, PhiResolution phiResolution, ConflictResolver constantConflictResolver, ConflictResolver labelConflictResolver) {
        this.phiResolution = phiResolution;
        this.registerAllocationConfig = registerAllocationConfig;
        this.conflictConstantResolver = constantConflictResolver;
        this.labelConflictResolver = labelConflictResolver;

        if (other == null) {
            this.values = new MergedAllocationStateMap(registerAllocationConfig);
            return;
        }

        this.values = new MergedAllocationStateMap(other.values);
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

            boolean isJump = op.lirInstruction instanceof StandardOp.JumpOp;
            if (curr == null) {
                if (isJump) {
                    if (phiResolution == PhiResolution.FromUsage || phiResolution == PhiResolution.FromUsageGlobal) {
                        // Variable has no usage, thus no location present.
                        continue;
                    }

                    if (phiResolution == PhiResolution.ByAllocator || phiResolution == PhiResolution.FromConflicts) {
                        continue;
                    }

                    throw new LabelNotResolvedError(block, labelOp, phiResolution);
                }

                throw new MissingLocationError(op.lirInstruction, block, orig);
            }

            if (orig.equals(curr)) {
                // For these cases we do not consider checking state taking the original
                // register as a symbol, because there's too many cases when this does
                // not work, for example RETURN with rax tends to contain the actual
                // generated variable instead of rax symbol, or NEAR_FOREIGN_CALL
                // keeps its own registers before and after allocation, but those
                // can also contain different variable symbols.
                continue;
            }

            if (!kindsEqual(orig, curr) && !isJump) {
                throw new KindsMismatchException(op.lirInstruction, block, orig, curr, true);
            }

            AllocationState state = this.values.get(curr);
            if (state.isUnknown()) {
                throw new ValueNotInRegisterException(op.lirInstruction, block, orig, curr, state);
            }

            if (state.isConflicted()) {
                var variable = orig.asVariable();
                var resolvedState = this.conflictConstantResolver.resolveConflictedState(variable, (ConflictedAllocationState) state, curr);

                if (phiResolution == PhiResolution.FromConflicts && resolvedState == null) {
                    resolvedState = this.labelConflictResolver.resolveConflictedState(variable, (ConflictedAllocationState) state, curr);
                    if (resolvedState == null) {
                        throw new ValueNotInRegisterException(op.lirInstruction, block, orig, curr, state);
                    }
                }

                this.values.put(curr, resolvedState);
                continue;
            }

            if (state instanceof ValueAllocationState valAllocState) {
                if (!kindsEqualFromState(orig, valAllocState.value)) {
                    throw new KindsMismatchException(op.lirInstruction, block, orig, valAllocState.value, false);
                }

                if (!valAllocState.value.equals(orig)) {
                    if (orig.getValue() instanceof CastValue castValue && valAllocState.value.getValue().equals(castValue.underlyingValue())) {
                        continue; // They aren't equal here because of the CastValue, so if they are equal afterwards, we skip next part.
                    }

                    if (valAllocState.value.getValue() instanceof ConstantValue) {
                        var variable = orig.asVariable();
                        var resolvedState = this.conflictConstantResolver.resolveValueState(variable, valAllocState, curr);
                        if (resolvedState != null) {
                            this.values.put(curr, resolvedState);
                            continue;
                        }
                    }

                    if (phiResolution == PhiResolution.FromConflicts && orig.isVariable()) {
                        var variable = orig.asVariable();
                        var resolvedState = labelConflictResolver.resolveValueState(variable, valAllocState, curr);
                        if (resolvedState != null) {
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
        if (orig.getValue() instanceof CastValue castOrig) {
            origKind = castOrig.underlyingValue().getValueKind();
        }

        return origKind.equals(currKind);
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

            for (int i = 0; i < op.temp.count; i++) {
                var curr = op.temp.curr[i];
                var orig = op.temp.orig[i];

                if (!kindsEqual(orig, curr)) {
                    // Make sure the assigned register has the correct kind for temp.
                    throw new KindsMismatchException(instruction.lirInstruction, block, orig, curr, true);
                }
            }

            this.checkInputs(op.stateValues, op, block, labelOp);

            int kindIdx = 0;
            for (int i = 0; i < op.stateValues.count; i++) {
                var orig = op.stateValues.orig[i];
                var curr = op.stateValues.curr[i];

                var state = this.values.get(curr);
                if (state instanceof ValueAllocationState valueAllocationState && valueAllocationState.getRAValue().equals(orig)) {
                    continue;
                }

                JavaKind kind = null;
                while (kindIdx < op.kinds.length) {
                    JavaKind target = op.kinds[kindIdx++];
                    if (!JavaKind.Illegal.equals(target)) {
                        kind = target;
                        break;
                    }
                    // Illegal values are ignored when iterating over state values
                    // but kept in the kinds array so we need to skip them.
                }

                if (!JavaKind.Object.equals(kind)) {
                    continue;
                }
                // Maybe check correspondence to JavaKind?
                // Object -> has ref type in LIRKind?

                var state = this.values.get(curr);
                if (state.isUnknown() || state.isConflicted()) {
                    continue;
                }

                var instr = op.lirInstruction;

                var valueAllocState = (ValueAllocationState) state;
                var source = valueAllocState.getSource();
                if (source == null) {
                    throw new IllegalStateException();
                }

                var v = valueAllocState.getValue();
                // How to check type is a reference?

                // Safepoint update -> change refered objects to unknown in-case GC cleared them?
                // Safepoint check -> check for correspondence to JavaKind
                // + checking correspondence to state
            }

            this.checkAliveConstraint(op, block);
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
                if (phiResolution == PhiResolution.FromJump) {
                    throw new LabelNotResolvedError(block, op, phiResolution);
                }

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
     * Update state with a ValueMove.
     *
     * @param valueMove Value move we update state from
     * @param block     Block where it is located
     */
    protected void updateWithValueMove(RAVInstruction.ValueMove valueMove, BasicBlock<?> block) {
        if (valueMove.location.getValue() instanceof RegisterValue) {
            this.values.put(valueMove.location, new ValueAllocationState(valueMove.variableOrConstant, valueMove, block));
        } else if (valueMove.location.isVariable()) {
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
