package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

public class ValueAllocationState extends AllocationState implements Cloneable {
    protected RAValue value;
    protected RAVInstruction.Base source;
    protected BasicBlock<?> block;

    public ValueAllocationState(RAValue raValue, RAVInstruction.Base source, BasicBlock<?> block) {
        var value = raValue.getValue();
        if (value instanceof RegisterValue || LIRValueUtil.isVariable(value) || value instanceof ConstantValue || value instanceof StackSlot || value instanceof VirtualStackSlot || Value.ILLEGAL.equals(value)) {
            // StackSlot, RegisterValue is present in start block in label as predefined argument
            // VirtualStackSlot is used for RESTORE_REGISTERS and SAVE_REGISTERS
            // ConstantValue act as Variable

            // We use variables as symbols for register validation
            // but real registers can also be used as that, in some cases.
            this.value = raValue;
            this.source = source;
            this.block = block;
        } else {
            throw GraalError.shouldNotReachHere("Invalid type of value used " + value);
        }
    }

    public ValueAllocationState(ValueAllocationState other) {
        this.value = other.getRAValue();
        this.source = other.getSource();
        this.block = other.getBlock();
    }

    public static ValueAllocationState createIllegal() {
        // TODO: pass in the block that created this value
        return new ValueAllocationState(new RAValue(Value.ILLEGAL), null, null);
    }

    public Value getValue() {
        return value.getValue();
    }

    public RAValue getRAValue() {
        return value;
    }

    public RAVInstruction.Base getSource() {
        return source;
    }

    public BasicBlock<?> getBlock() {
        return block;
    }

    public AllocationState meet(AllocationState other) {
        if (other.isUnknown()) {
            return new ConflictedAllocationState(createIllegal(), this);
        }

        if (other.isConflicted()) {
            var oldConfState = (ConflictedAllocationState) other;
            var newConfState = new ConflictedAllocationState(oldConfState.conflictedStates);
            newConfState.addConflictedValue(this);
            return newConfState;
        }

        var otherValueAllocState = (ValueAllocationState) other;
        if (!this.value.equals(otherValueAllocState.getRAValue())) {
            return new ConflictedAllocationState(this, otherValueAllocState);
        }

        return this;
    }

    @Override
    public boolean equals(AllocationState other) {
        return other instanceof ValueAllocationState otherVal && this.value.equals(otherVal.getRAValue());
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
