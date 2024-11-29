package com.oracle.truffle.espresso.verifier;

import java.io.Serial;

public class VerificationException extends Exception {
    @Serial private static final long serialVersionUID = 7039576945173150725L;

    public enum Kind {
        Verify,
        ClassFormat,
        NoClassDefFound,
    }

    final Kind kind;
    final boolean allowFallback;

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
}
