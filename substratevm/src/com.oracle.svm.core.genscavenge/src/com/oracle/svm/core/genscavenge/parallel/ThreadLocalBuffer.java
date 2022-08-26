package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.log.Log;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

/**
 * Thread local (non MT safe) buffer where pointers to grey objects are stored.
 */
public class ThreadLocalBuffer {
    private static final int INITIAL_SIZE = 128 * 1024 * 8;  // 128k words
    private static final OutOfMemoryError OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate a ThreadLocalBuffer");

    private Pointer buffer = WordFactory.nullPointer();
    private UnsignedWord size = WordFactory.unsigned(INITIAL_SIZE);
    private UnsignedWord top = WordFactory.zero();

    private Log log() {
        return Log.log();
    }

    void runtimeInit() {
        buffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(size);
        ensureBuffer();
    }

    private void ensureCapacity() {
        if (top.aboveOrEqual(size)) {
            size = size.multiply(2);
            buffer = ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(buffer, size);
            ensureBuffer();
            log().string("TLB grow to ").unsigned(size).newline();
        }
    }

    private void ensureBuffer() {
        if (buffer.isNull()) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(OUT_OF_MEMORY_ERROR);
        }
    }

    void push(Pointer ptr) {
        ensureCapacity();
        buffer.writeWord(top, ptr);
        top = top.add(8);
    }

    Object pop() {
        if (top.aboveThan(0)) {
            top = top.subtract(8);
            Pointer ptr = buffer.readWord(top);
            return ptr.toObjectNonNull();
        } else {
            return null;
        }
    }
}