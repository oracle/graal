package com.oracle.svm.core.heap;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.Safepoint;

public abstract class ParallelGC {
    protected final VMMutex mutex = new VMMutex("pargc");
    protected final VMCondition cond = new VMCondition(mutex);

    protected volatile boolean workerThreadActive;
    protected volatile boolean stopped;

    public abstract void startWorkerThreads();

    public void waitForNotification(boolean onWorkerThread) {
        Log trace = Log.log();
        mutex.lock();
        try {
            while (workerThreadActive != onWorkerThread) {
                trace.string("  blocking on ").string(Thread.currentThread().getName()).newline();
                cond.block();
                trace.string("  unblocking ").string(Thread.currentThread().getName()).newline();
            }
        } finally {
            mutex.unlock();
        }
    }

    public void signal(boolean onWorkerThread) {
        mutex.lock();
        try {
            Log.log().string("signaling on ").string(Thread.currentThread().getName()).newline();
            workerThreadActive = !onWorkerThread;
            cond.broadcast();
        } finally {
            mutex.unlock();
        }
    }

    public static void thawWorkerThreads() {
        Safepoint.Master.singleton().releaseParallelGCSafepoints();
    }
}
