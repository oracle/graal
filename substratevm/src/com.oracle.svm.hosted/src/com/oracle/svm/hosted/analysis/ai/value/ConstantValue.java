package com.oracle.svm.hosted.analysis.ai.value;

/**
 * Represents a constant value in the abstract domain.
 *
 * @param <Value> the type of the constant (e.g., int, float, char)
 */
public final class ConstantValue<Value> extends AbstractValue<ConstantValue<Value>> {
    private Value constant;

    /**
     * Constructs an empty ConstantValue.
     */
    public ConstantValue() {
    }

    /**
     * Constructs a ConstantValue with the specified constant.
     *
     * @param constant the constant value
     */
    public ConstantValue(Value constant) {
        this.constant = constant;
    }

    public Value getConstant() {
        return constant;
    }

    @Override
    public AbstractValueKind kind() {
        return AbstractValueKind.VAL;
    }

    @Override
    public boolean leq(ConstantValue<Value> other) {
        return equals(other);
    }

    @Override
    public boolean equals(ConstantValue<Value> other) {
        return constant == other.getConstant();
    }

    @Override
    public AbstractValueKind joinWith(ConstantValue<Value> other) {
        if (equals(other)) {
            return AbstractValueKind.VAL;
        }
        return AbstractValueKind.TOP;
    }

    @Override
    public AbstractValueKind widenWith(ConstantValue<Value> other) {
        return joinWith(other);
    }

    @Override
    public AbstractValueKind meetWith(ConstantValue<Value> other) {
        if (equals(other)) {
            return AbstractValueKind.VAL;
        }
        return AbstractValueKind.BOT;
    }
}