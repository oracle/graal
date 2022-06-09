package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import org.graalvm.word.Pointer;

public class TaskQueue {
    private final VMMutex mutex;
    private final VMCondition cond;
    private Object holderObject;
    private Pointer original, copy, objRef;
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

    public void put(Pointer original, Pointer copy, Pointer objRef, boolean compressed, Object holderObject) {
        try {
            mutex.lock();
            while (!empty) {
                cond.block();
            }
            this.original = original;
            this.copy = copy;
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
        Pointer orig, cp, ref;
        boolean comp;
        try {
            mutex.lock();
            idleCount++;
            while (empty) {
                cond.block();
            }
            orig = this.original;
            cp = this.copy;
            ref = this.objRef;
            comp = this.compressed;
            owner = this.holderObject;
        } finally {
            empty = true;
            idleCount--;
            mutex.unlock();
            cond.broadcast();
        }
        consumer.accept(orig, cp, ref, comp, owner);
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
        void accept(Pointer originalPtr, Pointer copy, Pointer objRef, boolean compressed, Object holderObject);
    }
}