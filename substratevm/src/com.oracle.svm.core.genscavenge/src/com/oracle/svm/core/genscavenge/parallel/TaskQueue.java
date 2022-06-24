package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import org.graalvm.word.Pointer;

public class TaskQueue {
    private final VMMutex mutex;
    private final VMCondition cond;
    private Object holderObject;
    private Pointer objRef;
    private int innerOffset;
    private boolean compressed;
    private boolean empty;
    private volatile int idleCount;

    public TaskQueue(String name) {
        mutex = new VMMutex(name + "-queue");
        cond = new VMCondition(mutex);
        empty = true;
    }

//    private UnsignedWord get() {
//        try {
//            mutex.lock();
//            while (empty) {
//                cond.block();
//            }
//            return word0;
//        } finally {
//            empty = true;
//            mutex.unlock();
//            cond.signal();
//        }
//    }

    public void put(Pointer objRef, int innerOffset, boolean compressed, Object holderObject) {
        try {
            mutex.lock();
            while (!empty) {
                cond.block();
            }
            this.innerOffset = innerOffset;
            this.objRef = objRef;
            this.compressed = compressed;
            this.holderObject = holderObject;
        } finally {
            empty = false;
            mutex.unlock();
            cond.broadcast();
        }
    }

    public void consume(Consumer consumer) {
        Object owner;
        Pointer orig, ref;
        int offset;
        boolean comp;
        try {
            mutex.lock();
            idleCount++;
            while (empty) {
                cond.block();
            }
            ref = this.objRef;
            offset = this.innerOffset;
            comp = this.compressed;
            owner = this.holderObject;
        } finally {
            empty = true;
            idleCount--;
            mutex.unlock();
            cond.broadcast();
        }
        consumer.accept(ref, offset, comp, owner);
    }

    public void waitUntilIdle(int expectedIdleCount) {
        try {
            mutex.lock();
            while (!empty) {
                cond.block();
            }
        } finally {
            mutex.unlock();
        }
        while (idleCount < expectedIdleCount);///signal?
    }

    public interface Consumer {
        void accept(Pointer objRef, int innerOffset, boolean compressed, Object holderObject);
    }
}