package com.oracle.truffle.espresso.polyglot;

/**
 * An exception thrown if a {@link Object} does not support a interop message. If this exception is
 * thrown then the receiver does not support the message at all and it is not supported for any
 * arguments given to the message.
 *
 * @since 0.11
 */
public final class UnsupportedMessageException extends InteropException {

    private static final long serialVersionUID = 2325609708563016963L;

    private UnsupportedMessageException(Throwable cause) {
        super(null, cause);
    }

    private UnsupportedMessageException() {
        super(null);
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String getMessage() {
        return "Message not supported.";
    }

    /**
     * Creates an {@link UnsupportedMessageException} to indicate that an interop message is not
     * supported.
     * <p>
     * This method is designed to be used in compiled code paths.
     *
     * @since 19.0
     */
    public static UnsupportedMessageException create() {
        return new UnsupportedMessageException();
    }

    /**
     * Creates an {@link UnsupportedMessageException} to indicate that an interop message is not
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
     */
    public static UnsupportedMessageException create(Throwable cause) {
        return new UnsupportedMessageException(cause);
    }

}
