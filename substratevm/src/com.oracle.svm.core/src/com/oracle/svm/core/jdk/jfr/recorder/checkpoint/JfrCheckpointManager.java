/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.checkpoint;

import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;
import com.oracle.svm.core.jdk.jfr.recorder.JfrRecorder;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.JfrTypeManager;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.JfrTypeSet;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceIdLoadBarrier;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkWriter;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrBuffer;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrMemorySpace;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrMemorySpaceRetrieval;
import com.oracle.svm.core.jdk.jfr.recorder.storage.operations.JfrBufferOperations;
import com.oracle.svm.core.jdk.jfr.support.JfrThreadLocal;
import com.oracle.svm.core.jdk.jfr.utilities.JfrCheckpointType;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

public final class JfrCheckpointManager implements JfrCheckpointClient {
    private static final int bufferCount = 2;
    private static final int bufferSize = 512 * 1024;

    private static JfrCheckpointManager instance;

    private final JfrChunkWriter chunkWriter;
    private JfrMemorySpace<JfrMemorySpaceRetrieval, JfrCheckpointManager> mspace;

    private JfrCheckpointManager(JfrChunkWriter chunkWriter) {
        this.chunkWriter = chunkWriter;
    }

    public static JfrCheckpointManager create(JfrChunkWriter chunkWriter) {
        assert (instance == null);
        instance = new JfrCheckpointManager(chunkWriter);
        return instance;
    }

    public static JfrCheckpointManager instance() {
        return instance;
    }

    public boolean initialize() {
        this.mspace = new JfrMemorySpace<>(bufferSize, 0, this, true);
        this.mspace.initialize(false);

        for (int i = 0; i < bufferCount * 2; i++) {
            JfrBuffer b = new JfrBuffer(bufferSize);
            b.initialize();
            this.mspace.live(i % 2 == 0).add(b);
        }
        assert (this.mspace.free().isEmpty());

        return JfrTypeManager.initialize() && JfrTraceIdLoadBarrier.initialize();
    }

    // Do not safepoint here
    public void writeTypeSet() {
        // JFR.LEAK.TODO
        Thread thread = Thread.currentThread();
        // {
        // if (LeakProfiler::is_running()) {
        // JfrCheckpointWriter leakp_writer(true, thread);
        // JfrCheckpointWriter writer(true, thread);
        // JfrTypeSet::serialize(&writer, &leakp_writer, false, false);
        // ObjectSampleCheckpoint::on_type_set(leakp_writer);
        // }

        // JFR.TDO buffer instance can be changed while writing due to out of space
        // Should move acquisition into writer by passing Manager instance into
        // constructor
        JfrBuffer b = lease(0, true, thread);
        JfrCheckpointWriter writer = new JfrCheckpointWriter(b, thread, this);
        writer.open();
        JfrTypeSet.serialize(writer, null, false, false);
        writer.close();

        write();
    }

    public void beginEpochShift() {
        assert (VMOperation.isInProgressAtSafepoint());
        JfrTraceIdEpoch.beginEpochShift();
    }

    public void onRotation() {
        assert (VMOperation.isInProgressAtSafepoint());

        JfrTypeManager.onRotation();
        notifyThreads();
    }

