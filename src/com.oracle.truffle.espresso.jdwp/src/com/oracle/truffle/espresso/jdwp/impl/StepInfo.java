package com.oracle.truffle.espresso.jdwp.impl;

public class StepInfo {

    private final int size;
    private final int depth;
    private Object thread;

    public StepInfo(int size, int depth, Object thread) {
        this.size = size;
        this.depth = depth;
        this.thread = thread;
    }

    public int getSize() {
        return size;
    }

    public int getDepth() {
        return depth;
    }

    public Object getThread() {
        return thread;
    }
}
