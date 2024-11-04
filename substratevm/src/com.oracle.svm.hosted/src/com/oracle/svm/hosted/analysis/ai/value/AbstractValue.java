package com.oracle.svm.hosted.analysis.ai.value;

/**
 * Base class for elements of an abstract domain (e.g., integer, interval, sign).
 *
 * @param <T> the type of the derived value
 */

public abstract class AbstractValue<T extends AbstractValue<T>> {
    /**
     * Returns the kind of this abstract value.
     *
     * @return the kind of the value
     */
    public abstract AbstractValueKind kind();

    /**
     * Checks if this value is less than or equal to another value.
     *
     * @param other the other value to compare with
     * @return true if this value is less than or equal to the other value, false otherwise
     */
    public abstract boolean leq(T other);

    /**
     * Checks if this value is equal to another value.
     *
     * @param other the other value to compare with
     * @return true if this value is equal to the other value, false otherwise
     */
    public abstract boolean equals(T other);

    /**
     * Joins this value with another value.
     *
     * @param other the other value to join with
     * @return the result of the join operation
     */
    public abstract AbstractValueKind joinWith(T other);

    /**
     * Widens this value with another value.
     *
     * @param other the other value to widen with
     * @return the result of the widen operation
     */
    public abstract AbstractValueKind widenWith(T other);

    /**
     * Meets this value with another value.
     *
     * @param other the other value to meet with
     * @return the result of the meet operation
     */
    public abstract AbstractValueKind meetWith(T other);
}