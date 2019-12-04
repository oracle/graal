package com.oracle.truffle.espresso.jdwp.impl;

public class ThreadLock {

    private volatile boolean locked;

    public synchronized void lock() {
        locked = true;
    }

    public synchronized void unlock() {
        locked = false;
    }

    public boolean isLocked() {
        return locked;
    }
}
