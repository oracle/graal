package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.LIRKindWithCast;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

public class MergedBlockVerifierState {
    public MergedAllocationStateMap values;

    protected PhiResolution phiResolution;

    public MergedBlockVerifierState(PhiResolution phiResolution) {
        this.values = new MergedAllocationStateMap();
        this.phiResolution = phiResolution;
    }

    protected MergedBlockVerifierState(MergedBlockVerifierState other, PhiResolution phiResolution) {
        this.phiResolution = phiResolution;

        if (other == null) {
            this.values = new MergedAllocationStateMap();
            return;
        }

        this.values = new MergedAllocationStateMap(other.values);
    }

    public MergedAllocationStateMap getValues() {
        return values;
    }

    public boolean meetWith(MergedBlockVerifierState other) {
        return this.values.mergeWith(other.getValues());
    }

    protected void checkInputs(RAVInstruction.ValueArrayPair values, RAVInstruction.Op op, BasicBlock<?> block, RAVInstruction.Op labelOp) {
        // Check that incoming values are not unknown or conflicted - these only matter if used
        for (int idx = 0; idx < values.count; idx++) {
            var orig = values.orig[idx];
            var curr = values.curr[idx];

            assert orig != null;

            if (curr == null) {
                if (op.lirInstruction instanceof StandardOp.JumpOp) {
                    if (phiResolution == PhiResolution.FromUsage) {
                        // Variable has no usage, thus no location present.
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

            if (!kindsEqual(orig, curr)) {
                throw new KindsMismatchException(op.lirInstruction, block, orig, curr, true);
            }

            AllocationState state = this.values.get(curr);
            if (state.isConflicted() || state.isUnknown()) {
                throw new ValueNotInRegisterException(op.lirInstruction, block, orig, curr, state);
            }

            if (state instanceof ValueAllocationState valAllocState) {
                if (!valAllocState.value.equals(orig)) {
                    if (orig instanceof CastValue castValue && valAllocState.value.equals(castValue.underlyingValue())) {
                        // check for underlying value for CastValue.
                        continue;
                    }

                    // Kind sizes should be checked here as well.
                    throw new KindsMismatchException(op.lirInstruction, block, orig, valAllocState.value, false);
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
            // TODO: figure out the correct semantics for these casts
            return origKind.getPlatformKind().equals(currKind.getPlatformKind());
        }

        return false;
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

            this.checkAliveConstraint(op, block);
        }
    }

    public void update(RAVInstruction.Base instruction, BasicBlock<?> block) {
        switch (instruction) {
            case RAVInstruction.Op op -> this.updateWithOp(op, block);
            case RAVInstruction.Spill spill -> this.values.putClone(spill.to, this.values.get(spill.from));
            case RAVInstruction.Reload reload -> this.values.putClone(reload.to, this.values.get(reload.from));
            case RAVInstruction.Move move -> this.values.putClone(move.to, this.values.get(move.from));
            case RAVInstruction.StackMove move -> this.values.putClone(move.to, this.values.get(move.from));
            case RAVInstruction.VirtualMove virtMove -> this.updateWithVirtualMove(virtMove);
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
            this.values.put(location, new ValueAllocationState(variable));
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
            this.values.put(virtMove.location, new ValueAllocationState(virtMove.variableOrConstant));
        } else if (LIRValueUtil.isVariable(virtMove.location)) {
            // v4|QWORD[.] = MOVE input: v3|QWORD[.] moveKind: QWORD
            // Move before allocation
            // TODO: maybe handle this better than VirtualMove with location as Variable
            // TestCase: BoxingTest.boxBoolean
            var locations = this.values.getValueLocations(virtMove.variableOrConstant);
            for (var location : locations) {
                this.values.put(location, new ValueAllocationState(virtMove.location));
            }
        } else {
            this.values.put(virtMove.location, new ValueAllocationState(virtMove.variableOrConstant));
        }
    }
}
