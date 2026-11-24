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

import static com.oracle.svm.core.snippets.SnippetRuntime.findForeignCall;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.guest.staging.core.thread.ThreadListener;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.internal.misc.Unsafe;

/**
 * Sparse per-thread storage for exact counts of call edges and of switch successors.
 * <p>
 * Every thread owns an open-addressing table (key, count) that starts at {@link #INITIAL_BUCKETS}
 * and doubles whenever it is half full, up to {@link #MAX_BUCKETS}. The fast path probes at most
 * {@link #MAX_PROBES} buckets and never allocates; everything else (growing, a long probe chain, a
 * table at its cap, a thread without a table) goes through one foreign call. A half-full table at
 * the cap is flushed: its counts move to the shared table and it is emptied, so it never fills up.
 * Flushes, exited threads and the dump-time snapshot fold private tables into one shared table of
 * the same layout that grows under a lock, so a key is dropped only when native memory runs out or
 * the shared table is full at its cap ({@link #MAX_SHARED_BUCKETS}).
 */
public final class CallCountProfileRecorder implements ThreadListener {
    /**
     * Most counters the table keys can tell apart: a key is the counter index plus one, as an
     * unsigned 32-bit value, so every non-negative int index fits.
     */
    private static final int MAX_COUNTERS = Integer.MAX_VALUE;
    /** Buckets of a fresh per-thread table (128 KB). */
    private static final int INITIAL_BUCKETS = 8_192;
    /** Largest per-thread table (4 MB); at half full it is flushed into the shared table. */
    private static final int MAX_BUCKETS = 262_144;
    /** Buckets of a fresh shared table. */
    private static final int INITIAL_SHARED_BUCKETS = 262_144;
    /**
     * Largest shared table (1 GB): bucket offsets are int byte offsets. A shared table at this cap
     * fills up instead of growing.
     */
    private static final int MAX_SHARED_BUCKETS = 1 << 26;
    /** Longest probe chain the inlined fast path follows before it calls the slow path. */
    private static final int MAX_PROBES = 8;
    private static final int HEADER_BYTES = 32;
    private static final int TOTAL_OFFSET = 0;
    private static final int DROPPED_OFFSET = 8;
    /** Bucket count minus one; swapped together with the table pointer on growth. */
    private static final int MASK_OFFSET = 16;
    /** Occupied buckets; a table grows when this reaches half the bucket count. */
    private static final int SIZE_OFFSET = 24;
    private static final int BUCKET_BYTES = 16;
    private static final int BUCKET_KEY = 0;
    private static final int BUCKET_COUNT = 8;

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final FastThreadLocalWord<Pointer> TABLE = FastThreadLocalFactory.createWord("CallCountProfileRecorder.table");

    /**
     * Memory location of the table contents. The tables are native memory that no Java code can
     * reach, so the fast path reads and writes them under this location only. The snippet that
     * inlines the fast path into instrumented code lists this location and
     * {@link #tablePointerLocation()} as its private locations ({@code SnippetInfo}): locations that
     * only the snippet accesses. The snippet's reads and writes then need not be ordered against the
     * other memory accesses of the method it is inlined into, so a counter update does not stop the
     * compiler from moving, combining or removing the method's own loads and stores. The slow path
     * runs as {@link #INCREMENT_SLOW_PATH}, which writes only these two locations (it may replace the
     * table).
     */
    public static final LocationIdentity TABLE_LOCATION = NamedLocationIdentity.mutable("PGOCallCountTables");

