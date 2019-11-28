package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.JDWPCallFrame;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;

public final class UnknownSuspendedInfo extends SuspendedInfo {

    private final JDWPContext context;

    UnknownSuspendedInfo(Object thread, JDWPContext jdwpContext) {
        super(null, null, thread);
        this.context = jdwpContext;
    }

    @Override
    public JDWPCallFrame[] getStackFrames() {
        return context.getStackTrace(getThread());
    }
}
