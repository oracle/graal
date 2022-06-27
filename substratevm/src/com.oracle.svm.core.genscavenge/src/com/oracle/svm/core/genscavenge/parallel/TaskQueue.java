package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.genscavenge.GreyToBlackObjRefVisitor;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import org.graalvm.word.Pointer;

import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TaskQueue {
    private static final int SIZE = 1024;

    private static class TaskData {
        private GreyToBlackObjRefVisitor visitor;
        private Pointer objRef;
        private int innerOffset;
        private boolean compressed;
        private Object holderObject;
    }

    private final VMMutex mutex;
    private final VMCondition cond;
    private final TaskData[] data;
    private int getIndex;
    private int putIndex;
    private volatile int idleCount;

    public TaskQueue(String name) {
        mutex = new VMMutex(name + "-queue");
        cond = new VMCondition(mutex);
        data = IntStream.range(0, SIZE).mapToObj(n -> new TaskData()).toArray(TaskData[]::new);
    }

    private int next(int index) {
        return (index + 1) % SIZE;
    }

    private boolean canGet() {
        return getIndex != putIndex;
    }

    private boolean canPut() {
        return next(putIndex) != getIndex;
    }

    public void put(GreyToBlackObjRefVisitor visitor, Pointer objRef, int innerOffset, boolean compressed, Object holderObject) {
        try {
            mutex.lock();
            while (!canPut()) {
                cond.block();
            }
            TaskData item = data[putIndex];
            item.visitor = visitor;
            item.objRef = objRef;
            item.innerOffset = innerOffset;
            item.compressed = compressed;
            item.holderObject = holderObject;
        } finally {
            putIndex = next(putIndex);
            mutex.unlock();
            cond.broadcast();
        }
    }

    public void consume(Consumer consumer) {
        GreyToBlackObjRefVisitor v;
        Pointer ref;
        int offset;
        boolean comp;
        Object owner;
        try {
            mutex.lock();
            idleCount++;
            while (!canGet()) {
                cond.block();
            }
            TaskData item = data[getIndex];
            v = item.visitor;
            ref = item.objRef;
            offset = item.innerOffset;
            comp = item.compressed;
            owner = item.holderObject;
        } finally {
            getIndex = next(getIndex);
            idleCount--;
            mutex.unlock();
            cond.broadcast();
        }
        consumer.accept(v, ref, offset, comp, owner);
    }

    public void waitUntilIdle(int expectedIdleCount) {
        try {
            mutex.lock();
            while (canGet()) {
                cond.block();
            }
        } finally {
            mutex.unlock();
        }
        while (idleCount < expectedIdleCount);///signal?
    }

    public interface Consumer {
        void accept(GreyToBlackObjRefVisitor visitor, Pointer objRef, int innerOffset, boolean compressed, Object holderObject);
    }
}