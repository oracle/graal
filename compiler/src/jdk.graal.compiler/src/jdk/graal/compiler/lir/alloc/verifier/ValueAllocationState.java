package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

public class ValueAllocationState extends AllocationState implements Cloneable {
    protected Value value;
    protected RAVInstruction.Base source;

    public ValueAllocationState(Value value, RAVInstruction.Base source) {
        if (value instanceof RegisterValue || LIRValueUtil.isVariable(value) || value instanceof ConstantValue || value instanceof StackSlot || value instanceof VirtualStackSlot || Value.ILLEGAL.equals(value)) {
            // StackSlot, RegisterValue is present in start block in label as predefined argument
            // VirtualStackSlot is used for RESTORE_REGISTERS and SAVE_REGISTERS
            // ConstantValue act as Variable

            // We use variables as symbols for register validation
            // but real registers can also be used as that, in some cases.
            this.value = value;
            this.source = source;
        } else {
            throw GraalError.shouldNotReachHere("Invalid type of value used " + value);
        }
    }

    public ValueAllocationState(ValueAllocationState other) {
        this.value = other.getValue();
    }

    public Value getValue() {
        return value;
    }

    public RAVInstruction.Base getSource() {
        return source;
    }

    public AllocationState meet(AllocationState other) {
        if (other.isUnknown()) {
            return this;
        }

        if (other.isConflicted()) {
            return other;
        }

        var otherValueAllocState = (ValueAllocationState) other;
        if (!this.value.equals(otherValueAllocState.getValue())) {
            return new ConflictedAllocationState(this, otherValueAllocState);
        }

        return this;
    }

    @Override
    public boolean equals(AllocationState other) {
        return other instanceof ValueAllocationState otherVal && this.value.equals(otherVal.getValue());
    }

    @Override
    public ValueAllocationState clone() {
        return new ValueAllocationState(this);
    }

    @Override
    public String toString() {
        return "Value {" + this.value + "}";
    }

    @Override
    public int hashCode() {
        return this.value.hashCode();
    }
}
