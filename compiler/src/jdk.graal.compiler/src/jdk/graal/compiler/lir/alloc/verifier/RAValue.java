package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
 * Wrapper around Value to change how indexing
 * in data structures like Map or Set is done.
 * <p>
 * Register is indexed by its name without the kind.
 * Variable is indexed by its number index without the kind.
 * Stack slots and constants remain as is, because the kind
 * does not mess with indexing.
 * </p>
 */
public class RAValue {
    /**
     * Create a new RAValue instance from Value.
     *
     * @param value Value we are wrapping
     * @return Instance of RAValue
     */
    public static RAValue create(Value value) {
        if (LIRValueUtil.isVariable(value)) {
            return new RAVariable(LIRValueUtil.asVariable(value));
        }

        return new RAValue(value);
    }

    protected Value value;

    protected RAValue(Value value) {
        this.value = value;
    }

    public Value getValue() {
        return this.value;
    }

    public boolean isIllegal() {
        return Value.ILLEGAL.equals(value);
    }

    public RAVariable asVariable() {
        return (RAVariable) this;
    }

    public boolean isVariable() {
        return false;
    }

    @Override
    public int hashCode() {
        if (this.value instanceof RegisterValue registerValue) {
            return registerValue.getRegister().hashCode();
        }

        return this.value.hashCode();
    }

    /**
     * Equal RegisterValue on it's Register, not Register and kind,
     * otherwise same as Value.
     *
     * @param other The reference object with which to compare.
     * @return Are said values equal?
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof RAValue otherValueWrap) {
            if (this.value instanceof RegisterValue thisReg && otherValueWrap.value instanceof RegisterValue otherReg) {
                return thisReg.getRegister().equals(otherReg.getRegister());
            }

            return this.value.equals(otherValueWrap.value);
        }

        return false;
    }

    @Override
    public String toString() {
        if (value instanceof RegisterValue regValue) {
            return regValue.getRegister().toString();
        }

        return value.toString();
    }
}
