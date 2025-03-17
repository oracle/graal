package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.value.AbstractValue;
import com.oracle.svm.hosted.analysis.ai.domain.value.AbstractValueKind;

import java.util.Objects;
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
 * This way, we only have to implement specific methods for a more complicated abstract domain
 * without writing boilerplate code for methods enforced by {@link AbstractDomain}
 *
 * @param <Value>  the type of derived {@link AbstractValue}
 * @param <Domain> the type of derived {@link AbstractDomain}
 */
public class LatticeDomain<
        Value extends AbstractValue<Value>,
        Domain extends LatticeDomain<Value, Domain>>
        extends AbstractDomain<Domain> {

    private AbstractValueKind kind;
    private Value value;

    /**
     * Supply the value directly, and deduce the kind from the value
     * @param valueSupplier the supplier of the value
     */
    public LatticeDomain(Supplier<Value> valueSupplier) {
        this.value = valueSupplier.get();
        this.kind = value.getKind();
    }

    /**
     * Supply the kind and the value
     * @param kind the kind of the domain
     * @param valueSupplier the supplier of the value
     */
    public LatticeDomain(AbstractValueKind kind, Supplier<Value> valueSupplier) {
        this.kind = kind;
        this.value = valueSupplier.get();
    }

    public Value getValue() {
        return value;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LatticeDomain<?, ?> that = (LatticeDomain<?, ?>) o;
        return kind == that.kind && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
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
        updateKind();
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
        updateKind();
    }

    protected void setValue(Value value) {
        this.kind = value.getKind();
        this.value = value.copyOf();
        updateKind();
    }

    /**
     * NOTE:
     * This analysisMethod is used for keeping the kind in a consistent state after performing operations
     * Use this in the derived domain in every analysisMethod that somehow modifies the internal state
     */
    protected void updateKind() {
        kind = value.getKind();

        if (kind == AbstractValueKind.BOT) {
            return;
        }
        if (kind == AbstractValueKind.TOP) {
            value.clear();
        }
    }

    private void checkKind() {
        if (kind != AbstractValueKind.VAL) {
            throw new IllegalStateException("Invalid kind for operation");
        }
    }

    @Override
    public String toString() {
        return "LatticeDomain{" +
                "value=" + value +
                ", kind=" + kind +
                '}';
    }

    /* Should be implemented by the derived classes */
    @Override
    public Domain copyOf() {
        return null;
    }
}
