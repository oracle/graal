/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.pgo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;

/**
 * Drives the native counter tables of the PGO recorders through their production paths: growth,
 * long probe chains, the flush of a table at its cap, a thread without a table, the safepoint
 * snapshot, also while other threads take the shared locks, clearing and reuse, and the reuse of
 * an exited thread's stack-sample table.
 * <p>
 * The tests reach the recorders' private table code through {@link Alias} declarations. Only the
 * thread's table pointer is set and read through the recorders' {@code currentTableForTesting} and
 * {@code setTableForTesting}, because Native Image accesses a thread-local only through a
 * compile-time constant, which an alias of it is not. The tests run in images with and without
 * {@code --pgo-instrument}: without it no thread has a table, so they install their own.
 */
public class RecorderTableNativeTest {
    /**
     * First counter or site index of the keys a test adds: far above any index an image gives out,
     * so the test's keys never meet the keys of the image's own instrumented code.
     */
    private static final int TEST_KEYS = 1_000_000_000;
    /** Like {@link #TEST_KEYS}, for the tests that fill a table to its cap. */
    private static final int CAP_TEST_KEYS = 1_500_000_000;
    /** Keys the growth tests add, in multiples of a fresh table's buckets: enough to grow it several times. */
    private static final int GROWTH_KEYS_PER_INITIAL_BUCKET = 3;
    /** Largest share of keys, in percent, that may need more probes than the fast path follows. */
    private static final int MAX_LONG_CHAIN_PERCENT = 1;
    /** Like {@link #TEST_KEYS}, for the test that takes snapshots while other threads take the shared locks. */
    private static final int SNAPSHOT_TEST_KEYS = 1_900_000_000;
    /** Keys that test puts into each shared table first, so that reading a shared table takes a while. */
    private static final int SNAPSHOT_TEST_SHARED_KEYS = 100_000;
    /** Threads that take the shared locks while that test takes snapshots. */
    private static final int LOCK_THREADS = 4;
    /** How long that test takes snapshots. */
    private static final long SNAPSHOT_TEST_NANOS = 2_000_000_000L;
    /** Arrays that each of those threads keeps alive, and their length, so that its allocation requests collections. */
    private static final int GARBAGE_SLOTS = 1024;
    private static final int GARBAGE_LONGS = 64;
    /** Threads that the stack-sample table test starts one after another. */
    private static final int SEQUENTIAL_THREADS = 20;

    @Before
    public void requireNativeImageRuntime() {
        Assume.assumeTrue(ImageInfo.inImageCode());
    }

    /**
     * Buckets of a table that starts at {@code initialBuckets} after {@code keys} distinct keys,
     * by the recorders' growth rule: a table doubles whenever an insertion finds it half full.
     */
    private static long capacityAfterInserting(int initialBuckets, int keys) {
        long capacity = initialBuckets;
        while (2L * keys > capacity) {
            capacity *= 2;
        }
        return capacity;
    }

