package com.oracle.truffle.espresso.polyglot;

/**
 * Common super class for exceptions that can occur when sending interop
 * messages. This super class is used to catch any kind of these exceptions.
 */
public abstract class InteropException extends Exception {

    InteropException(String message, Throwable cause) {
        super(message, cause);
    }

    InteropException(String message) {
        super(message);
    }

    /**
     * Returns the cause of an interop exception.
     *
     * {@inheritDoc}
     *
     * @since 20.2
     */
    @Override
    // GR-23961 - after language adoption we should make this non-synchronized as initCause is not
    // longer used
    public final synchronized Throwable getCause() {
        return super.getCause();
    }

    /**
     * No stack trace for interop exceptions.
     *
     * @since 20.2
     */
    @SuppressWarnings("sync-override")
    @Override
    public final Throwable fillInStackTrace() {
        return this;
    }

    private static final long serialVersionUID = -5173354806966156285L;
}

