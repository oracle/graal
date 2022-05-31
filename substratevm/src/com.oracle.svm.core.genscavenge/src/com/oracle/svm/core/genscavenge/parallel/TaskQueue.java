package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

///rm
public class TaskQueue {
    private static final UnsignedWord ZERO = WordFactory.zero();

    private final VMMutex mutex;
    private final VMCondition cond;
    private UnsignedWord item;

    public TaskQueue(String name) {
        mutex = new VMMutex(name + "-queue");
        cond = new VMCondition(mutex);
    }

    public UnsignedWord get() {
        try {
            mutex.lock();
            while (item.equal(ZERO)) {
                cond.block();
            }
            return item;
        } finally {
            item = ZERO;
            mutex.unlock();
            cond.signal();
        }
    }

    public void put(UnsignedWord obj) {
        try {
            mutex.lock();
            while (item.notEqual(ZERO)) {
                cond.block();
            }
            item = obj;
        } finally {
            mutex.unlock();
            cond.signal();
        }
    }

    public void waitUntilEmpty() {
        try {
            mutex.lock();
            while (item.notEqual(ZERO)) {
                cond.block();
            }
        } finally {
            mutex.unlock();
        }
    }
}