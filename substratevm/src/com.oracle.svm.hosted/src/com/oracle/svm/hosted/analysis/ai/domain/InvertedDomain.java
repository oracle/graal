package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Reverse Adaptor of an {@link AbstractDomain}
 * Reverses the top and bottom elements of an abstract domain
 * and also reverses meet and join operations
 */
public final class InvertedDomain<
        Domain extends InvertedDomain<Domain>>
        extends AbstractDomain<Domain> {

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
    public boolean leq(Domain other) {
        return other.equals(other.getDomain()) || !domain.leq(other.getDomain());
    }

    @Override
    public boolean equals(Domain other) {
        return domain.equals(other.getDomain());
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
    public void joinWith(Domain other) {
        domain.meetWith(other.getDomain());
    }

    @Override
    public void widenWith(Domain other) {
        /*
            Since we do not have narrowing as a counterpart to widening,
            we don't do anything here
         */
    }

    @Override
    public void meetWith(Domain other) {
        other.joinWith(other.getDomain());
    }

    @Override
    public String toString() {
        return "InvertedDomain{" +
                "domain=" + domain +
                '}';
    }

    @SuppressWarnings("unchecked")
    @Override
    public Domain copyOf() {
        try {
            return (Domain) this.getClass().getDeclaredConstructor(domain.getClass()).newInstance(domain.copyOf());
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy InvertedDomain", e);
        }
    }
}