package com.oracle.truffle.espresso.shared.meta;

import java.io.Serial;

/**
 * Indicates that an exception was thrown during class loading.
 */
public class ClassLoadingException extends Exception {
    @Serial private static final long serialVersionUID = -6396583822642157602L;

    private final boolean isClassNotFoundException;
    private final RuntimeException exception;

    public ClassLoadingException(RuntimeException e, boolean isClassNotFoundException) {
        this.isClassNotFoundException = isClassNotFoundException;
        this.exception = e;
    }

    public boolean isClassNotFoundException() {
        return isClassNotFoundException;
    }

    public RuntimeException getException() {
        return exception;

    }
}