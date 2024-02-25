/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.collections.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.collections.UninterruptibleEntry;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.sampler.JfrExecutionSampler;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jfr.utils.JfrVisited;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.sampler.SamplerSampleWriter;
import com.oracle.svm.core.sampler.SamplerSampleWriterData;
import com.oracle.svm.core.sampler.SamplerSampleWriterDataAccess;
import com.oracle.svm.core.sampler.SamplerStackWalkVisitor;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;

/**
 * Repository that collects all metadata about stacktraces.
 */
public class JfrStackTraceRepository implements JfrRepository {
    private static final int DEFAULT_STACK_DEPTH = 64;
    private static final int MIN_STACK_DEPTH = 1;
    private static final int MAX_STACK_DEPTH = 2048;

    private final VMMutex mutex;
    private final JfrStackTraceEpochData epochData0;
    private final JfrStackTraceEpochData epochData1;

    private int stackTraceDepth;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrStackTraceRepository() {
        this.mutex = new VMMutex("jfrStackTraceRepository");
        this.epochData0 = new JfrStackTraceEpochData();
        this.epochData1 = new JfrStackTraceEpochData();
        this.stackTraceDepth = DEFAULT_STACK_DEPTH;
    }

    public void setStackTraceDepth(int value) {
        stackTraceDepth = UninterruptibleUtils.Math.clamp(value, MIN_STACK_DEPTH, MAX_STACK_DEPTH);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getStackTraceDepth() {
        return stackTraceDepth;
    }

    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @NeverInline("Starting a stack walk in the caller frame.")
    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    public long getStackTraceId(int skipCount) {
        if (DeoptimizationSupport.enabled()) {
            /* Stack traces are not supported if JIT compilation is used (GR-43686). */
            return 0;
        }

        /*
         * JFR stack traces use the same thread-local buffers as the execution sampler. So, we need
         * to prevent the sampler from modifying the buffer, while it is used by the code below.
         */
        JfrExecutionSampler.singleton().preventSamplingInCurrentThread();
        try {
            /* Try to walk the stack. */
            SamplerSampleWriterData data = StackValue.get(SamplerSampleWriterData.class);
            if (SamplerSampleWriterDataAccess.initialize(data, skipCount, true)) {
                JfrThreadLocal.setSamplerWriterData(data);
                try {
                    SamplerSampleWriter.begin(data);
                    Pointer sp = KnownIntrinsics.readCallerStackPointer();
                    CodePointer ip = FrameAccess.singleton().readReturnAddress(sp);
                    SamplerStackWalkVisitor visitor = ImageSingletons.lookup(SamplerStackWalkVisitor.class);
                    if (JavaStackWalker.walkCurrentThread(sp, ip, visitor) || data.getTruncated()) {
                        return storeDeduplicatedStackTrace(data);
                    }
                } finally {
                    JfrThreadLocal.setSamplerWriterData(WordFactory.nullPointer());
                }
            }
            return 0L;
        } finally {
            JfrExecutionSampler.singleton().allowSamplingInCurrentThread();
        }
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    private long storeDeduplicatedStackTrace(SamplerSampleWriterData data) {
        if (SamplerSampleWriter.isValid(data)) {
            /* There is a valid stack trace in the buffer, so deduplicate and store it. */
            Pointer start = data.getStartPos().add(SamplerSampleWriter.getHeaderSize());
            UnsignedWord size = data.getCurrentPos().subtract(start);

            CIntPointer statusPtr = StackValue.get(CIntPointer.class);
            JfrStackTraceTableEntry epochSpecificEntry = getOrPutStackTrace(start, size, data.getHashCode(), statusPtr);
            if (epochSpecificEntry.isNonNull()) {
                /* Only commit the data in the thread-local buffer if it is new data. */
                if (statusPtr.read() == JfrStackTraceTableEntryStatus.INSERTED) {
                    boolean success = SamplerSampleWriter.end(data, SamplerSampleWriter.JFR_STACK_TRACE_END);
                    assert success : "must succeed because data was valid earlier";
                }
                return epochSpecificEntry.getId();
            }
        }
        return 0L;
    }

    /**
     * If the same stack trace already exists in the repository, then this method returns the
     * matching {@link JfrStackTraceTableEntry entry}. Otherwise, it tries to add the stack trace to
     * the repository. If this is successful, then the newly added entry is returned. Otherwise,
     * null is returned.
     *
     * NOTE: the returned value is only valid until the JFR epoch changes. So, this method may only
     * be used from uninterruptible code.
     */
    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    public JfrStackTraceTableEntry getOrPutStackTrace(Pointer start, UnsignedWord size, int hashCode, CIntPointer statusPtr) {
        mutex.lockNoTransition();
        try {
            return getOrPutStackTrace0(start, size, hashCode, statusPtr);
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    private JfrStackTraceTableEntry getOrPutStackTrace0(Pointer start, UnsignedWord size, int hashCode, CIntPointer statusPtr) {
        assert size.rawValue() == (int) size.rawValue();

        JfrStackTraceTableEntry entry = StackValue.get(JfrStackTraceTableEntry.class);
        entry.setHash(hashCode);
        entry.setSize((int) size.rawValue());
        entry.setRawStackTrace(start);
        entry.setSerialized(false);

        JfrStackTraceEpochData epochData = getEpochData(false);
        JfrStackTraceTableEntry result = (JfrStackTraceTableEntry) epochData.table.get(entry);
        if (result.isNonNull()) {
            /* There is an existing stack trace. */
            int status = result.getSerialized() ? JfrStackTraceTableEntryStatus.EXISTING_SERIALIZED : JfrStackTraceTableEntryStatus.EXISTING_RAW;
            statusPtr.write(status);
            return result;
        } else {
            /*
             * Insert a new entry into the hashtable. We need to copy the raw stacktrace data from
             * the thread-local buffer to the C heap because the thread-local buffer will be
             * overwritten or freed at some point.
             */
            Pointer to = NullableNativeMemory.malloc(size, NmtCategory.JFR);
            if (to.isNonNull()) {
                UnmanagedMemoryUtil.copy(start, to, size);
                entry.setRawStackTrace(to);

                JfrStackTraceTableEntry newEntry = (JfrStackTraceTableEntry) epochData.table.getOrPut(entry);
                if (newEntry.isNonNull()) {
                    statusPtr.write(JfrStackTraceTableEntryStatus.INSERTED);
                    return newEntry;
                }

                /* Hashtable entry allocation failed. */
                NullableNativeMemory.free(to);
                to = WordFactory.nullPointer();
            }

            /* Some allocation failed. */
            statusPtr.write(JfrStackTraceTableEntryStatus.INSERT_FAILED);
            return WordFactory.nullPointer();
        }
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    public void commitSerializedStackTrace(JfrStackTraceTableEntry entry) {
        mutex.lockNoTransition();
        try {
            entry.setSerialized(true);
            getEpochData(false).unflushedEntries++;
        } finally {
            mutex.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        if (flushpoint) {
            /*
             * Flushing is not support for stack traces at the moment. When a stack trace is
             * serialized, the methods getOrPutStackTrace() and commitSerializedStackTrace() are
             * used. The lock is not held all the time, so a flushpoint could destroy the JfrBuffer
             * of the epoch, while it is being written.
             */
            return EMPTY;
        }

        mutex.lockNoTransition();
        try {
            JfrStackTraceEpochData epochData = getEpochData(!flushpoint);
            int count = epochData.unflushedEntries;
            if (count != 0) {
                writer.writeCompressedLong(JfrType.StackTrace.getId());
                writer.writeCompressedInt(count);
                writer.write(epochData.buffer);
            }
            epochData.clear(flushpoint);
            return count == 0 ? EMPTY : NON_EMPTY;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    private JfrStackTraceEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    /** Returns null if the buffer allocation failed. */
    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    public JfrBuffer getCurrentBuffer() {
        JfrStackTraceEpochData epochData = getEpochData(false);
        if (epochData.buffer.isNull()) {
            epochData.buffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
        }
        return epochData.buffer;
    }

    @Uninterruptible(reason = "Prevent epoch change.", callerMustBe = true)
    public void setCurrentBuffer(JfrBuffer value) {
        getEpochData(false).buffer = value;
    }

    /**
     * Each entry contains raw stack trace data (i.e., a sequence of instruction pointers, without
     * any metadata).
     */
    @RawStructure
    public interface JfrStackTraceTableEntry extends JfrVisited {
        @RawField
        Pointer getRawStackTrace();

        @RawField
        void setRawStackTrace(Pointer pointer);

        @RawField
        int getSize();

        @RawField
        void setSize(int size);

        @RawField
        boolean getSerialized();

        @RawField
        void setSerialized(boolean serialized);
    }

    public static final class JfrStackTraceTable extends AbstractUninterruptibleHashtable {
        private static long nextId;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrStackTraceTable() {
            super(NmtCategory.JFR);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected JfrStackTraceTableEntry[] createTable(int size) {
            return new JfrStackTraceTableEntry[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry a, UninterruptibleEntry b) {
            JfrStackTraceTableEntry entry1 = (JfrStackTraceTableEntry) a;
            JfrStackTraceTableEntry entry2 = (JfrStackTraceTableEntry) b;
            /* We explicitly ignore the field 'serialized' because its value can change. */
            return entry1.getSize() == entry2.getSize() && LibC.memcmp(entry1.getRawStackTrace(), entry2.getRawStackTrace(), WordFactory.unsigned(entry1.getSize())) == 0;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            JfrStackTraceTableEntry result = (JfrStackTraceTableEntry) copyToHeap(valueOnStack, SizeOf.unsigned(JfrStackTraceTableEntry.class));
            if (result.isNonNull()) {
                result.setId(++nextId);
            }
            return result;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected void free(UninterruptibleEntry entry) {
            JfrStackTraceTableEntry stackTraceEntry = (JfrStackTraceTableEntry) entry;
            /* The base method will free only the entry itself, not the pointer with stacktrace. */
            NullableNativeMemory.free(stackTraceEntry.getRawStackTrace());
            super.free(entry);
        }
    }

    public static class JfrStackTraceTableEntryStatus {
        /* There was no existing entry in the hashtable, so a new entry was inserted. */
        public static final int INSERTED = 1;
        /* There was an existing entry for a raw stack trace in the hashtable. */
        public static final int EXISTING_RAW = INSERTED << 1;
        /* There was an existing entry for a serialized stack trace in the hashtable. */
        public static final int EXISTING_SERIALIZED = EXISTING_RAW << 1;
        /* Some error occurred while trying to insert a new entry into the hashtable. */
        public static final int INSERT_FAILED = EXISTING_SERIALIZED << 1;
    }

    private static class JfrStackTraceEpochData {
        private final JfrStackTraceTable table;
        private int unflushedEntries;
        private JfrBuffer buffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrStackTraceEpochData() {
            this.table = new JfrStackTraceTable();
            this.unflushedEntries = 0;
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear(boolean flushpoint) {
            if (!flushpoint) {
                table.clear();
            }
            unflushedEntries = 0;
            JfrBufferAccess.reinitialize(buffer);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            table.teardown();
            unflushedEntries = 0;
            JfrBufferAccess.free(buffer);
            buffer = WordFactory.nullPointer();
        }
    }
}
