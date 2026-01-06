package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.LIRKindWithCast;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.StandardOp;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

public class MergedBlockVerifierState {
    public MergedAllocationStateMap values;

    protected PhiResolution phiResolution;

    public MergedBlockVerifierState(PhiResolution phiResolution) {
        this.values =  new MergedAllocationStateMap();
        this.phiResolution = phiResolution;
    }

    protected MergedBlockVerifierState(MergedBlockVerifierState other, PhiResolution phiResolution) {
        this.phiResolution = phiResolution;

        if (other == null) {
            this.values =  new MergedAllocationStateMap();
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

    protected boolean checkInputs(RAVInstruction.ValueArrayPair values, boolean isJump) {
        // Check that incoming values are not unknown or conflicted - these only matter if used
        for (int idx = 0; idx < values.count; idx++) {
            var orig = values.orig[idx];
            var curr = values.curr[idx];

            if (curr == null && isJump && phiResolution == PhiResolution.FromUsage) {
                // Whenever PhiResolution = FromUsage, variable is not used and thus no register present.
                continue;
            }

            assert orig != null;

            if (curr == null) {
                if (isJump) {
                    throw new RuntimeException(this.getMissingLabelOrJumpErrMsg("JUMP", values));
                }

                assert false;
            }

            if (orig.equals(curr)) {
                // In this case nothing has changed so we have nothing to verify
                continue;
            }

            AllocationState state = this.values.get(curr);

            if (!kindsEqual(orig, curr)) {
                return false;
            }

            if (state.isConflicted() || state.isUnknown()) {
                return false;
            }

            if (state instanceof ValueAllocationState valAllocState) {
                if (!valAllocState.value.equals(orig)) {
                    if (orig instanceof CastValue castValue && valAllocState.value.equals(castValue.underlyingValue())) {
                        // check for underlying value for CastValue.
                        continue;
                    }

                    // Kind sizes should be checked here as well.
                    return false;
                }

                continue;
            }

            throw new IllegalStateException(); // Should never reach here.
        }

        return true;
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

    protected boolean checkAliveConstraint(RAVInstruction.Op instruction) {
        for (int i = 0; i < instruction.alive.count; i++) {
            Value value = instruction.alive.curr[i];
            if (Value.ILLEGAL.equals(value)) {
                continue; // TODO: remove IllegalValues from these arrays.
            }

            for (int j = 0; j < instruction.temp.count; j++) {
                if (value.equals(instruction.temp.curr[j])) {
                    return false;
                }
            }

            for (int j = 0; j < instruction.dests.count; j++) {
                if (value.equals(instruction.dests.curr[j])) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean check(RAVInstruction.Base instruction) {
        if (instruction instanceof RAVInstruction.Op op) {
            boolean isJump = op.lirInstruction instanceof StandardOp.JumpOp;

            if (!checkInputs(op.uses, isJump)) {
                return false;
            }

            if (!checkInputs(op.alive, isJump)) {
                return false;
            }

            for (int i = 0; i < op.temp.count; i++) {
                var curr = op.temp.curr[i];
                var orig = op.temp.orig[i];

                if (!kindsEqual(orig, curr)) {
                    // Make sure the assigned register has the correct kind for temp.
                    return false;
                }
            }

            if (!this.checkAliveConstraint(op)) {
                return false;
            }
        }

        return true;
    }

    public String getMissingLabelOrJumpErrMsg(String subject, RAVInstruction.ValueArrayPair values) {
        String errorMsg = "[";
        for (int j = 0; j < values.count; j++) {
            errorMsg += values.orig[j].toString();
            if (values.curr[j] != null) {
                errorMsg += " -> " + values.curr[j].toString();
            } else {
                errorMsg += " -> ?";
            }

            if (j != values.count - 1) {
                errorMsg += ", ";
            }
        }

        return "Failed to resolve: " + subject + " " + errorMsg + "]";
    }

    public void update(RAVInstruction.Base instruction) {
        switch (instruction) {
            case RAVInstruction.Op op -> {
                for (int i = 0; i < op.dests.count; i++) {
                    if (Value.ILLEGAL.equals(op.dests.orig[i])) {
                        continue; // Safe to ignore, when destination is illegal value, not when used.
                    }

                    if ((phiResolution == PhiResolution.FromPredecessors
                            || phiResolution == PhiResolution.FromUsage)
                            && op.dests.curr[i] == null) {
                        // This can happen for certain instructions - jump or label, and we need to
                        // resolve appropriate registers for these, if we do not, we throw in check()
                        continue;
                    }

                    // Here, FromJump resolution will fail if it was not completed
                    if (op.dests.curr[i] == null && phiResolution == PhiResolution.FromJump) {
                        throw new RuntimeException(this.getMissingLabelOrJumpErrMsg("LABEL", op.dests));
                    }

                    assert op.dests.curr[i] != null;
                    assert op.dests.orig[i] != null;

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
            case RAVInstruction.Spill spill ->
                    this.values.putClone(spill.to, this.values.get(spill.from));
            case RAVInstruction.Reload reload ->
                    this.values.putClone(reload.to, this.values.get(reload.from));
            case RAVInstruction.Move move -> {
                var value = this.values.get(move.from);
                if (value.isUnknown()) {
                    // Hotfix for blockDefinitions, where if we moved a Value we need to make
                    // sure it's saved in the state, so that value is not propagated further
                    // causing Circular Exception
                    // TestCase: ConditionalElimination17
                    value = new ValueAllocationState(Value.ILLEGAL);
                }

                this.values.putClone(move.to, value);
            }
            case RAVInstruction.StackMove move ->
                    this.values.putClone(move.to, this.values.get(move.from));
            case RAVInstruction.VirtualMove virtMove -> {
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
            default -> throw new IllegalStateException();
        }
    }
}
