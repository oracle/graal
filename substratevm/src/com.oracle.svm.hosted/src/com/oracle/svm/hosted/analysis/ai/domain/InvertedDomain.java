package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.Objects;

/**
 * Reverse Adaptor of an {@link AbstractDomain}
 * Reverses the top and bottom elements of an abstract domain
 * and also reverses meet and join operations
 * NOTE: Our framework doesn't use narrowing ( yet ) so we don't have a counterpart for widening.
 *       We can overcome this obstacle by implementing widening as a meet operation. But this is not ideal,
 *       since programs that do not terminate will not be able to use this domain
 *       + the fixpoint computation may be much slower on programs that use loops.
 */
public final class InvertedDomain<Domain extends AbstractDomain<Domain>>
        extends AbstractDomain<InvertedDomain<Domain>> {

    private final Domain domain;

    public InvertedDomain(Domain domain) {
        this.domain = domain;
    }

    public Domain getDomain() {
        return domain;
    }

    @Override
    public boolean isBot() {
        return domain.isTop();
    }

    @Override
    public boolean isTop() {
        return domain.isBot();
    }

    @Override
    public boolean leq(InvertedDomain<Domain> other) {
        return domain.equals(other.domain) || !domain.leq(other.domain);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        InvertedDomain<?> that = (InvertedDomain<?>) o;
        return Objects.equals(domain, that.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(domain);
    }

    @Override
    public void setToBot() {
        domain.setToTop();
    }

    @Override
    public void setToTop() {
        domain.setToBot();
    }

    @Override
    public void joinWith(InvertedDomain<Domain> other) {
        domain.meetWith(other.getDomain());
    }

    @Override
    public void widenWith(InvertedDomain<Domain> other) {
        domain.meetWith(other.getDomain());
    }

    @Override
    public void meetWith(InvertedDomain<Domain> other) {
        domain.joinWith(other.getDomain());
    }

    @Override
    public String toString() {
        return "InvertedDomain{" +
                "domain=" + domain +
                '}';
    }

    @Override
    public InvertedDomain<Domain> copyOf() {
        return new InvertedDomain<>(domain.copyOf());
    }
}