    @Test
    public void callCountTableHandlesCollisionFullDuplicateAndClear() {
        final int keys = GROWTH_KEYS_PER_INITIAL_BUCKET * CallCountTables.INITIAL_BUCKETS;
        Pointer table = CallCountTables.allocateTable(CallCountTables.INITIAL_BUCKETS);
        Assert.assertTrue("could not allocate call-count table test memory", table.isNonNull());
        Pointer previous = CallCountProfileRecorder.currentTableForTesting();
        CallCountProfileRecorder.setTableForTesting(table);
        try {
            int testBase = TEST_KEYS;
            boolean routedThroughRuntime = CallCountProfileRecorder.isEnabled();
            for (int counterIndex = 0; counterIndex < keys; counterIndex++) {
                incrementCallCount(routedThroughRuntime, testBase + counterIndex);
            }
            incrementCallCount(routedThroughRuntime, testBase); // an existing key after growth
            table = CallCountProfileRecorder.currentTableForTesting(); // growth replaced the table
            long capacity = table.readLong(CallCountTables.MASK_OFFSET) + 1;
            int occupied = 0;
            long privateCount = 0;
            for (int index = 0; index < capacity; index++) {
                int bucket = CallCountTables.HEADER_BYTES + index * CallCountTables.BUCKET_BYTES;
                if (table.readLong(bucket + CallCountTables.BUCKET_KEY) != 0) {
                    occupied++;
                    privateCount += table.readLong(bucket + CallCountTables.BUCKET_COUNT);
                }
            }
            Assert.assertEquals(keys, occupied);
            Assert.assertEquals(capacityAfterInserting(CallCountTables.INITIAL_BUCKETS, keys), capacity);
            /* Growth keeps chains short: only a few keys need more probes than the fast path follows. */
            int longChains = 0;
            for (int counterIndex = 0; counterIndex < keys; counterIndex++) {
                if (!CallCountTables.addBounded(table, testBase + counterIndex + 1L)) {
                    longChains++;
                }
            }
            Assert.assertTrue("long chains: " + longChains, longChains * 100 < keys * MAX_LONG_CHAIN_PERCENT);
            privateCount += keys - longChains; // the chain check incremented every short-chain key once more
            Assert.assertEquals(keys + 1 + keys - longChains, privateCount);

            int overflowCounter = testBase + keys;
            Pointer stolen = CallCountProfileRecorder.currentTableForTesting();
            CallCountProfileRecorder.setTableForTesting(Word.nullPointer()); // a thread without a table counts in the shared table
            incrementCallCount(routedThroughRuntime, overflowCounter);
            CallCountProfileRecorder.setTableForTesting(stolen);
            CallCountTables.takeSnapshot(); // the production VM operation merges and clears the live private table
            Assert.assertEquals(1, CallCountTables.snapshot.getOrDefault(overflowCounter, 0L).longValue());
            long snapshotPrivateCount = 0;
            for (int counterIndex = testBase; counterIndex < testBase + keys; counterIndex++) {
                snapshotPrivateCount += CallCountTables.snapshot.getOrDefault(counterIndex, 0L);
            }
            Assert.assertEquals(privateCount, snapshotPrivateCount);

            /*
             * In an instrumented image the test thread's own instrumented code keeps recording into
             * this table, so only the test's keys can be required to be gone.
             */
            table = CallCountProfileRecorder.currentTableForTesting();
            capacity = table.readLong(CallCountTables.MASK_OFFSET) + 1;
            for (int index = 0; index < capacity; index++) {
                long counterIndex = table.readLong(CallCountTables.HEADER_BYTES + index * CallCountTables.BUCKET_BYTES + CallCountTables.BUCKET_KEY) - 1;
                Assert.assertFalse("the snapshot left test key " + counterIndex + " in the table", counterIndex >= testBase && counterIndex < testBase + keys);
            }
            Assert.assertTrue(CallCountTables.addBounded(table, testBase + 1L));
            boolean reused = false;
            for (int index = 0; !reused && index < capacity; index++) {
                int bucket = CallCountTables.HEADER_BYTES + index * CallCountTables.BUCKET_BYTES;
                reused = table.readLong(bucket + CallCountTables.BUCKET_KEY) == testBase + 1L && table.readLong(bucket + CallCountTables.BUCKET_COUNT) == 1;
            }
            Assert.assertTrue("a cleared table must take a key again", reused);
        } finally {
            Pointer current = CallCountProfileRecorder.currentTableForTesting();
            CallCountProfileRecorder.setTableForTesting(previous);
            freeUnlessSame(current, previous);
        }
    }

