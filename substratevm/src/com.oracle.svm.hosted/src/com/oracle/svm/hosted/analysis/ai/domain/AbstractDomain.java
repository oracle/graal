package com.oracle.svm.hosted.analysis.ai.domain;

import java.util.function.Supplier;

/**
 * Basic API for Abstract Domains
 * <p>
 * More detailed description can be found here:
 * Patrick Cousot & Radhia Cousot. Abstract interpretation: a unified lattice
 * model for static analysis of programs by construction or approximation of
 * fixpoints.
 * <p>
 * We do not use narrowing operation in our abstract interpretation
 * All derived generic domains need to extend this class
 * Derived domains should have default constructor as well as copy constructor
 * Sample usage:
 * public final class MyCustomDomain extends AbstractDomain<MyCustomDomain> {}
 *
 * @param <Derived> type of the derived AbstractDomain
 */

public abstract class AbstractDomain<Derived extends AbstractDomain<Derived>> {
    /**
     * Checks if the domain is the bottom element
     *
     * @return true if the domain is the bottom element
     */
    public abstract boolean isBot();

    /**
     * Checks if the domain is the top element
     *
     * @return true if the domain is the top element
     */
    public abstract boolean isTop();

    /**
     * Checks if the domain is less or equal to the other domain
     *
     * @param other domain to compare with
     * @return true if the domain is less or equal to the other domain
     */
    public abstract boolean leq(Derived other);

    /**
     * Checks if the domain is equal to the other domain
     *
     * @param other domain to compare with
     * @return true if the domain is equal to the other domain
     */
    public abstract boolean equals(Derived other);

    /**
     * Sets the domain to the bottom element
     */
    public abstract void setToBot();

    /**
     * Sets the domain to the top element
     */
    public abstract void setToTop();

    /**
     * Joins the domain with the other domain, modifying the domain
     *
     * @param other domain to join with
     */
    public abstract void joinWith(Derived other);

    /**
     * Widens the domain with the other domain, modifying the domain
     *
     * @param other domain to widen with
     */
    public abstract void widenWith(Derived other);

    /**
     * Meets the domain with the other domain, modifying the domain
     *
     * @param other domain to meet with
     */
    public abstract void meetWith(Derived other);

    /**
     * String representation of the domain
     * @return string representation of the domain
     */
    public abstract String toString();

    /**
     * Joins the domain with the other domain, returning a new domain
     * If the domain is a lattice, this is the least upper bound operation
     * @param other domain to join with
     * @return new domain after joining
     */
    public Derived join(Derived other) {
        Derived copy = copyOf();
        copy.joinWith(other);
        return copy;
    }

    /**
     * Widens the domain with the other domain, returning a new domain
     * Used for acceleration of the fixpoint computation
     * @param other domain to widen with
     * @return new domain after widening
     */
    public Derived widen(Derived other) {
        Derived copy = copyOf();
        copy.widenWith(other);
        return copy;
    }

    /**
     * Meets the domain with the other domain, returning a new domain
     * If the domain is a lattice, this is the greatest lower bound operation
     * @param other domain to meet with
     * @return new domain after meeting
     */
    public Derived meet(Derived other) {
        Derived copy = copyOf();
        copy.meetWith(other);
        return copy;
    }

    /**
     * Creates a copy of the domain element
     *
     * @return copy of the domain element
     */
    public abstract Derived copyOf();

//    public static <T extends AbstractDomain<T>> T createTop(Supplier<T> supplier) {
//        T instance = supplier.get();
//        instance.setToTop();
//        return instance;
//    }
//
//    public static <T extends AbstractDomain<T>> T createBot(Supplier<T> supplier) {
//        T instance = supplier.get();
//        instance.setToBot();
//        return instance;
//    }
}
