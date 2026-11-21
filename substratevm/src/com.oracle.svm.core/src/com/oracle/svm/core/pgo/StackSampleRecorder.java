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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.Threading;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.CodeInfoDecoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ParameterizedStackFrameVisitor;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.core.code.CodeInfo;
import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.guest.staging.core.thread.ThreadListener;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.options.Option;
import jdk.internal.misc.Unsafe;

/**
 * Periodic call-stack sampler of a {@code --pgo-instrument} image.
 * <p>
 * Every Java thread registers a recurring callback that walks its own stack and records the return
 * addresses. Recurring callbacks must not allocate on the Java heap, so each thread aggregates its
 * raw address chains in a private native hash table (open addressing, an address arena, sample
 * counts); tables are linked into a global list when created and read at dump time, when addresses
 * are decoded into method/BCI frames including inlined frames. A table is never freed: when its
 * thread exits, it waits in a free list for the next thread that starts, which records into it
 * after the samples it already holds. So the tables take as much memory as the most threads that
 * ran at one time needed.
 * <p>
 * Because the samples come from the very image that will be profiled, every frame resolves against
 * the same methods, unlike stacks recorded by another build.
 */
public final class StackSampleRecorder implements ThreadListener {

    public static final class Options {
        @Option(help = "Sampling interval in milliseconds for call-stack samples in a --pgo-instrument image. 0 disables sampling.")//
        public static final RuntimeOptionKey<Long> PGOSamplingIntervalMillis = new RuntimeOptionKey<>(10L);
    }

    /** Return addresses recorded per sample; deeper frames are truncated (outermost dropped). */
    private static final int MAX_FRAMES = 64;

    /*
     * Per-thread table layout (native memory, zero-initialized):
     *   header: nextTable(8) samples(8) dropped(8) truncated(8) arenaUsed(8) nextFreeTable(8)
     *   buckets[BUCKETS]: hash(4) length(4) count(8) arenaOffset(8)   = 24 bytes each
     *   arena[ARENA_LONGS]: recorded addresses
     */
    private static final int HEADER_BYTES = 48;
    private static final int NEXT_OFFSET = 0;
    private static final int SAMPLES_OFFSET = 8;
    private static final int DROPPED_OFFSET = 16;
    private static final int TRUNCATED_OFFSET = 24;
    private static final int ARENA_USED_OFFSET = 32;
    /** Link of the free list ({@link #FREE_TABLES}); the list of all tables uses {@link #NEXT_OFFSET}. */
    private static final int NEXT_FREE_OFFSET = 40;
    private static final int BUCKETS = 8192;
    private static final int BUCKET_BYTES = 24;
    private static final int BUCKET_HASH = 0;
    private static final int BUCKET_LENGTH = 4;
    private static final int BUCKET_COUNT = 8;
    private static final int BUCKET_ARENA = 16;
    private static final int ARENA_LONGS = 65536;
    private static final int TABLE_BYTES = HEADER_BYTES + BUCKETS * BUCKET_BYTES + ARENA_LONGS * Long.BYTES;
    /* Scratch for the current walk, right after the arena: addresses [0, MAX_FRAMES), truncated flag, length. */
    private static final int SCRATCH_OFFSET = TABLE_BYTES;
    private static final int SCRATCH_TRUNCATED = SCRATCH_OFFSET + MAX_FRAMES * Long.BYTES;
    private static final int SCRATCH_LENGTH = SCRATCH_TRUNCATED + Long.BYTES;
    private static final int ALLOCATION_BYTES = SCRATCH_LENGTH + Long.BYTES;

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final FastThreadLocalWord<Pointer> TABLE = FastThreadLocalFactory.createWord("StackSampleRecorder.table");
    /** Head of the list of every table ever created; tables are never freed. */
    private static final CGlobalData<Pointer> TABLES = CGlobalDataFactory.createWord();
    /**
     * Head of the list of the tables of exited threads, which a starting thread takes before it
     * allocates one. Changed only under {@link #FREE_LOCK}, which only uninterruptible code takes.
     */
    private static final CGlobalData<Pointer> FREE_TABLES = CGlobalDataFactory.createWord();
    private static final CGlobalData<Pointer> FREE_LOCK = CGlobalDataFactory.createWord();
    /** Threads that got a table; a table serves one thread after another. */
    private static final CGlobalData<Pointer> SAMPLED_THREADS = CGlobalDataFactory.createWord();
    /** Set when the dump starts; from then on no thread records a sample, see {@link #stopSampling}. */
    private static volatile boolean stopped;

