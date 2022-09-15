package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.log.Log;
import org.graalvm.word.Pointer;

/**
 * Synchronized buffer where chunks to be scanned are stored.
 */
public class ChunkBuffer {
    private static final int SIZE = 1024 * 1024; ///handle overflow + reduce for chunks
    private static final Pointer[] buffer = new Pointer[SIZE];
    private int top;

    private Log debugLog() {
        return ParallelGCImpl.debugLog();
    }

    void push(Pointer ptr) {
        ParallelGCImpl.mutex.lock();
        assert top < SIZE;
        buffer[top++] = ptr;
        ParallelGCImpl.mutex.unlock();
    }

    Object pop() {
        ParallelGCImpl.mutex.lock();
        try {
            if (top > 0) {
                return buffer[--top].toObjectNonNull();
            } else {
                return null;
            }
        } finally {
            ParallelGCImpl.mutex.unlock();
        }
    }
}