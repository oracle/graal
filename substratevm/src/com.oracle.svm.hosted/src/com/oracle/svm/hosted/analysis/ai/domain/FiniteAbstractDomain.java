package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.value.AbstractValueKind;

import java.util.Objects;

/**
 * A generic wrapper class for handling domains with finitely many states.
 * For example SignDomain, ParityDomain, etc.
 *
 * @param <State> the type of the finite state
 */
public abstract class FiniteAbstractDomain<State> extends AbstractDomain<FiniteAbstractDomain<State>> {
    private State state;
    private AbstractValueKind kind;

    public FiniteAbstractDomain(State initialState, AbstractValueKind initialKind) {
        this.state = initialState;
        this.kind = initialKind;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public AbstractValueKind getKind() {
        return kind;
    }

    public void setKind(AbstractValueKind kind) {
        this.kind = kind;
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
    public boolean leq(FiniteAbstractDomain<State> other) {
        if (isBot()) return true;
        if (other.isBot()) return false;
        if (other.isTop()) return true;
        if (isTop()) return false;
        return state.equals(other.state);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FiniteAbstractDomain<?> that = (FiniteAbstractDomain<?>) o;
        return Objects.equals(state, that.state) && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, kind);
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
    public void joinWith(FiniteAbstractDomain<State> other) {
        if (isTop() || other.isBot()) return;
        if (other.isTop()) {
            setToTop();
            return;
        }
        if (isBot()) {
            state = other.state;
            kind = other.kind;
            return;
        }
        kind = state.equals(other.state) ? AbstractValueKind.VAL : AbstractValueKind.TOP;
    }

    @Override
    public void widenWith(FiniteAbstractDomain<State> other) {
        joinWith(other);
    }

    @Override
    public void meetWith(FiniteAbstractDomain<State> other) {
        if (isBot() || other.isTop()) return;
        if (other.isBot()) {
            setToBot();
            return;
        }
        if (isTop()) {
            state = other.state;
            kind = other.kind;
            return;
        }
        kind = state.equals(other.state) ? AbstractValueKind.VAL : AbstractValueKind.BOT;
    }

    @Override
    public String toString() {
        return "FiniteAbstractDomain{" +
                "state=" + state +
                ", kind=" + kind +
                '}';
    }

    @Override
    public abstract FiniteAbstractDomain<State> copyOf();
}
