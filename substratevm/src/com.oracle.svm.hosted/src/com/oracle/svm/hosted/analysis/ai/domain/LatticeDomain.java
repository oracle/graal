package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValue;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;

/**
 * Class for creating lattice-like abstract domains
 * Provides basic logic for handling abstract values
 * Sample usage:
 * public class CustomAbstractValue extends AbstractValue<CustomAbstractValue> {}
 * <p>
 * public class CustomAbstractDomain extends LatticeDomain<Value, CustomAbstractDomain> {}
 *
 * @param <Value>  the type of derived AbstractValue
 * @param <Domain> the type of derived AbstractDomain
 */

public class LatticeDomain<
        Value extends AbstractValue<Value>,
        Domain extends LatticeDomain<Value, Domain>>
        extends AbstractDomain<Domain> {

    protected AbstractValueKind kind;
    private Value value;

    /**
     * Default constructor should create lattice element that will be
     * the entry point in fixpoint iteration
     */
    public LatticeDomain() {
        this.kind = AbstractValueKind.VAL;
    }

    /**
     * Constructors for creating TOP or BOT elements
     *
     * @param kind desired kind, can be either TOP or BOT,
     * @throws IllegalAccessException when VAL kind is provided
     */
    public LatticeDomain(AbstractValueKind kind) throws IllegalAccessException {
        if (kind == AbstractValueKind.VAL) {
            throw new IllegalAccessException("Cannot initialize with VAL kind directly");
        }
        this.kind = kind;
    }

    public AbstractValueKind getKind() {
        return kind;
    }

    @Override
    public Domain copyOf() {
        return null; // Placeholder, implement copyOf inside the actual domain extending LatticeDomain
    }

    public boolean isBot() {
        return kind == AbstractValueKind.BOT;
    }

    public boolean isTop() {
        return kind == AbstractValueKind.TOP;
    }

    public boolean isValue() {
        return kind == AbstractValueKind.VAL;
    }

    public void setToBot() {
        kind = AbstractValueKind.BOT;
    }

    public void setToTop() {
        kind = AbstractValueKind.TOP;
    }

    public boolean leq(Domain other) {
        if (isBot()) return true;
        if (other.isBot()) return false;
        if (other.isTop()) return true;
        if (isTop()) return false;
        checkKind();
        return value.leq(other.getValue());
    }

    public boolean equals(Domain other) {
        if (isBot()) return other.isBot();
        if (isTop()) return other.isTop();
        checkKind();
        return value.equals(other.getValue());
    }

    public void joinWith(Domain other) {
        performJoinOperation(other, () -> kind = value.joinWith(other.getValue()));
    }

    public void widenWith(Domain other) {
        performJoinOperation(other, () -> kind = value.widenWith(other.getValue()));
    }

    public void meetWith(Domain other) {
        performMeetOperation(other, () -> kind = value.meetWith(other.getValue()));
    }

    @Override
    public String toString() {
        return "ConstantDomain{" +
                "kind=" + kind +
                ", value=" + value +
                '}';
    }

    private void performJoinOperation(Domain other, Runnable operation) {
        if (isTop() || other.isBot()) return;
        if (other.isTop()) {
            setToTop();
            return;
        }
        if (isBot()) {
            kind = other.getKind();
            value = other.getValue();
            return;
        }
        operation.run();
    }

    private void performMeetOperation(Domain other, Runnable operation) {
        if (isBot() || other.isTop()) return;
        if (other.isBot()) {
            setToBot();
            return;
        }
        if (isTop()) {
            kind = other.getKind();
            value = other.getValue();
            return;
        }
        operation.run();
    }

    protected Value getValue() {
        return value;
    }

    protected void setValue(Value value) {
        this.kind = value.kind();
        this.value = value;
    }

    protected void normalize() {
        if (kind == AbstractValueKind.BOT) {
            return;
        }
        kind = value.kind();
    }

    private void checkKind() {
        if (kind != AbstractValueKind.VAL) {
            throw new IllegalStateException("Invalid kind for operation");
        }
    }
}
