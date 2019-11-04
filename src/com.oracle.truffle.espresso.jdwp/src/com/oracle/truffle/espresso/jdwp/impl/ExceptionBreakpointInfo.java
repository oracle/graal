package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public class ExceptionBreakpointInfo extends AbstractBreakpointInfo {

    private final KlassRef klass;
    private final boolean caught;
    private final boolean unCaught;

    public ExceptionBreakpointInfo(int requestid, KlassRef klass, boolean caught, boolean unCaught) {
        super(requestid);
        this.klass = klass;
        this.caught = caught;
        this.unCaught = unCaught;
    }

    public KlassRef getKlass() {
        return klass;
    }

    public boolean isCaught() {
        return caught;
    }

    public boolean isUnCaught() {
        return unCaught;
    }

    @Override
    public boolean isExceptionBreakpoint() {
        return true;
    }
}