    @Test
    public void callCountTableFlushesAtItsCap() {
        final int keys = CallCountTables.MAX_BUCKETS / 2 + 1;
        Pointer table = CallCountTables.allocateTable(CallCountTables.MAX_BUCKETS);
        Assert.assertTrue("could not allocate call-count table test memory", table.isNonNull());
        Pointer previous = CallCountProfileRecorder.currentTableForTesting();
        CallCountProfileRecorder.setTableForTesting(table);
        try {
            int testBase = CAP_TEST_KEYS;
            boolean routedThroughRuntime = CallCountProfileRecorder.isEnabled();
            for (int counterIndex = testBase; counterIndex < testBase + keys; counterIndex++) {
                incrementCallCount(routedThroughRuntime, counterIndex);
            }
            table = CallCountProfileRecorder.currentTableForTesting();
            long capacity = table.readLong(CallCountTables.MASK_OFFSET) + 1;
            int privateTestKeys = 0;
            for (int index = 0; index < capacity; index++) {
                long counterIndex = table.readLong(CallCountTables.HEADER_BYTES + index * CallCountTables.BUCKET_BYTES + CallCountTables.BUCKET_KEY) - 1;
                if (counterIndex >= testBase && counterIndex < testBase + keys) {
                    privateTestKeys++;
                }
            }
            CallCountTables.takeSnapshot();
            long snapshotTestCount = 0;
            for (int counterIndex = testBase; counterIndex < testBase + keys; counterIndex++) {
                snapshotTestCount += CallCountTables.snapshot.getOrDefault(counterIndex, 0L);
            }
            /* The last key finds the table at its cap half full, so it is flushed and holds only that key. */
            Assert.assertEquals(CallCountTables.MAX_BUCKETS, capacity);
            Assert.assertEquals(1, privateTestKeys);
            Assert.assertEquals(keys, snapshotTestCount);
        } finally {
            Pointer current = CallCountProfileRecorder.currentTableForTesting();
            CallCountProfileRecorder.setTableForTesting(previous);
            freeUnlessSame(current, previous);
        }
    }

    @Test
    public void typeProfileTableHandlesCollisionFullDuplicateAndClear() {
        final int keys = GROWTH_KEYS_PER_INITIAL_BUCKET * TypeProfileTables.INITIAL_BUCKETS;
        Pointer table = TypeProfileTables.allocateTable(TypeProfileTables.INITIAL_BUCKETS);
        Assert.assertTrue("could not allocate type profile table test memory", table.isNonNull());
        Pointer previous = TypeProfileRecorder.currentTableForTesting();
        TypeProfileRecorder.setTableForTesting(table);
        try {
            int testBase = TEST_KEYS;
            boolean routedThroughRuntime = TypeProfileRecorder.isEnabled();
            for (int siteIndex = 0; siteIndex < keys; siteIndex++) {
                recordType(routedThroughRuntime, testBase + siteIndex);
            }
            long firstKey = typeTableKey(testBase);
            recordType(routedThroughRuntime, testBase); // an existing key after growth
            table = TypeProfileRecorder.currentTableForTesting(); // growth replaced the table
            long capacity = table.readLong(TypeProfileTables.MASK_OFFSET) + 1;
            int occupied = 0;
            long privateCount = 0;
            for (int index = 0; index < capacity; index++) {
                int bucket = TypeProfileTables.HEADER_BYTES + index * TypeProfileTables.BUCKET_BYTES;
                if (table.readLong(bucket + TypeProfileTables.BUCKET_KEY) != 0) {
                    occupied++;
                    privateCount += table.readLong(bucket + TypeProfileTables.BUCKET_COUNT);
                }
            }
            Assert.assertEquals(keys, occupied);
            Assert.assertEquals(capacityAfterInserting(TypeProfileTables.INITIAL_BUCKETS, keys), capacity);
            int longChains = 0;
            for (int siteIndex = 0; siteIndex < keys; siteIndex++) {
                if (!TypeProfileTables.addBounded(table, typeTableKey(testBase + siteIndex))) {
                    longChains++;
                }
            }
            Assert.assertTrue("long chains: " + longChains, longChains * 100 < keys * MAX_LONG_CHAIN_PERCENT);
            privateCount += keys - longChains; // the chain check incremented every short-chain key once more
            Assert.assertEquals(keys + 1 + keys - longChains, privateCount);

            int overflowSite = testBase + keys;
            Pointer stolen = TypeProfileRecorder.currentTableForTesting();
            TypeProfileRecorder.setTableForTesting(Word.nullPointer()); // a thread without a table counts in the shared table
            recordType(routedThroughRuntime, overflowSite);
            TypeProfileRecorder.setTableForTesting(stolen);
            TypeProfileTables.mergeIntoShared(stolen, true);
            /*
             * In an instrumented image the test thread's own instrumented code keeps recording into
             * this table, so only the test's keys can be required to be gone.
             */
            for (int index = 0; index < capacity; index++) {
                long key = stolen.readLong(TypeProfileTables.HEADER_BYTES + index * TypeProfileTables.BUCKET_BYTES + TypeProfileTables.BUCKET_KEY);
                long siteIndex = (key >>> 32) - 1;
                Assert.assertFalse("the merge left test key " + siteIndex + " in the table", key != 0 && siteIndex >= testBase && siteIndex < testBase + keys);
            }
            TypeProfileTables.takeSnapshot();
            Assert.assertEquals(1, TypeProfileTables.snapshot.getOrDefault(typeTableKey(overflowSite), 0L).longValue());
            long snapshotPrivateCount = 0;
            for (Map.Entry<Long, Long> entry : TypeProfileTables.snapshot.entrySet()) {
                long siteIndex = (entry.getKey() >>> 32) - 1;
                if (siteIndex >= testBase && siteIndex < testBase + keys) {
                    snapshotPrivateCount += entry.getValue();
                }
            }
            Assert.assertEquals(privateCount, snapshotPrivateCount);
            Assert.assertTrue(TypeProfileTables.addBounded(stolen, firstKey));
            boolean reused = false;
            for (int index = 0; !reused && index < capacity; index++) {
                int bucket = TypeProfileTables.HEADER_BYTES + index * TypeProfileTables.BUCKET_BYTES;
                reused = stolen.readLong(bucket + TypeProfileTables.BUCKET_KEY) == firstKey && stolen.readLong(bucket + TypeProfileTables.BUCKET_COUNT) == 1;
            }
            Assert.assertTrue("a cleared table must take a key again", reused);
            TypeProfileTables.mergeIntoShared(stolen, true);
        } finally {
            Pointer current = TypeProfileRecorder.currentTableForTesting();
            TypeProfileRecorder.setTableForTesting(previous);
            freeUnlessSame(current, previous);
        }
    }

