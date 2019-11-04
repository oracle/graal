package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.espresso.jdwp.api.BreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public class AbstractBreakpointInfo implements BreakpointInfo {

    private final int requestId;
    private Breakpoint breakpoint;

    public AbstractBreakpointInfo(int requestId) {
        this.requestId = requestId;
    }

    @Override
    public void setBreakpoint(Breakpoint bp) {
        this.breakpoint = bp;
    }

    @Override
    public Breakpoint getBreakpoint() {
        return breakpoint;
    }

    @Override
    public int getRequestId() {
        return requestId;
    }

    @Override
    public KlassRef getKlass() {
        return null;
    }

    @Override
    public boolean isCaught() {
        return false;
    }

    @Override
    public boolean isUnCaught() {
        return false;
    }

    @Override
    public Object getThread() {
        return null;
    }

    @Override
    public long getClassId() {
        return 0;
    }

    @Override
    public long getMethodId() {
        return 0;
    }

    @Override
    public byte getTypeTag() {
        return 0;
    }

    @Override
    public long getBci() {
        return 0;
    }

    @Override
    public boolean isExceptionBreakpoint() {
        return false;
    }

    @Override
    public boolean isLineBreakpoint() {
        return false;
    }
}
