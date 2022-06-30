package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import java.util.function.BiConsumer;

public class ObjectQueue {///rm
    private final VMMutex mutex;
    private final VMCondition cond;
    private final Object[] item;
    private boolean empty;
    private volatile int idleCount;

    public ObjectQueue(String name) {
        mutex = new VMMutex(name + "-queue");
        cond = new VMCondition(mutex);
        item = new Object[2];
        empty = true;
    }

    private Object[] get() {
//        Log.log().string("  >>> OQ.get() called").newline();
        try {
            mutex.lock();
            idleCount++;
            while (empty) {
//                Log.log().string("  >>> OQ.get() WAIT").newline();
                cond.block();
            }
//            Log.log().string("  >>> OQ.get() returns").newline();
            return item;
        } finally {
            empty = true;
            idleCount--;
            mutex.unlock();
            cond.broadcast();
        }
    }

    public void put(Object value0, Object value1) {
//        Log.log().string("  >>> OQ.put() called").newline();
        try {
            mutex.lock();
            while (!empty) {
//                Log.log().string("  >>> OQ.put() WAIT").newline();
                cond.block();
            }
            item[0] = value0;
            item[1] = value1;
//            Log.log().string("  >>> OQ.put() returns").newline();
        } finally {
            empty = false;
            mutex.unlock();
            cond.broadcast();
        }
    }

    public void consume(BiConsumer<Object, Object> consumer) {
        Object val0, val1;
//        Log.log().string("  >>> OQ.consume() called").newline();
        try {
            mutex.lock();
            idleCount++;
            while (empty) {
//                Log.log().string("  >>> OQ.consume() WAIT").newline();
                cond.block();
            }
//            Log.log().string("  >>> OQ.consume() unblocks").newline();
            val0 = item[0];
            val1 = item[1];
        } finally {
            empty = true;
            idleCount--;
            mutex.unlock();
            cond.broadcast();
        }
        consumer.accept(val0, val1);
    }

    public void waitUntilIdle(int expectedIdleCount) {
        try {
            mutex.lock();
//            Log.log().string(">>> OQ.wait() empty=").bool(empty)
//                    .string(", idle=").signed(idleCount).newline();
            while (!empty) {
                cond.block();
            }
        } finally {
            mutex.unlock();
        }
        while (idleCount < expectedIdleCount);
    }
}