    private static final RecordingVisitor VISITOR = new RecordingVisitor();
    private static final Threading.RecurringCallback CALLBACK = new SampleCallback();

    /** Set at image build time when the sampler is registered; frame metadata must then carry signatures. */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private static boolean requiresMethodSignatures;

    private StackSampleRecorder() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static StackSampleRecorder create() {
        requiresMethodSignatures = true;
        return new StackSampleRecorder();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean requiresMethodSignatures() {
        return requiresMethodSignatures;
    }

    @Override
    public void beforeThreadRun() {
        long interval = Options.PGOSamplingIntervalMillis.getValue();
        if (interval <= 0 || TABLE.get().isNonNull()) {
            return;
        }
        Pointer table = takeFreeTable();
        if (table.isNull()) {
            table = UntrackedNullableNativeMemory.calloc(Word.unsigned(ALLOCATION_BYTES));
            if (table.isNull()) {
                return;
            }
            Pointer head = TABLES.get();
            Pointer expected;
            do {
                expected = head.readWord(0);
                table.writeWord(NEXT_OFFSET, expected);
            } while (!head.logicCompareAndSwapWord(0, expected, table, LocationIdentity.ANY_LOCATION));
        }
        TABLE.set(table);
        UNSAFE.getAndAddLong(null, SAMPLED_THREADS.get().rawValue(), 1);
        if (RecurringCallbackSupport.isEnabled()) {
            /*
             * A background callback, so that a JFR execution sampler that uses recurring callbacks
             * can take over the thread's only callback slot while it records; sampling resumes when
             * it stops.
             */
            RecurringCallbackSupport.installBackgroundCallback(TimeUnit.MILLISECONDS.toNanos(interval), CALLBACK);
        }
    }

    @Override
    @Uninterruptible(reason = "Only uninterruptible code may be executed after Thread.exit.")
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        if (RecurringCallbackSupport.isEnabled()) {
            RecurringCallbackSupport.uninstallBackgroundCallback(isolateThread);
        }
        Pointer table = TABLE.get(isolateThread);
        if (table.isNonNull()) {
            /* Cleared first, so that the table has one owner at a time and is freed into the list once. */
            TABLE.set(isolateThread, Word.nullPointer());
            returnFreeTable(table);
        }
    }

    /** A table from the free list, or null if it is empty. */
    @Uninterruptible(reason = "Holds the free-list lock, which other threads take in uninterruptible code.")
    private static Pointer takeFreeTable() {
        lockFreeTables();
        Pointer table = FREE_TABLES.get().readWord(0);
        if (table.isNonNull()) {
            FREE_TABLES.get().writeWord(0, table.readWord(NEXT_FREE_OFFSET));
            table.writeWord(NEXT_FREE_OFFSET, Word.nullPointer());
        }
        unlockFreeTables();
        return table;
    }

    @Uninterruptible(reason = "Holds the free-list lock, which other threads take in uninterruptible code.")
    private static void returnFreeTable(Pointer table) {
        lockFreeTables();
        table.writeWord(NEXT_FREE_OFFSET, FREE_TABLES.get().readWord(0));
        FREE_TABLES.get().writeWord(0, table);
        unlockFreeTables();
    }

