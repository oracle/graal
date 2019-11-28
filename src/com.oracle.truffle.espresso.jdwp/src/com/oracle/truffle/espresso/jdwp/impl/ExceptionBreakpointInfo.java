package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public final class ExceptionBreakpointInfo extends AbstractBreakpointInfo {

    private final KlassRef klass;
    private final boolean caught;
    private final boolean unCaught;


    public ExceptionBreakpointInfo(RequestFilter filter, KlassRef klass, boolean caught, boolean unCaught) {
        super(filter);
        this.klass = klass;
        this.caught = caught;
        this.unCaught = unCaught;
    }

    @Override
    public KlassRef getKlass() {
        return klass;
    }

    @Override
    public boolean isCaught() {
        return caught;
    }

    @Override
    public boolean isUnCaught() {
        return unCaught;
    }

    @Override
    public boolean isExceptionBreakpoint() {
        return true;
    }
}
