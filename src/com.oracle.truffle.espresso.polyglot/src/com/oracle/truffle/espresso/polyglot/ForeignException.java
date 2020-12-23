package com.oracle.truffle.espresso.polyglot;

public final class ForeignException extends RuntimeException {

    private ForeignException() {
        throw new RuntimeException("No instance of ForeignException can be created directly");
    }

    @Override
    public String getMessage() {
        assert Interop.isException(this);
        if (Interop.hasExceptionMessage(this)) {
            try {
                Object message = Interop.getExceptionMessage(this);
                return Interop.asString(message);
            } catch (UnsupportedMessageException e) {
                throw new AssertionError("Unexpected exception", e);
            }
        }
        return null;
    }

    @Override
    public Throwable getCause() {
        assert Interop.isException(this);
        if (Interop.hasExceptionCause(this)) {
            Object cause = null;
            try {
                cause = Interop.getExceptionCause(this);
            } catch (UnsupportedMessageException e) {
                throw new AssertionError("Unexpected exception", e);
            }
            assert Interop.isException(cause);
            if (Polyglot.isForeignObject(cause)) {
                return Polyglot.cast(ForeignException.class, cause);
            } else {
                return (Throwable) cause;
            }
        }
        return null;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return super.getStackTrace(); // empty
    }

    /**
     * Unsupported, {@link ForeignException} instances are not writable therefore setting the stack
     * trace has no effect for them.
     */
    @Override
    public void setStackTrace(StackTraceElement[] stackTrace) {
        // validate arguments to fullfil contract
        for (int i = 0; i < stackTrace.length; i++) {
            if (stackTrace[i] == null) {
                throw new NullPointerException("stackTrace[" + i + "]");
            }
        }
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    @SuppressWarnings("sync-override")
    @Override
    public Throwable initCause(Throwable cause) {
        throw new UnsupportedOperationException("Not supported. Pass in the cause using the constructors instead.");
    }
}