    public static final SubstrateForeignCallDescriptor INCREMENT_SLOW_PATH = findForeignCall(CallCountProfileRecorder.class, "incrementSlowPath", NO_SIDE_EFFECT, TABLE_LOCATION,
                    TABLE.getLocationIdentity());
    /** Pointer to the shared table; allocated lazily and replaced on growth, always under {@link #SHARED_LOCK}. */
    private static final CGlobalData<Pointer> SHARED = CGlobalDataFactory.createWord();
    private static final CGlobalData<Pointer> SHARED_LOCK = CGlobalDataFactory.createWord();
    /** Events that could not be counted in any table: no table could be allocated, or the shared table is full. */
    private static final CGlobalData<Pointer> SHARED_DROPPED = CGlobalDataFactory.createWord();
    /** Call edges by counter index. Switch successors take indexes too but have no entry here. */
    private static final ConcurrentMap<Integer, CallCountProfileKey> KEYS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_INDEX = new AtomicInteger();
    /**
     * Call edges are created after analysis, during compilation. This placeholder, created when the
     * class is initialized at build time, lets the analysis see a numbered
     * {@link CallCountProfileKey} in {@link #KEYS} with all its fields set; the writer and
     * the statistics leave it out.
     */
    private static final CallCountProfileKey REACHABILITY_PLACEHOLDER = create(
                    new ProfileFrame[]{new ProfileFrame("Lcom/oracle/svm/core/pgo/CallCountProfileRecorder;.__unused__()V", 0)});

    private static boolean enabled;
    /** The last snapshot by counter index; built from {@link #snapshotKeys} at the first read. */
    private static Map<Integer, Long> snapshot;
    /** Keys and counts of the shared table at the last snapshot, as read at the safepoint. */
    private static long[] snapshotKeys;
    private static long[] snapshotCounts;
    private static long snapshotTotal;
    private static long snapshotDropped;

    private CallCountProfileRecorder() {
    }

    /**
     * Location of the per-thread table pointer. The snippet that inlines the fast path lists it as a
     * private location, see {@link #TABLE_LOCATION}.
     */
    public static LocationIdentity tablePointerLocation() {
        return TABLE.getLocationIdentity();
    }

