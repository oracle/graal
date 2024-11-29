package com.oracle.truffle.espresso.verifier;

import java.io.Serial;

public final class VerifierError extends Error {
    @Serial private static final long serialVersionUID = -2712346647465726225L;

    static RuntimeException shouldNotReachHere(String message) {
        throw new VerifierError("should not reach here: " + message);
    }

    static RuntimeException shouldNotReachHere(String message, Throwable cause) {
        throw new VerifierError("should not reach here: " + message, cause);
    }

    private VerifierError(String message) {
        super(message);
    }

    private VerifierError(String message, Throwable cause) {
        super(message, cause);
    }
}
