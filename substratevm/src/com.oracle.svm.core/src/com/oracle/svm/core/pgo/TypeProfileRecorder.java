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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import com.oracle.svm.core.hub.DynamicHubIntrinsics;
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
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.internal.misc.Unsafe;

/**
 * Allocation-free recorder of the dynamic types that {@code --pgo-instrument} images observe at two
 * kinds of sites: the receiver of an indirect call ({@code virtualInvokeProfiles}) and the object
 * of a monitor enter ({@code monitorProfiles}). Each receiver site has its own counts per type;
 * monitor profiles are global per type, so every monitor enter counts into one shared site
 * ({@link #monitorKey()}).
 * <p>
 * Same storage shape as {@link CallCountProfileRecorder}: a per-thread open-addressing table keyed
 * by (site, type id) that starts at {@link #INITIAL_BUCKETS} and doubles whenever it is half full,
 * up to {@link #MAX_BUCKETS}, and is flushed into the shared table when it is half full at the cap;
 * a fast path bounded to {@link #MAX_PROBES} probes; one foreign call for growth, flushes, long
 * chains, or a missing table; and a shared table of the same layout that grows under a lock when
 * private tables are merged into it.
 */
public final class TypeProfileRecorder implements ThreadListener {
    /**
     * Most sites the table keys can tell apart: a key holds the site index plus one in its upper 32
     * bits, so every non-negative int index fits.
     */
    private static final int MAX_SITES = Integer.MAX_VALUE;
    /** Buckets of a fresh per-thread table (256 KB). */
    private static final int INITIAL_BUCKETS = 16_384;
    /** Largest per-thread table (4 MB); at half full it is flushed into the shared table. */
    private static final int MAX_BUCKETS = 262_144;
    /** Buckets of a fresh shared table. */
    private static final int INITIAL_SHARED_BUCKETS = 131_072;
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
    private static final FastThreadLocalWord<Pointer> TABLE = FastThreadLocalFactory.createWord("TypeProfileRecorder.table");

    /**
     * Location of the table contents. The tables are native memory that is never aliased with any
     * Java-visible memory, so the fast path accesses them under this name and a snippet that contains
     * the fast path declares it private. The slow path runs as {@link #RECORD_SLOW_PATH}, which kills
     * only this location and {@link #tablePointerLocation()} (it may replace the table).
     */
    public static final LocationIdentity TABLE_LOCATION = NamedLocationIdentity.mutable("PGOTypeProfileTables");

    public static final SubstrateForeignCallDescriptor RECORD_SLOW_PATH = findForeignCall(TypeProfileRecorder.class, "recordSlowPath", NO_SIDE_EFFECT, TABLE_LOCATION,
                    TABLE.getLocationIdentity());
    /** Pointer to the shared table; allocated lazily and replaced on growth, always under {@link #SHARED_LOCK}. */
    private static final CGlobalData<Pointer> SHARED = CGlobalDataFactory.createWord();
    private static final CGlobalData<Pointer> SHARED_LOCK = CGlobalDataFactory.createWord();
    /** Events that could not be counted in any table: no table could be allocated, or the shared table is full. */
    private static final CGlobalData<Pointer> SHARED_DROPPED = CGlobalDataFactory.createWord();
    private static final ConcurrentMap<Integer, TypeProfileKey> KEYS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_SITE = new AtomicInteger();
    /**
     * Counts of {@link #recordType} in hosted unit tests ({@code TypeProfileRecorderTest}), which run
     * without the native tables; null in an image.
     */
    private static final ConcurrentMap<Long, Long> HOSTED_COUNTS = SubstrateUtil.HOSTED ? new ConcurrentHashMap<>() : null;

    /**
     * Descriptor of every image type by type id, registered after compilation. Lets sites whose
     * receiver type state was unknown to the static analysis (saturated or absent static type
     * profile) still be decoded: the recorder keys by the dynamic hub id, this table names it.
     */
    private static final ConcurrentMap<Integer, String> TYPE_DESCRIPTORS_BY_ID = new ConcurrentHashMap<>();

    /**
     * Sites are created after analysis, during compilation. This placeholder, created when the class
     * is initialized at build time, lets the analysis see a numbered {@link TypeProfileKey} in
     * {@link #KEYS} with all its fields set; the decoder and the statistics leave it out.
     */
    private static final TypeProfileKey REACHABILITY_PLACEHOLDER = createReceiverKey(
                    new ProfileFrame[]{new ProfileFrame("Lcom/oracle/svm/core/pgo/TypeProfileRecorder;.__unused__()V", -1)}, new int[]{-1}, new String[]{"Ljava/lang/Object;"});

    private static Map<Long, Long> snapshot;
    private static long snapshotTotal;
    private static long snapshotDropped;
    /** Build-time constant captured in the instrumented image. */
    private static boolean enabled;
    private static boolean monitorProfilesEnabled;

