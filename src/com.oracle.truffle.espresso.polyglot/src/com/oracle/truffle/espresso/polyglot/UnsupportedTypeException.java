package com.oracle.truffle.espresso.polyglot;

/**
 * An exception thrown if an interop {@link Object} does not support the type of one ore more
 * arguments.
 *
 * @since 0.11
 */
public final class UnsupportedTypeException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final Object[] suppliedValues;

    private UnsupportedTypeException(String message, Object[] suppliedValues) {
        super(message); // GR-23961 - after language adoption we should initialize the cause with
        // null.
        this.suppliedValues = suppliedValues;
    }

    private UnsupportedTypeException(String message, Object[] suppliedValues, Throwable cause) {
        super(message, cause);
        this.suppliedValues = suppliedValues;
    }

    /**
     * Returns the arguments of the foreign object access that were not supported by the foreign
     * {@link Object}.
     *
     * @return the unsupported arguments
     * @since 0.11
     */
    public Object[] getSuppliedValues() {
        return suppliedValues;
    }

    /**
     * Creates an {@link UnsupportedTypeException} to indicate that an argument type is not
     * supported.
     *
     * @since 19.0
     */
    public static UnsupportedTypeException create(Object[] suppliedValues) {
        return new UnsupportedTypeException((String) null, suppliedValues);
    }

    /**
     * Creates an {@link UnsupportedTypeException} to indicate that an argument type is not
     * supported.
     *
     * @since 19.0
     */
    public static UnsupportedTypeException create(Object[] suppliedValues, String hint) {
        return new UnsupportedTypeException(hint, suppliedValues);
    }

    /**
     * Creates an {@link UnsupportedTypeException} to indicate that an argument type is not
     * supported.
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
     * @param cause the guest language exception that caused the error.
     * @since 20.2
     */
    @SuppressWarnings("deprecation")
    public static UnsupportedTypeException create(Object[] suppliedValues, String hint, Throwable cause) {
        return new UnsupportedTypeException(hint, suppliedValues, cause);
    }

}