    @Test
    public void typeProfileTableFlushesAtItsCap() {
        final int keys = TypeProfileTables.MAX_BUCKETS / 2 + 1;
        Pointer table = TypeProfileTables.allocateTable(TypeProfileTables.MAX_BUCKETS);
        Assert.assertTrue("could not allocate type profile table test memory", table.isNonNull());
        Pointer previous = TypeProfileRecorder.currentTableForTesting();
        TypeProfileRecorder.setTableForTesting(table);
        try {
            int testBase = CAP_TEST_KEYS;
            boolean routedThroughRuntime = TypeProfileRecorder.isEnabled();
            for (int siteIndex = testBase; siteIndex < testBase + keys; siteIndex++) {
                recordType(routedThroughRuntime, siteIndex);
            }
            table = TypeProfileRecorder.currentTableForTesting();
            long capacity = table.readLong(TypeProfileTables.MASK_OFFSET) + 1;
            int privateTestKeys = 0;
            for (int index = 0; index < capacity; index++) {
                long siteIndex = (table.readLong(TypeProfileTables.HEADER_BYTES + index * TypeProfileTables.BUCKET_BYTES + TypeProfileTables.BUCKET_KEY) >>> 32) - 1;
                if (siteIndex >= testBase && siteIndex < testBase + keys) {
                    privateTestKeys++;
                }
            }
            TypeProfileTables.takeSnapshot();
            long snapshotTestCount = 0;
            for (Map.Entry<Long, Long> entry : TypeProfileTables.snapshot.entrySet()) {
                long siteIndex = (entry.getKey() >>> 32) - 1;
                if (siteIndex >= testBase && siteIndex < testBase + keys) {
                    snapshotTestCount += entry.getValue();
                }
            }
            Assert.assertEquals(TypeProfileTables.MAX_BUCKETS, capacity);
            Assert.assertEquals(1, privateTestKeys);
            Assert.assertEquals(keys, snapshotTestCount);
        } finally {
            Pointer current = TypeProfileRecorder.currentTableForTesting();
            TypeProfileRecorder.setTableForTesting(previous);
            freeUnlessSame(current, previous);
        }
    }

