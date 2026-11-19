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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.core.heap.UnknownPrimitiveField;
import com.oracle.svm.shared.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.internal.misc.Unsafe;

/**
 * Registry of the instrumented branch sites and storage of their counts.
 * <p>
 * The build numbers a site when compiled code first uses it and records it in the registry that
 * the writer reads. At run time every site has a true and a false count in each thread's private
 * block ({@link BranchProfileThreadCounters}) and in the shared totals, a global data area sized
 * once compilation has numbered every site. Exiting threads fold their blocks into the shared
 * totals; the dump adds the blocks of live threads.
 */
public final class BranchProfileRecorder {

    private static final int COUNTERS_PER_BRANCH = 2;
    /**
     * Most branch sites the counter memory can address. Compiled code and the shared counter area
     * use int byte offsets, and every site takes {@link #COUNTERS_PER_BRANCH} longs.
     */
    private static final int MAX_BRANCHES = Integer.MAX_VALUE / (COUNTERS_PER_BRANCH * Long.BYTES);
    private static final int TRUE_OFFSET = 0;
    private static final int FALSE_OFFSET = 1;

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    /** Counter memory is never aliased with Java heap memory, so base loads can be hoisted. A snippet that touches it must declare it private. */
    public static final LocationIdentity COUNTER_LOCATION = NamedLocationIdentity.mutable("PGOBranchCounters");

    /** Every numbered physical graph site, by counter index; the writer sums identical sites. */
    private static final ConcurrentMap<Integer, BranchProfileKey> KEYS = new ConcurrentHashMap<>();
    private static final AtomicInteger nextCounterIndex = new AtomicInteger();

    /**
     * Shared totals: zero-initialized global data sized after compilation. Exited threads fold their
     * private counts into it, and a thread whose private block could not be allocated counts here
     * directly.
     */
    private static final CGlobalData<Pointer> SHARED_COUNTS = CGlobalDataFactory.createBytes(
                    BranchProfileRecorder::sharedCountsBytes, "__svm_pgo_branch_counters");

    /** Number of long slots per counter block; final once compilation has registered all sites. */
    @UnknownPrimitiveField(availability = AfterCompilation.class) //
    private static int registeredSlots;

    /** Merged view (shared + live threads) that the writer reads; null outside a dump. */
    private static long[] snapshot;

    /** Whether the image counts branches; false with {@code -H:-PGOProfileBranches}. Set at build time. */
    private static boolean enabled;

    /**
     * Sites are created after analysis, during compilation. This placeholder, created when the class
     * is initialized at build time, lets the analysis see a numbered {@link BranchProfileKey}
     * with all its fields set and the registry write in {@link #register}; the writer and the
     * statistics leave it out.
     */
    private static final BranchProfileKey REACHABILITY_PLACEHOLDER = create(
                    new ProfileFrame[]{new ProfileFrame("Lcom/oracle/svm/core/pgo/BranchProfileRecorder;.__unused__()V", -1)}, -1, -1, "unused", 0L);

    private BranchProfileRecorder() {
    }

    /** Turns on branch counting; called with {@link BranchProfileThreadCounters} registered. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void enable() {
        enabled = true;
    }

    /**
     * Whether the image counts branches. Without it no thread has a counter block, so nothing may
     * read or increment the counts.
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /** Creates a key with its counter index right away, as if compiled code already used it. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static BranchProfileKey create(ProfileFrame[] context, int trueSuccessorBci, int falseSuccessorBci, String conditionKind, long conditionFingerprint) {
        BranchProfileKey key = createUnnumbered(context, trueSuccessorBci, falseSuccessorBci, conditionKind, conditionFingerprint);
        key.counterIndexForCode();
        return key;
    }

    /**
     * Creates the key of one branch. The key is numbered and registered
     * only when one of its counter nodes is lowered ({@link BranchProfileKey#counterIndexForCode()}).
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static BranchProfileKey createUnnumbered(ProfileFrame[] context, int trueSuccessorBci, int falseSuccessorBci, String conditionKind, long conditionFingerprint) {
        if (context.length == 0) {
            throw new IllegalArgumentException("PGO branch profiler: a branch site needs a calling context");
        }
        return new BranchProfileKey(context, trueSuccessorBci, falseSuccessorBci, conditionKind, conditionFingerprint, BranchProfileKey.NO_INDEX);
    }

    /** Gives a key the next counter index and adds it to the registry the writer reads. */
    @Platforms(Platform.HOSTED_ONLY.class)
    static int register(BranchProfileKey key) {
        int index = nextCounterIndex.getAndIncrement();
        if (index < 0 || index >= MAX_BRANCHES) {
            throw new IllegalStateException("PGO branch profiler: the image has more than " + MAX_BRANCHES + " branch sites, the most its counter memory can address");
        }
        KEYS.put(index, key);
        return index;
    }

