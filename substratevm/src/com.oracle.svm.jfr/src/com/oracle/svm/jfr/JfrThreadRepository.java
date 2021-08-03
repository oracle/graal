/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import com.oracle.svm.core.jdk.UninterruptibleHashtable;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.JavaLangThreadGroupSubstitutions;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jfr.traceid.JfrTraceIdEpoch;

import jdk.jfr.internal.Options;

/**
 * Repository that collects all metadata about threads and thread groups.
 */
public final class JfrThreadRepository implements JfrConstantPool {

    private static final long INITIAL_BUFFER_SIZE = Options.getThreadBufferSize();

    private final VMMutex mutex;

    private final JfrThreadEpochData epochData0;
    private final JfrThreadEpochData epochData1;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrThreadRepository() {
        this.epochData0 = new JfrThreadEpochData();
        this.epochData1 = new JfrThreadEpochData();
        this.mutex = new VMMutex("jfrThreadRepository");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private JfrThreadEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void clearEpochData(JfrThreadEpochData epochData) {
        epochData.setThreadCount(0);
        epochData.setThreadGroupCount(0);

        epochData.getVisitedThreadGroups().clear();

        JfrBufferAccess.reinitialize(epochData.getThreadBuffer());
        JfrBufferAccess.reinitialize(epochData.getThreadGroupBuffer());
    }

    @Uninterruptible(reason = "Releasing repository buffers.")
    public void teardown() {
        epochData0.getVisitedThreadGroups().teardown();
        epochData1.getVisitedThreadGroups().teardown();

        JfrBufferAccess.free(epochData0.getThreadBuffer());
        JfrBufferAccess.free(epochData1.getThreadBuffer());
        epochData0.setThreadBuffer(WordFactory.nullPointer());
        epochData1.setThreadBuffer(WordFactory.nullPointer());

        JfrBufferAccess.free(epochData0.getThreadGroupBuffer());
        JfrBufferAccess.free(epochData1.getThreadGroupBuffer());
        epochData0.setThreadGroupBuffer(WordFactory.nullPointer());
        epochData1.setThreadGroupBuffer(WordFactory.nullPointer());
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public void serializeThread(Thread thread) {
        if (!SubstrateJVM.isRecording()) {
            return;
        }

        mutex.lockNoTransition();
        JfrThreadEpochData epochData = getEpochData(false);
        try {
            if (epochData.getThreadBuffer().isNull()) {
                // This will happen only on the first call of the serialize method.
                epochData.setThreadBuffer(JfrBufferAccess.allocate(WordFactory.unsigned(INITIAL_BUFFER_SIZE), JfrBufferType.C_HEAP));
            }

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, epochData.getThreadBuffer());

            JfrNativeEventWriter.putLong(data, thread.getId());
            JfrNativeEventWriter.putString(data, thread.getName());
            JfrNativeEventWriter.putLong(data, thread.getId());
            JfrNativeEventWriter.putString(data, thread.getName());
            JfrNativeEventWriter.putLong(data, thread.getId());

            ThreadGroup threadGroup = thread.getThreadGroup();
            if (threadGroup != null) {
                long threadGroupId = JavaLangThreadGroupSubstitutions.getThreadGroupId(threadGroup);
                JfrNativeEventWriter.putLong(data, threadGroupId);
                serializeThreadGroup(threadGroupId, threadGroup);
            } else {
                JfrNativeEventWriter.putLong(data, 0);
            }
            epochData.incThreadCount();

            // Maybe during writing, the thread buffer was replaced with a new (larger) one, so we
            // need to update the repository pointer as well.
            epochData.setThreadBuffer(data.getJfrBuffer());

            JfrNativeEventWriter.commit(data);
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private void serializeThreadGroup(long threadGroupId, ThreadGroup threadGroup) {
        VMError.guarantee(mutex.isOwner(), "The current thread is not the owner of the mutex!");

        JfrThreadEpochData epochData = getEpochData(false);
        if (epochData.getThreadGroupBuffer().isNull()) {
            // This will happen only on the first call of the serialize method.
            epochData.setThreadGroupBuffer(JfrBufferAccess.allocate(WordFactory.unsigned(INITIAL_BUFFER_SIZE), JfrBufferType.C_HEAP));
        }

        JfrVisited jfrVisited = StackValue.get(JfrVisited.class);
        jfrVisited.setThreadGroupId(threadGroupId);
        jfrVisited.setHash((int) threadGroupId);
        if (!epochData.getVisitedThreadGroups().putIfAbsent(jfrVisited)) {
            return;
        }

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.getThreadGroupBuffer());
        JfrNativeEventWriter.putLong(data, threadGroupId);

        ThreadGroup parentThreadGroup = JavaLangThreadGroupSubstitutions.getParentThreadGroupUnsafe(threadGroup);
        long parentThreadGroupId = 0;
        if (parentThreadGroup != null) {
            parentThreadGroupId = JavaLangThreadGroupSubstitutions.getThreadGroupId(parentThreadGroup);
        }
        JfrNativeEventWriter.putLong(data, parentThreadGroupId);

        JfrNativeEventWriter.putString(data, threadGroup.getName());
        epochData.incThreadGroupCount();

        // Maybe during writing, the thread group buffer was replaced with a new (larger) one, so we
        // need to update the repository pointer as well.
        epochData.setThreadGroupBuffer(data.getJfrBuffer());

        JfrNativeEventWriter.commit(data);

        if (parentThreadGroupId > 0) {
            // Parent is not null, need to visit him as well.
            serializeThreadGroup(parentThreadGroupId, parentThreadGroup);
        }
    }

    @Override
    public int write(JfrChunkWriter writer) {
        JfrThreadEpochData epochData = getEpochData(true);
        int count = writeThreads(writer, epochData);
        count += writeThreadGroups(writer, epochData);
        clearEpochData(epochData);
        return count;
    }

    private int writeThreads(JfrChunkWriter writer, JfrThreadEpochData epochData) {
        VMError.guarantee(epochData.getThreadCount() > 0, "Thread repository must not be empty.");

        writer.writeCompressedLong(JfrTypes.Thread.getId());
        writer.writeCompressedInt(epochData.getThreadCount());
        writer.write(epochData.getThreadBuffer());

        return NON_EMPTY;
    }

    private int writeThreadGroups(JfrChunkWriter writer, JfrThreadEpochData epochData) {
        if (epochData.getThreadGroupCount() == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrTypes.ThreadGroup.getId());
        writer.writeCompressedInt(epochData.getThreadGroupCount());
        writer.write(epochData.getThreadGroupBuffer());

        return NON_EMPTY;
    }

    /**
     * After writing all the data into the chunk, it is necessary to clear data structures and to
     * re-register all threads and thread groups of the currently running threads (i.e., it is
     * necessary to iterate over all threads). Otherwise, this would leak memory.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void reinitializeRepository() {
        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            serializeThread(JavaThreads.fromVMThread(thread));
        }
    }

    @RawStructure
    interface JfrVisited extends UninterruptibleEntry<JfrVisited> {
        @RawField
        void setThreadGroupId(long threadGroupId);

        @RawField
        long getThreadGroupId();
    }

    static class JfrVisitedThreadGroups extends UninterruptibleHashtable<JfrVisited> {

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected JfrVisited[] createTable(int size) {
            return new JfrVisited[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void free(JfrVisited t) {
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(t);
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(JfrVisited a, JfrVisited b) {
            return a.getThreadGroupId() == b.getThreadGroupId();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected JfrVisited copyToHeap(JfrVisited visitedOnStack) {
            return allocateOnHeap((Pointer) visitedOnStack, SizeOf.unsigned(JfrVisited.class));
        }
    }
}