    /**
     * Takes snapshots while other threads count events in the shared tables and allocate. A thread
     * without a table of its own takes the shared lock in uninterruptible code, and allocating
     * requests collections, that is, safepoints. A snapshot that held the lock where it can stop at
     * a safepoint would hang the image: it would wait for the safepoint, and the safepoint would
     * wait for a thread that spins on the lock. Then every event the threads counted must be in the
     * last snapshot.
     */
    @Test
    public void snapshotsWhileOtherThreadsTakeTheSharedLocks() throws InterruptedException {
        Pointer callCountTable = CallCountProfileRecorder.currentTableForTesting();
        Pointer typeTable = TypeProfileRecorder.currentTableForTesting();
        CallCountProfileRecorder.setTableForTesting(Word.nullPointer());
        TypeProfileRecorder.setTableForTesting(Word.nullPointer());
        try {
            for (int index = SNAPSHOT_TEST_KEYS + LOCK_THREADS; index < SNAPSHOT_TEST_KEYS + LOCK_THREADS + SNAPSHOT_TEST_SHARED_KEYS; index++) {
                CallCountTables.incrementSlowPath(index + 1L);
                TypeProfileTables.recordSlowPath(typeTableKey(index));
            }
        } finally {
            TypeProfileRecorder.setTableForTesting(typeTable);
            CallCountProfileRecorder.setTableForTesting(callCountTable);
        }

        AtomicBoolean stop = new AtomicBoolean();
        long[] events = new long[LOCK_THREADS];
        Thread[] threads = new Thread[LOCK_THREADS];
        for (int i = 0; i < LOCK_THREADS; i++) {
            int thread = i;
            threads[i] = new Thread(() -> events[thread] = countInSharedTablesUntil(stop, SNAPSHOT_TEST_KEYS + thread));
            threads[i].start();
        }
        int snapshots = 0;
        try {
            long end = System.nanoTime() + SNAPSHOT_TEST_NANOS;
            while (System.nanoTime() < end) {
                TypeProfileTables.takeSnapshot();
                CallCountTables.takeSnapshot();
                snapshots++;
            }
        } finally {
            stop.set(true);
            for (Thread thread : threads) {
                thread.join();
            }
        }
        TypeProfileTables.takeSnapshot();
        CallCountTables.takeSnapshot();
        Assert.assertTrue(snapshots > 0);
        for (int thread = 0; thread < LOCK_THREADS; thread++) {
            int index = SNAPSHOT_TEST_KEYS + thread;
            Assert.assertTrue("thread " + thread + " counted no events", events[thread] > 0);
            Assert.assertEquals(events[thread], TypeProfileTables.snapshot.getOrDefault(typeTableKey(index), 0L).longValue());
            Assert.assertEquals(events[thread], CallCountTables.snapshot.getOrDefault(index, 0L).longValue());
        }
    }

    /**
     * Counts events of {@code index} in the shared tables, as a thread without tables does, and
     * allocates, until {@code stop} is set. Returns the events counted in each table.
     */
    private static long countInSharedTablesUntil(AtomicBoolean stop, int index) {
        Pointer callCountTable = CallCountProfileRecorder.currentTableForTesting();
        Pointer typeTable = TypeProfileRecorder.currentTableForTesting();
        CallCountProfileRecorder.setTableForTesting(Word.nullPointer());
        TypeProfileRecorder.setTableForTesting(Word.nullPointer());
        Object[] garbage = new Object[GARBAGE_SLOTS];
        long events = 0;
        try {
            while (!stop.get()) {
                CallCountTables.incrementSlowPath(index + 1L);
                TypeProfileTables.recordSlowPath(typeTableKey(index));
                garbage[(int) (events % GARBAGE_SLOTS)] = new long[GARBAGE_LONGS];
                events++;
            }
        } finally {
            /* At exit, an instrumented image merges these tables into the shared ones and frees them. */
            TypeProfileRecorder.setTableForTesting(typeTable);
            CallCountProfileRecorder.setTableForTesting(callCountTable);
        }
        return events;
    }

