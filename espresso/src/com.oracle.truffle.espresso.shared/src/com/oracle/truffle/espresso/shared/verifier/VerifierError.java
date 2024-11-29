package com.oracle.truffle.espresso.shared.verifier;

import java.io.Serial;

/**
 * Indicates that something wrong happened, that is not part of the java bytecode verifier
 * specification.
 * <p>
 * This is usually due to unexpected usage of the {@link VerificationTypeInfo} or
 * {@link StackMapFrameParser} API, but it could also indicate a bug in the verifier itself.
 */
public final class VerifierError extends Error {
    @Serial private static final long serialVersionUID = -2712346647465726225L;

    private static VerifierError shouldNotReachHere(String message) {
        throw new VerifierError("should not reach here: " + message);
    }

    static VerifierError fatal(String message) {
        throw shouldNotReachHere(message);
    }

    private VerifierError(String message) {
        super(message);
    }
}
