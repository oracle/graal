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

package com.oracle.svm.core.jdk.jfr.recorder.storage;

import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.svm.core.jdk.jfr.JfrOptions;
import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;
import com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkWriter;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrPostBox;
import com.oracle.svm.core.jdk.jfr.recorder.service.JfrPostBox.JfrMsg;
import com.oracle.svm.core.jdk.jfr.recorder.storage.operations.JfrBufferOperations;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperation;

public final class JfrStorage implements JfrMemorySpace.Client {

    // JFR.TODO
    // Verify BigInteger? int? long? for the various fields, especially for
    // mspace and size related ones
    private static final int inMemoryDiscardThreshholdDelta = 2;
    private static final int threadLocalCacheCount = 8;
    private static final int threadLocalScavengeThreshhold = threadLocalCacheCount / 2;

    private static final int promotionRetry = 100;

    private static JfrStorage instance;

    private final JfrChunkWriter chunkWriter;
    private final JfrPostBox postBox;
    private JfrStorageControl control;

    private final ReentrantLock bufferLock = new ReentrantLock();

    JfrMemorySpace<JfrRetrieval, JfrStorage> globalMSpace;
    JfrMemorySpace<JfrThreadLocalRetrieval, JfrStorage> threadLocalMSpace;

    // Slight adjustment from jdk/jdk using a MemorySpace for the FullList that uses
    // the free() list
    JfrMemorySpace<JfrSequentialRetrieval, JfrStorage> fullSpace;

    private JfrStorage(JfrChunkWriter chunkWriter, JfrPostBox postBox) {
        this.chunkWriter = chunkWriter;
        this.postBox = postBox;
    }

    public static JfrStorage create(JfrChunkWriter chunkWriter, JfrPostBox postBox) {
        assert (instance == null);
        instance = new JfrStorage(chunkWriter, postBox);
        return instance;
    }

    public boolean initialize() {
        // JFR.TODO
        // Get options from JfrOptionSet

        int numGlobalBuffers = JfrOptions.getGlobalBufferCount();
        assert (numGlobalBuffers >= inMemoryDiscardThreshholdDelta);

        int globalBufferSize = JfrOptions.getGlobalBufferSize();
        int threadBufferSize = JfrOptions.getThreadBufferSize();

        this.globalMSpace = new JfrMemorySpace<>(globalBufferSize, numGlobalBuffers, this);
        this.globalMSpace.initialize(false);

        this.threadLocalMSpace = new JfrMemorySpace<>(threadBufferSize, threadLocalCacheCount, this);
        this.threadLocalMSpace.initialize(true);

        this.fullSpace = new JfrMemorySpace<>(0, numGlobalBuffers, this);
        this.fullSpace.initialize(true);

        this.control = new JfrStorageControl(numGlobalBuffers - inMemoryDiscardThreshholdDelta, bufferLock);
        control.setScavengeThreshhold(threadLocalScavengeThreshhold);

        return true;
    }

    public JfrStorageControl getStorageControl() {
        return this.control;
    }

    private JfrPostBox getPostBox() {
        return this.postBox;
    }

    public void registerFull(JfrBuffer buffer, Thread thread) {
        assert (buffer != null);
        assert (buffer.isRetired());
        assert (buffer.acquiredBy(thread));
        assert (fullSpace != null);
        assert (fullSpace.live().isEmpty());

        boolean notify;

        this.bufferLock.lock();
        try {
            notify = this.control.incrementFull();
            fullSpace.free().add(buffer);
        } finally {
            this.bufferLock.unlock();
        }

        if (notify) {
            postBox.post(JfrMsg.FULLBUFFER);
        }
    }

    private void release(JfrBuffer buffer, Thread t) {
        assert (buffer != null);
        assert (!buffer.isLeased());
        assert (!buffer.isTransient());
        assert (!buffer.isRetired());

        if (!buffer.isEmpty()) {
            if (!flushRegularBuffer(buffer, t)) {
                buffer.reinitialize();
            }
        }

        assert (buffer.isEmpty());
        assert (buffer.identity() != -1);

        this.control.incrementDead();
        buffer.setRetired();
    }

    public JfrBuffer flush(JfrBuffer cur, int used, int requested, Thread t) {
        int curPos = cur.getCommittedPosition();
        requested += used;
        if (!cur.isLeased()) {
            flushRegular(cur, curPos, used, requested, t);
        } else {
            throw new UnsupportedOperationException();
        }
        return cur;
    }

