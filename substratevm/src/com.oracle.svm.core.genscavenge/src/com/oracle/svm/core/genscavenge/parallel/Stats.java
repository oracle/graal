package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import org.graalvm.nativeimage.IsolateThread;

public abstract class Stats {
    private static final Stats realStats = new RealStats();
    private static final Stats noopStats = new NoopStats();

    static Stats stats() {
        return realStats;
    }

    static Stats noopStats() {
        return noopStats;
    }

    public void reset() {}
    public void print(Log log) {}
    void install() {}
    public void noteAllocTime(long t) {}
    public void noteForwardInstallTime(long t) {}
    public void noteObjectVisitTime(long t) {}
    public void noteQueueCopyTime(long t) {}
    public void noteQueueScanTime(long t) {}
    public void noteLostObject() {}
    void notePut(int putIndex, int getIndex, int capacity) {}
    void noteGet() {}
}

class NoopStats extends Stats {
}

class RealStats extends Stats {
    private static final FastThreadLocalObject<StatsImpl> statsTL =
            FastThreadLocalFactory.createObject(StatsImpl.class, "ParGC.Stats");

    private final StatsImpl defaultStats = new StatsImpl();
    private final StatsImpl totalStats = new StatsImpl();

    private StatsImpl impl() {
        StatsImpl stats = statsTL.get();
        if (stats == null) {
            stats = defaultStats;
            statsTL.set(stats);
        }
        return stats;
    }

    @Override
    void install() {
        statsTL.set(new StatsImpl());
    }

    @Override
    public void reset() {
        for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
            StatsImpl stats = statsTL.get(vmThread);
            if (stats != null) {
                stats.reset();
            }
        }
    }

    @Override
    public void print(Log log) {
        totalStats.reset();
        log.string("PGC stats:").newline();
        for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
            StatsImpl stats = statsTL.get(vmThread);
            if (stats != null) {
                Thread jt = PlatformThreads.fromVMThread(vmThread);
                log.string("  ").string(jt.getName()).string(":").newline();
                totalStats.objectVisitTime += stats.objectVisitTime;
                totalStats.allocTime += stats.allocTime;
                totalStats.forwardInstallTime += stats.forwardInstallTime;
                totalStats.queueScanTime += stats.queueScanTime;
                totalStats.queueCopyTime += stats.queueCopyTime;
                totalStats.lostObjectCount += stats.lostObjectCount;
                totalStats.putCount += stats.putCount;
                totalStats.getCount += stats.getCount;
                totalStats.maxQueueSize = Math.max(totalStats.maxQueueSize, stats.maxQueueSize);
                stats.print(log);
            }
        }
        log.string("  total:").newline();
        totalStats.print(log);
    }

    @Override
    public void noteAllocTime(long t) {
        impl().noteAllocTime(t);
    }

    @Override
    public void noteForwardInstallTime(long t) {
        impl().noteForwardInstallTime(t);
    }

    @Override
    public void noteObjectVisitTime(long t) {
        impl().noteObjectVisitTime(t);
    }

    @Override
    public void noteQueueCopyTime(long t) {
        impl().noteQueueCopyTime(t);
    }

    @Override
    public void noteQueueScanTime(long t) {
        impl().noteQueueScanTime(t);
    }

    @Override
    public void noteLostObject() {
        impl().noteLostObject();
    }

    @Override
    void notePut(int putIndex, int getIndex, int capacity) {
        impl().notePut(putIndex, getIndex, capacity);
    }

    @Override
    public void noteGet() {
        impl().noteGet();
    }
}

class StatsImpl extends Stats {
    int allocTime, forwardInstallTime, objectVisitTime, queueCopyTime, queueScanTime;
    int putCount, getCount, lostObjectCount, maxQueueSize;

    @Override
    public void reset() {
        allocTime = forwardInstallTime = objectVisitTime = queueCopyTime = queueScanTime = 0;
        putCount = getCount = lostObjectCount = maxQueueSize = 0;
    }

    @Override
    public void print(Log log) {
        log.string("    visit ").unsigned(objectVisitTime)
                .string("  alloc ").unsigned(allocTime)
                .string("  fwptr ").unsigned(forwardInstallTime)
                .string("  qscan ").unsigned(queueScanTime)
                .string("  qcopy ").unsigned(queueCopyTime)
                .string("  lost ").unsigned(lostObjectCount).newline();
        log.string("    put ").unsigned(putCount)
                .string("  get ").unsigned(getCount)
                .string("  max ").unsigned(maxQueueSize).newline();
    }

    @Override
    public void noteAllocTime(long t) {
        allocTime += t;
    }

    @Override
    public void noteForwardInstallTime(long t) {
        forwardInstallTime += t;
    }

    @Override
    public void noteObjectVisitTime(long t) {
        objectVisitTime += t;
    }

    @Override
    public void noteQueueCopyTime(long t) {
        queueCopyTime += t;
    }

    @Override
    public void noteQueueScanTime(long t) {
        queueScanTime += t;
    }

    @Override
    public void noteLostObject() {
        lostObjectCount++;
    }

    @Override
    void notePut(int putIndex, int getIndex, int capacity) {
        int size = putIndex - getIndex;
        if (size < 0) {
            size += capacity;
        }
        if (size > maxQueueSize) {
            maxQueueSize = size;
        }
        putCount++;
    }

    @Override
    void noteGet() {
        getCount++;
    }
}
