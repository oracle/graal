package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.lir.Variable;

/**
 * Wrapper around Variable to change how indexing
 * in data structures like Map or Set is done.
 * <p>
 * We index only by the Variable index instead of
 * including the kind as well.
 * </p>
 */
public class RAVariable extends RAValue {
    protected Variable variable;

    protected RAVariable(Variable variable) {
        super(variable);
        this.variable = variable;
    }

    @Override
    public RAVariable asVariable() {
        return this;
    }

    @Override
    public boolean isVariable() {
        return true;
    }

    public Variable getVariable() {
        return variable;
    }

    @Override
    public int hashCode() {
        return variable.index;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof RAVariable raVariable) {
            return variable.index == raVariable.variable.index;
        }

        return false;
    }

    @Override
    public String toString() {
        return "v" + variable.index;
    }
}
