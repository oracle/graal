package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.log.Log;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Synchronized buffer that stores "grey" heap chunks to be scanned.
 */
public class ChunkBuffer {
    private static final int SIZE = 10 * 1024; ///handle overflow
    private static final Pointer[] buffer = new Pointer[SIZE];
    private int top;

    private Log debugLog() {
        return ParallelGCImpl.debugLog();
    }

    /**
     * This method must be called with ParallelGCImpl.mutex locked
     */
    void push(Pointer ptr) {
        assert top < SIZE;
        buffer[top++] = ptr;
    }

    Pointer pop() {
        ParallelGCImpl.mutex.lock();
        try {
            if (top > 0) {
                return buffer[--top];
            } else {
                return WordFactory.nullPointer();
            }
        } finally {
            ParallelGCImpl.mutex.unlock();
        }
    }

    int size() {
        ParallelGCImpl.mutex.lock();
        try {
            return top;
        } finally {
            ParallelGCImpl.mutex.unlock();
        }
    }
}