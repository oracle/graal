package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.LIRKindWithCast;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class MergedBlockVerifierState {
    public MergedAllocationStateMap values;

    protected PhiResolution phiResolution;
    protected RegisterAllocationConfig registerAllocationConfig;

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

    // TODO: reconsider the merging/meeting logic
    // It might make sense to only keep certain states if it is certain to be defined / have a value present
    // If a new value was defined in one branch in a certain register and other branches do not have anything in this
    // register, then the value could not be defined at merging block and if used later it could be verified as valid
    // but it is not.
    // If overwritten in one branch but block before branching also has a certain value here
    // it would just be marked as unknown / conflicted -> Only care if value after merge is ValueAllocatedState!
    public boolean meetWith(MergedBlockVerifierState other) {
        return this.values.mergeWith(other.getValues());
    }

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
                // In this case nothing has changed so we have nothing to verify
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
                var variable = LIRValueUtil.asVariable(orig);
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
                    if (orig instanceof CastValue castValue && valAllocState.value.equals(castValue.underlyingValue())) {
                        continue; // They aren't equal here because of the CastValue, so if they are equal afterwards, we skip next part.
                    }

                    if (valAllocState.value instanceof ConstantValue) {
                        var variable = LIRValueUtil.asVariable(orig);
                        var resolvedState = this.conflictConstantResolver.resolveValueState(variable, valAllocState, curr);
                        if (resolvedState != null) {
                            this.values.put(curr, resolvedState);
                            continue;
                        }
                    }

                    if (phiResolution == PhiResolution.FromConflicts && LIRValueUtil.isVariable(orig)) {
                        var variable = LIRValueUtil.asVariable(orig);
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

    protected boolean kindsEqual(Value orig, Value curr) {
        var origKind = orig.getValueKind();
        var currKind = curr.getValueKind();

        if (currKind.equals(origKind)) {
            return true;
        }

        if (origKind instanceof LIRKindWithCast || currKind instanceof LIRKindWithCast) {
            // TestCase: BoxingTest.boxShort
            // MOV (x: [v11|QWORD[.] + 12], y: reinterpret: v0|DWORD as: WORD) size: WORD
            // MOV (x: [rax|QWORD[.] + 12], y: r10|WORD(DWORD)) size: WORD
            return origKind.getPlatformKind().equals(currKind.getPlatformKind());
        }

        // TODO: maybe we should also look at the type before cast
        // LIRKindWithCast.getActualKind()
        // CastValue.underlyingValue().getValueKind()
        return false;
    }

    protected boolean kindsEqualFromState(Value orig, Value fromState) {
        ValueKind<?> origKind = orig.getValueKind();
        ValueKind<?> currKind = fromState.getValueKind();
        if (orig instanceof CastValue castOrig) {
            origKind = castOrig.underlyingValue().getValueKind();
        }

        return origKind.equals(currKind);
    }

    protected void checkAliveConstraint(RAVInstruction.Op instruction, BasicBlock<?> block) {
        for (int i = 0; i < instruction.alive.count; i++) {
            Value value = instruction.alive.curr[i];
            if (Value.ILLEGAL.equals(value)) {
                continue; // TODO: remove IllegalValues from these arrays.
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
                if (state instanceof ValueAllocationState valueAllocationState && valueAllocationState.getValue().equals(orig)) {
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

                // TODO: how to handle object kind?
                // If the same virtual value is present in the register then there isn't anything to be done
                // but maybe if it was changed we also maybe want to make sure that there's a pointer (Object) present?
                // but for that we would need to track that information based on GC instructions
            }

            this.checkAliveConstraint(op, block);
        }
    }

    public void update(RAVInstruction.Base instruction, BasicBlock<?> block) {
        switch (instruction) {
            case RAVInstruction.Op op -> this.updateWithOp(op, block);
            case RAVInstruction.VirtualMove virtMove -> this.updateWithVirtualMove(virtMove);
            case RAVInstruction.Move move -> this.values.putClone(move.to, this.values.get(move.from));
            default -> throw GraalError.shouldNotReachHere("Invalid RAV instruction " + instruction);
        }
    }

    protected void updateWithOp(RAVInstruction.Op op, BasicBlock<?> block) {
        for (int i = 0; i < op.dests.count; i++) {
            if (Value.ILLEGAL.equals(op.dests.orig[i])) {
                continue; // Safe to ignore, when destination is illegal value, not when used.
            }

            assert op.dests.orig[i] != null;

            if (op.dests.curr[i] == null) {
                if (phiResolution == PhiResolution.FromJump) {
                    throw new LabelNotResolvedError(block, op, phiResolution);
                }

                continue;
            }

            Value location = op.dests.curr[i];
            Value variable = op.dests.orig[i];

            if (location.equals(variable)) {
                // Only check register validity if it was changed by the register allocator
                // for example: rbp is used as input to start block and forbidden to be used by the allocator
                this.values.putWithoutRegCheck(location, new ValueAllocationState(variable, op));
            } else {
                this.values.put(location, new ValueAllocationState(variable, op));
            }
        }

        for (int i = 0; i < op.temp.count; i++) {
            var value = op.temp.curr[i];
            if (Value.ILLEGAL.equals(value)) {
                continue;
            }

            // We cannot believe the contents of registers used as temp, thus we need to reset.
            Value location = op.temp.curr[i];
            this.values.put(location, UnknownAllocationState.INSTANCE);
        }
    }

    protected void updateWithVirtualMove(RAVInstruction.VirtualMove virtMove) {
        if (virtMove.location instanceof RegisterValue) {
            this.values.put(virtMove.location, new ValueAllocationState(virtMove.variableOrConstant, virtMove));
        } else if (LIRValueUtil.isVariable(virtMove.location)) {
            // v4|QWORD[.] = MOVE input: v3|QWORD[.] moveKind: QWORD
            // Move before allocation
            // TODO: maybe handle this better than VirtualMove with location as Variable
            // TestCase: BoxingTest.boxBoolean
            var locations = this.values.getValueLocations(virtMove.variableOrConstant);
            for (var location : locations) {
                this.values.put(location, new ValueAllocationState(virtMove.location, virtMove));
            }
        } else {
            this.values.put(virtMove.location, new ValueAllocationState(virtMove.variableOrConstant, virtMove));
        }
    }
}
