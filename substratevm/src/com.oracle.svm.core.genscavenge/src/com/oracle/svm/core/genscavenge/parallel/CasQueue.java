package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import org.graalvm.word.Pointer;

import java.util.concurrent.atomic.AtomicInteger;

/// Lame, test only. Doesn't handle overflows
public class CasQueue {
    private static final int SIZE = 1024 * 1024;

    final Stats stats = new Stats();

    private final Pointer[] data = new Pointer[SIZE];
    private final AtomicInteger getIndex = new AtomicInteger(0);
    private final AtomicInteger putIndex = new AtomicInteger(0);

    private Log log() {
        return ParallelGCImpl.log();
    }

    public boolean put(Pointer ptr) {
        int cur = putIndex.get();
        int witness;
        while ((witness = putIndex.compareAndExchange(cur, cur + 1)) != cur) {
            cur = witness;
        }
        data[cur] = ptr;
        return true;
    }

    public boolean consume(Consumer consumer) {
        int cur = getIndex.get();
        int witness;
        while (cur < putIndex.get() && (witness = getIndex.compareAndExchange(cur, cur + 1)) != cur) {
            cur = witness;
        }
        if (cur >= putIndex.get()) {
            return false;
        }
        Object obj = data[cur].toObject();
        consumer.accept(obj);
        return true;
    }

    public void drain(Consumer consumer) {
        while (consume(consumer));
        getIndex.set(0);
        putIndex.set(0);
    }

    interface Consumer {
        void accept(Object object);
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