    private TypeProfileRecorder() {
    }

    /** Location of the per-thread table pointer; a snippet that reads it must declare it private. */
    public static LocationIdentity tablePointerLocation() {
        return TABLE.getLocationIdentity();
    }

    public static TypeProfileRecorder create() {
        enabled = true;
        return new TypeProfileRecorder();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void enableMonitorProfiles() {
        monitorProfilesEnabled = true;
    }

    public static boolean monitorProfilesEnabled() {
        return monitorProfilesEnabled;
    }

    /** Creates a receiver key with its site index right away, as if compiled code already used it. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static TypeProfileKey createReceiverKey(ProfileFrame[] context, int[] receiverTypeIds, String[] receiverTypeDescriptors) {
        TypeProfileKey key = createUnnumberedReceiverKey(context, receiverTypeIds, receiverTypeDescriptors);
        key.siteIndexForCode();
        return key;
    }

    /**
     * Creates the key of one receiver call. The key is numbered and registered only when one
     * of its counter nodes is lowered ({@link TypeProfileKey#siteIndexForCode()}).
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static TypeProfileKey createUnnumberedReceiverKey(ProfileFrame[] context, int[] receiverTypeIds, String[] receiverTypeDescriptors) {
        if (context.length == 0 || receiverTypeIds.length != receiverTypeDescriptors.length) {
            throw new IllegalArgumentException("PGO receiver profiler: a site needs a calling context and one descriptor per receiver type id");
        }
        return new TypeProfileKey(TypeProfileKey.NO_INDEX, context, receiverTypeIds, receiverTypeDescriptors, false);
    }

    /** Gives a receiver key the next site index and adds it to the registry the writer reads. */
    @Platforms(Platform.HOSTED_ONLY.class)
    static int register(TypeProfileKey key) {
        int index = NEXT_SITE.getAndIncrement();
        if (index < 0 || index >= MAX_SITES) {
            throw new IllegalStateException("PGO receiver profiler: the image has more than " + MAX_SITES + " receiver sites, the most its table keys can tell apart");
        }
        KEYS.put(index, key);
        return index;
    }

    /** Receiver keys numbered so far, that is, receiver keys whose counters compiled code uses. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static int numberedReceiverSiteCount() {
        int count = 0;
        for (TypeProfileKey key : KEYS.values()) {
            if (key != REACHABILITY_PLACEHOLDER && !key.isMonitorKey()) {
                count++;
            }
        }
        return count;
    }

    /**
     * The one key that every monitor enter counts under. Monitor profiles are global by dynamic
     * type, so a key for each monitor enter would only take table capacity and
     * displace receiver keys without adding information to the profile.
     */
    public static TypeProfileKey monitorKey() {
        return MonitorSiteHolder.SITE;
    }

    private static TypeProfileKey createMonitorKey() {
        int index = NEXT_SITE.getAndIncrement();
        if (index < 0 || index >= MAX_SITES) {
            throw new IllegalStateException("PGO monitor profiler: the image has more than " + MAX_SITES + " receiver and monitor sites, the most the table keys can tell apart");
        }
        TypeProfileKey key = new TypeProfileKey(index, new ProfileFrame[0], new int[0], new String[0], true);
        KEYS.put(index, key);
        return key;
    }

    /** Delays allocation so images without monitor instrumentation contain no monitor key. */
    private static final class MonitorSiteHolder {
        private static final TypeProfileKey SITE = createMonitorKey();
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

    /** Registers the descriptor of an image type so that receivers unknown to a site's static profile can be named. */
    public static void registerTypeDescriptor(int typeId, String descriptor) {
        TYPE_DESCRIPTORS_BY_ID.putIfAbsent(typeId, descriptor);
    }

    /**
     * Snippet body: records the dynamic type of {@code object}, the receiver of an indirect call or
     * the object of a monitor enter. No hosted branch, nothing that can throw, and memory accesses
     * only to {@link #tablePointerLocation()} and {@link #TABLE_LOCATION}, so that the node lowering
     * to it need not be a memory kill.
     */
    public static void record(int siteIndex, Object object) {
        if (object != null) {
            recordTypeRuntime(siteIndex, DynamicHubIntrinsics.readHub(object).getTypeID());
        }
    }

    /**
     * Snippet body for a probe whose object escape analysis replaced: the type is a compile-time
     * constant. Same rules as {@link #record}.
     */
    public static void recordKnownType(int siteIndex, int typeId) {
        recordTypeRuntime(siteIndex, typeId);
    }

    /** Records one event of type {@code typeId} at site {@code siteIndex}, in {@link #HOSTED_COUNTS} when hosted. */
    static void recordType(int siteIndex, int typeId) {
        if (SubstrateUtil.HOSTED) {
            HOSTED_COUNTS.merge(((long) (siteIndex + 1) << 32) | Integer.toUnsignedLong(typeId), 1L, Long::sum);
            return;
        }
        recordTypeRuntime(siteIndex, typeId);
    }

    private static void recordTypeRuntime(int siteIndex, int typeId) {
        long key = ((long) (siteIndex + 1) << 32) | (typeId & 0xffffffffL);
        Pointer table = TABLE.get();
        if (table.isNonNull() && addBounded(table, key)) {
            table.writeLong(TOTAL_OFFSET, table.readLong(TOTAL_OFFSET, TABLE_LOCATION) + 1, TABLE_LOCATION);
        } else {
            callSlowPath(RECORD_SLOW_PATH, key);
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callSlowPath(@ConstantNodeParameter ForeignCallDescriptor descriptor, long key);

    /**
     * Rare path, compiled separately so that the snippet contains no allocation or atomics: grows the
     * private table when it is half full, or flushes it into the shared table when it is half full
     * at its cap, then inserts with an unbounded probe. Only a thread without a table spills events
     * to the shared table directly.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    @Uninterruptible(reason = "May replace the thread's table and writes native counter memory; needs no deoptimization state.")
    private static void recordSlowPath(long key) {
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
     * uninterruptible slow path, so a safepoint merge sees either the old table or the complete new
     * one. Returns the table to use (the old one when it was flushed or nothing was done).
     */
    @Uninterruptible(reason = "Swaps the thread's table; must not be interrupted by a merge.")
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

    /** Bucket index of {@code key} before masking; the same hash as {@code CallCountProfileRecorder.mix}. */
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
            // Recording into a thread's own table never takes this lock.
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

    private static void takeSnapshot() {
        if (SubstrateUtil.HOSTED) {
            snapshot = new HashMap<>(HOSTED_COUNTS);
            snapshotTotal = HOSTED_COUNTS.values().stream().mapToLong(Long::longValue).sum();
            snapshotDropped = 0;
            return;
        }
        SnapshotOperation operation = new SnapshotOperation();
        operation.enqueue();
        snapshotTotal = operation.total;
        snapshotDropped = operation.dropped;
        if (operation.copyFailed) {
            Log.log().string("[PGO] no native memory to copy the dynamic type profiles; their ").signed(operation.total).string(" events count as dropped").newline();
        }
        Map<Long, Long> copied = new HashMap<>();
        Pointer copy = operation.copy;
        if (copy.isNonNull()) {
            try {
                int mask = (int) copy.readLong(MASK_OFFSET);
                for (int index = 0; index <= mask; index++) {
                    int bucket = HEADER_BYTES + index * BUCKET_BYTES;
                    long key = copy.readLong(bucket + BUCKET_KEY);
                    if (key != 0) {
                        copied.put(key, copy.readLong(bucket + BUCKET_COUNT));
                    }
                }
            } finally {
                UntrackedNullableNativeMemory.free(copy);
            }
        }
        snapshot = copied;
    }

    /**
     * Folds the live threads' tables into the shared table at a safepoint and copies the shared
     * table into native memory, which {@link #takeSnapshot} reads into a map after the operation.
     * Nothing may allocate or reach a safepoint while it holds the shared lock: the other holders
     * spin on the lock in uninterruptible code, so a holder that stopped at a safepoint would wait
     * for a safepoint that a spinning thread keeps from being reached. Allocating on the Java heap
     * in the operation would make running out of memory fatal.
     */
    private static final class SnapshotOperation extends JavaVMOperation {
        /** Copy of the shared table; null if there is no shared table or no memory for the copy. */
        private Pointer copy = Word.nullPointer();
        private long total;
        private long dropped;
        /** Whether there was no memory for the copy; the shared table's events then count as dropped. */
        private boolean copyFailed;

        SnapshotOperation() {
            super(VMOperationInfos.get(SnapshotOperation.class, "Collect PGO type profiles", SystemEffect.SAFEPOINT));
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

    /**
     * Takes the snapshot that {@link #decodeProfiles()} and {@link #statistics()} read for the dump.
     * Filling it runs instrumented {@link HashMap} code, so the dump takes it after the other
     * recorders' snapshots.
     */
    public static void prepareSnapshot() {
        takeSnapshot();
    }

    /** Decodes the receiver and the monitor profiles from the last snapshot; takes one if none exists. */
    public static DecodedTypeProfiles decodeProfiles() {
        if (snapshot == null) {
            takeSnapshot();
        }
        return new DecodedTypeProfiles(decodeReceiverProfiles(), decodeMonitorProfiles());
    }

    private static List<DecodedReceiverProfile> decodeReceiverProfiles() {
        Map<ContextKey, Map<String, Long>> aggregate = new HashMap<>();
        for (Map.Entry<Long, Long> entry : snapshot.entrySet()) {
            long key = entry.getKey();
            int siteIndex = (int) (key >>> 32) - 1;
            int typeId = (int) key;
            TypeProfileKey profileKey = KEYS.get(siteIndex);
            if (profileKey == null || profileKey.isMonitorKey()) {
                continue;
            }
            String descriptor = profileKey.receiverTypeDescriptor(typeId);
            if (descriptor == null) {
                descriptor = TYPE_DESCRIPTORS_BY_ID.get(typeId);
            }
            if (descriptor == null) {
                continue;
            }
            ContextKey context = new ContextKey(List.of(profileKey.context()));
            aggregate.computeIfAbsent(context, _ -> new HashMap<>()).merge(descriptor, entry.getValue(), Long::sum);
        }
        List<DecodedReceiverProfile> result = new ArrayList<>();
        aggregate.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(
                        new DecodedReceiverProfile(entry.getKey().frames.toArray(ProfileFrame[]::new), new TreeMap<>(entry.getValue()))));
        return result;
    }

    /** The types of the locked objects counted under the monitor key, with their counts. */
    private static Map<String, Long> decodeMonitorProfiles() {
        Map<String, Long> result = new TreeMap<>();
        for (Map.Entry<Long, Long> entry : snapshot.entrySet()) {
            long key = entry.getKey();
            int siteIndex = (int) (key >>> 32) - 1;
            int typeId = (int) key;
            TypeProfileKey profileKey = KEYS.get(siteIndex);
            if (profileKey == null || !profileKey.isMonitorKey()) {
                continue;
            }
            String descriptor = TYPE_DESCRIPTORS_BY_ID.get(typeId);
            if (descriptor != null) {
                result.merge(descriptor, entry.getValue(), Long::sum);
            }
        }
        return result;
    }

    public static Statistics statistics() {
        if (snapshot == null) {
            takeSnapshot();
        }
        int receiverSites = 0;
        for (TypeProfileKey profileKey : KEYS.values()) {
            if (profileKey != REACHABILITY_PLACEHOLDER && !profileKey.isMonitorKey()) {
                receiverSites++;
            }
        }
        long receiverEvents = 0;
        long monitorEvents = 0;
        long unknownReceivers = 0;
        long unknownMonitors = 0;
        for (Map.Entry<Long, Long> entry : snapshot.entrySet()) {
            long key = entry.getKey();
            int siteIndex = (int) (key >>> 32) - 1;
            int typeId = (int) key;
            TypeProfileKey profileKey = KEYS.get(siteIndex);
            if (profileKey == null) {
                continue;
            }
            long count = entry.getValue();
            if (profileKey.isMonitorKey()) {
                monitorEvents += count;
                if (!TYPE_DESCRIPTORS_BY_ID.containsKey(typeId)) {
                    unknownMonitors += count;
                }
            } else {
                receiverEvents += count;
                if (profileKey.receiverTypeDescriptor(typeId) == null && !TYPE_DESCRIPTORS_BY_ID.containsKey(typeId)) {
                    unknownReceivers += count;
                }
            }
        }
        return new Statistics(receiverSites, receiverEvents, monitorEvents, snapshotTotal, snapshotDropped, unknownReceivers, unknownMonitors);
    }

    public record DecodedTypeProfiles(List<DecodedReceiverProfile> receiverProfiles, Map<String, Long> monitorProfiles) {
        public DecodedTypeProfiles {
            receiverProfiles = List.copyOf(receiverProfiles);
            monitorProfiles = Map.copyOf(monitorProfiles);
        }
    }

    /** Receiver type counts of one calling context; {@code context} is innermost frame first. */
    public record DecodedReceiverProfile(ProfileFrame[] context, Map<String, Long> countsByTypeDescriptor) {
        public DecodedReceiverProfile {
            context = context.clone();
            countsByTypeDescriptor = Map.copyOf(countsByTypeDescriptor);
        }
    }

    /**
     * What the recorder saw: the numbered receiver sites, the receiver and monitor events, all
     * events, the events that were lost (no table had room for them, or no memory was left to copy
     * them for the dump), and the events whose type has no name in the image.
     */
    public record Statistics(int receiverSites, long receiverEvents, long monitorEvents, long events, long dropped, long unknownReceivers, long unknownMonitors) {
    }

    /** A calling context as a map key, ordered by {@link ProfileFrame#CONTEXT_LIST_ORDER}. */
    private record ContextKey(List<ProfileFrame> frames) implements Comparable<ContextKey> {
        @Override
        public int compareTo(ContextKey other) {
            return ProfileFrame.CONTEXT_LIST_ORDER.compare(frames, other.frames);
        }
    }
}
