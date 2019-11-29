package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.FieldRef;
import com.oracle.truffle.espresso.jdwp.api.JDWPListener;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public final class EmptyListener implements JDWPListener {

    @Override
    public void vmStarted(Object mainThread) {

    }

    @Override
    public void classPrepared(KlassRef klass, Object currentThread) {

    }

    @Override
    public void threadStarted(Object thread) {

    }

    @Override
    public void threadDied(Object thread) {

    }

    @Override
    public boolean hasFieldModificationBreakpoint(FieldRef field, Object receiver, Object value) {
        return false;
    }

    @Override
    public boolean hasFieldAccessBreakpoint(FieldRef field, Object receiver) {
        return false;
    }
}
