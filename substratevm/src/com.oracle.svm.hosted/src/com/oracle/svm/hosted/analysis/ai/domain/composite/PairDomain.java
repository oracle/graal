package com.oracle.svm.hosted.analysis.ai.domain.composite;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Represents a pair of two abstract domains.
 * Implemented using the cartesian product of the two abstract domains.
 *
 * @param <First>  the type of the first abstract domain
 * @param <Second> the type of the second abstract domain
 */
public record PairDomain<
        First extends AbstractDomain<First>,
        Second extends AbstractDomain<Second>>(First first, Second second)
        implements AbstractDomain<PairDomain<First, Second>> {

    @Override
    public boolean isBot() {
        return first.isBot() && second.isBot();
    }

    @Override
    public boolean isTop() {
        return first.isTop() && second.isTop();
    }

    @Override
    public boolean leq(PairDomain<First, Second> other) {
        return first.leq(other.first) && second.leq(other.second);
    }

    @Override
    public void setToBot() {
        first.setToBot();
        second.setToBot();
    }

    @Override
    public void setToTop() {
        first.setToTop();
        second.setToTop();
    }

    @Override
    public void joinWith(PairDomain<First, Second> other) {
        first.joinWith(other.first);
        second.joinWith(other.second);
    }

    @Override
    public void widenWith(PairDomain<First, Second> other) {
        first.widenWith(other.first);
        second.widenWith(other.second);
    }

    @Override
    public void meetWith(PairDomain<First, Second> other) {
        first.meetWith(other.first);
        second.meetWith(other.second);
    }

    @Override
    public String toString() {
        return "PairDomain{" + first + ", " + second + '}';
    }

    @Override
    public PairDomain<First, Second> copyOf() {
        return new PairDomain<>(first.copyOf(), second.copyOf());
    }
}
