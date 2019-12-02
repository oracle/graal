package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

public final class UnknownSuspendedInfo extends SuspendedInfo {

    private final JDWPContext context;

    UnknownSuspendedInfo(Object thread, JDWPContext jdwpContext) {
        super(null, null, thread);
        this.context = jdwpContext;
    }

    @Override
    public CallFrame[] getStackFrames() {
        return context.getStackTrace(getThread());
    }
}
