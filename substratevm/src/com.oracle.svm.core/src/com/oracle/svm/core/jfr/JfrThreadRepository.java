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
package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jfr.utils.JfrVisited;
import com.oracle.svm.core.jfr.utils.JfrVisitedTable;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.JavaLangThreadGroupSubstitutions;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

/**
 * Repository that collects all metadata about threads and thread groups.
 *
 * Note that the JFR trace ID for threads is the only trace ID that is not epoch-specific: the trace
 * ID is stable over epochs and all alive threads are re-registered right away when the epoch
 * changes.
 */
public final class JfrThreadRepository implements JfrRepository {
    public static final int VIRTUAL_THREAD_GROUP_ID = 1;

    private final VMMutex mutex;
    private final JfrThreadEpochData epochData0;
    private final JfrThreadEpochData epochData1;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrThreadRepository() {
        this.mutex = new VMMutex("jfrThreadRepository");
        this.epochData0 = new JfrThreadEpochData();
        this.epochData1 = new JfrThreadEpochData();
    }

    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @Uninterruptible(reason = "Required to get epoch data.")
    public void clearPreviousEpoch() {
        assert VMOperation.isInProgressAtSafepoint() && SubstrateJVM.getChunkWriter().isLockedByCurrentThread();
        getEpochData(true).clear(false);
    }

