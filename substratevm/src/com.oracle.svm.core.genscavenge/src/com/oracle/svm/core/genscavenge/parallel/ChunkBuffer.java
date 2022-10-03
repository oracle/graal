package com.oracle.svm.core.genscavenge.parallel;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

/**
 * Synchronized buffer that stores "grey" heap chunks to be scanned.
 */
public class ChunkBuffer {
    private Pointer buffer;
    private int size, top, step;

    ChunkBuffer(int maxChunks, int refSize) {
        this.step = refSize;
        this.size = maxChunks * refSize;
        this.buffer = UnmanagedMemory.malloc(this.size);
    }

    /**
     * This method must be called with ParallelGCImpl.mutex locked
     */
    void push(Pointer ptr) {
        assert top < size;
        buffer.writeWord(top, ptr);
        top += step;
    }

    Pointer pop() {
        ParallelGCImpl.mutex.lock();
        try {
            if (top > 0) {
                top -= step;
                return buffer.readWord(top);
            } else {
                return WordFactory.nullPointer();
            }
        } finally {
            ParallelGCImpl.mutex.unlock();
        }
    }
}