package com.oracle.truffle.espresso.runtime;

public class EspressoException extends RuntimeException {
    private static final long serialVersionUID = -7667957575377419520L;
    private final StaticObject exception;

    public EspressoException(StaticObject exception) {
        assert exception != null;
        assert exception != StaticObject.NULL;
        // TODO(peterssen): Check that exception is a real exception object (e.g. exception
        // instanceof Exception)
        this.exception = exception;
    }

    public StaticObject getException() {
        return exception;
    }
}