    @Uninterruptible(reason = "Prevent any JFR events from triggering.")
    public void registerRunningThreads() {
        assert VMOperation.isInProgressAtSafepoint();
        assert SubstrateJVM.get().isRecording();

        /* Register the virtual thread group unconditionally. */
        long virtualThreadGroupId = registerThreadGroup0(Target_java_lang_Thread.virtualThreadGroup());
        assert virtualThreadGroupId == VIRTUAL_THREAD_GROUP_ID;

        for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
            /*
             * IsolateThreads without a Java thread just started executing and will register
             * themselves later on.
             */
            Thread thread = PlatformThreads.fromVMThread(isolateThread);
            if (thread != null) {
                registerThread(thread);
                // Re-register vthreads that are already mounted.
                Thread vthread = PlatformThreads.getMountedVirtualThread(thread);
                if (vthread != null) {
                    registerThread(vthread);
                }
            }
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public void registerThread(Thread thread) {
        if (!SubstrateJVM.get().isRecording()) {
            return;
        }

        long threadId = JavaThreads.getThreadId(thread);

        JfrVisited visitedThread = StackValue.get(JfrVisited.class);
        visitedThread.setId(threadId);
        visitedThread.setHash(UninterruptibleUtils.Long.hashCode(threadId));

        mutex.lockNoTransition();
        try {
            JfrThreadEpochData epochData = getEpochData(false);
            if (!epochData.threadTable.putIfAbsent(visitedThread)) {
                return;
            }

            /* New thread, so serialize it to the buffer. */
            if (epochData.threadBuffer.isNull()) {
                epochData.threadBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
            }

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, epochData.threadBuffer);

            /* Similar to JfrThreadConstant::serialize in HotSpot. */
            boolean isVirtual = JavaThreads.isVirtual(thread);
            long osThreadId = isVirtual ? 0 : threadId;
            long threadGroupId = registerThreadGroup(thread, isVirtual);

            JfrNativeEventWriter.putLong(data, threadId);
            JfrNativeEventWriter.putString(data, thread.getName()); // OS thread name
            JfrNativeEventWriter.putLong(data, osThreadId); // OS thread id
            JfrNativeEventWriter.putString(data, thread.getName()); // Java thread name
            JfrNativeEventWriter.putLong(data, threadId); // Java thread id
            JfrNativeEventWriter.putLong(data, threadGroupId); // Java thread group
            JfrNativeEventWriter.putBoolean(data, isVirtual);
            if (!JfrNativeEventWriter.commit(data)) {
                return;
            }

            epochData.unflushedThreadCount++;
            /* The buffer may have been replaced with a new one. */
            epochData.threadBuffer = data.getJfrBuffer();
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private long registerThreadGroup(Thread thread, boolean isVirtual) {
        if (isVirtual) {
            /* For virtual threads, a fixed thread group id is reserved. */
            return VIRTUAL_THREAD_GROUP_ID;
        }
        ThreadGroup group = JavaThreads.getRawThreadGroup(thread);
        return registerThreadGroup0(group);
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private long registerThreadGroup0(ThreadGroup threadGroup) {
        if (threadGroup == null) {
            return 0;
        }

        long threadGroupId = JavaLangThreadGroupSubstitutions.getThreadGroupId(threadGroup);
        JfrVisited jfrVisited = StackValue.get(JfrVisited.class);
        jfrVisited.setId(threadGroupId);
        jfrVisited.setHash(UninterruptibleUtils.Long.hashCode(threadGroupId));

        JfrThreadEpochData epochData = getEpochData(false);
        if (!epochData.threadGroupTable.putIfAbsent(jfrVisited)) {
            return threadGroupId;
        }

        /* New thread group, so serialize it to the buffer. */
        if (epochData.threadGroupBuffer.isNull()) {
            epochData.threadGroupBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
        }

        ThreadGroup parentThreadGroup = JavaLangThreadGroupSubstitutions.getParentThreadGroupUnsafe(threadGroup);
        long parentThreadGroupId = registerThreadGroup0(parentThreadGroup);

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.threadGroupBuffer);

        JfrNativeEventWriter.putLong(data, threadGroupId);
        JfrNativeEventWriter.putLong(data, parentThreadGroupId);
        JfrNativeEventWriter.putString(data, threadGroup.getName());
        if (!JfrNativeEventWriter.commit(data)) {
            return threadGroupId;
        }

        epochData.unflushedThreadGroupCount++;
        /* The buffer may have been replaced with a new one. */
        epochData.threadGroupBuffer = data.getJfrBuffer();
        return threadGroupId;
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public boolean hasUnflushedData() {
        mutex.lockNoTransition();
        try {
            JfrThreadEpochData epochData = getEpochData(false);
            return epochData.unflushedThreadCount > 0 || epochData.unflushedThreadGroupCount > 0;
        } finally {
            mutex.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        mutex.lockNoTransition();
        try {
            JfrThreadEpochData epochData = getEpochData(!flushpoint);
            int count = writeThreads(writer, epochData);
            count += writeThreadGroups(writer, epochData);
            epochData.clear(flushpoint);
            return count;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "May write current epoch data.")
    private static int writeThreads(JfrChunkWriter writer, JfrThreadEpochData epochData) {
        int threadCount = epochData.unflushedThreadCount;
        if (threadCount == 0) {
            return JfrRepository.EMPTY;
        }

        writer.writeCompressedLong(JfrType.Thread.getId());
        writer.writeCompressedInt(epochData.unflushedThreadCount);
        writer.write(epochData.threadBuffer);
        JfrBufferAccess.reinitialize(epochData.threadBuffer);
        epochData.unflushedThreadCount = 0;
        return JfrRepository.NON_EMPTY;
    }

    @Uninterruptible(reason = "May write current epoch data.")
    private static int writeThreadGroups(JfrChunkWriter writer, JfrThreadEpochData epochData) {
        int threadGroupCount = epochData.unflushedThreadGroupCount;
        if (threadGroupCount == 0) {
            return JfrRepository.EMPTY;
        }

        writer.writeCompressedLong(JfrType.ThreadGroup.getId());
        writer.writeCompressedInt(threadGroupCount);
        writer.write(epochData.threadGroupBuffer);
        JfrBufferAccess.reinitialize(epochData.threadGroupBuffer);
        epochData.unflushedThreadGroupCount = 0;
        return JfrRepository.NON_EMPTY;
    }

    @Uninterruptible(reason = "Prevent epoch change.", callerMustBe = true)
    private JfrThreadEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    private static class JfrThreadEpochData {
        /*
         * We need to keep track of the threads because it is not guaranteed that registerThread is
         * only invoked once per thread (there can be races where we might re-register already
         * running threads).
         */
        private final JfrVisitedTable threadTable;
        private final JfrVisitedTable threadGroupTable;
        private int unflushedThreadCount;
        private int unflushedThreadGroupCount;
        private JfrBuffer threadBuffer;
        private JfrBuffer threadGroupBuffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrThreadEpochData() {
            this.threadTable = new JfrVisitedTable();
            this.threadGroupTable = new JfrVisitedTable();
            this.unflushedThreadCount = 0;
            this.unflushedThreadGroupCount = 0;
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear(boolean flushpoint) {
            if (!flushpoint) {
                threadTable.clear();
                threadGroupTable.clear();
            }

            unflushedThreadCount = 0;
            unflushedThreadGroupCount = 0;

            JfrBufferAccess.reinitialize(threadBuffer);
            JfrBufferAccess.reinitialize(threadGroupBuffer);
        }

        void teardown() {
            threadTable.teardown();
            threadGroupTable.teardown();

            unflushedThreadCount = 0;
            unflushedThreadGroupCount = 0;

            JfrBufferAccess.free(threadBuffer);
            threadBuffer = WordFactory.nullPointer();

            JfrBufferAccess.free(threadGroupBuffer);
            threadGroupBuffer = WordFactory.nullPointer();
        }
    }
}