    private JfrBuffer flushRegular(JfrBuffer buffer, int dataStart, int used, int requested, Thread t) {
        flushRegularBuffer(buffer, t);

        if (buffer.isExcluded()) {
            return buffer;
        }

        if (buffer.getFreeSize() >= requested) {
            if (used > 0) {
                ByteBuffer tmp = buffer.getBuffer().duplicate();
                assert (dataStart + used <= buffer.end());

                tmp.position(dataStart);
                tmp.limit(dataStart + used);

                buffer.getBuffer().position(buffer.getCommittedPosition());
                buffer.getBuffer().put(tmp);
            }
            return buffer;
        } else {
            // JFR.TODO
            // Large buffer required and is not currently supported
            JfrLogger.logWarning("JFR: Large buffer required but not supported. Event data will be lost: ", buffer.getFreeSize(), requested);
        }

        return buffer;
    }

    private boolean flushRegularBuffer(JfrBuffer buffer, Thread t) {
        assert (buffer != null);
        assert (!buffer.isLeased());
        assert (!buffer.isTransient());

        int unflushedSize = buffer.getUnflushedSize();
        if (unflushedSize == 0) {
            buffer.reinitialize();
            assert (buffer.isEmpty());
            return true;
        }

        if (buffer.isExcluded()) {
            boolean threadExcluded = JavaThreads.getThreadLocal(t).isExcluded();
            buffer.reinitialize(threadExcluded);
            assert (buffer.isEmpty());
            if (!threadExcluded) {
                // JFR.TODO
                // state change from exclusion to inclusion requires a thread checkpoint
                // JfrCheckpointManager::write_thread_checkpoint(thread);
            }
            return true;
        }

        JfrBuffer promotionBuffer = acquirePromotionBuffer(unflushedSize, globalMSpace, promotionRetry, t);
        if (promotionBuffer == null) {
            writeDataLoss(buffer, t);
            return false;
        }
        assert (promotionBuffer.acquiredBySelf());
        assert (promotionBuffer.getFreeSize() >= unflushedSize);

        buffer.promoteTo(promotionBuffer, unflushedSize);
        assert (buffer.isEmpty());

        return true;
    }

    private void writeDataLoss(JfrBuffer buffer, Thread t) {
        int unflushedSize = buffer.getUnflushedSize();
        buffer.reinitialize();
        if (unflushedSize == 0) {
            return;
        }
        writeDataLossEvent(buffer, unflushedSize, t);
    }

    private void writeDataLossEvent(JfrBuffer buffer, int unflushedSize, Thread t) {
        assert (buffer != null);
        assert (buffer.isEmpty());
        int totalDataLost = JavaThreads.getThreadLocal(t).addDataLost(unflushedSize);
        JfrLogger.logInfo("JFR: Data loss event:", totalDataLost);
        // JFR.TODO
        // if (EventDataLoss::is_enabled()) {
        // JfrNativeEventWriter writer(buffer, thread);
        // writer.write<u8>(EventDataLoss::eventId);
        // writer.write(JfrTicks::now());
        // writer.write(unflushed_size);
        // writer.write(total_data_loss);
        // }
    }

    private JfrBuffer acquirePromotionBuffer(int size, JfrMemorySpace<?, ?> mspace, int retryCount, Thread t) {
        assert (size <= mspace.minElementSize());
        while (true) {
            JfrBuffer b = JfrMemorySpace.acquireLiveWithRetry(size, mspace, retryCount, t);
            if (b == null && this.control.shouldDiscard()) {
                this.discardOldest(t);
                continue;
            }
            return b;
        }
    }

    private void discardOldest(Thread t) {
        if (this.bufferLock.tryLock()) {
            int fullPreDiscard = control.fullCount();
            int fullPostDiscard = 0;
            int discardSize = 0;
            try {
                if (!this.control.shouldDiscard()) {
                    // Another thread handled it
                    return;
                }

                while (true) {
                    JfrBuffer oldestNode = this.fullSpace.free().remove();
                    if (oldestNode == null) {
                        break;
                    }
                    assert (oldestNode.isRetired());
                    assert (oldestNode.identity() != -1);
                    discardSize += oldestNode.discard();
                    assert (oldestNode.getUnflushedSize() == 0);
                    fullPostDiscard = this.control.decrementFull();
                    if (oldestNode.isTransient()) {
                        // JFR.TODO Verify the node is in threadLocalMSpace
                        JfrLogger.logError("Releasing a transient node");
                        threadLocalMSpace.releaseLive(oldestNode);
                        continue;
                    }
                    oldestNode.reinitialize();
                    oldestNode.release();
                    break;
                }
            } finally {
                this.bufferLock.unlock();
            }
            int discards = fullPreDiscard - fullPostDiscard;
            if (discards > 0) {
                JfrLogger.logDebug("Cleared", discards, "full buffer(s) of", discardSize, "bytes");
                JfrLogger.logDebug("Current number of full buffers:", fullPostDiscard);
            }
        }
    }

    public boolean bufferIsLocked() {
        return this.bufferLock.isHeldByCurrentThread();
    }

    public static JfrStorage instance() {
        return instance;
    }