    /**
     * A spin lock rather than a lock-free list: a thread that read the head and its link could
     * otherwise install a stale link after other threads took that head and gave it back.
     */
    @Uninterruptible(reason = "Native spin lock of the free list.")
    private static void lockFreeTables() {
        Pointer lock = FREE_LOCK.get();
        while (!UNSAFE.compareAndSetInt(null, lock.rawValue(), 0, 1)) {
            // Held for a few instructions only.
        }
    }

    @Uninterruptible(reason = "Native spin lock of the free list.")
    private static void unlockFreeTables() {
        UNSAFE.putIntRelease(null, FREE_LOCK.get().rawValue(), 0);
    }

    private static final class SampleCallback implements Threading.RecurringCallback {
        @Override
        public void run(Threading.RecurringCallbackAccess access) {
            Pointer table = TABLE.get();
            if (table.isNull() || stopped) {
                return;
            }
            walk(table);
            record(table);
        }

        /** Walks from this method's caller; runtime frames above the application are stripped at dump time. */
        @NeverInline("Starting a stack walk in the caller frame")
        @Uninterruptible(reason = "Stack walk must not be interrupted.")
        private static void walk(Pointer table) {
            table.writeLong(SCRATCH_TRUNCATED, 0);
            table.writeLong(SCRATCH_LENGTH, 0);
            Pointer sp = KnownIntrinsics.readCallerStackPointer();
            CodePointer ip = KnownIntrinsics.readReturnAddress();
            JavaStackWalker.walkCurrentThread(sp, Word.nullPointer(), ip, VISITOR, null);
        }

