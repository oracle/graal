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
 * @param <T> derived AbstractDomain
 */

public abstract class AbstractDomain<T extends AbstractDomain<T>> {
    protected abstract T copyOf();

    public abstract boolean isBot();

    public abstract boolean isTop();

    public abstract boolean leq(T other);

    public abstract boolean equals(T other);

    public abstract void setToBot();

    public abstract void setToTop();

    public abstract void joinWith(T other);

    public abstract void widenWith(T other);

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        @SuppressWarnings("unchecked")
        T other = (T) obj;
        return this.equals(other);
    }
}
