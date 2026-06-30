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
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.jfr.traceid.JfrEpoch;
import com.oracle.svm.core.jfr.utils.JfrVisited;
import com.oracle.svm.core.jfr.utils.JfrVisitedTable;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.thread.JavaLangThreadGroupSubstitutions;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.thread.Target_java_lang_VirtualThread;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.shared.Uninterruptible;

/**
 * Repository that collects all metadata about threads and thread groups.
 * <p>
 * Note that the JFR trace ID for threads is the only trace ID that is not epoch-specific: the trace
 * ID is stable over epochs and all alive threads are re-registered right away when the epoch
 * changes.
 */
public final class JfrThreadRepository implements JfrRepository {
    /** The virtual thread group is always registered. */
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

    public void reset() {
        epochData0.clear(false);
        epochData1.clear(false);
    }

    @Uninterruptible(reason = "Required to get epoch data.")
    public void clearPreviousEpoch() {
        assert SubstrateJVM.getChunkWriter().isLockedByCurrentThread();
        mutex.lockNoTransition();
        try {
            getEpochData(true).clear(false);
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Prevent races with epoch changes.")
    public static long getVThreadEpochId(Thread vthread) {
        assert JavaThreads.isVirtual(vthread);
        Target_java_lang_VirtualThread v = JavaThreads.toVirtualTarget(vthread);
        return v.jfrEpochId;
    }

    @Uninterruptible(reason = "Prevent any JFR events from triggering.")
    public void registerRunningThreads() {
        assert VMOperation.isInProgressAtSafepoint();
        assert SubstrateJVM.get().isRecording();

        /* Register the virtual thread group unconditionally. */
        long virtualThreadGroupId = registerThreadGroup(Target_java_lang_Thread.virtualThreadGroup());
        assert virtualThreadGroupId == VIRTUAL_THREAD_GROUP_ID;

        for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
            /*
             * IsolateThreads without a Java thread just started executing and will register
             * themselves later on.
             */
            Thread thread = PlatformThreads.fromVMThread(isolateThread);
            if (thread != null) {
                registerPlatformThread(thread);

                if (SubstrateJVM.shouldRegisterVThreadsEagerly()) {
                    /* Re-register vthreads that are already mounted. */
                    Thread vthread = PlatformThreads.getMountedVirtualThread(thread);
                    if (vthread != null) {
                        registerVThread(vthread);
                    }
                }
            }
        }
    }

    /**
     * Registers a platform thread for the current epoch. Platform threads are registered eagerly,
     * so event-writing code never needs to call this method.
     */
    @Uninterruptible(reason = "Prevent epoch changes. Prevent races with VM operations that start/stop recording.")
    void registerPlatformThread(Thread thread) {
        assert !JavaThreads.isVirtual(thread);
        if (!SubstrateJVM.get().isRecording()) {
            return;
        }

        registerThread0(thread, false);
    }

    /** If a virtual thread is mounted, this registers that virtual thread for the current epoch. */
    @Uninterruptible(reason = "Prevent epoch changes. Prevent races with VM operations that start/stop recording.")
    public void registerMountedVThread() {
        Thread currentThread = JavaThreads.getCurrentThreadOrNull();
        if (JavaThreads.isVirtual(currentThread)) {
            registerVThread(currentThread);
        }
    }

    /**
     * Registers a virtual thread for the current epoch.
     * <p>
     * Virtual threads are usually not registered {@link SubstrateJVM#shouldRegisterVThreadsEagerly() eagerly},
     * as this would keep long-lived metadata for virtual threads alive that never emit JFR events. Code
     * that still has the virtual thread object can call this method before writing a JFR thread id.
     */
    @NeverInline("Prevent inlining epoch-sensitive virtual-thread registration into interruptible code.")
    @Uninterruptible(reason = "Prevent epoch changes. Prevent races with VM operations that start/stop recording.")
    public void registerVThread(Thread thread) {
        assert JavaThreads.isVirtual(thread);
        if (!SubstrateJVM.get().isRecording() || isVirtualThreadAlreadyRegistered(thread)) {
            return;
        }

        registerThread0(thread, true);
    }

    /**
     * Registers virtual-thread metadata when only the thread id and name are available.
     * <p>
     * Capture sites that still have the virtual-thread object pass the vthread's observed
     * {@link Target_java_lang_VirtualThread#jfrEpochId}. If that epoch still matches the current
     * epoch, the vthread was already registered and this method can skip acquiring the repository
     * mutex.
     */
    @Uninterruptible(reason = "Prevent epoch changes. Prevent races with VM operations that start/stop recording.")
    public void registerVThread(long vThreadId, String vthreadName, long vthreadEpochId) {
        if (!SubstrateJVM.get().isRecording() || isVirtualThreadAlreadyRegistered(vthreadEpochId)) {
            return;
        }

        assert vThreadId != 0L && vthreadName != null;
        registerThread0(vThreadId, vthreadName, 0L, true, null, null);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private void registerThread0(Thread thread, boolean isVirtual) {
        assert SubstrateJVM.get().isRecording();
        assert isVirtual == JavaThreads.isVirtual(thread);

        long threadId = JavaThreads.getThreadId(thread);
        long osThreadId = isVirtual ? 0 : threadId;
        String name = thread.getName();
        ThreadGroup threadGroup = isVirtual ? null : JavaThreads.getRawThreadGroup(thread);
        Target_java_lang_VirtualThread vthread = isVirtual ? JavaThreads.toVirtualTarget(thread) : null;
        registerThread0(threadId, name, osThreadId, isVirtual, threadGroup, vthread);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private void registerThread0(long threadId, String name, long osThreadId, boolean isVirtual, ThreadGroup threadGroup, Target_java_lang_VirtualThread vthread) {
        JfrVisited visitedThread = StackValue.get(JfrVisited.class);
        visitedThread.setId(threadId);
        visitedThread.setHash(UninterruptibleUtils.Long.hashCode(threadId));

        mutex.lockNoTransition();
        try {
            JfrThreadEpochData epochData = getEpochData(false);
            if (!epochData.threadTable.putIfAbsent(visitedThread)) {
                if (vthread != null) {
                    vthread.jfrEpochId = JfrEpoch.getInstance().currentEpochId();
                }
                return;
            }

            /* New thread, so serialize it to the buffer. */
            if (epochData.threadBuffer.isNull()) {
                epochData.threadBuffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
            }

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, epochData.threadBuffer);

            /* Similar to JfrThreadConstant::serialize in HotSpot. */
            long threadGroupId = isVirtual ? VIRTUAL_THREAD_GROUP_ID : registerThreadGroup(threadGroup);

            JfrNativeEventWriter.putLong(data, threadId);
            JfrNativeEventWriter.putString(data, name); // OS thread name
            JfrNativeEventWriter.putLong(data, osThreadId); // OS thread id
            JfrNativeEventWriter.putString(data, name); // Java thread name
            JfrNativeEventWriter.putLong(data, threadId); // Java thread id
            JfrNativeEventWriter.putLong(data, threadGroupId); // Java thread group
            JfrNativeEventWriter.putBoolean(data, isVirtual);
            if (!JfrNativeEventWriter.commit(data)) {
                return;
            }

            if (vthread != null) {
                vthread.jfrEpochId = JfrEpoch.getInstance().currentEpochId();
            }

            epochData.unflushedThreadCount++;
            /* The buffer may have been replaced with a new one. */
            epochData.threadBuffer = data.getJfrBuffer();
        } finally {
            mutex.unlock();
        }
    }

    /**
     * Virtual threads only need to be registered once per epoch. This fast path lets repeated
     * virtual-thread remounts avoid taking the global thread repository lock. The JFR epoch is
     * bumped when recording starts so stale epoch ids from a previous recording do not suppress
     * registration for a fresh recording.
     */
    @Uninterruptible(reason = "Epoch must not change while in this method.", callerMustBe = true)
    private static boolean isVirtualThreadAlreadyRegistered(Thread thread) {
        assert JavaThreads.isVirtual(thread);

        /* Threads only need to be registered once per epoch. */
        Target_java_lang_VirtualThread vthread = JavaThreads.toVirtualTarget(thread);
        return isVirtualThreadAlreadyRegistered(vthread.jfrEpochId);
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.", callerMustBe = true)
    private static boolean isVirtualThreadAlreadyRegistered(long vthreadEpochId) {
        return vthreadEpochId == JfrEpoch.getInstance().currentEpochId();
    }

    @Uninterruptible(reason = "Epoch must not change while in this method.")
    private long registerThreadGroup(ThreadGroup threadGroup) {
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
        long parentThreadGroupId = registerThreadGroup(parentThreadGroup);

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

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public boolean hasUnflushedPreviousEpochData() {
        mutex.lockNoTransition();
        try {
            JfrThreadEpochData epochData = getEpochData(true);
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
        boolean epoch = previousEpoch ? JfrEpoch.getInstance().previousEpoch() : JfrEpoch.getInstance().currentEpoch();
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
            threadBuffer = Word.nullPointer();

            JfrBufferAccess.free(threadGroupBuffer);
            threadGroupBuffer = Word.nullPointer();
        }
    }
}
