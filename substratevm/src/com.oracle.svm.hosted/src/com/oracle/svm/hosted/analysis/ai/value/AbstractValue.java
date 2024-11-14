package com.oracle.svm.hosted.analysis.ai.value;

/**
 * Base class for representing the structure of elements of an abstract domain.
 * (constant, interval, set, etc.)
 * @param <Derived> the type of the derived value
 */

public abstract class AbstractValue<Derived extends AbstractValue<Derived>> {
    /**
     * Returns the kind of this abstract value.
     *
     * @return the kind of the value (TOP, BOT, or VAL)
     */
    public abstract AbstractValueKind kind();

    /**
     * Checks if this value is less than or equal to another value.
     *
     * @param other the other value to compare with
     * @return true if this value is less than or equal to the other value, false otherwise
     */
    public abstract boolean leq(Derived other);

    /**
     * Checks if this value is equal to another value.
     *
     * @param other the other value to compare with
     * @return true if this value is equal to the other value, false otherwise
     */
    public abstract boolean equals(Derived other);

    /**
     * Joins this value with another value.
     *
     * @param other the other value to join with
     * @return the kind of AbstractValue resulted from this operation
     */
    public abstract AbstractValueKind joinWith(Derived other);

    /**
     * Widens this value with another value.
     *
     * @param other the other value to widen with
     * @return the kind of AbstractValue resulted from this operation
     */
    public abstract AbstractValueKind widenWith(Derived other);

    /**
     * Meets this value with another value.
     *
     * @param other the other value to meet with
     * @return the kind of AbstractValue resulted from this operation
     */
    public abstract AbstractValueKind meetWith(Derived other);

    /**
     * Returns a string representation of this value.
     *
     * @return a string representation of this value
     */
    public abstract String toString();
}