package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.log.Log;

/**
 * A single wall-clock stopwatch that can be repeatedly {@linkplain #open started} and
 * {@linkplain #close() stopped}.
 */
class Timer implements AutoCloseable {
    private final String name;
    private long openNanos;
    private long closeNanos;
    private long collectedNanos;

    public Timer(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Timer open() {
        openNanos = System.nanoTime();
        closeNanos = 0L;
        return this;
    }

    @Override
    public void close() {
        /* If a timer was not opened, pretend it was opened at the start of the VM. */
        if (openNanos == 0L) {
            openNanos = HeapChunkProvider.getFirstAllocationTime();
        }
        closeNanos = System.nanoTime();
        collectedNanos += closeNanos - openNanos;
    }

    public void reset() {
        openNanos = 0L;
        closeNanos = 0L;
        collectedNanos = 0L;
    }

    public long getFinish() {
        assert closeNanos > 0L : "Should have closed timer";
        return closeNanos;
    }

    /** Get all the nanoseconds collected between open/close pairs since the last reset. */
    long getCollectedNanos() {
        return collectedNanos;
    }

    /** Get the nanoseconds collected by the most recent open/close pair. */
    long getLastIntervalNanos() {
        assert openNanos > 0L : "Should have opened timer";
        assert closeNanos > 0L : "Should have closed timer";
        return closeNanos - openNanos;
    }

    static long getTimeSinceFirstAllocation(final long nanos) {
        return nanos - HeapChunkProvider.getFirstAllocationTime();
    }
}

/** Collection timers primarily for {@link GCImpl}. */
public class Timers {
    final Timer blackenImageHeapRoots = new Timer("blackenImageHeapRoots");
    final Timer blackenDirtyCardRoots = new Timer("blackenDirtyCardRoots");
    final Timer blackenStackRoots = new Timer("blackenStackRoots");
    final Timer cheneyScanFromRoots = new Timer("cheneyScanFromRoots");
    final Timer cheneyScanFromDirtyRoots = new Timer("cheneyScanFromDirtyRoots");
    final Timer collection = new Timer("collection");
    final Timer referenceObjects = new Timer("referenceObjects");
    final Timer promotePinnedObjects = new Timer("promotePinnedObjects");
    final Timer rootScan = new Timer("rootScan");
    final Timer scanGreyObjects = new Timer("scanGreyObjects");
    final Timer releaseSpaces = new Timer("releaseSpaces");
    final Timer verifyAfter = new Timer("verifyAfter");
    final Timer verifyBefore = new Timer("verifyBefore");
    final Timer walkThreadLocals = new Timer("walkThreadLocals");
    final Timer walkRuntimeCodeCache = new Timer("walkRuntimeCodeCache");
    final Timer cleanRuntimeCodeCache = new Timer("cleanRuntimeCodeCache");
    final Timer mutator = new Timer("mutator");

    public Timers() {
    }

    void resetAllExceptMutator() {
        final Log trace = Log.noopLog();
        trace.string("[Timers.resetAllExceptMutator:");
        verifyBefore.reset();
        collection.reset();
        rootScan.reset();
        cheneyScanFromRoots.reset();
        cheneyScanFromDirtyRoots.reset();
        promotePinnedObjects.reset();
        blackenStackRoots.reset();
        walkThreadLocals.reset();
        walkRuntimeCodeCache.reset();
        cleanRuntimeCodeCache.reset();
        blackenImageHeapRoots.reset();
        blackenDirtyCardRoots.reset();
        scanGreyObjects.reset();
        referenceObjects.reset();
        releaseSpaces.reset();
        verifyAfter.reset();
        /* The mutator timer is *not* reset here. */
        trace.string("]").newline();
    }

    void logAfterCollection(final Log log) {
        if (log.isEnabled()) {
            log.newline();
            log.string("  [GC nanoseconds:");
            logOneTimer(log, "    ", verifyBefore);
            logOneTimer(log, "    ", collection);
            logOneTimer(log, "      ", rootScan);
            logOneTimer(log, "        ", cheneyScanFromRoots);
            logOneTimer(log, "        ", cheneyScanFromDirtyRoots);
            logOneTimer(log, "          ", promotePinnedObjects);
            logOneTimer(log, "          ", blackenStackRoots);
            logOneTimer(log, "          ", walkThreadLocals);
            logOneTimer(log, "          ", walkRuntimeCodeCache);
            logOneTimer(log, "          ", cleanRuntimeCodeCache);
            logOneTimer(log, "          ", blackenImageHeapRoots);
            logOneTimer(log, "          ", blackenDirtyCardRoots);
            logOneTimer(log, "          ", scanGreyObjects);
            logOneTimer(log, "      ", referenceObjects);
            logOneTimer(log, "      ", releaseSpaces);
            logOneTimer(log, "    ", verifyAfter);
            logGCLoad(log, "    ", "GCLoad", collection, mutator);
            log.string("]");
        }
    }

    static void logOneTimer(final Log log, final String prefix, final Timer timer) {
        if (timer.getCollectedNanos() > 0) {
            log.newline().string(prefix).string(timer.getName()).string(": ").signed(timer.getCollectedNanos());
        }
    }

    /**
     * Log the "GC load" for the past collection as the collection time divided by the sum of the
     * previous mutator interval plus the collection time. This method uses wall-time, and so does
     * not take in to account that the collector is single-threaded, while the mutator might be
     * multi-threaded.
     */
    private static void logGCLoad(Log log, String prefix, String label, Timer cTimer, Timer mTimer) {
        final long collectionNanos = cTimer.getLastIntervalNanos();
        final long mutatorNanos = mTimer.getLastIntervalNanos();
        final long intervalNanos = mutatorNanos + collectionNanos;
        final long intervalGCPercent = (((100 * collectionNanos) + (intervalNanos / 2)) / intervalNanos);
        log.newline().string(prefix).string(label).string(": ").signed(intervalGCPercent).string("%");
    }
}