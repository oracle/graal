package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.log.Log;
import org.graalvm.word.Pointer;

/// thread local, non MT safe
public class ThreadLocalTaskStack {
    private static final int SIZE = 128 * 1024;

    private final Pointer[] data = new Pointer[SIZE];
    private int top = 0;

    private Log log() {
        return ParallelGCImpl.log();
    }

    boolean push(Pointer ptr) {
        if (top >= SIZE) {
            log().string("TT cannot put task\n");
            return false;
        }
        data[top++] = ptr;
        return true;
    }

    Object pop() {
        if (top > 0) {
            return data[--top].toObjectNonNull();
        } else {
            return null;
        }
    }

    int size() {
        return top;
    } ///rm?
}