    /**
     * A thread that starts after another one exited takes over a stack-sample table of an exited
     * thread, so the tables do not grow with the number of threads an image runs. Only the threads
     * of a sampling {@code --pgo-instrument} image have tables. A thread joins before its table is
     * back in the free list, so the next thread may still allocate one; most must not.
     */
    @Test
    public void stackSampleTablesOfExitedThreadsAreReused() throws InterruptedException {
        Assume.assumeTrue("threads have stack-sample tables only in a sampling --pgo-instrument image", StackSampleRecorder.currentTableForTesting().isNonNull());
        long[] tables = new long[SEQUENTIAL_THREADS];
        for (int i = 0; i < SEQUENTIAL_THREADS; i++) {
            int thread = i;
            Thread started = new Thread(() -> tables[thread] = StackSampleRecorder.currentTableForTesting().rawValue());
            started.start();
            started.join();
        }
        Set<Long> distinct = new HashSet<>();
        for (long table : tables) {
            Assert.assertNotEquals("a thread of a sampling image has no stack-sample table", 0, table);
            distinct.add(table);
        }
        Assert.assertTrue(SEQUENTIAL_THREADS + " threads that ran one after another had " + distinct.size() + " stack-sample tables", distinct.size() <= SEQUENTIAL_THREADS / 2);
    }

    @Test
    public void stackSampleTableAccountsForArenaDropTruncationAndDuplicate() {
        Pointer table = UntrackedNullableNativeMemory.calloc(Word.unsigned(StackSampleTables.ALLOCATION_BYTES));
        Assert.assertTrue("could not allocate stack-sample table test memory", table.isNonNull());
        try {
            int uniqueStacks = StackSampleTables.ARENA_LONGS / StackSampleTables.MAX_FRAMES + 1;
            for (int sample = 0; sample < uniqueStacks; sample++) {
                writeScratchStack(table, 1L + (long) sample * StackSampleTables.MAX_FRAMES, sample == 0);
                StackSampleCallback.record(table);
            }
            /* Reinsert the first stack after the arena is full; an existing key must still update. */
            writeScratchStack(table, 1L, false);
            StackSampleCallback.record(table);
            long largestBucketCount = 0;
            for (int index = 0; index < StackSampleTables.BUCKETS; index++) {
                largestBucketCount = Math.max(largestBucketCount,
                                table.readLong(StackSampleTables.HEADER_BYTES + index * StackSampleTables.BUCKET_BYTES + StackSampleTables.BUCKET_COUNT));
            }
            /* One full-depth stack more than the arena holds is dropped; the first stack was truncated. */
            Assert.assertEquals(uniqueStacks + 1, table.readLong(StackSampleTables.SAMPLES_OFFSET));
            Assert.assertEquals(1, table.readLong(StackSampleTables.DROPPED_OFFSET));
            Assert.assertEquals(1, table.readLong(StackSampleTables.TRUNCATED_OFFSET));
            Assert.assertEquals(2, largestBucketCount);
        } finally {
            UntrackedNullableNativeMemory.free(table);
        }
    }

    private static void incrementCallCount(boolean routedThroughRuntime, int counterIndex) {
        if (routedThroughRuntime) {
            CallCountProfileRecorder.increment(counterIndex);
        } else {
            CallCountTables.incrementSlowPath(counterIndex + 1L);
        }
    }

    /** Key of the type profile recorder: site index plus one in the high word, type id 1 in the low word. */
    private static long typeTableKey(int siteIndex) {
        return ((long) (siteIndex + 1) << 32) | 1;
    }

