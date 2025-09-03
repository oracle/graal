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

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.Timer;

/** Collection timers primarily for {@link GCImpl}. */
final class Timers {
    final Timer blackenImageHeapRoots = new Timer("blackenImageHeapRoots");
    final Timer blackenDirtyCardRoots = new Timer("blackenDirtyCardRoots");
    final Timer blackenStackRoots = new Timer("blackenStackRoots");
    final Timer scanFromRoots = new Timer("scanFromRoots");
    final Timer scanFromDirtyRoots = new Timer("scanFromDirtyRoots");
    final Timer collection = new Timer("collection");
    final Timer cleanCodeCache = new Timer("cleanCodeCache");
    final Timer referenceObjects = new Timer("referenceObjects");
    final Timer promotePinnedObjects = new Timer("promotePinnedObjects");
    final Timer rootScan = new Timer("rootScan");
    final Timer scanGreyObjects = new Timer("scanGreyObjects");
    final Timer oldPlanning = new Timer("oldPlanning");
    final Timer oldFixup = new Timer("oldFixup");
    final Timer oldFixupAlignedChunks = new Timer("oldFixupAlignedChunks");
    final Timer oldFixupImageHeap = new Timer("oldFixupImageHeap");
    final Timer oldFixupMetaspace = new Timer("oldFixupMetspace");
    final Timer oldFixupThreadLocals = new Timer("oldFixupThreadLocals");
    final Timer oldFixupRuntimeCodeCache = new Timer("oldFixupRuntimeCodeCache");
    final Timer oldFixupStack = new Timer("oldFixupStack");
    final Timer oldFixupUnalignedChunks = new Timer("oldFixupUnalignedChunks");
    final Timer oldCompaction = new Timer("oldCompaction");
    final Timer oldCompactionRememberedSets = new Timer("oldCompactionRememberedSets");
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
        scanFromRoots.reset();
        scanFromDirtyRoots.reset();
        promotePinnedObjects.reset();
        blackenStackRoots.reset();
        walkThreadLocals.reset();
        walkRuntimeCodeCache.reset();
        cleanRuntimeCodeCache.reset();
        blackenImageHeapRoots.reset();
        blackenDirtyCardRoots.reset();
        scanGreyObjects.reset();
        if (SerialGCOptions.useCompactingOldGen()) {
            oldPlanning.reset();
            oldFixup.reset();
            oldFixupAlignedChunks.reset();
            oldFixupImageHeap.reset();
            oldFixupMetaspace.reset();
            oldFixupThreadLocals.reset();
            oldFixupRuntimeCodeCache.reset();
            oldFixupStack.reset();
            oldFixupUnalignedChunks.reset();
            oldCompaction.reset();
            oldCompactionRememberedSets.reset();
        }
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
            logOneTimer(log, "        ", scanFromRoots);
            logOneTimer(log, "        ", scanFromDirtyRoots);
            logOneTimer(log, "          ", promotePinnedObjects);
            logOneTimer(log, "          ", blackenStackRoots);
            logOneTimer(log, "          ", walkThreadLocals);
            logOneTimer(log, "          ", walkRuntimeCodeCache);
            logOneTimer(log, "          ", cleanRuntimeCodeCache);
            logOneTimer(log, "          ", blackenImageHeapRoots);
            logOneTimer(log, "          ", blackenDirtyCardRoots);
            logOneTimer(log, "          ", scanGreyObjects);
            if (SerialGCOptions.useCompactingOldGen()) {
                logOneTimer(log, "      ", oldPlanning);
                logOneTimer(log, "      ", oldFixup);
                logOneTimer(log, "          ", oldFixupAlignedChunks);
                logOneTimer(log, "          ", oldFixupImageHeap);
                logOneTimer(log, "          ", oldFixupMetaspace);
                logOneTimer(log, "          ", oldFixupThreadLocals);
                logOneTimer(log, "          ", oldFixupRuntimeCodeCache);
                logOneTimer(log, "          ", oldFixupStack);
                logOneTimer(log, "          ", oldFixupUnalignedChunks);
                logOneTimer(log, "      ", oldCompaction);
                logOneTimer(log, "          ", oldCompactionRememberedSets);
            }
            logOneTimer(log, "      ", cleanCodeCache);
            logOneTimer(log, "      ", referenceObjects);
            logOneTimer(log, "      ", releaseSpaces);
            logGCLoad(log, "    ", "GCLoad", collection, mutator);
            log.string("]");
        }
    }

    static void logOneTimer(Log log, String prefix, Timer timer) {
        if (timer.totalNanos() > 0) {
            log.newline().string(prefix).string(timer.name()).string(": ").signed(timer.totalNanos());
        }
    }

    /**
     * Log the "GC load" for the past collection as the collection time divided by the sum of the
     * previous mutator interval plus the collection time. This method uses wall-time, and so does
     * not take in to account that the collector is single-threaded, while the mutator might be
     * multi-threaded.
     */
    private static void logGCLoad(Log log, String prefix, String label, Timer cTimer, Timer mTimer) {
        long collectionNanos = cTimer.lastIntervalNanos();
        long mutatorNanos = mTimer.lastIntervalNanos();
        long intervalNanos = mutatorNanos + collectionNanos;
        long intervalGCPercent = (((100 * collectionNanos) + (intervalNanos / 2)) / intervalNanos);
        log.newline().string(prefix).string(label).string(": ").signed(intervalGCPercent).string("%");
    }
}