    public static CallCountProfileRecorder createRecorder() {
        enabled = true;
        return new CallCountProfileRecorder();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Gives out the next counter index, for a call edge ({@link #register}) or a switch successor
     * ({@link SwitchProfileKey}) whose counter node reached machine code. Both are counted in
     * the same tables; only call edges also have a {@link CallCountProfileKey} in
     * {@link #KEYS}, because a switch's own key holds its successors.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static int allocateIndex() {
        int index = NEXT_INDEX.getAndIncrement();
        if (index < 0 || index >= MAX_COUNTERS) {
            throw new IllegalStateException("PGO call-count profiler: the image has more than " + MAX_COUNTERS + " call edges and switch successors, the most its table keys can tell apart");
        }
        return index;
    }

    /** Creates the key of a call edge with its counter index right away, as if compiled code already used it. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static CallCountProfileKey create(ProfileFrame[] context) {
        CallCountProfileKey key = createUnnumbered(context);
        key.counterIndexForCode();
        return key;
    }

    /**
     * Creates the key of one call edge. The key is numbered and registered only when one of
     * its counter nodes is lowered ({@link CallCountProfileKey#counterIndexForCode()}).
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static CallCountProfileKey createUnnumbered(ProfileFrame[] context) {
        if (context.length == 0) {
            throw new IllegalArgumentException("PGO call-count profiler: a call edge needs a calling context");
        }
        return new CallCountProfileKey(CallCountProfileKey.NO_INDEX, context);
    }

    /** Gives a call-edge key the next counter index and adds it to the registry the writer reads. */
    @Platforms(Platform.HOSTED_ONLY.class)
    static int register(CallCountProfileKey key) {
        int index = allocateIndex();
        KEYS.put(index, key);
        return index;
    }

    /** Counters numbered so far, call edges and switch successors, that is, counters compiled code uses. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static int numberedCounterCount() {
        return NEXT_INDEX.get() - 1; // without the reachability placeholder
    }

    @Override
    @Uninterruptible(reason = "Thread state not set up.")
    public void afterThreadAttach(IsolateThread isolateThread) {
        if (TABLE.get(isolateThread).isNull()) {
            TABLE.set(isolateThread, allocateTable(INITIAL_BUCKETS));
        }
    }

    @Override
    @Uninterruptible(reason = "Only uninterruptible code may be executed after Thread.exit.")
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        Pointer table = TABLE.get(isolateThread);
        if (table.isNonNull()) {
            mergeIntoShared(table, false);
            TABLE.set(isolateThread, Word.nullPointer());
            UntrackedNullableNativeMemory.free(table);
        }
    }

    /**
     * Counts one execution of counter {@code counterIndex} in the current thread's table. This is the
     * fast path that the snippet of {@code CallCountProfileCounterNode} inlines before every
     * instrumented call and into every instrumented switch successor, so it has to stay small and
     * harmless to the code around it:
     * <ul>
     * <li>It reads and writes only the thread's table ({@link #TABLE_LOCATION}) and the table pointer
     * ({@link #tablePointerLocation()}), which the snippet declares private. The counter node can
     * therefore lower to this code without being a memory kill, that is, without a barrier to the
     * compiler's optimization of the method's own loads and stores.</li>
     * <li>It cannot throw and does not allocate, so the inlined code needs no exception edge and no
     * frame state.</li>
     * <li>Everything rare (no table yet, a half-full table, a long probe chain) is the out-of-line
     * call {@link #incrementSlowPath}.</li>
     * </ul>
     */
    @Uninterruptible(reason = "Counter key and total publication must be observed atomically by a safepoint snapshot.")
    public static void increment(int counterIndex) {
        /* Inlined unsigned widening: Integer.toUnsignedLong is not @Uninterruptible. */
        long key = (counterIndex + 1) & 0xffffffffL;
        Pointer table = TABLE.get();
        if (table.isNonNull() && addBounded(table, key)) {
            table.writeLong(TOTAL_OFFSET, table.readLong(TOTAL_OFFSET, TABLE_LOCATION) + 1, TABLE_LOCATION);
        } else {
            callSlowPath(INCREMENT_SLOW_PATH, key);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPath(@ConstantNodeParameter ForeignCallDescriptor descriptor, long key);

    /**
     * Rare path: grows the private table when it is half full, or flushes it into the shared table
     * when it is half full at its cap, then inserts with an unbounded probe. Only a thread without a
     * table spills events to the shared table directly.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    @Uninterruptible(reason = "May replace the thread's table and writes native counter memory; needs no deoptimization state.")
    private static void incrementSlowPath(long key) {
        Pointer table = TABLE.get();
        if (table.isNonNull()) {
            table = growOrFlushIfHalfFull(table);
            if (addUnbounded(table, key, 1)) {
                table.writeLong(TOTAL_OFFSET, table.readLong(TOTAL_OFFSET, TABLE_LOCATION) + 1, TABLE_LOCATION);
                return;
            }
        }
        addDirectlyToShared(key, 1);
    }

    /**
     * Fast-path insert: follows at most {@link #MAX_PROBES} buckets and refuses to fill the table past
     * half, so chains stay short and the slow path grows the table in time.
     */
    @Uninterruptible(reason = "Operates only on native counter tables.")
    private static boolean addBounded(Pointer table, long key) {
        int mask = (int) table.readLong(MASK_OFFSET, TABLE_LOCATION);
        int index = mix(key) & mask;
        for (int probe = 0; probe < MAX_PROBES; probe++) {
            int bucket = HEADER_BYTES + index * BUCKET_BYTES;
            long existing = table.readLong(bucket + BUCKET_KEY, TABLE_LOCATION);
            if (existing == key) {
                table.writeLong(bucket + BUCKET_COUNT, table.readLong(bucket + BUCKET_COUNT, TABLE_LOCATION) + 1, TABLE_LOCATION);
                return true;
            }
            if (existing == 0) {
                long size = table.readLong(SIZE_OFFSET, TABLE_LOCATION);
                if (size * 2 >= mask + 1L) {
                    return false;
                }
                table.writeLong(bucket + BUCKET_COUNT, 1, TABLE_LOCATION);
                table.writeLong(bucket + BUCKET_KEY, key, TABLE_LOCATION);
                table.writeLong(SIZE_OFFSET, size + 1, TABLE_LOCATION);
                return true;
            }
            index = (index + 1) & mask;
        }
        return false;
    }

    /** Slow-path insert: probes the whole table; fails only when every bucket is taken. */
    @Uninterruptible(reason = "Operates only on native counter tables.")
    private static boolean addUnbounded(Pointer table, long key, long count) {
        int mask = (int) table.readLong(MASK_OFFSET, TABLE_LOCATION);
        int index = mix(key) & mask;
        for (int probe = 0; probe <= mask; probe++) {
            int bucket = HEADER_BYTES + index * BUCKET_BYTES;
            long existing = table.readLong(bucket + BUCKET_KEY, TABLE_LOCATION);
            if (existing == key) {
                table.writeLong(bucket + BUCKET_COUNT, table.readLong(bucket + BUCKET_COUNT, TABLE_LOCATION) + count, TABLE_LOCATION);
                return true;
            }
            if (existing == 0) {
                table.writeLong(bucket + BUCKET_COUNT, count, TABLE_LOCATION);
                table.writeLong(bucket + BUCKET_KEY, key, TABLE_LOCATION);
                table.writeLong(SIZE_OFFSET, table.readLong(SIZE_OFFSET, TABLE_LOCATION) + 1, TABLE_LOCATION);
                return true;
            }
            index = (index + 1) & mask;
        }
        return false;
    }

    /** A zeroed table with {@code buckets} buckets and its mask set; null on allocation failure. */
    @Uninterruptible(reason = "Operates only on native counter tables.")
    private static Pointer allocateTable(int buckets) {
        Pointer table = UntrackedNullableNativeMemory.calloc(Word.unsigned(HEADER_BYTES).add(Word.unsigned(buckets).multiply(BUCKET_BYTES)));
        if (table.isNonNull()) {
            table.writeLong(MASK_OFFSET, buckets - 1L, TABLE_LOCATION);
        }
        return table;
    }

    /**
     * Doubles the thread's table when it is at least half full and below {@link #MAX_BUCKETS}. A
     * half-full table at the cap, or one that cannot grow for lack of memory, is flushed instead:
     * its counts move to the shared table and it is emptied. A private table therefore never fills
     * up, which would make every search for a missing key scan all buckets. Runs only in the
     * uninterruptible slow path, so a safepoint snapshot sees either the old table or the complete
     * new one. Returns the table to use (the old one when it was flushed or nothing was done).
     */
    @Uninterruptible(reason = "Swaps the thread's table; must not be interrupted by a snapshot.")
    private static Pointer growOrFlushIfHalfFull(Pointer table) {
        long buckets = table.readLong(MASK_OFFSET, TABLE_LOCATION) + 1;
        if (table.readLong(SIZE_OFFSET, TABLE_LOCATION) * 2 < buckets) {
            return table;
        }
        Pointer grown = buckets < MAX_BUCKETS ? rehash(table, (int) (buckets * 2)) : Word.nullPointer();
        if (grown.isNull()) {
            mergeIntoShared(table, true);
            return table;
        }
        TABLE.set(grown);
        UntrackedNullableNativeMemory.free(table);
        return grown;
    }

    /** Copies a table into a fresh one with {@code buckets} buckets; null on allocation failure. */
    @Uninterruptible(reason = "Operates only on native counter tables.")
    private static Pointer rehash(Pointer source, int buckets) {
        Pointer target = allocateTable(buckets);
        if (target.isNull()) {
            return target;
        }
        target.writeLong(TOTAL_OFFSET, source.readLong(TOTAL_OFFSET, TABLE_LOCATION), TABLE_LOCATION);
        target.writeLong(DROPPED_OFFSET, source.readLong(DROPPED_OFFSET, TABLE_LOCATION), TABLE_LOCATION);
        int sourceMask = (int) source.readLong(MASK_OFFSET, TABLE_LOCATION);
        for (int index = 0; index <= sourceMask; index++) {
            int bucket = HEADER_BYTES + index * BUCKET_BYTES;
            long key = source.readLong(bucket + BUCKET_KEY, TABLE_LOCATION);
            if (key != 0) {
                addUnbounded(target, key, source.readLong(bucket + BUCKET_COUNT, TABLE_LOCATION));
            }
        }
        return target;
    }

    @Uninterruptible(reason = "Operates only on native counter tables.")
    private static void clearTable(Pointer table) {
        int mask = (int) table.readLong(MASK_OFFSET, TABLE_LOCATION);
        for (int index = 0; index <= mask; index++) {
            int bucket = HEADER_BYTES + index * BUCKET_BYTES;
            table.writeLong(bucket + BUCKET_KEY, 0, TABLE_LOCATION);
            table.writeLong(bucket + BUCKET_COUNT, 0, TABLE_LOCATION);
        }
        table.writeLong(TOTAL_OFFSET, 0, TABLE_LOCATION);
        table.writeLong(DROPPED_OFFSET, 0, TABLE_LOCATION);
        table.writeLong(SIZE_OFFSET, 0, TABLE_LOCATION);
    }

    /**
     * Bucket index of {@code key} before masking: the first half of the MurmurHash3 64-bit finalizer
     * ({@code fmix64}), that is, a shift-xor, one multiplication and a second shift-xor that folds
     * the product's high bits into the low bits the mask keeps. It costs one multiplication on the
     * fast path. Measured with linear probing at the half load the tables allow, it is as good as an
     * ideal random hash: about 1.5 probes per lookup on average, and 0.4% to 0.8% of the keys need
     * more probes than the fast path follows ({@link #MAX_PROBES}), for consecutive, random and
     * strided keys and for the type profile recorder's (site, type) keys. The full {@code fmix64}, with
     * a second multiplication, measures the same; without any mixing, strided keys and (site, type)
     * keys need thousands of probes.
     */
    @Uninterruptible(reason = "Operates only on native counter tables.")
    private static int mix(long key) {
        long value = key;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (int) value;
    }

    /**
     * The current thread's table, for {@code RecorderTableNativeTest}: the test installs tables of
     * its own and checks them after the production code has grown, flushed or cleared them.
     */
    public static Pointer currentTableForTesting() {
        return TABLE.get();
    }

    /** Sets the current thread's table, for {@code RecorderTableNativeTest}. */
    public static void setTableForTesting(Pointer table) {
        TABLE.set(table);
    }

    /** Spill for a thread without a private table; grows the shared table as needed. */
    @Uninterruptible(reason = "Shared fallback must not be stopped while holding the native merge lock.")
    private static void addDirectlyToShared(long key, long count) {
        lockShared();
        Pointer shared = sharedTableForInsert();
        if (shared.isNonNull() && addUnbounded(shared, key, count)) {
            shared.writeLong(TOTAL_OFFSET, shared.readLong(TOTAL_OFFSET, TABLE_LOCATION) + count, TABLE_LOCATION);
        } else {
            addSharedDropped(count);
        }
        unlockShared();
    }

    /** Folds a private table into the shared table and optionally clears it. Takes the shared lock. */
    @Uninterruptible(reason = "Called after Thread.exit, at a safepoint, or from the uninterruptible slow path.")
    private static void mergeIntoShared(Pointer source, boolean clear) {
        lockShared();
        Pointer shared = sharedTableForInsert();
        if (shared.isNonNull()) {
            shared.writeLong(TOTAL_OFFSET, shared.readLong(TOTAL_OFFSET, TABLE_LOCATION) + source.readLong(TOTAL_OFFSET, TABLE_LOCATION), TABLE_LOCATION);
            shared.writeLong(DROPPED_OFFSET, shared.readLong(DROPPED_OFFSET, TABLE_LOCATION) + source.readLong(DROPPED_OFFSET, TABLE_LOCATION), TABLE_LOCATION);
            int sourceMask = (int) source.readLong(MASK_OFFSET, TABLE_LOCATION);
            for (int index = 0; index <= sourceMask; index++) {
                int bucket = HEADER_BYTES + index * BUCKET_BYTES;
                long key = source.readLong(bucket + BUCKET_KEY, TABLE_LOCATION);
                if (key != 0) {
                    long count = source.readLong(bucket + BUCKET_COUNT, TABLE_LOCATION);
                    shared = sharedTableForInsert();
                    if (shared.isNull() || !addUnbounded(shared, key, count)) {
                        addSharedDropped(count);
                    }
                }
            }
        } else {
            addSharedDropped(source.readLong(TOTAL_OFFSET, TABLE_LOCATION));
        }
        if (clear) {
            clearTable(source);
        }
        unlockShared();
    }

    /**
     * The shared table, allocated on first use and doubled when half full up to
     * {@link #MAX_SHARED_BUCKETS}, so merges do not run out of space. Caller holds
     * {@link #SHARED_LOCK}. Null only on allocation failure.
     */
    @Uninterruptible(reason = "Operates only on native counter tables under the shared lock.")
    private static Pointer sharedTableForInsert() {
        Pointer shared = SHARED.get().readWord(0);
        if (shared.isNull()) {
            shared = allocateTable(INITIAL_SHARED_BUCKETS);
            SHARED.get().writeWord(0, shared);
            return shared;
        }
        long buckets = shared.readLong(MASK_OFFSET, TABLE_LOCATION) + 1;
        if (buckets < MAX_SHARED_BUCKETS && shared.readLong(SIZE_OFFSET, TABLE_LOCATION) * 2 >= buckets) {
            Pointer grown = rehash(shared, (int) (buckets * 2));
            if (grown.isNonNull()) {
                SHARED.get().writeWord(0, grown);
                UntrackedNullableNativeMemory.free(shared);
                return grown;
            }
        }
        return shared;
    }

    @Uninterruptible(reason = "Native spin lock for rare fallback, exit, and snapshot merges.")
    private static void lockShared() {
        Pointer lock = SHARED_LOCK.get();
        while (!UNSAFE.compareAndSetInt(null, lock.rawValue(), 0, 1)) {
            // Private-table increments never acquire this lock.
        }
    }

    @Uninterruptible(reason = "Native spin lock for rare fallback, exit, and snapshot merges.")
    private static void unlockShared() {
        UNSAFE.putIntRelease(null, SHARED_LOCK.get().rawValue(), 0);
    }

    @Uninterruptible(reason = "May be called while a thread is attaching or exiting.")
    private static void addSharedDropped(long count) {
        if (count != 0) {
            UNSAFE.getAndAddLong(null, SHARED_DROPPED.get().rawValue(), count);
        }
    }

    /** Takes a snapshot and fills its map. */
    private static void takeSnapshot() {
        captureSnapshot();
        fillSnapshotMap();
    }

    /**
     * Copies the tables at a safepoint, then reads the copy into arrays. Filling the map runs
     * instrumented {@link HashMap} code, which this leaves to {@link #fillSnapshotMap()}.
     */
    private static void captureSnapshot() {
        SnapshotOperation operation = new SnapshotOperation();
        operation.enqueue();
        if (operation.copyFailed) {
            Log.log().string("[PGO] no native memory to copy the call counts; their ").signed(operation.total).string(" events count as dropped").newline();
        }
        long[] keys = new long[0];
        long[] counts = new long[0];
        Pointer copy = operation.copy;
        if (copy.isNonNull()) {
            try {
                int buckets = (int) copy.readLong(MASK_OFFSET) + 1;
                keys = new long[buckets];
                counts = new long[buckets];
                for (int index = 0; index < buckets; index++) {
                    int bucket = HEADER_BYTES + index * BUCKET_BYTES;
                    keys[index] = copy.readLong(bucket + BUCKET_KEY);
                    counts[index] = copy.readLong(bucket + BUCKET_COUNT);
                }
            } finally {
                UntrackedNullableNativeMemory.free(copy);
            }
        }
        snapshotKeys = keys;
        snapshotCounts = counts;
        snapshotTotal = operation.total;
        snapshotDropped = operation.dropped;
        snapshot = null;
    }

    private static void fillSnapshotMap() {
        Map<Integer, Long> copied = new HashMap<>();
        for (int index = 0; index < snapshotKeys.length; index++) {
            if (snapshotKeys[index] != 0) {
                copied.put((int) snapshotKeys[index] - 1, snapshotCounts[index]);
            }
        }
        snapshot = copied;
    }

    /** The map of the last snapshot; takes a snapshot if none exists. */
    private static Map<Integer, Long> snapshot() {
        if (snapshotKeys == null) {
            captureSnapshot();
        }
        if (snapshot == null) {
            fillSnapshotMap();
        }
        return snapshot;
    }

    /**
     * Folds the live threads' tables into the shared table at a safepoint and copies the shared
     * table into native memory, which {@link #captureSnapshot} reads after the operation. It
     * allocates nothing on the Java heap: running out of memory in a VM operation is fatal.
     */
    private static final class SnapshotOperation extends JavaVMOperation {
        /** Copy of the shared table; null if there is no shared table or no memory for the copy. */
        private Pointer copy = Word.nullPointer();
        private long total;
        private long dropped;
        /** Whether there was no memory for the copy; the shared table's events then count as dropped. */
        private boolean copyFailed;

        SnapshotOperation() {
            super(VMOperationInfos.get(SnapshotOperation.class, "Collect PGO call counts", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                Pointer table = TABLE.get(thread);
                if (table.isNonNull()) {
                    mergeIntoShared(table, true);
                }
            }
            copyShared();
        }

        @Uninterruptible(reason = "Holds the shared lock, which other threads take in uninterruptible code.")
        private void copyShared() {
            lockShared();
            Pointer shared = SHARED.get().readWord(0);
            dropped = SHARED_DROPPED.get().readLong(0);
            if (shared.isNonNull()) {
                total = shared.readLong(TOTAL_OFFSET);
                dropped += shared.readLong(DROPPED_OFFSET);
                UnsignedWord bytes = Word.unsigned(HEADER_BYTES).add(Word.unsigned(shared.readLong(MASK_OFFSET) + 1).multiply(BUCKET_BYTES));
                copy = UntrackedNullableNativeMemory.malloc(bytes);
                if (copy.isNonNull()) {
                    UnmanagedMemoryUtil.copy(shared, copy, bytes);
                } else {
                    dropped += total;
                    copyFailed = true;
                }
            }
            unlockShared();
        }
    }

    /** The count of a call edge or switch successor in the last snapshot; takes one if none exists. */
    public static long getCount(int counterIndex) {
        return snapshot().getOrDefault(counterIndex, 0L);
    }

    public static List<CallCountProfileKey> getKeys() {
        List<CallCountProfileKey> result = new ArrayList<>();
        for (CallCountProfileKey key : KEYS.values()) {
            if (key != REACHABILITY_PLACEHOLDER) {
                result.add(key);
            }
        }
        result.sort(Comparator.comparing(CallCountProfileKey::context, ProfileFrame.CONTEXT_ORDER));
        return result;
    }

    /**
     * Takes the snapshot that {@link #getCount} reads for the dump. It runs no instrumented code:
     * the map is filled at the first read.
     */
    public static void prepareSnapshot() {
        captureSnapshot();
    }

    public static Statistics statistics() {
        Map<Integer, Long> counts = snapshot();
        long attributed = 0;
        int mappedKeys = 0;
        for (Map.Entry<Integer, Long> entry : counts.entrySet()) {
            if (KEYS.containsKey(entry.getKey())) {
                mappedKeys++;
                attributed += entry.getValue();
            }
        }
        return new Statistics(KEYS.size() - 1, snapshotTotal, snapshotDropped, counts.size(), mappedKeys, attributed);
    }

    /**
     * What the call-count tables saw: the numbered call edges, the counted events (call edges and
     * switch successors), the events no table had room for, the distinct counters that were hit, and
     * how many of those counters and of their events belong to call edges.
     */
    public record Statistics(int callEdges, long events, long dropped, int keys, int mappedKeys, long attributedEvents) {
    }
}
