package com.oracle.truffle.espresso.runtime;

public class EspressoKillError extends Error {

    private static final long serialVersionUID = 4177254032591630887L;

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