    private void notifyThreads() {
        assert (VMOperation.isInProgressAtSafepoint());
        for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            Thread t = JavaThreads.fromVMThread(thread);
            JfrThreadLocal jtl = JavaThreads.getThreadLocal(t);
            if (jtl != null && jtl.hasEventWriter()) {
                jtl.setNotified(true);
            }
        }
    }

    public void endEpochShift() {
        assert (VMOperation.isInProgressAtSafepoint());
        JfrTraceIdEpoch.endEpochShift();
    }

    private long write() {
        assert (this.mspace.free().isEmpty());

        JfrBufferOperations.BufferOperation<JfrBuffer> wo = new JfrBufferOperations.CheckpointWrite<>(chunkWriter);
        JfrBufferOperations.CompositeAnd<JfrBuffer> wro = new JfrBufferOperations.CompositeAnd<>(
                new JfrBufferOperations.MutexedWrite<>(wo),
                new JfrBufferOperations.ReleaseExcision<>(this.mspace, this.mspace.live(true)));

        this.mspace.iterateLiveList(wro, true);

        return wo.elements();
    }

    private long writeStaticTypeSet(Thread thread) {
        assert thread != null;
        JfrBuffer b = lease(0, true, thread);
        JfrCheckpointWriter writer = new JfrCheckpointWriter(b, thread, JfrCheckpointType.STATICS, this);
        writer.open();
        JfrTypeManager.writeStaticTypes(writer);
        writer.close();
        return writer.usedSize();
    }

    private long writeThreads(Thread thread) {
        assert thread != null;
        // can safepoint here
        // ThreadInVMfromNative transition((JavaThread*)thread);
        // ResetNoHandleMark rnhm;
        // ResourceMark rm(thread);
        // HandleMark hm(thread);
        JfrBuffer b = lease(0, true, thread);
        JfrCheckpointWriter writer = new JfrCheckpointWriter(b, thread, JfrCheckpointType.THREADS, this);
        writer.open();
        JfrTypeManager.writeThreads(writer);
        writer.close();
        return writer.usedSize();
    }

    public long writeStaticTypeSetAndThreads() {
        // JFR.TODO
        // Surrounding implemetations are in place but actual write of content is empty

        // Thread thread = Thread.currentThread();
        // writeStaticTypeSet(thread);
        // writeThreads(thread);
        // return write();

        return 0;
    }

    public void clear() {
        // JFR.TODO
        JfrTraceIdLoadBarrier.clear();
        clearTypeSet();
        // DiscardOperation discard_operation(mutexed); // mutexed discard mode
        // ReleaseOperation ro(_mspace, _mspace->live_list(true));
        // DiscardReleaseOperation discard_op(&discard_operation, &ro);
        // assert(_mspace->free_list_is_empty(), "invariant");
        // process_live_list(discard_op, _mspace, true); // previous epoch list
        // return discard_operation.elements();
    }

    private void clearTypeSet() {
        assert (!JfrRecorder.isRecording());
        // MutexLocker cld_lock(ClassLoaderDataGraph_lock);
        // MutexLocker module_lock(Module_lock);

        JfrTypeSet.clear();
    }

    @Override
    public void registerFull(JfrBuffer buffer, Thread thread) {
        assert (buffer != null);
        assert (buffer.acquiredBy(thread));
        assert (buffer.isRetired());
    }

    @Override
    public JfrBuffer flush(JfrBuffer oldBuffer, int used, int requested, Thread t) {
        assert (oldBuffer != null && oldBuffer.isLeased());
        if (requested == 0) {
            // indicates a lease is being returned
            release(oldBuffer);
        }
        JfrBuffer newBuffer = lease(used + requested, true, t);
        try {
            newBuffer.getGlobalLock().lock();
            oldBuffer.transferTo(newBuffer, used);
            release(oldBuffer);
        } catch (Exception e) {
            JfrLogger.logError("JfrBuffer transfer failed. Data may be corrupted");
        } finally {
            newBuffer.getGlobalLock().unlock();
        }

        return newBuffer;
    }

    private void release(JfrBuffer buffer) {
        buffer.clearLeased();
        if (buffer.isTransient()) {
            buffer.setRetired();
        } else {
            buffer.release();
        }
    }

    @Override
    public JfrBuffer lease(int size, boolean previousEpoch, Thread t) {
        return lease(size, 100, previousEpoch, t);
    }

    private JfrBuffer lease(int size, int retryCount, boolean previousEpoch, Thread t) {
        int max = this.mspace.minElementSize();
        JfrBuffer b;
        if (size <= max) {
            b = JfrMemorySpace.acquireLiveLeaseWithRetry(size, this.mspace, retryCount, t, previousEpoch);
            if (b != null) {
                return b;
            }
        }

        b = JfrMemorySpace.acquireTransientLeaseToLive(size, this.mspace, t, previousEpoch);

        return b;
    }

}
