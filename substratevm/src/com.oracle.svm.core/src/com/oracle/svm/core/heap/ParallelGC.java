package com.oracle.svm.core.heap;

public abstract class ParallelGC {
    protected volatile boolean stopped;

    public abstract void startWorkerThreads();
}
