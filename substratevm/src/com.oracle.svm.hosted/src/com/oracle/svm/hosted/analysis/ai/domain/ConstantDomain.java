package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;

/**
 * Abstract domain for flat lattice, also known as a 3 level lattice.
 * For domains that can be represented as a constant value and have infinite ascending and descending chains.
 *         ⊤
 *       / | \
 * ... -1  0  1 ...
 *      \ | /
 *        ⊥
 *
 * @param <Value> the type of the constant value (e.g., Integer, Long, Float, Double)
 */
public final class ConstantDomain<Value extends Number> extends AbstractDomain<ConstantDomain<Value>> {

    private AbstractValueKind kind;
    private Value value;

    public ConstantDomain() {
        value = getDefaultValue();
        kind = AbstractValueKind.BOT;
    }

    public ConstantDomain(AbstractValueKind kind) {
        this.kind = kind;
    }

    public ConstantDomain(Value value) {
        this.value = value;
        this.kind = AbstractValueKind.VAL;
    }

    public ConstantDomain(ConstantDomain<Value> other) {
        this.value = other.value;
        this.kind = other.kind;
    }

    public void incrementValue() {
        if (isTop() || isBot()) {
            return;
        }
        value = increment(value);
    }

    public void decrementValue() {
        if (isTop() || isBot()) {
            return;
        }
        value = decrement(value);
    }

    public Value getValue() {
        return value;
    }

    @Override
    public ConstantDomain<Value> copyOf() {
        return new ConstantDomain<>(value);
    }

    @Override
    public boolean isBot() {
        return kind == AbstractValueKind.BOT;
    }

    @Override
    public boolean isTop() {
        return kind == AbstractValueKind.TOP;
    }

    @Override
    public boolean leq(ConstantDomain<Value> other) {
        if (isBot()) {
            return true;
        }
        if (other.isBot()) {
            return false;
        }
        if (other.isTop()) {
            return true;
        }
        if (isTop()) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public boolean equals(ConstantDomain<Value> other) {
        if (isBot()) {
            return other.isBot();
        }
        if (isTop()) {
            return other.isTop();
        }
        if (!other.isVal()) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public void setToBot() {
        kind = AbstractValueKind.BOT;
    }

    @Override
    public void setToTop() {
        kind = AbstractValueKind.TOP;
    }

    @Override
    public void joinWith(ConstantDomain<Value> other) {
        if (isTop() || other.isBot()) {
            return;
        }
        if (other.isTop()) {
            setToTop();
            return;
        }
        if (isBot()) {
            value = other.value;
            return;
        }

        kind = value.equals(other.value) ? AbstractValueKind.VAL : AbstractValueKind.TOP;
    }

    @Override
    public void widenWith(ConstantDomain<Value> other) {
        joinWith(other);
    }

    @Override
    public void meetWith(ConstantDomain<Value> other) {
        if (isTop() || other.isBot()) {
            return;
        }
        if (other.isBot()) {
            setToBot();
            return;
        }
        if (isTop()) {
            value = other.value;
            return;
        }

        kind = value.equals(other.value) ? AbstractValueKind.VAL : AbstractValueKind.BOT;
    }

    @Override
    public String toString() {
        return "ConstantDomain{" +
                "kind=" + kind +
                ", value=" + value +
                '}';
    }

    private boolean isVal() {
        return kind == AbstractValueKind.VAL;
    }

    @SuppressWarnings("unchecked")
    private Value getDefaultValue() {
        return switch (value) {
            case Integer i -> (Value) Integer.valueOf(0);
            case Long l -> (Value) Long.valueOf(0);
            case Float v -> (Value) Float.valueOf(0);
            case Double v -> (Value) Double.valueOf(0);
            case null, default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private Value increment(Value value) {
        return switch (value) {
            case Integer i -> (Value) Integer.valueOf(i + 1);
            case Long l -> (Value) Long.valueOf(l + 1);
            case Float f -> (Value) Float.valueOf(f + 1);
            case Double d -> (Value) Double.valueOf(d + 1);
            case null, default -> value;
        };
    }

    @SuppressWarnings("unchecked")
    private Value decrement(Value value) {
        return switch (value) {
            case Integer i -> (Value) Integer.valueOf(i - 1);
            case Long l -> (Value) Long.valueOf(l - 1);
            case Float f -> (Value) Float.valueOf(f - 1);
            case Double d -> (Value) Double.valueOf(d - 1);
            case null, default -> value;
        };
    }
}