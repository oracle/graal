package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Interface for abstract domain in GraalAF.
 *
 * @param <Derived> type of the derived {@link AbstractDomain}
 */
public interface AbstractDomain<Derived extends AbstractDomain<Derived>> {

    /**
     * Checks if the domain is the bottom element
     *
     * @return true if the domain is the bottom element
     */
    boolean isBot();

    /**
     * Checks if the domain is the top element
     *
     * @return true if the domain is the top element
     */
    boolean isTop();

    /**
     * Checks if the domain is less or equal to the other domain
     *
     * @param other domain to compare with
     * @return true if the domain is less or equal to the other domain
     */
    boolean leq(Derived other);

    /**
     * Sets the domain to the bottom element
     */
    void setToBot();

    /**
     * Sets the domain to the top element
     */
    void setToTop();

    /**
     * Joins the domain with the other domain, modifying the domain
     *
     * @param other domain to join with
     */
    void joinWith(Derived other);

    /**
     * Widens the domain with the other domain, modifying the domain
     *
     * @param other domain to widen with
     */
    void widenWith(Derived other);

    /**
     * Meets the domain with the other domain, modifying the domain
     *
     * @param other domain to meet with
     */
    void meetWith(Derived other);

    /**
     * String representation of the domain
     *
     * @return string representation of the domain
     */
    String toString();

    /**
     * Creates a copy of the domain
     *
     * @return copy of the domain
     */
    Derived copyOf();

    /**
     * Joins the domain with the other domain, returning a new domain
     * If the domain is a lattice, this is the least upper bound operation
     *
     * @param other domain to join with
     * @return new domain after joining
     */
    default Derived join(Derived other) {
        Derived copy = copyOf();
        copy.joinWith(other);
        return copy;
    }

    /**
     * Widens the domain with the other domain, returning a new domain
     * Used for acceleration of the fixpoint computation
     *
     * @param other domain to widen with
     * @return new domain after widening
     */
    default Derived widen(Derived other) {
        Derived copy = copyOf();
        copy.widenWith(other);
        return copy;
    }

    /**
     * Meets the domain with the other domain, returning a new domain
     * If the domain is a lattice, this is the greatest lower bound operation
     *
     * @param other domain to meet with
     * @return new domain after meeting
     */
    default Derived meet(Derived other) {
        Derived copy = copyOf();
        copy.meetWith(other);
        return copy;
    }

    /**
     * Creates a top value of the domain
     *
     * @param domain of which we want to get a top value
     * @return a new instance of the domain set to top
     */

    static <Domain extends AbstractDomain<Domain>> Domain createTop(Domain domain) {
        Domain copy = domain.copyOf();
        copy.setToTop();
        return copy;
    }

    /**
     * Creates a bot value of the domain
     *
     * @param domain of which we want to get a bot value
     * @return a new instance of the domain set to bot
     */
    static <Domain extends AbstractDomain<Domain>> Domain createBot(Domain domain) {
        Domain copy = domain.copyOf();
        copy.setToBot();
        return copy;
    }
}
