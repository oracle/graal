package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.value.AbstractValue;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;

import java.util.function.Supplier;

/**
 * LatticeDomain provides basic logic for handling operations
 * on abstract domains that are lattices by definition
 * Sample usage:
 * <p>
 * public final class CustomAbstractValue extends AbstractValue<CustomAbstractValue> {}
 * <p>
 * public final class CustomAbstractDomain extends LatticeDomain<CustomAbstractValue, CustomAbstractDomain> {}
 * <p>
 * This way, we only have to implement domain specific methods inside CustomAbstractDomain
 * without writing boilerplate code for handling methods from AbstractDomain.
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

    public LatticeDomain(Supplier<Value> valueSupplier) {
        this.value = valueSupplier.get();
        this.kind = AbstractValueKind.VAL;
    }

    public LatticeDomain(AbstractValueKind kind, Supplier<Value> valueSupplier) {
        this.kind = kind;
        this.value = valueSupplier.get();
    }

    public AbstractValueKind getKind() {
        return kind;
    }

    public boolean isBot() {
        return kind == AbstractValueKind.BOT;
    }

    public boolean isTop() {
        return kind == AbstractValueKind.TOP;
    }

    public boolean isVal() {
        return kind == AbstractValueKind.VAL;
    }

    public void setToBot() {
        kind = AbstractValueKind.BOT;
        value.clear();
    }

    public void setToTop() {
        kind = AbstractValueKind.TOP;
        value.clear();
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

    @Override
    public Domain copyOf() {
        return null;
    }

    protected void performJoinOperation(Domain other, Runnable operation) {
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

    protected void performMeetOperation(Domain other, Runnable operation) {
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

    /**
     * This method is used for keeping the domain in a consistent state after performing operations
     */
    protected void checkConsistency() {
        if (kind == AbstractValueKind.BOT) {
            return;
        }
        kind = value.kind();
        if (kind == AbstractValueKind.TOP) {
            value.clear();
        }
    }

    private void checkKind() {
        if (kind != AbstractValueKind.VAL) {
            throw new IllegalStateException("Invalid kind for operation");
        }
    }
}
