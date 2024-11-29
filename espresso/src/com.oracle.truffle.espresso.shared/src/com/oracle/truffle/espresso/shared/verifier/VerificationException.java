package com.oracle.truffle.espresso.shared.verifier;

import java.io.Serial;

/**
 * Indicates that bytecode verification rejected a method, with {@link VerificationException#kind()}
 * alluding to why verification failed.
 * <p>
 * The {@link #getMessage() message} of this exception provides a brief explanation of what caused
 * this failed.
 */
public class VerificationException extends Exception {
    @Serial private static final long serialVersionUID = 7039576945173150725L;

    /**
     * Indicates what the runtime should throw in response to a {@link VerificationException}.
     */
    public enum Kind {
        /** Corresponds to {@link VerifyError} */
        Verify,
        /** Corresponds to {@link ClassFormatError} */
        ClassFormat,
        /** Corresponds to {@link NoClassDefFoundError} */
        NoClassDefFound,
    }

    private final Kind kind;
    private final boolean allowFallback;

    VerificationException(String message, Kind kind) {
        this(message, kind, true);
    }

    VerificationException(String message, Kind kind, boolean allowFallback) {
        super(message);
        this.kind = kind;
        this.allowFallback = allowFallback;
    }

    public Kind kind() {
        return kind;
    }

    boolean allowFallback() {
        return allowFallback;
    }
}
