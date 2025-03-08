package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;

import java.util.Objects;

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
        this.kind = AbstractValueKind.BOT;
    }

    public ConstantDomain(Value value) {
        this.kind = AbstractValueKind.VAL;
        this.value = value;
    }

    public ConstantDomain(AbstractValueKind kind) {
        this.kind = kind;
        if (kind == AbstractValueKind.VAL) {
            throw new IllegalArgumentException("Invalid kind for this constructor");
        }
    }

    public Value getValue() {
        if (kind == AbstractValueKind.VAL) {
            return value;
        }
        return null;
    }

    public static <Value extends Number> ConstantDomain<Value> bottom() {
        return new ConstantDomain<>(AbstractValueKind.BOT);
    }

    public static <Value extends Number> ConstantDomain<Value> top() {
        return new ConstantDomain<>(AbstractValueKind.TOP);
    }

    @Override
    public boolean isBot() {
        return kind == AbstractValueKind.BOT;
    }

    @Override
    public boolean isTop() {
        return kind == AbstractValueKind.TOP;
    }

    public boolean isValue() {
        return kind == AbstractValueKind.VAL;
    }

    @Override
    public void setToBot() {
        kind = AbstractValueKind.BOT;
        value = null;
    }

    @Override
    public void setToTop() {
        kind = AbstractValueKind.TOP;
        value = null;
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
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConstantDomain<?> that = (ConstantDomain<?>) o;
        return equals((ConstantDomain<Value>) that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
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
            kind = other.kind;
            value = other.value;
            return;
        }
        if (!value.equals(other.value)) {
            setToTop();
        }
    }

    @Override
    public void widenWith(ConstantDomain<Value> other) {
        joinWith(other);
    }

    @Override
    public void meetWith(ConstantDomain<Value> other) {
        if (isBot() || other.isTop()) {
            return;
        }
        if (other.isBot()) {
            setToBot();
            return;
        }
        if (isTop()) {
            kind = other.kind;
            value = other.value;
            return;
        }
        if (!value.equals(other.value)) {
            setToBot();
        }
    }

    @Override
    public ConstantDomain<Value> copyOf() {
        ConstantDomain<Value> copy = new ConstantDomain<>();
        copy.kind = this.kind;
        copy.value = this.value;
        return copy;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case BOT -> "⊥";
            case TOP -> "⊤";
            case VAL -> value.toString();
            default -> throw new IllegalStateException("Unexpected value: " + kind);
        };
    }

    private boolean equals(ConstantDomain<Value> other) {
        if (isBot() && other.isBot()) {
            return true;
        }
        if (isTop() && other.isTop()) {
            return true;
        }
        if (isValue() && other.isValue()) {
            return value.equals(other.value);
        }
        return false;
    }
}
