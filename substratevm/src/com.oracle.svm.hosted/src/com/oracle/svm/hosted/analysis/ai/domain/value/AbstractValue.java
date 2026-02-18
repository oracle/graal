package com.oracle.svm.hosted.analysis.ai.domain.value;

/**
 * Represents the structure of abstract contexts inside from an abstract domain.
 * This can be used for easier implementation of complex abstract domains.
 *
 * @param <Derived> the type of the derived {@link AbstractValue}
 */
public interface AbstractValue<Derived extends AbstractValue<Derived>> {

    /**
     * Returns the kind of this abstract value.
     *
     * @return the kind of the value (TOP, BOT, or VAL)
     */
    AbstractValueKind getKind();

    /**
     * Checks if this value is less than or equal to another value.
     *
     * @param other the other value to compare with
     * @return true if this value is less than or equal to the other value, false otherwise
     */
    boolean leq(Derived other);

    /**
     * Checks if this value is equal to another value.
     *
     * @param other the other value to compare with
     * @return true if this value is equal to the other value, false otherwise
     */
    boolean equals(Object other);

    /**
     * Joins this value with another value.
     *
     * @param other the other value to join with
     * @return the kind of AbstractValue resulted from this operation
     */
    AbstractValueKind joinWith(Derived other);

    /**
     * Widens this value with another value.
     *
     * @param other the other value to widen with
     * @return the kind of AbstractValue resulted from this operation
     */
    AbstractValueKind widenWith(Derived other);

    /**
     * Meets this value with another value.
     *
     * @param other the other value to meet with
     * @return the kind of AbstractValue resulted from this operation
     */
    AbstractValueKind meetWith(Derived other);

    /**
     * Returns a string representation of this value.
     *
     * @return a string representation of this value
     */
    String toString();

    /**
     * Some abstract values require a lot of memory to store their state.
     * This analysisMethod can be used to clear the memory and reset the value to a default state.
     */
    void clear();

    /**
     * Creates a copy of this abstract value.
     *
     * @return a copy of this abstract value
     */
    Derived copyOf();
}
