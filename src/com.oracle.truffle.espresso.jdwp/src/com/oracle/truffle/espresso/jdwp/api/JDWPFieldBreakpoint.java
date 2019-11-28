package com.oracle.truffle.espresso.jdwp.api;

public interface JDWPFieldBreakpoint {
    int getRequestId();
    boolean isModificationBreakpoint();
    boolean isAccessBreakpoint();
}
