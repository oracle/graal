package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import org.graalvm.word.Pointer;

public class TaskQueue {
    private static final int SIZE = 128 * 1024;

    private final VMMutex mutex;
    private final VMCondition cond;
    private final Pointer[] data;
    private int idleCount;
    private int getIndex;
    private int putIndex;

    public TaskQueue(String name) {
        mutex = new VMMutex(name + "-queue");
        cond = new VMCondition(mutex);
        data = new Pointer[SIZE];
    }

    private Log log() {
        return Log.noopLog();
    }

    private int next(int index) {
        return (index + 1) % SIZE;
    }

    private boolean canGet() {
        return getIndex != putIndex;
    }

    private boolean canPut() {
        return next(putIndex) != getIndex;
    }

    public boolean put(Pointer ptr) {
        try {
            mutex.lock();
            if (!canPut()) {
                log().string("TQ cannot put task\n");
                return false;
            }
            data[putIndex] = ptr;
            putIndex = next(putIndex);
        } finally {
            mutex.unlock();
        }
        cond.broadcast();
        return true;
    }

    public void consume(Consumer consumer) {
        Pointer ptr;
        mutex.lock();
        try {
            while (!canGet()) {
                log().string("TQ cannot get task\n");
                idleCount++;
                cond.block();
                idleCount--;
            }
            ptr = data[getIndex];
            getIndex = next(getIndex);
        } finally {
            mutex.unlock();
        }
        cond.broadcast();
        consumer.accept(ptr.toObject());
    }

    // Non MT safe. Only call when no workers are running
    public void drain(Consumer consumer) {
        while (canGet()) {
            Object obj = data[getIndex].toObject();
            consumer.accept(obj);
            getIndex = next(getIndex);
        }
    }

    public void waitUntilIdle(int expectedIdleCount) {
        log().string("TQ waitForIdle\n");
        while (true) {
            try {
                mutex.lock();
                while (canGet()) {
                    cond.block();
                }
                if (idleCount >= expectedIdleCount) {
                    log().string("TQ waitForIdle over\n");
                    return;
                }
            } finally {
                mutex.unlock();
            }
        }
    }

    interface Consumer {
        void accept(Object object);
    }
}