package com.oracle.truffle.espresso.polyglot;

/**
 * An exception thrown if an array does not contain a element with an index. Interop exceptions are
 * supposed to be caught and converted into a guest language error by the caller.
 *
 * @see #getInvalidIndex()
 * @since 19.0
 */
public final class InvalidArrayIndexException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final long invalidIndex;

    private InvalidArrayIndexException(long invalidIndex) {
        super(null); // GR-23961 - after language adoption we should initialize the cause with null.
        this.invalidIndex = invalidIndex;
    }

    private InvalidArrayIndexException(long invalidIndex, Throwable cause) {
        super(null, cause);
        this.invalidIndex = invalidIndex;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String getMessage() {
        return "Invalid array index " + invalidIndex + ".";
    }

    /**
     * Returns the invalid index that was used.
     *
     * @since 19.0
     */
    public long getInvalidIndex() {
        return invalidIndex;
    }

    /**
     * Creates an {@link InvalidArrayIndexException} to indicate that an array index is invalid.
     *
     * @param invalidIndex the index that could not be accessed
     * @since 19.0
     */
    public static InvalidArrayIndexException create(long invalidIndex) {
        return new InvalidArrayIndexException(invalidIndex);
    }

    /**
     * Creates an {@link InvalidArrayIndexException} to indicate that an array index is invalid.
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
     * @param invalidIndex the index that could not be accessed
     * @param cause the guest language exception that caused the error.
     * @since 20.2
     */
    @SuppressWarnings("deprecation")
    public static InvalidArrayIndexException create(long invalidIndex, Throwable cause) {
        return new InvalidArrayIndexException(invalidIndex, cause);
    }

}
