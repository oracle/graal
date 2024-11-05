package com.oracle.svm.hosted.analysis.ai.domain;

/**
 * Basic API for Abstract Domains
 * <p>
 * More detailed description can be found here:
 * Patrick Cousot & Radhia Cousot. Abstract interpretation: a unified lattice
 * model for static analysis of programs by construction or approximation of
 * fixpoints.
 * <p>
 * We do not use narrowing operation in our abstract interpretation
 * Sample usage:
 * public class MyCustomDomain extends AbstractDomain<MyCustomDomain> {}
 *
 * @param <T> type of the derived AbstractDomain
 */

public abstract class AbstractDomain<T extends AbstractDomain<T>> {
    /**
     * Creates a copy of the domain element
     *
     * @return copy of the domain element
     */
    protected abstract T copyOf();

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
    public abstract boolean leq(T other);

    /**
     * Checks if the domain is equal to the other domain
     *
     * @param other domain to compare with
     * @return true if the domain is equal to the other domain
     */
    public abstract boolean equals(T other);

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
    public abstract void joinWith(T other);

    /**
     * Widens the domain with the other domain, modifying the domain
     *
     * @param other domain to widen with
     */
    public abstract void widenWith(T other);

    /**
     * Meets the domain with the other domain, modifying the domain
     *
     * @param other domain to meet with
     */
    public abstract void meetWith(T other);

    public T join(T other) {
        T copy = copyOf();
        copy.joinWith(other);
        return copy;
    }

    public T widen(T other) {
        T copy = copyOf();
        copy.widenWith(other);
        return copy;
    }

    public T meet(T other) {
        T copy = copyOf();
        copy.meetWith(other);
        return copy;
    }

    public static <T extends AbstractDomain<T>> T createTop(Class<T> clazz) {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            instance.setToTop();
            return instance;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error creating top instance of " + clazz.getName(), e);
        }
    }

    public static <T extends AbstractDomain<T>> T createBot(Class<T> clazz) {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            instance.setToBot();
            return instance;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error creating bottom instance of " + clazz.getName(), e);
        }
    }
}
