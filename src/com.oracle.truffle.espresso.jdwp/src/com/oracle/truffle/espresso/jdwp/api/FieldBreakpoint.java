package com.oracle.truffle.espresso.jdwp.api;

public interface FieldBreakpoint {
    int getRequestId();

    boolean isModificationBreakpoint();

    boolean isAccessBreakpoint();
}
