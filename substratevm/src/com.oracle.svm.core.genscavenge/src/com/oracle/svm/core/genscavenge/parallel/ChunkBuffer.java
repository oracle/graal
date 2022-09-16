package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.log.Log;
import org.graalvm.word.WordFactory;

/**
 * Synchronized buffer where chunks to be scanned are stored.
 */
public class ChunkBuffer {
    private static final int SIZE = 10 * 1024; ///handle overflow
    private static final HeapChunk.Header<?>[] buffer = new HeapChunk.Header<?>[SIZE];
    private int top;

    private Log debugLog() {
        return ParallelGCImpl.debugLog();
    }

    void push(HeapChunk.Header<?> ptr) {
        ParallelGCImpl.mutex.lock();
        assert top < SIZE;
        buffer[top++] = ptr;
        ParallelGCImpl.mutex.unlock();
    }

    HeapChunk.Header<?> pop() {
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