package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class TaskQueue {
    private static final int SIZE = 1024; ///handle overflow

    private static class TaskData {
        private Object object;///no need to wrap
    }

    final Stats stats;

    private final VMMutex mutex;
    private final VMCondition cond;
    private final TaskData[] data;
    private final AtomicInteger idleCount;
    private int getIndex;
    private int putIndex;

    public TaskQueue(String name) {
        mutex = new VMMutex(name + "-queue");
        cond = new VMCondition(mutex);
        data = IntStream.range(0, SIZE).mapToObj(n -> new TaskData()).toArray(TaskData[]::new);
        idleCount = new AtomicInteger(0);
        stats = new Stats();
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

    public void put(Object object) {
        try {
            mutex.lock();
            while (!canPut()) {
                Log.log().string("PP cannot put task\n");
                cond.block();
            }
            TaskData item = data[putIndex];
            item.object = object;
        } finally {
            putIndex = next(putIndex);
            stats.noteTask(putIndex, getIndex);
            mutex.unlock();
            cond.broadcast();
        }
    }

    public void consume(Consumer consumer) {
        Object obj;
        idleCount.incrementAndGet();
        mutex.lock();
        try {
            while (!canGet()) {
                Log.log().string("PP cannot get task\n");
                cond.block();
            }
            TaskData item = data[getIndex];
            obj = item.object;
        } finally {
            getIndex = next(getIndex);
            mutex.unlock();
            idleCount.decrementAndGet();
            cond.broadcast();
        }
        consumer.accept(obj);
    }

    // Non MT safe. Only call when no workers are running
    public void drain(Consumer consumer) {
        idleCount.decrementAndGet();
        while (canGet()) {
            TaskData item = data[getIndex];
            Object obj = item.object;
            consumer.accept(obj);
            getIndex = next(getIndex);
        }
        idleCount.incrementAndGet();
    }

    public void waitUntilIdle(int expectedIdleCount) {
        Log log = Log.log().string("PP waitForIdle ").unsigned(idleCount.get()).newline();
        try {
            mutex.lock();
            while (canGet()) {
                cond.block();
            }
        } finally {
            mutex.unlock();
        }
        while (idleCount.get() < expectedIdleCount);///signal?
        log.string("PP waitForIdle over").newline();
    }

    public interface Consumer {
        void accept(Object object); ///j.u.f.Consumer
    }

    // Non MT safe, needs locking
    public static class Stats {
        private int count, maxSize;

        void noteTask(int putIndex, int getIndex) {
            int size = putIndex - getIndex;
            if (size < 0) {
                size += SIZE;
            }
            if (size > maxSize) {
                maxSize = size;
            }
            count++;
        }

        public int getCount() {
            return count;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void reset() {
            count = maxSize = 0;
        }
    }
}