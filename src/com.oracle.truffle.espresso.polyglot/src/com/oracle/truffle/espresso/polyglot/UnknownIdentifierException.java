package com.oracle.truffle.espresso.polyglot;

/**
 * An exception thrown if an object does not contain a member with such an identifier. Interop
 * exceptions are supposed to be caught and converted into a guest language error by the caller.
 *
 * @see #getUnknownIdentifier()
 * @since 0.11
 */
public final class UnknownIdentifierException extends InteropException {

    private static final long serialVersionUID = 1857745390734085182L;

    private final String unknownIdentifier;

    private UnknownIdentifierException(String unknownIdentifier) {
        super(null); // GR-23961 - after language adoption we should initialize the cause with null.
        this.unknownIdentifier = unknownIdentifier;
    }

    private UnknownIdentifierException(String unknownIdentifier, Throwable cause) {
        super(null, cause);
        this.unknownIdentifier = unknownIdentifier;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String getMessage() {
        return "Unknown identifier: " + unknownIdentifier;
    }

    /**
     * Returns the identifier that could not be accessed.
     *
     * @return the unaccessible identifier
     * @since 0.11
     */
    public String getUnknownIdentifier() {
        return unknownIdentifier;
    }

    /**
     * Creates an {@link UnknownIdentifierException} to indicate that an identifier is missing.
     *
     * @param unknownIdentifier the identifier that could not be accessed
     * @since 19.0
     */
    public static UnknownIdentifierException create(String unknownIdentifier) {
        return new UnknownIdentifierException(unknownIdentifier);
    }

    /**
     * Creates an {@link UnknownIdentifierException} to indicate that an identifier is missing.
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
     * @param unknownIdentifier the identifier that could not be accessed
     * @param cause the guest language exception that caused the error.
     * @since 20.2
     */
    @SuppressWarnings("deprecation")
    public static UnknownIdentifierException create(String unknownIdentifier, Throwable cause) {
        return new UnknownIdentifierException(unknownIdentifier, cause);
    }

}
