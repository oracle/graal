package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

public class TaskQueue {
    private final VMMutex mutex;
    private final VMCondition cond;
    private Object object, holderObject;
    private Pointer objRef;
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

    public void put(Object original, Pointer objRef, boolean compressed, Object holderObject) {
        try {
            mutex.lock();
            while (!empty) {
                cond.block();
            }
            this.object = original;
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
        Object obj, owner;
        Pointer ref;
        boolean comp;
        try {
            mutex.lock();
            idleCount++;
            while (empty) {
                cond.block();
            }
            obj = this.object;
            ref = this.objRef;
            comp = this.compressed;
            owner = this.holderObject;
        } finally {
            empty = true;
            idleCount--;
            mutex.unlock();
            cond.broadcast();
        }
        consumer.accept(obj, ref, comp, owner);
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
        void accept(Object original, Pointer objRef, boolean compressed, Object holderObject);
    }
}