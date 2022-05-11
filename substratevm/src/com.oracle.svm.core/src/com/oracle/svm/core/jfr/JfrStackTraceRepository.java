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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.jdk.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jfr.utils.JfrVisited;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.sampler.SamplerBuffer;
import com.oracle.svm.core.sampler.SamplerBufferAccess;

/**
 * Repository that collects all metadata about stacktraces.
 */
public class JfrStackTraceRepository implements JfrConstantPool {
    private int maxDepth = SubstrateOptions.MaxJavaStackTraceDepth.getValue();

    private final VMMutex mutex;
    private final JfrStackTraceEpochData epochData0;
    private final JfrStackTraceEpochData epochData1;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrStackTraceRepository() {
        this.epochData0 = new JfrStackTraceEpochData();
        this.epochData1 = new JfrStackTraceEpochData();
        this.mutex = new VMMutex("jfrStackTraceRepository");
    }

    public void setStackTraceDepth(int depth) {
        if (depth < 0 || depth > SubstrateOptions.MaxJavaStackTraceDepth.getValue()) {
            throw new IllegalArgumentException("StackTrace depth (" + depth + ") is not in a valid range!");
        }
        this.maxDepth = depth;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void acquireLock() {
        mutex.lockNoTransition();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void releaseLock() {
        mutex.unlock();
    }

    @Uninterruptible(reason = "Releasing repository buffers.")
    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public long getStackTraceId(@SuppressWarnings("unused") int skipCount) {
        assert maxDepth >= 0;
        return 0;
    }

    @Uninterruptible(reason = "All code that accesses a sampler buffer must be uninterruptible.")
    public long getStackTraceId(Pointer start, Pointer end, int hashCode, CIntPointer isRecorded) {
        JfrStackTraceEpochData epochData = getEpochData(false);
        JfrStackTraceTableEntry entry = StackValue.get(JfrStackTraceTableEntry.class);

        UnsignedWord size = end.subtract(start);
        entry.setHash(hashCode);
        entry.setSize((int) size.rawValue());
        /* Do not copy stacktrace into new entry unless it is necessary. */
        entry.setStackTrace(start);

        JfrStackTraceTableEntry result = (JfrStackTraceTableEntry) epochData.visitedStackTraces.get(entry);
        if (result.isNonNull()) {
            isRecorded.write(1);
            return result.getId();
        } else {
            /* Replace the previous pointer with new one (entry size and hash remains the same). */
            SamplerBuffer buffer = SamplerBufferAccess.allocate(size);
            Pointer to = SamplerBufferAccess.getDataStart(buffer);
            entry.setStackTrace(to);
            /* Copy the stacktrace into separate native memory entry in hashtable. */
            UnmanagedMemoryUtil.copy(start, to, size);

            JfrStackTraceTableEntry newEntry = (JfrStackTraceTableEntry) epochData.visitedStackTraces.getOrPut(entry);
            isRecorded.write(0);
            return newEntry.getId();
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public void serializeStackTraceHeader(long stackTraceId, boolean isTruncated, int stackTraceLength) {
        JfrStackTraceEpochData epochData = getEpochData(false);
        if (epochData.stackTraceBuffer.isNull()) {
            // This will happen only on the first call.
            epochData.stackTraceBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
        }

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.stackTraceBuffer);

        /* JFR Stacktrace id. */
        JfrNativeEventWriter.putLong(data, stackTraceId);
        /* Is truncated. */
        JfrNativeEventWriter.putBoolean(data, isTruncated);
        /* Stacktrace size. */
        JfrNativeEventWriter.putInt(data, stackTraceLength);

        JfrNativeEventWriter.commit(data);

        /*
         * Maybe during writing, the thread buffer was replaced with a new (larger) one, so we need
         * to update the repository pointer as well.
         */
        epochData.stackTraceBuffer = data.getJfrBuffer();
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public void serializeUnknownStackTraceElement() {
        serializeStackTraceElement0(0, -1, -1);
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public void serializeStackTraceElement(FrameInfoQueryResult stackTraceElement) {
        serializeStackTraceElement0(SubstrateJVM.getMethodRepo().getMethodId(stackTraceElement), stackTraceElement.getSourceLineNumber(), stackTraceElement.getBci());
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private void serializeStackTraceElement0(long methodId, int sourceLineNumber, int bci) {
        JfrStackTraceEpochData epochData = getEpochData(false);
        assert epochData.stackTraceBuffer.isNonNull();

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.stackTraceBuffer);

        /* Method id. */
        JfrNativeEventWriter.putLong(data, methodId);
        /* Line number. */
        JfrNativeEventWriter.putInt(data, sourceLineNumber);
        /* Bytecode index. */
        JfrNativeEventWriter.putInt(data, bci);
        /* Frame type id. */
        JfrNativeEventWriter.putLong(data, JfrFrameType.FRAME_AOT_COMPILED.getId());

        JfrNativeEventWriter.commit(data);

        /*
         * Maybe during writing, the thread buffer was replaced with a new (larger) one, so we need
         * to update the repository pointer as well.
         */
        epochData.stackTraceBuffer = data.getJfrBuffer();
    }

    @Override
    public int write(JfrChunkWriter writer) {
        JfrStackTraceEpochData epochData = getEpochData(true);
        int count = writeStackTraces(writer, epochData);
        epochData.clear();
        return count;
    }

    private static int writeStackTraces(JfrChunkWriter writer, JfrStackTraceEpochData epochData) {
        int stackTraceCount = epochData.visitedStackTraces.getSize();
        if (stackTraceCount == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrType.StackTrace.getId());
        writer.writeCompressedInt(stackTraceCount);
        writer.write(epochData.stackTraceBuffer);

        return NON_EMPTY;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private JfrStackTraceEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    @RawStructure
    public interface JfrStackTraceTableEntry extends JfrVisited {
        @RawField
        Pointer getStackTrace();

        @RawField
        void setStackTrace(Pointer pointer);

        @RawField
        int getSize();

        @RawField
        void setSize(int size);
    }

    public static final class JfrStackTraceTable extends AbstractUninterruptibleHashtable {
        private long nextId;

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
            return entry1.getSize() == entry2.getSize() && LibC.memcmp(entry1.getStackTrace(), entry2.getStackTrace(), WordFactory.unsigned(entry1.getSize())) == 0;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            JfrStackTraceTableEntry result = (JfrStackTraceTableEntry) copyToHeap(valueOnStack, SizeOf.unsigned(JfrStackTraceTableEntry.class));
            result.setId(++nextId);
            return result;
        }
    }

    private static class JfrStackTraceEpochData {
        private JfrBuffer stackTraceBuffer;
        private final JfrStackTraceTable visitedStackTraces;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrStackTraceEpochData() {
            this.visitedStackTraces = new JfrStackTraceTable();
        }

        void clear() {
            visitedStackTraces.clear();
            if (stackTraceBuffer.isNonNull()) {
                JfrBufferAccess.reinitialize(stackTraceBuffer);
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            visitedStackTraces.teardown();
            if (stackTraceBuffer.isNonNull()) {
                JfrBufferAccess.free(stackTraceBuffer);
            }
            stackTraceBuffer = WordFactory.nullPointer();
        }
    }
}
