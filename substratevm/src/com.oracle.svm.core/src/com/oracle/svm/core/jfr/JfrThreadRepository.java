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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordFactory;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jfr.utils.JfrVisited;
import com.oracle.svm.core.jfr.utils.JfrVisitedTable;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.JavaLangThreadGroupSubstitutions;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;

/**
 * Repository that collects all metadata about threads and thread groups.
 */
public final class JfrThreadRepository {
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
                Thread thread = PlatformThreads.fromVMThread(isolateThread);
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
        mutex.lockNoTransition();
        try {
            registerThread0(thread);
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private void registerThread0(Thread thread) {
        JfrThreadEpochData epochData = getEpochData(false);
        if (epochData.threadBuffer.isNull()) {
            // This will happen only on the first call.
            epochData.threadBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
        }

        JfrVisited visitedThread = StackValue.get(JfrVisited.class);
        visitedThread.setId(JavaThreads.getThreadId(thread));
        visitedThread.setHash((int) JavaThreads.getThreadId(thread));
        if (!epochData.visitedThreads.putIfAbsent(visitedThread)) {
            return;
        }

        epochData.unflushedThreadCount++;

        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
        JfrNativeEventWriterDataAccess.initialize(data, epochData.threadBuffer);

        // needs to be in sync with JfrThreadConstant::serialize
        boolean isVirtual = JavaThreads.isVirtual(thread);
        long osThreadId = isVirtual ? 0 : JavaThreads.getThreadId(thread);
        ThreadGroup threadGroup = thread.getThreadGroup();
        long threadGroupId = getThreadGroupId(isVirtual, threadGroup);

        JfrNativeEventWriter.putLong(data, JavaThreads.getThreadId(thread)); // JFR trace id
        JfrNativeEventWriter.putString(data, thread.getName()); // Java or native thread name
        JfrNativeEventWriter.putLong(data, osThreadId); // OS thread id
        JfrNativeEventWriter.putString(data, thread.getName()); // Java thread name
        JfrNativeEventWriter.putLong(data, JavaThreads.getThreadId(thread)); // Java thread id
        JfrNativeEventWriter.putLong(data, threadGroupId); // Java thread group
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            JfrNativeEventWriter.putBoolean(data, isVirtual); // isVirtual
        }
        if (!isVirtual && threadGroup != null) {
            registerThreadGroup(threadGroupId, threadGroup);
        }
        JfrNativeEventWriter.commit(data);

        // Maybe during writing, the thread buffer was replaced with a new (larger) one, so we
        // need to update the repository pointer as well.
        epochData.threadBuffer = data.getJfrBuffer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    private static long getThreadGroupId(boolean isVirtual, ThreadGroup threadGroup) {
        if (isVirtual) {
            // java thread group - VirtualThread threadgroup reserved id 1
            return 1;
        } else if (threadGroup == null) {
            return 0;
        } else {
            return JavaLangThreadGroupSubstitutions.getThreadGroupId(threadGroup);
        }
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private void registerThreadGroup(long threadGroupId, ThreadGroup threadGroup) {
        VMError.guarantee(mutex.isOwner(), "The current thread is not the owner of the mutex!");

        JfrThreadEpochData epochData = getEpochData(false);
        if (epochData.threadGroupBuffer.isNull()) {
            // This will happen only on the first call.
            epochData.threadGroupBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
        }

        JfrVisited jfrVisited = StackValue.get(JfrVisited.class);
        jfrVisited.setId(threadGroupId);
        jfrVisited.setHash((int) threadGroupId);
        if (!epochData.visitedThreadGroups.putIfAbsent(jfrVisited)) {
            return;
        }
        epochData.unflushedThreadGroupCount++;

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

    @Uninterruptible(reason = "Locking without transition.")
    private void maybeLock(boolean flush) {
        if (flush) {
            mutex.lockNoTransition();
        }
    }

    @Uninterruptible(reason = "Locking without transition.")
    private void maybeUnlock(boolean flush) {
        if (flush) {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Must not be interrupted for operations that emit events, potentially writing to this pool.")
    public int write(JfrChunkWriter writer, boolean flush) {
        JfrThreadEpochData epochData = getEpochData(!flush);
        int count = writeThreads(writer, epochData);
        count += writeThreadGroups(writer, epochData);

        return count;
    }

    @Uninterruptible(reason = "Must not be interrupted for operations that emit events, potentially writing to this pool.")
    public SignedWord maybeWrite(JfrChunkWriter writer, boolean flush, SignedWord lastCheckpointOffset) {

        JfrThreadEpochData epochData = getEpochData(!flush);
        maybeLock(flush);
        try {
            if (epochData.unflushedThreadCount == 0) {
                return lastCheckpointOffset;
            }
            SignedWord start = writer.beginEvent();
            long delta = 0;
            if (lastCheckpointOffset.greaterOrEqual(0)) {
                delta = lastCheckpointOffset.subtract(start).rawValue();
            }
            writer.writeCompressedLong(JfrReservedEvent.EVENT_CHECKPOINT.getId());
            writer.writeCompressedLong(JfrTicks.elapsedTicks());
            writer.writeCompressedLong(0); // duration
            writer.writeCompressedLong(delta);
            writer.writeByte(JfrCheckpointType.Threads.getId());

            // If only writing threads pool, count is 1
            int poolCount = 1;
            if (epochData.unflushedThreadGroupCount > 0) {
                poolCount = 2;
            }
            writer.writeInt(poolCount);

            int actualPoolCount = write(writer, flush);
            assert poolCount == actualPoolCount;

            writer.endEvent(start);
            return start;
        } finally {
            maybeUnlock(flush);
            /*
             * Thread repository epoch data may be cleared after a flush because threads are only
             * registered once with fixed IDs. There is no need to do a lookup in this repo for a
             * thread's ID. However, we preserve visitedThreads and visitedThreadGroups until the
             * epoch change to avoid unnecessary re-registrations.
             */
            if (!flush) {
                epochData.clear();
            }
        }
    }

    @Uninterruptible(reason = "May write current epoch data.")
    private static int writeThreads(JfrChunkWriter writer, JfrThreadEpochData epochData) {
        VMError.guarantee(epochData.unflushedThreadCount > 0, "Thread repository must not be empty.");

        writer.writeCompressedLong(JfrType.Thread.getId());
        writer.writeCompressedInt(epochData.unflushedThreadCount);
        writer.write(epochData.threadBuffer);
        JfrBufferAccess.reinitialize(epochData.threadBuffer);
        epochData.unflushedThreadCount = 0;
        return JfrConstantPool.NON_EMPTY;
    }

    @Uninterruptible(reason = "May write current epoch data.")
    private static int writeThreadGroups(JfrChunkWriter writer, JfrThreadEpochData epochData) {
        int threadGroupCount = epochData.unflushedThreadGroupCount;
        if (threadGroupCount == 0) {
            return JfrConstantPool.EMPTY;
        }

        writer.writeCompressedLong(JfrType.ThreadGroup.getId());
        writer.writeCompressedInt(threadGroupCount);
        writer.write(epochData.threadGroupBuffer);
        JfrBufferAccess.reinitialize(epochData.threadGroupBuffer);
        epochData.unflushedThreadGroupCount = 0;
        return JfrConstantPool.NON_EMPTY;
    }

    @Uninterruptible(reason = "Releasing repository buffers.")
    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    private static class JfrThreadEpochData {
        /*
         * We need to keep track of the threads because it is not guaranteed that registerThread is
         * only invoked once per thread (there can be races when re-registering already running
         * threads).
         */
        private final JfrVisitedTable visitedThreads;
        private final JfrVisitedTable visitedThreadGroups;
        private int unflushedThreadCount;
        private int unflushedThreadGroupCount;

        private JfrBuffer threadBuffer;
        private JfrBuffer threadGroupBuffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrThreadEpochData() {
            this.visitedThreads = new JfrVisitedTable();
            this.visitedThreadGroups = new JfrVisitedTable();
            this.unflushedThreadCount = 0;
            this.unflushedThreadGroupCount = 0;
        }

        @Uninterruptible(reason = "May write current epoch data.")
        public void clear() {
            visitedThreads.clear();
            visitedThreadGroups.clear();
            unflushedThreadCount = 0;
            unflushedThreadGroupCount = 0;
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