        /**
         * Open-addressed insert of the scratch stack into the thread's table. Allocation-free, and
         * uninterruptible so that a sample that starts before {@link #stopSampling} sets
         * {@link #stopped} is complete when its safepoint is reached.
         */
        @Uninterruptible(reason = "Must not reach a safepoint between the check of stopped and the last write to the table.")
        private static void record(Pointer table) {
            int length = (int) table.readLong(SCRATCH_LENGTH);
            if (length <= 0 || stopped) {
                return;
            }
            table.writeLong(SAMPLES_OFFSET, table.readLong(SAMPLES_OFFSET) + 1);
            if (table.readLong(SCRATCH_TRUNCATED) != 0) {
                table.writeLong(TRUNCATED_OFFSET, table.readLong(TRUNCATED_OFFSET) + 1);
            }
            int hash = 1;
            for (int i = 0; i < length; i++) {
                long address = table.readLong(SCRATCH_OFFSET + i * Long.BYTES);
                hash = 31 * hash + (int) (address ^ (address >>> 32));
            }
            int index = hash & (BUCKETS - 1);
            for (int probe = 0; probe < BUCKETS; probe++) {
                int bucket = HEADER_BYTES + index * BUCKET_BYTES;
                int bucketLength = table.readInt(bucket + BUCKET_LENGTH);
                if (bucketLength == 0) {
                    long arenaUsed = table.readLong(ARENA_USED_OFFSET);
                    if (arenaUsed + length > ARENA_LONGS) {
                        table.writeLong(DROPPED_OFFSET, table.readLong(DROPPED_OFFSET) + 1);
                        return;
                    }
                    int arenaBase = HEADER_BYTES + BUCKETS * BUCKET_BYTES;
                    for (int i = 0; i < length; i++) {
                        table.writeLong(arenaBase + (int) (arenaUsed + i) * Long.BYTES, table.readLong(SCRATCH_OFFSET + i * Long.BYTES));
                    }
                    table.writeLong(ARENA_USED_OFFSET, arenaUsed + length);
                    table.writeInt(bucket + BUCKET_HASH, hash);
                    table.writeInt(bucket + BUCKET_LENGTH, length);
                    table.writeLong(bucket + BUCKET_ARENA, arenaUsed);
                    table.writeLong(bucket + BUCKET_COUNT, 1);
                    return;
                }
                if (bucketLength == length && table.readInt(bucket + BUCKET_HASH) == hash && sameAddresses(table, bucket, length)) {
                    table.writeLong(bucket + BUCKET_COUNT, table.readLong(bucket + BUCKET_COUNT) + 1);
                    return;
                }
                index = (index + 1) & (BUCKETS - 1);
            }
            table.writeLong(DROPPED_OFFSET, table.readLong(DROPPED_OFFSET) + 1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static boolean sameAddresses(Pointer table, int bucket, int length) {
            int arenaBase = HEADER_BYTES + BUCKETS * BUCKET_BYTES + (int) table.readLong(bucket + BUCKET_ARENA) * Long.BYTES;
            for (int i = 0; i < length; i++) {
                if (table.readLong(arenaBase + i * Long.BYTES) != table.readLong(SCRATCH_OFFSET + i * Long.BYTES)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class RecordingVisitor extends ParameterizedStackFrameVisitor {
        @Override
        @Uninterruptible(reason = "Called during stack walk.")
        protected boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, Object data) {
            /* Word values cannot travel through the Object parameter; the table is thread-local anyway. */
            Pointer table = TABLE.get();
            int length = (int) table.readLong(SCRATCH_LENGTH);
            if (length >= MAX_FRAMES) {
                table.writeLong(SCRATCH_TRUNCATED, 1);
                return false;
            }
            table.writeLong(SCRATCH_OFFSET + length * Long.BYTES, ip.rawValue());
            table.writeLong(SCRATCH_LENGTH, length + 1);
            return true;
        }

        @Override
        @Uninterruptible(reason = "Called during stack walk.")
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame, Object data) {
            return true;
        }

        @Override
        @Uninterruptible(reason = "Called during stack walk.")
        protected boolean unknownFrame(Pointer sp, CodePointer ip, Object data) {
            return false;
        }
    }

    // --- dump-time decoding -----------------------------------------------------------------

    /** A decoded sample: its calling context, innermost frame first, and how often it was seen. */
    public record DecodedSample(ProfileFrame[] context, long count) {
    }

    /** What the sampler saw: {@code samples} counts the dropped and the truncated samples too. */
    public record Statistics(long samples, long dropped, long truncated, long threads, long unresolvedAddresses, int decodedStacks) {
    }

    private static long unresolvedAddresses;

    /**
     * Stops sampling in every thread; the tables keep the samples they hold. The profile dump
     * calls this first, so that no thread writes a table while the dump reads it and the dump
     * does not sample its own work. A thread that is recording a sample when {@link #stopped} is
     * set finishes it before it reaches the safepoint of {@link StopSamplingOperation}, and a
     * sample that starts later sees {@link #stopped}.
     */
    public static void stopSampling() {
        stopped = true;
        Pointer firstTable = TABLES.get().readWord(0);
        if (firstTable.isNonNull()) {
            new StopSamplingOperation().enqueue();
        }
    }

    /** The current thread's table, for {@code RecorderTableNativeTest}. */
    public static Pointer currentTableForTesting() {
        return TABLE.get();
    }

    private static final class StopSamplingOperation extends JavaVMOperation {
        StopSamplingOperation() {
            super(VMOperationInfos.get(StopSamplingOperation.class, "Stop PGO stack sampling", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            /* Reaching the safepoint is all it takes. */
        }
    }

    public static boolean hasSamples() {
        for (Pointer table = TABLES.get().readWord(0); table.isNonNull(); table = table.readWord(NEXT_OFFSET)) {
            if (table.readLong(SAMPLES_OFFSET) > 0) {
                return true;
            }
        }
        return false;
    }

    public static Statistics statistics(int decodedStacks) {
        long samples = 0;
        long dropped = 0;
        long truncated = 0;
        for (Pointer table = TABLES.get().readWord(0); table.isNonNull(); table = table.readWord(NEXT_OFFSET)) {
            samples += table.readLong(SAMPLES_OFFSET);
            dropped += table.readLong(DROPPED_OFFSET);
            truncated += table.readLong(TRUNCATED_OFFSET);
        }
        return new Statistics(samples, dropped, truncated, SAMPLED_THREADS.get().readLong(0), unresolvedAddresses, decodedStacks);
    }

    /**
     * Decodes every recorded address chain into method/BCI frames. Frames of the Native Image
     * runtime on top of the stack (the safepoint slow path that ran the sampler) are dropped, and
     * identical decoded stacks from all threads are merged.
     */
    public static List<DecodedSample> decodeSamples() {
        Map<List<ProfileFrame>, long[]> merged = new HashMap<>();
        CodeInfoDecoder.FrameInfoCursor cursor = new CodeInfoDecoder.FrameInfoCursor();
        for (Pointer table = TABLES.get().readWord(0); table.isNonNull(); table = table.readWord(NEXT_OFFSET)) {
            int arenaBase = HEADER_BYTES + BUCKETS * BUCKET_BYTES;
            for (int b = 0; b < BUCKETS; b++) {
                int bucket = HEADER_BYTES + b * BUCKET_BYTES;
                int length = table.readInt(bucket + BUCKET_LENGTH);
                if (length == 0) {
                    continue;
                }
                long count = table.readLong(bucket + BUCKET_COUNT);
                int arena = arenaBase + (int) table.readLong(bucket + BUCKET_ARENA) * Long.BYTES;
                List<ProfileFrame> key = decode(cursor, table, arena, length);
                if (key != null) {
                    merged.computeIfAbsent(key, _ -> new long[1])[0] += count;
                }
            }
        }
        List<DecodedSample> result = new ArrayList<>(merged.size());
        merged.forEach((key, count) -> result.add(new DecodedSample(key.toArray(ProfileFrame[]::new), count[0])));
        result.sort((a, b) -> {
            int c = Long.compare(b.count(), a.count());
            return c != 0 ? c : ProfileFrame.CONTEXT_ORDER.compare(a.context(), b.context());
        });
        return result;
    }

    /** The frames of one recorded address chain, innermost first; null if nothing is left. */
    private static List<ProfileFrame> decode(CodeInfoDecoder.FrameInfoCursor cursor, Pointer table, int arena, int length) {
        List<ProfileFrame> frames = new ArrayList<>();
        boolean seenApplicationFrame = false;
        for (int i = 0; i < length; i++) {
            CodePointer ip = Word.pointer(table.readLong(arena + i * Long.BYTES));
            CodeInfo info = CodeInfoTable.lookupImageCodeInfo(ip);
            if (info.isNull()) {
                unresolvedAddresses++;
                break;
            }
            cursor.initialize(info, ip, false);
            while (cursor.advance()) {
                FrameInfoQueryResult frame = cursor.get();
                if (frame.getSourceClass() == null || frame.isNativeMethod()) {
                    continue;
                }
                String className = frame.getSourceClass().getName();
                if (!seenApplicationFrame && isNativeImageRuntime(className)) {
                    continue;
                }
                seenApplicationFrame = true;
                frames.add(new ProfileFrame(methodDescriptor(className, frame.getSourceMethodName(), frame.getSourceMethodSignature()), frame.getBci()));
            }
        }
        return frames.isEmpty() ? null : frames;
    }

    private static boolean isNativeImageRuntime(String className) {
        return className.startsWith("com.oracle.svm.") || className.startsWith("org.graalvm.nativeimage.") || className.startsWith("org.graalvm.word.");
    }

    /**
     * {@code Lpkg/Cls;.name(params)ret}: the same canonical form the hosted side derives from a
     * resolved method. Package dots become slashes; the slash inside a hidden class name (e.g.
     * {@code Foo$$Lambda/0x1234}) becomes a dot, as JVMCI renders it in type descriptors.
     */
    static String methodDescriptor(String className, String methodName, String signature) {
        StringBuilder result = new StringBuilder(className.length() + methodName.length() + signature.length() + 4);
        boolean array = className.startsWith("[");
        if (!array) {
            result.append('L');
        }
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            result.append(c == '.' ? '/' : c == '/' ? '.' : c);
        }
        if (!array) {
            result.append(';');
        }
        return result.append('.').append(methodName).append(signature).toString();
    }
}
