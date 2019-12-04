package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public interface BreakpointInfo {

    RequestFilter getFilter();

    void setBreakpoint(Breakpoint bp);

    Breakpoint getBreakpoint();

    int getRequestId();

    KlassRef getKlass();

    boolean isCaught();

    boolean isUnCaught();

    Object getThread();

    long getClassId();

    long getMethodId();

    byte getTypeTag();

    long getBci();

    boolean isLineBreakpoint();

    boolean isExceptionBreakpoint();

    void addSuspendPolicy(byte suspendPolicy);

    byte getSuspendPolicy();
}