    private static void recordType(boolean routedThroughRuntime, int siteIndex) {
        if (routedThroughRuntime) {
            TypeProfileTables.recordTypeRuntime(siteIndex, 1);
        } else {
            TypeProfileTables.recordSlowPath(typeTableKey(siteIndex));
        }
    }

    /** Writes a full-depth stack of consecutive addresses starting at {@code firstAddress} to the scratch area. */
    private static void writeScratchStack(Pointer table, long firstAddress, boolean truncated) {
        table.writeLong(StackSampleTables.SCRATCH_LENGTH, StackSampleTables.MAX_FRAMES);
        table.writeLong(StackSampleTables.SCRATCH_TRUNCATED, truncated ? 1 : 0);
        for (int frame = 0; frame < StackSampleTables.MAX_FRAMES; frame++) {
            table.writeLong(StackSampleTables.SCRATCH_OFFSET + frame * Long.BYTES, firstAddress + frame);
        }
    }

    /** Frees the table the test installed, or the one growth replaced it with. */
    private static void freeUnlessSame(Pointer current, Pointer previous) {
        if (current.isNonNull() && current.notEqual(previous)) {
            UntrackedNullableNativeMemory.free(current);
        }
    }

    @TargetClass(CallCountProfileRecorder.class)
    static final class CallCountTables {
        // Checkstyle: stop
        @Alias //
        static int INITIAL_BUCKETS;
        @Alias //
        static int MAX_BUCKETS;
        @Alias //
        static int HEADER_BYTES;
        @Alias //
        static int MASK_OFFSET;
        @Alias //
        static int BUCKET_BYTES;
        @Alias //
        static int BUCKET_KEY;
        @Alias //
        static int BUCKET_COUNT;
        // Checkstyle: resume
        @Alias //
        static Map<Integer, Long> snapshot;

        @Alias
        static native Pointer allocateTable(int buckets);

        @Alias
        static native boolean addBounded(Pointer table, long key);

        @Alias
        static native void incrementSlowPath(long key);

        @Alias
        static native void takeSnapshot();
    }

    @TargetClass(TypeProfileRecorder.class)
    static final class TypeProfileTables {
        // Checkstyle: stop
        @Alias //
        static int INITIAL_BUCKETS;
        @Alias //
        static int MAX_BUCKETS;
        @Alias //
        static int HEADER_BYTES;
        @Alias //
        static int MASK_OFFSET;
        @Alias //
        static int BUCKET_BYTES;
        @Alias //
        static int BUCKET_KEY;
        @Alias //
        static int BUCKET_COUNT;
        // Checkstyle: resume
        @Alias //
        static Map<Long, Long> snapshot;

        @Alias
        static native Pointer allocateTable(int buckets);

        @Alias
        static native boolean addBounded(Pointer table, long key);

        @Alias
        static native void recordTypeRuntime(int siteIndex, int typeId);

        @Alias
        static native void recordSlowPath(long key);

        @Alias
        static native void mergeIntoShared(Pointer source, boolean clear);

        @Alias
        static native void takeSnapshot();
    }

    @TargetClass(StackSampleRecorder.class)
    static final class StackSampleTables {
        // Checkstyle: stop
        @Alias //
        static int MAX_FRAMES;
        @Alias //
        static int HEADER_BYTES;
        @Alias //
        static int SAMPLES_OFFSET;
        @Alias //
        static int DROPPED_OFFSET;
        @Alias //
        static int TRUNCATED_OFFSET;
        @Alias //
        static int BUCKETS;
        @Alias //
        static int BUCKET_BYTES;
        @Alias //
        static int BUCKET_COUNT;
        @Alias //
        static int ARENA_LONGS;
        @Alias //
        static int SCRATCH_OFFSET;
        @Alias //
        static int SCRATCH_TRUNCATED;
        @Alias //
        static int SCRATCH_LENGTH;
        @Alias //
        static int ALLOCATION_BYTES;
        // Checkstyle: resume
    }

    @TargetClass(className = "com.oracle.svm.core.pgo.StackSampleRecorder", innerClass = "SampleCallback")
    static final class StackSampleCallback {
        @Alias
        static native void record(Pointer table);
    }
}