    /** Keys numbered so far, that is, keys whose counters compiled code uses. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static int numberedSiteCount() {
        return KEYS.size() - 1; // without the reachability placeholder
    }

    /**
     * Called by generated code: increments the true count of site {@code counterIndex} when
     * {@code onTrueSuccessor} is set and its false count otherwise. Within one thread's private
     * block the increment is a plain load/add/store, so per-thread counts are exact; only the
     * exit-time merge needs atomics.
     * <p>
     * There is deliberately no null check: {@link BranchProfileThreadCounters} points the
     * thread-local at a private block (or at the shared counters if that allocation failed) when the
     * thread attaches, before it can execute any Java code. A null-check diamond at every site cost
     * 5.4% and a displacement add 4.2% on a single-worker training run.
     */
    public static void increment(int counterIndex, boolean onTrueSuccessor) {
        /*
         * This is the body of a snippet: no hosted branch, no bounds-checked array access, nothing
         * that could throw, or the snippet graph would carry an exception path.
         */
        int slot = counterIndex * COUNTERS_PER_BRANCH + (onTrueSuccessor ? TRUE_OFFSET : FALSE_OFFSET);
        Pointer block = BranchProfileThreadCounters.BLOCK.get();
        int offset = slot * Long.BYTES;
        block.writeLong(offset, block.readLong(offset, COUNTER_LOCATION) + 1, COUNTER_LOCATION);
    }

    /** Address of shared slot 0; a link-time constant. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static Pointer sharedCounts() {
        return SHARED_COUNTS.get();
    }

    /** Evaluated during image layout, after {@link #sealRegistry()}. */
    private static int sharedCountsBytes() {
        return Math.max(registeredSlots, COUNTERS_PER_BRANCH) * Long.BYTES;
    }

    /** Seals the site registry once compilation has created every counter. */
    public static void sealRegistry() {
        registeredSlots = nextCounterIndex.get() * COUNTERS_PER_BRANCH;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    static int registeredSlots() {
        return registeredSlots;
    }

    /** Folds an exiting thread's private block into the shared totals. */
    @Uninterruptible(reason = "Called after Thread.exit; touches only global and native memory.")
    static void mergeIntoShared(Pointer block) {
        int slots = registeredSlots;
        Pointer shared = sharedCounts();
        for (int slot = 0; slot < slots; slot++) {
            long value = block.readLong(slot * Long.BYTES);
            if (value != 0) {
                UNSAFE.getAndAddLong(null, shared.add(slot * Long.BYTES).rawValue(), value);
            }
        }
    }

    /**
     * Takes the snapshot that the counts of {@link #getKeys()} read for the dump: the shared
     * totals plus the private blocks of the live threads, read at a safepoint.
     */
    static void prepareSnapshot() {
        /*
         * Increments the placeholder site, which the writer leaves out, so that the analysis sees
         * the counter update path even in an image without any instrumented branch.
         */
        increment(REACHABILITY_PLACEHOLDER.getCounterIndex(), true);
        increment(REACHABILITY_PLACEHOLDER.getCounterIndex(), false);
        takeSnapshot();
    }

    private static void takeSnapshot() {
        int slots = registeredSlots;
        long[] merged = new long[slots];
        Pointer shared = sharedCounts();
        for (int slot = 0; slot < slots; slot++) {
            merged[slot] = shared.readLong(slot * Long.BYTES);
        }
        BranchProfileThreadCounters.collectLiveThreads(merged);
        snapshot = merged;
    }

    private static long[] readableCounts() {
        if (snapshot == null) {
            takeSnapshot();
        }
        return snapshot;
    }

    static long getTrueCount(int counterIndex) {
        return readableCounts()[counterIndex * COUNTERS_PER_BRANCH + TRUE_OFFSET];
    }

    static long getFalseCount(int counterIndex) {
        return readableCounts()[counterIndex * COUNTERS_PER_BRANCH + FALSE_OFFSET];
    }

    /** The numbered keys in a deterministic order, for the writer. */
    public static List<BranchProfileKey> getKeys() {
        List<BranchProfileKey> result = new ArrayList<>();
        for (BranchProfileKey key : KEYS.values()) {
            if (key != REACHABILITY_PLACEHOLDER) {
                result.add(key);
            }
        }
        result.sort(COUNTER_COMPARATOR);
        return result;
    }

    private static final Comparator<BranchProfileKey> COUNTER_COMPARATOR = (left, right) -> {
        int result = compareContexts(left.getContext(), right.getContext());
        if (result == 0) {
            result = Integer.compare(left.getTrueSuccessorBci(), right.getTrueSuccessorBci());
        }
        if (result == 0) {
            result = Integer.compare(left.getFalseSuccessorBci(), right.getFalseSuccessorBci());
        }
        if (result == 0) {
            result = left.getConditionKind().compareTo(right.getConditionKind());
        }
        if (result == 0) {
            result = Long.compareUnsigned(left.getConditionFingerprint(), right.getConditionFingerprint());
        }
        if (result == 0) {
            result = Integer.compare(left.getCounterIndex(), right.getCounterIndex());
        }
        return result;
    };

    /** Compares the method descriptors frame by frame, then the bytecode indexes. */
    private static int compareContexts(ProfileFrame[] left, ProfileFrame[] right) {
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int result = left[i].methodDescriptor().compareTo(right[i].methodDescriptor());
            if (result != 0) {
                return result;
            }
        }
        int result = Integer.compare(left.length, right.length);
        for (int i = 0; result == 0 && i < length; i++) {
            result = Integer.compare(left[i].bci(), right[i].bci());
        }
        return result;
    }
}
