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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.JavaLangThreadGroupSubstitutions;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperation;
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

    @Uninterruptible(reason = "Prevent any JFR events from triggering.")
    public void registerRunningThreads() {
        assert VMOperation.isInProgressAtSafepoint();
        mutex.lockNoTransition();
        try {
            for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
                // IsolateThreads without a Java thread just started executing and will register
                // themselves later on.
                Thread thread = JavaThreads.fromVMThread(isolateThread);
                if (thread != null) {
                    registerThread0(thread);
                }
            }
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    public void registerThread(Thread thread) {
        if (!SubstrateJVM.isRecording()) {
            return;
        }

        mutex.lockNoTransition();
        try {
            registerThread0(thread);
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private void registerThread0(Thread thread) {
        assert SubstrateJVM.isRecording();
        JfrThreadEpochData epochData = getEpochData(false);
        if (epochData.threadBuffer.isNull()) {
            // This will happen only on the first call.
            epochData.threadBuffer = JfrBufferAccess.allocate(WordFactory.unsigned(INITIAL_BUFFER_SIZE), JfrBufferType.C_HEAP);
        }

        JfrVisited visitedThread = StackValue.get(JfrVisited.class);
        visitedThread.setId(thread.getId());
        visitedThread.setHash((int) thread.getId());
        if (!epochData.visitedThreads.putIfAbsent(visitedThread)) {
            return;
        }

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.threadBuffer);

        JfrNativeEventWriter.putLong(data, thread.getId()); // JFR trace id
        JfrNativeEventWriter.putString(data, thread.getName()); // Java or native thread name
        JfrNativeEventWriter.putLong(data, thread.getId()); // OS thread id
        JfrNativeEventWriter.putString(data, thread.getName()); // Java thread name
        JfrNativeEventWriter.putLong(data, thread.getId()); // Java thread id

        ThreadGroup threadGroup = thread.getThreadGroup();
        if (threadGroup != null) {
            long threadGroupId = JavaLangThreadGroupSubstitutions.getThreadGroupId(threadGroup);
            JfrNativeEventWriter.putLong(data, threadGroupId);
            registerThreadGroup(threadGroupId, threadGroup);
        } else {
            JfrNativeEventWriter.putLong(data, 0);
        }
        JfrNativeEventWriter.commit(data);

        // Maybe during writing, the thread buffer was replaced with a new (larger) one, so we
        // need to update the repository pointer as well.
        epochData.threadBuffer = data.getJfrBuffer();
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private void registerThreadGroup(long threadGroupId, ThreadGroup threadGroup) {
        VMError.guarantee(mutex.isOwner(), "The current thread is not the owner of the mutex!");

        JfrThreadEpochData epochData = getEpochData(false);
        if (epochData.threadGroupBuffer.isNull()) {
            // This will happen only on the first call.
            epochData.threadGroupBuffer = JfrBufferAccess.allocate(WordFactory.unsigned(INITIAL_BUFFER_SIZE), JfrBufferType.C_HEAP);
        }

        JfrVisited jfrVisited = StackValue.get(JfrVisited.class);
        jfrVisited.setId(threadGroupId);
        jfrVisited.setHash((int) threadGroupId);
        if (!epochData.visitedThreadGroups.putIfAbsent(jfrVisited)) {
            return;
        }

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.threadGroupBuffer);
        JfrNativeEventWriter.putLong(data, threadGroupId);

        ThreadGroup parentThreadGroup = JavaLangThreadGroupSubstitutions.getParentThreadGroupUnsafe(threadGroup);
        long parentThreadGroupId = 0;
        if (parentThreadGroup != null) {
            parentThreadGroupId = JavaLangThreadGroupSubstitutions.getThreadGroupId(parentThreadGroup);
        }
        JfrNativeEventWriter.putLong(data, parentThreadGroupId);
        JfrNativeEventWriter.putString(data, threadGroup.getName());
        JfrNativeEventWriter.commit(data);

        // Maybe during writing, the thread group buffer was replaced with a new (larger) one, so we
        // need to update the repository pointer as well.
        epochData.threadGroupBuffer = data.getJfrBuffer();

        if (parentThreadGroupId > 0) {
            // Parent is not null, need to visit him as well.
            registerThreadGroup(parentThreadGroupId, parentThreadGroup);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private JfrThreadEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    @Override
    public int write(JfrChunkWriter writer) {
        JfrThreadEpochData epochData = getEpochData(true);
        int count = writeThreads(writer, epochData);
        count += writeThreadGroups(writer, epochData);
        epochData.clear();
        return count;
    }

    private static int writeThreads(JfrChunkWriter writer, JfrThreadEpochData epochData) {
        VMError.guarantee(epochData.visitedThreads.getSize() > 0, "Thread repository must not be empty.");

        writer.writeCompressedLong(JfrTypes.Thread.getId());
        writer.writeCompressedInt(epochData.visitedThreads.getSize());
        writer.write(epochData.threadBuffer);

        return NON_EMPTY;
    }

    private static int writeThreadGroups(JfrChunkWriter writer, JfrThreadEpochData epochData) {
        int threadGroupCount = epochData.visitedThreadGroups.getSize();
        if (threadGroupCount == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrTypes.ThreadGroup.getId());
        writer.writeCompressedInt(threadGroupCount);
        writer.write(epochData.threadGroupBuffer);

        return NON_EMPTY;
    }

    @Uninterruptible(reason = "Releasing repository buffers.")
    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @RawStructure
    interface JfrVisited extends UninterruptibleEntry {
        @RawField
        long getId();

        @RawField
        void setId(long value);
    }

    private static class JfrVisitedTable extends AbstractUninterruptibleHashtable {

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected JfrVisited[] createTable(int size) {
            return new JfrVisited[size];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public JfrVisited[] getTable() {
            return (JfrVisited[]) super.getTable();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry v0, UninterruptibleEntry v1) {
            JfrVisited a = (JfrVisited) v0;
            JfrVisited b = (JfrVisited) v1;
            return a.getId() == b.getId();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry visitedOnStack) {
            return copyToHeap(visitedOnStack, SizeOf.unsigned(JfrVisited.class));
        }
    }

    private static class JfrThreadEpochData {
        /*
         * We need to keep track of the threads because it is not guaranteed that registerThread is
         * only invoked once per thread (there can be races when re-registering already running
         * threads).
         */
        private final JfrVisitedTable visitedThreads;
        private final JfrVisitedTable visitedThreadGroups;

        private JfrBuffer threadBuffer;
        private JfrBuffer threadGroupBuffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrThreadEpochData() {
            this.visitedThreads = new JfrVisitedTable();
            this.visitedThreadGroups = new JfrVisitedTable();
        }

        public void clear() {
            visitedThreads.clear();
            visitedThreadGroups.clear();

            JfrBufferAccess.reinitialize(threadBuffer);
            JfrBufferAccess.reinitialize(threadGroupBuffer);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public void teardown() {
            visitedThreads.teardown();
            visitedThreadGroups.teardown();

            JfrBufferAccess.free(threadBuffer);
            threadBuffer = WordFactory.nullPointer();

            JfrBufferAccess.free(threadGroupBuffer);
            threadGroupBuffer = WordFactory.nullPointer();
        }
    }
}
