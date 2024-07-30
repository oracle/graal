/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.log.Log;

/**
 * A single wall-clock stopwatch that can be repeatedly {@linkplain #open started} and
 * {@linkplain #close() stopped}.
 */
final class Timer implements AutoCloseable {
    private final String name;
    private boolean wasOpened;
    private long openNanos;
    private boolean wasClosed;
    private long closeNanos;
    private long collectedNanos;

    Timer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Timer open() {
        return openAt(System.nanoTime());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Timer openAt(long nanoTime) {
        openNanos = nanoTime;
        wasOpened = true;
        closeNanos = 0L;
        wasClosed = false;
        return this;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void close() {
        closeAt(System.nanoTime());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void closeAt(long nanoTime) {
        closeNanos = nanoTime;
        wasClosed = true;
        collectedNanos += closeNanos - getOpenedTime();
    }

    public void reset() {
        openNanos = 0L;
        wasOpened = false;
        closeNanos = 0L;
        wasClosed = false;
        collectedNanos = 0L;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getOpenedTime() {
        if (!wasOpened) {
            /* If a timer was not opened, pretend it was opened at the start of the VM. */
            assert openNanos == 0;
            return Isolates.getCurrentStartNanoTime();
        }
        return openNanos;
    }

    public long getClosedTime() {
        assert wasClosed : "Should have closed timer";
        return closeNanos;
    }

    /** Get all the nanoseconds collected between open/close pairs since the last reset. */
    long getMeasuredNanos() {
        return collectedNanos;
    }

    /** Get the nanoseconds collected by the most recent open/close pair. */
    long getLastIntervalNanos() {
        return getClosedTime() - getOpenedTime();
    }
}

/** Collection timers primarily for {@link GCImpl}. */
final class Timers {
    final Timer blackenImageHeapRoots = new Timer("blackenImageHeapRoots");
    final Timer blackenDirtyCardRoots = new Timer("blackenDirtyCardRoots");
    final Timer blackenStackRoots = new Timer("blackenStackRoots");
    final Timer cheneyScanFromRoots = new Timer("cheneyScanFromRoots");
    final Timer cheneyScanFromDirtyRoots = new Timer("cheneyScanFromDirtyRoots");
    final Timer collection = new Timer("collection");
    final Timer cleanCodeCache = new Timer("cleanCodeCache");
    final Timer referenceObjects = new Timer("referenceObjects");
    final Timer promotePinnedObjects = new Timer("promotePinnedObjects");
    final Timer rootScan = new Timer("rootScan");
    final Timer scanGreyObjects = new Timer("scanGreyObjects");
    final Timer releaseSpaces = new Timer("releaseSpaces");
    final Timer walkThreadLocals = new Timer("walkThreadLocals");
    final Timer walkRuntimeCodeCache = new Timer("walkRuntimeCodeCache");
    final Timer cleanRuntimeCodeCache = new Timer("cleanRuntimeCodeCache");
    final Timer mutator = new Timer("mutator");

    Timers() {
    }

    void resetAllExceptMutator() {
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
        cleanCodeCache.reset();
        referenceObjects.reset();
        releaseSpaces.reset();
        /* The mutator timer is *not* reset here. */
    }

    void logAfterCollection(Log log) {
        if (log.isEnabled()) {
            log.newline();
            log.string("  [GC nanoseconds:");
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
            logOneTimer(log, "      ", cleanCodeCache);
            logOneTimer(log, "      ", referenceObjects);
            logOneTimer(log, "      ", releaseSpaces);
            logGCLoad(log, "    ", "GCLoad", collection, mutator);
            log.string("]");
        }
    }

    static void logOneTimer(Log log, String prefix, Timer timer) {
        if (timer.getMeasuredNanos() > 0) {
            log.newline().string(prefix).string(timer.getName()).string(": ").signed(timer.getMeasuredNanos());
        }
    }

    /**
     * Log the "GC load" for the past collection as the collection time divided by the sum of the
     * previous mutator interval plus the collection time. This method uses wall-time, and so does
     * not take in to account that the collector is single-threaded, while the mutator might be
     * multi-threaded.
     */
    private static void logGCLoad(Log log, String prefix, String label, Timer cTimer, Timer mTimer) {
        long collectionNanos = cTimer.getLastIntervalNanos();
        long mutatorNanos = mTimer.getLastIntervalNanos();
        long intervalNanos = mutatorNanos + collectionNanos;
        long intervalGCPercent = (((100 * collectionNanos) + (intervalNanos / 2)) / intervalNanos);
        log.newline().string(prefix).string(label).string(": ").signed(intervalGCPercent).string("%");
    }
}
