package com.oracle.truffle.espresso.polyglot;

/**
 * An exception thrown if a executable or instantiable object was provided with the wrong number of
 * arguments.
 *
 * @since 0.11
 */
public final class ArityException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final int expectedArity;
    private final int actualArity;

    private ArityException(int expectedArity, int actualArity, Throwable cause) {
        super(null, cause);
        this.expectedArity = expectedArity;
        this.actualArity = actualArity;
    }

    private ArityException(int expectedArity, int actualArity) {
        super(null); // GR-23961 - after language adoption we should initialize the cause with null.
        this.expectedArity = expectedArity;
        this.actualArity = actualArity;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String getMessage() {
        return "Arity error - expected: " + expectedArity + " actual: " + actualArity;
    }

    /**
     * Returns the number of arguments that the foreign object expects.
     *
     * @return the number of expected arguments
     * @since 0.11
     */
    public int getExpectedArity() {
        return expectedArity;
    }

    /**
     * Returns the actual number of arguments provided by the foreign access.
     *
     * @return the number of provided arguments
     * @since 0.11
     */
    public int getActualArity() {
        return actualArity;
    }

    /**
     * Creates an {@link ArityException} to indicate that the wrong number of arguments were
     * provided.
     *
     * @param expectedArity the number of arguments expected by the foreign object
     * @param actualArity the number of provided by the foreign access
     * @since 19.0
     */
    public static ArityException create(int expectedArity, int actualArity) {
        return new ArityException(expectedArity, actualArity);
    }

    /**
     * Creates an {@link ArityException} to indicate that the wrong number of arguments were
     * provided.
     * <p>
     * In addition a cause may be provided. The cause should only be set if the guest language code
     * caused this problem. An example for this is a language specific proxy mechanism that invokes
     * guest language code to describe an object. If the guest language code fails to execute and
     * this interop exception is a valid interpretation of the error, then the error should be
     * provided as cause. The cause can then be used by the source language as new exception cause
     * if the {@link InteropException} is translated to a source language error. If the
     * {@link InteropException} is discarded, then the cause will most likely get discarded by the
     * source language as well.
     *
     * @param expectedArity the number of arguments expected by the foreign object
     * @param actualArity the number of provided by the foreign access
     * @param cause the guest language exception that caused the error.
     * @since 20.2
     */
    @SuppressWarnings("deprecation")
    public static ArityException create(int expectedArity, int actualArity, Throwable cause) {
        return new ArityException(expectedArity, actualArity, cause);
    }
}