    public static JfrBuffer acquireThreadLocalBuffer(Thread thread) {
        JfrBuffer b = JfrMemorySpace.mspaceGetLive(0, instance().threadLocalMSpace, thread);
        if (b == null) {
            JfrLogger.logWarning("Unable to allocate thread local buffer");
            return null;
        }
        assert (b.acquiredBySelf());
        return b;
    }

    public static void releaseThreadLocal(JfrBuffer buffer, Thread t) {
        assert (buffer != null);
        instance().release(buffer, t);

        if (instance().getStorageControl().shouldScavenge()) {
            instance().getPostBox().post(JfrMsg.DEADBUFFER);
        }
    }

    public int clear() {
        int fullElements = clearFull();

        JfrBufferOperations.ConcurrentDiscard<JfrBuffer> discardOp = new JfrBufferOperations.ConcurrentDiscard<>(
                new JfrBufferOperations.DefaultDiscarder<>());
        JfrBufferOperations.ScavengingRelease<JfrBuffer> rtlo = new JfrBufferOperations.ScavengingRelease<>(
                threadLocalMSpace, threadLocalMSpace.live());

        JfrBufferOperations.CompositeAnd<JfrBuffer> tldo = new JfrBufferOperations.CompositeAnd<>(discardOp, rtlo);

        threadLocalMSpace.iterateLiveList(tldo);

        assert (globalMSpace.free().isEmpty());
        assert (!globalMSpace.live().isEmpty());

        globalMSpace.iterateLiveList(tldo);

        return fullElements + discardOp.elements();
    }

    private int clearFull() {
        if (fullSpace.free().isEmpty()) {
            return 0;
        }

        JfrBufferOperations.Discard<JfrBuffer> discardOp = new JfrBufferOperations.Discard<>(
                new JfrBufferOperations.DefaultDiscarder<>());
        int count = processFull(discardOp);

        if (count != 0) {
            logWrite(count, discardOp.size());
        }

        return count;

    }

    public int write() {
        assert (chunkWriter.isValid());
        int fullElements = writeFull(chunkWriter);

        assert (globalMSpace.free().isEmpty());
        assert (!globalMSpace.live().isEmpty());

        JfrBufferOperations.UnbufferedWriteToChunk<JfrBuffer> wo = new JfrBufferOperations.UnbufferedWriteToChunk<>(
                chunkWriter);
        JfrBufferOperations.PredicatedConcurrentWrite<JfrBuffer> cnewo = new JfrBufferOperations.PredicatedConcurrentWrite<>(
                wo, new JfrBufferOperations.Excluded<>(true));

        JfrBufferOperations.ScavengingRelease<JfrBuffer> rtlo = new JfrBufferOperations.ScavengingRelease<>(
                threadLocalMSpace, threadLocalMSpace.live());

        JfrBufferOperations.CompositeAnd<JfrBuffer> tlop = new JfrBufferOperations.CompositeAnd<>(cnewo, rtlo);

        threadLocalMSpace.iterateLiveList(tlop);

        return fullElements + wo.elements();
    }

    public int writeFull(JfrChunkWriter writer) {
        assert (writer.isValid());
        if (fullSpace.free().isEmpty()) {
            return 0;
        }

        JfrBufferOperations.BufferOperation<JfrBuffer> wo = new JfrBufferOperations.UnbufferedWriteToChunk<>(writer);

        JfrBufferOperations.CompositeAnd<JfrBuffer> writeOp = new JfrBufferOperations.CompositeAnd<>(
                new JfrBufferOperations.MutexedWrite<>(wo), new JfrBufferOperations.Release<>(threadLocalMSpace));

        int count = processFull(writeOp);
        if (count != 0) {
            logWrite(count, writeOp.size());
        }
        return count;
    }

    private int processFull(JfrBufferOperations.BufferOperation<JfrBuffer> operation) {
        int count = 0;
        while (!fullSpace.free().isEmpty()) {
            JfrBuffer n = fullSpace.free().remove();
            operation.process(n);
            count++;
        }
        return count;
    }

    public int writeAtSafepoint() {
        assert (VMOperation.isInProgressAtSafepoint());
        int fullElements = writeFull(chunkWriter);

        JfrBufferOperations.UnbufferedWriteToChunk<JfrBuffer> wo = new JfrBufferOperations.UnbufferedWriteToChunk<>(
                chunkWriter);
        JfrBufferOperations.PredicatedSafepointWrite<JfrBuffer> cnewo = new JfrBufferOperations.PredicatedSafepointWrite<>(
                wo, new JfrBufferOperations.Excluded<>(true));

        threadLocalMSpace.iterateLiveList(cnewo);

        assert (globalMSpace.free().isEmpty());
        assert (!globalMSpace.live().isEmpty());
        globalMSpace.iterateLiveList(cnewo);

        return fullElements + wo.elements();
    }

    private void logWrite(int count, int amount) {
        JfrLogger.log(0, JfrLogger.Level.DEBUG, "Processed: " + count + " full buffers and " + amount + " bytes");
    }
}
