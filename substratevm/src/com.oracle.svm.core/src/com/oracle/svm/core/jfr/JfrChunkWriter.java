/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.StandardCharsets;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jfr.sampler.JfrExecutionSampler;
import com.oracle.svm.core.jfr.sampler.JfrRecurringCallbackExecutionSampler;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.sampler.SamplerBuffersAccess;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.ThreadingSupportImpl;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.locks.VMMutex;

import com.oracle.svm.core.util.VMError;

import com.oracle.svm.core.jfr.JfrBufferNodeLinkedList.JfrBufferNode;
import static com.oracle.svm.core.jfr.JfrThreadLocal.getJavaBufferList;
import static com.oracle.svm.core.jfr.JfrThreadLocal.getNativeBufferList;

/**
 * This class is used when writing the in-memory JFR data to a file. For all operations, except
 * those listed in {@link JfrUnlockedChunkWriter}, it is necessary to acquire the {@link #lock}
 * before invoking the operation.
 *
 * If an operation needs both a safepoint and the lock, then it is necessary to acquire the lock
 * outside of the safepoint. Otherwise, this will result in deadlocks as other threads may hold the
 * lock while they are paused at a safepoint.
 */
public final class JfrChunkWriter implements JfrUnlockedChunkWriter {
    public static final byte[] FILE_MAGIC = {'F', 'L', 'R', '\0'};
    public static final short JFR_VERSION_MAJOR = 2;
    public static final short JFR_VERSION_MINOR = 0;
    private static final int CHUNK_SIZE_OFFSET = 8;
    private static final int FILE_STATE_OFFSET = 64;

    public static final long METADATA_TYPE_ID = 0;
    public static final long CONSTANT_POOL_TYPE_ID = 1;
    private static final byte COMPLETE = 0;
    private final JfrGlobalMemory globalMemory;
    private final VMMutex lock;
    private final boolean compressedInts;
    private final JfrMetadata metadata;
    private long notificationThreshold;

    private String filename;
    private RawFileOperationSupport.RawFileDescriptor fd;
    private long chunkStartTicks;
    private long chunkStartNanos;
    private byte generation;
    public SignedWord lastCheckpointOffset;
    private boolean newChunk = true;
    private boolean isFinal = false;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrChunkWriter(JfrGlobalMemory globalMemory, JfrMetadata metadata) {
        this.lock = new VMMutex("JfrChunkWriter");
        this.compressedInts = true;
        this.globalMemory = globalMemory;
        this.metadata = metadata;
    }

    @Override
    public void initialize(long maxChunkSize) {
        this.notificationThreshold = maxChunkSize;
    }

    @Override
    public JfrChunkWriter lock() {
        assert !VMOperation.isInProgressAtSafepoint() : "could cause deadlocks";
        lock.lock();
        return this;
    }

    public void unlock() {
        lock.unlock();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean hasOpenFile() {
        return getFileSupport().isValid(fd);
    }

    public void setFilename(String filename) {
        assert lock.isOwner();
        this.filename = filename;
    }

    public void maybeOpenFile() {
        assert lock.isOwner();
        if (filename != null) {
            openFile(filename);
        }
    }

    public boolean openFile(String outputFile) {
        assert lock.isOwner();
        isFinal = false;
        generation = 1;
        newChunk = true;
        chunkStartNanos = JfrTicks.currentTimeNanos();
        chunkStartTicks = JfrTicks.elapsedTicks();
        filename = outputFile;
        fd = getFileSupport().open(filename, RawFileOperationSupport.FileAccessMode.READ_WRITE);
        writeFileHeader();
        lastCheckpointOffset = WordFactory.signed(-1); // must reset this on new chunk
        return true;
    }

    @Uninterruptible(reason = "Prevent safepoints as those could change the top pointer.")
    public boolean write(JfrBuffer buffer) {
        return write(buffer, true);
    }

    @Uninterruptible(reason = "Prevent safepoints as those could change the top pointer.")
    public boolean write(JfrBuffer buffer, boolean reset) {
        assert JfrBufferAccess.isAcquired(buffer) || VMOperation.isInProgressAtSafepoint() || buffer.getBufferType() == JfrBufferType.C_HEAP;
        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(buffer);
        if (unflushedSize.equal(0)) {
            return false;
        }

        boolean success = getFileSupport().write(fd, buffer.getTop(), unflushedSize);
        if (reset) {
            JfrBufferAccess.increaseTop(buffer, unflushedSize);
        }
        if (!success) {
            // We lost some data because the write failed.
            return false;
        }
        return getFileSupport().position(fd).greaterThan(WordFactory.signed(notificationThreshold));
    }

    /**
     * Write all the in-memory data to the file.
     */
    public void closeFile(JfrThreadRepository threadRepo) {
        assert lock.isOwner();
        /*
         * Switch to a new epoch. This is done at a safepoint to ensure that we end up with
         * consistent data, even if multiple threads have JFR events in progress.
         */
        JfrChangeEpochOperation op = new JfrChangeEpochOperation();
        op.enqueue();

        /*
         * After changing the epoch, all subsequently triggered JFR events will be recorded into the
         * data structures of the new epoch. This guarantees that the data in the old epoch can be
         * persisted to a file without a safepoint.
         */
        if (threadRepo.isDirty(false)) {
            writeThreadCheckpointEvent(threadRepo, false);
        }
        SignedWord constantPoolPosition = writeCheckpointEvent(false);
        writeMetadataEvent();
        patchFileHeader(constantPoolPosition);
        getFileSupport().close(fd);

        filename = null;
        fd = WordFactory.nullPointer();
        newChunk = false;
    }

    public void flush(JfrThreadRepository threadRepo) {
        assert lock.isOwner();
        flushStorage();

        if (threadRepo.isDirty(true)) {
            writeThreadCheckpointEvent(threadRepo, true);
        }
        SignedWord constantPoolPosition = writeCheckpointEvent(true);
        writeMetadataEvent();

        patchFileHeader(constantPoolPosition, true);
        newChunk = false;
        // unlike rotate chunk, don't close file.
    }

    private void writeFileHeader() {
        // Write the header - some data gets patched later on.
        getFileSupport().write(fd, FILE_MAGIC); // magic
        getFileSupport().writeShort(fd, JFR_VERSION_MAJOR); // version
        getFileSupport().writeShort(fd, JFR_VERSION_MINOR);
        assert getFileSupport().position(fd).equal(CHUNK_SIZE_OFFSET);
        getFileSupport().writeLong(fd, 0L); // chunk size
        getFileSupport().writeLong(fd, 0L); // last checkpoint offset
        getFileSupport().writeLong(fd, 0L); // metadata position
        getFileSupport().writeLong(fd, chunkStartNanos); // startNanos
        getFileSupport().writeLong(fd, 0L); // durationNanos
        getFileSupport().writeLong(fd, chunkStartTicks);
        getFileSupport().writeLong(fd, JfrTicks.getTicksFrequency());
        getFileSupport().writeByte(fd, nextGeneration()); // in hotspot a 1 byte generation is
                                                          // written
        getFileSupport().writeByte(fd, (byte) 0); // in hotspot 1 byte PAD padding
        short flags = 0;
        flags += compressedInts ? 1 : 0;
        flags += isFinal ? 1 * 2 : 0;

        getFileSupport().writeShort(fd, flags);
    }

    public void patchFileHeader(SignedWord constantPoolPosition) {
        patchFileHeader(constantPoolPosition, false);
    }

    private void patchFileHeader(SignedWord constantPoolPosition, boolean flushpoint) {
        assert lock.isOwner();
        SignedWord currentPos = getFileSupport().position(fd);
        long chunkSize = getFileSupport().position(fd).rawValue();
        long durationNanos = JfrTicks.currentTimeNanos() - chunkStartNanos;
        getFileSupport().seek(fd, WordFactory.signed(CHUNK_SIZE_OFFSET));
        getFileSupport().writeLong(fd, chunkSize);
        getFileSupport().writeLong(fd, constantPoolPosition.rawValue());
        getFileSupport().writeLong(fd, metadata.getMetadataPosition().rawValue());
        getFileSupport().writeLong(fd, chunkStartNanos);
        getFileSupport().writeLong(fd, durationNanos);
        getFileSupport().seek(fd, WordFactory.signed(FILE_STATE_OFFSET));
        if (flushpoint) {
            // chunk is not finished
            getFileSupport().writeByte(fd, nextGeneration()); // there are 4 bytes at the end. The
                                                              // first byte is the finished flag.
        } else {
            getFileSupport().writeByte(fd, COMPLETE);
        }
        getFileSupport().writeByte(fd, (byte) 0);
        short flags = 0;
        flags += compressedInts ? 1 : 0;
        flags += isFinal ? 1 * 2 : 0;

        getFileSupport().writeShort(fd, flags);

        // need to move pointer back to correct position for next write
        getFileSupport().seek(fd, currentPos);
    }

    private byte nextGeneration() {
        if (generation == Byte.MAX_VALUE) {
            // similar to Hotspot, restart counter if required.
            generation = 1;
            return Byte.MAX_VALUE;
        }
        return generation++;
    }

    private SignedWord writeThreadCheckpointEvent(JfrConstantPool threadRepo, boolean flush) {

        SignedWord start = beginEvent();

        if (lastCheckpointOffset.lessThan(0)) {
            lastCheckpointOffset = start;
        }
        writeCompressedLong(CONSTANT_POOL_TYPE_ID);
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(lastCheckpointOffset.subtract(start).rawValue()); // deltaToNext
        writeCompressedLong(8); // Checkpoint type is "THREADS"

        SignedWord poolCountPos = getFileSupport().position(fd);
        getFileSupport().writeInt(fd, 0); // We'll patch this later.

        int poolCount = threadRepo.write(this, flush);

        SignedWord currentPos = getFileSupport().position(fd);
        getFileSupport().seek(fd, poolCountPos);
        getFileSupport().writeInt(fd, makePaddedInt(poolCount));
        getFileSupport().seek(fd, currentPos);
        endEvent(start);
        lastCheckpointOffset = start;

        return start;
    }

    private SignedWord writeCheckpointEvent(boolean flush) {
        assert lock.isOwner();
        SignedWord start = beginEvent();

        if (lastCheckpointOffset.lessThan(0)) {
            lastCheckpointOffset = start;
        }
        writeCompressedLong(CONSTANT_POOL_TYPE_ID);
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(lastCheckpointOffset.subtract(start).rawValue()); // deltaToNext
        writeBoolean(true); // flush

        SignedWord poolCountPos = getFileSupport().position(fd);
        getFileSupport().writeInt(fd, 0); // We'll patch this later.

        int poolCount = 0;
        if (newChunk) {
            poolCount = writeSerializers(flush);
        }
        poolCount += writeRepositories(flush);

        SignedWord currentPos = getFileSupport().position(fd);
        getFileSupport().seek(fd, poolCountPos);
        getFileSupport().writeInt(fd, makePaddedInt(poolCount));
        getFileSupport().seek(fd, currentPos);
        endEvent(start);
        lastCheckpointOffset = start;

        return start;
    }

    /**
     * Repositories earlier in the write order may reference entries of repositories later in the
     * write order. This ordering is required to prevent races during flushing without changing
     * epoch.
     */
    private int writeRepositories(boolean flush) {
        int count = 0;
        count += com.oracle.svm.core.jfr.SubstrateJVM.getStackTraceRepo().write(this, flush);
        count += com.oracle.svm.core.jfr.SubstrateJVM.getMethodRepo().write(this, flush);
        count += com.oracle.svm.core.jfr.SubstrateJVM.getTypeRepository().write(this, flush);
        count += com.oracle.svm.core.jfr.SubstrateJVM.getSymbolRepository().write(this, flush);
        return count;
    }

    private int writeSerializers(boolean flush) {
        int count = 0;
        for (JfrConstantPool constantPool : JfrSerializerSupport.get().getSerializers()) {
            int poolCount = constantPool.write(this, flush);
            count += poolCount;
        }
        return count;
    }

    private void writeMetadataEvent() {
        assert lock.isOwner();
        // always write metadata on a new chunk!
        if (!metadata.isDirty() && !newChunk) {
            return;
        }
        SignedWord start = beginEvent();
        writeCompressedLong(METADATA_TYPE_ID);
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(metadata.getCurrentMetadataId()); // metadata id
        writeBytes(metadata.getDescriptorAndClearDirtyFlag()); // payload
        endEvent(start);
        metadata.setMetadataPosition(start);
    }

    public boolean shouldRotateDisk() {
        assert lock.isOwner();
        return getFileSupport().isValid(fd) && getFileSupport().size(fd).greaterThan(WordFactory.signed(notificationThreshold));
    }

    public SignedWord beginEvent() {
        SignedWord start = getFileSupport().position(fd);
        // Write a placeholder for the size. Will be patched by endEvent,
        getFileSupport().writeInt(fd, 0);
        return start;
    }

    public void endEvent(SignedWord start) {
        SignedWord end = getFileSupport().position(fd);
        SignedWord writtenBytes = end.subtract(start);
        getFileSupport().seek(fd, start);
        getFileSupport().writeInt(fd, makePaddedInt(writtenBytes.rawValue()));
        getFileSupport().seek(fd, end);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeBoolean(boolean value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.isOwned();
        writeByte((byte) (value ? 1 : 0));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeByte(byte value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.isOwned();
        getFileSupport().writeByte(fd, value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeBytes(byte[] values) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.isOwned();
        getFileSupport().write(fd, values);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeCompressedInt(int value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.isOwned();
        writeCompressedLong(value & 0xFFFFFFFFL);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeCompressedLong(long value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.isOwned();
        long v = value;
        if ((v & ~0x7FL) == 0L) {
            getFileSupport().writeByte(fd, (byte) v); // 0-6
            return;
        }
        getFileSupport().writeByte(fd, (byte) (v | 0x80L)); // 0-6
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            getFileSupport().writeByte(fd, (byte) v); // 7-13
            return;
        }
        getFileSupport().writeByte(fd, (byte) (v | 0x80L)); // 7-13
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            getFileSupport().writeByte(fd, (byte) v); // 14-20
            return;
        }
        getFileSupport().writeByte(fd, (byte) (v | 0x80L)); // 14-20
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            getFileSupport().writeByte(fd, (byte) v); // 21-27
            return;
        }
        getFileSupport().writeByte(fd, (byte) (v | 0x80L)); // 21-27
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            getFileSupport().writeByte(fd, (byte) v); // 28-34
            return;
        }
        getFileSupport().writeByte(fd, (byte) (v | 0x80L)); // 28-34
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            getFileSupport().writeByte(fd, (byte) v); // 35-41
            return;
        }
        getFileSupport().writeByte(fd, (byte) (v | 0x80L)); // 35-41
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            getFileSupport().writeByte(fd, (byte) v); // 42-48
            return;
        }
        getFileSupport().writeByte(fd, (byte) (v | 0x80L)); // 42-48
        v >>>= 7;

        if ((v & ~0x7FL) == 0L) {
            getFileSupport().writeByte(fd, (byte) v); // 49-55
            return;
        }
        getFileSupport().writeByte(fd, (byte) (v | 0x80L)); // 49-55
        getFileSupport().writeByte(fd, (byte) (v >>> 7)); // 56-63, last byte as is.
    }

    @Fold
    static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.bigEndian();
    }

    public void writeString(String str) {
        if (str.isEmpty()) {
            getFileSupport().writeByte(fd, StringEncoding.EMPTY_STRING.byteValue);
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            getFileSupport().writeByte(fd, StringEncoding.UTF8_BYTE_ARRAY.byteValue);
            writeCompressedInt(bytes.length);
            getFileSupport().write(fd, bytes);
        }
    }

    private static int makePaddedInt(long sizeWritten) {
        return JfrNativeEventWriter.makePaddedInt(NumUtil.safeToInt(sizeWritten));
    }

    public enum StringEncoding {
        NULL(0),
        EMPTY_STRING(1),
        CONSTANT_POOL(2),
        UTF8_BYTE_ARRAY(3),
        CHAR_ARRAY(4),
        LATIN1_BYTE_ARRAY(5);

        public byte byteValue;

        StringEncoding(int byteValue) {
            this.byteValue = (byte) byteValue;
        }
    }

    private class JfrChangeEpochOperation extends JavaVMOperation {

        protected JfrChangeEpochOperation() {
            super(VMOperationInfos.get(JfrChangeEpochOperation.class, "JFR change epoch", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
            changeEpoch();
        }

        /**
         * We need to ensure that all JFR events that are triggered by the current thread are
         * recorded for the next epoch. Otherwise, those JFR events could pollute the data that we
         * currently try to persist. To ensure that, we must uninterruptedly flush all data that is
         * currently in-flight.
         */
        @Uninterruptible(reason = "Prevent pollution of the current thread's thread local JFR buffer.")
        private void changeEpoch() {
            processSamplerBuffers();

            /*
             * Write unflushed data from the thread-local event buffers to the output file. We do
             * *not* reinitialize the thread-local buffers as the individual threads will handle
             * space reclamation on their own time.
             */
            traverseList(getJavaBufferList(), true, true);
            traverseList(getNativeBufferList(), false, true);

            JfrBuffers buffers = globalMemory.getBuffers();
            for (int i = 0; i < globalMemory.getBufferCount(); i++) {
                JfrBuffer buffer = buffers.addressOf(i).read();
                assert !JfrBufferAccess.isAcquired(buffer);
                write(buffer);
                JfrBufferAccess.reinitialize(buffer);
            }

            JfrTraceIdEpoch.getInstance().changeEpoch();

            // Now that the epoch changed, re-register all running threads for the new epoch.
            SubstrateJVM.getThreadRepo().registerRunningThreads();
        }

        /**
         * The VM is at a safepoint, so all other threads have a native state. However, execution
         * sampling could still be executed. For the {@link JfrRecurringCallbackExecutionSampler},
         * it is sufficient to mark this method as uninterruptible to prevent execution of the
         * recurring callbacks. If the SIGPROF-based sampler is used, the signal handler may still
         * be executed at any time for any thread (including the current thread). To prevent races,
         * we need to ensure that there are no threads that execute the SIGPROF handler while we are
         * accessing the currently active buffers of other threads.
         */
        @Uninterruptible(reason = "Prevent JFR recording.")
        private void processSamplerBuffers() {
            assert VMOperation.isInProgressAtSafepoint();
            assert ThreadingSupportImpl.isRecurringCallbackPaused();

            JfrExecutionSampler.singleton().disallowThreadsInSamplerCode();
            try {
                processSamplerBuffers0();
            } finally {
                JfrExecutionSampler.singleton().allowThreadsInSamplerCode();
            }
        }

        @Uninterruptible(reason = "Prevent JFR recording.")
        private void processSamplerBuffers0() {
            SamplerBuffersAccess.processActiveBuffers();
            SamplerBuffersAccess.processFullBuffers(false);
        }
    }

    @Uninterruptible(reason = "Prevent pollution of the current thread's thread local JFR buffer.")
    private void flushStorage() {
        traverseList(getJavaBufferList(), true, false);
        traverseList(getNativeBufferList(), false, false);

        JfrBuffers buffers = globalMemory.getBuffers();
        for (int i = 0; i < globalMemory.getBufferCount(); i++) {
            JfrBuffer buffer = buffers.addressOf(i).read();
            if (!JfrBufferAccess.acquire(buffer)) { // one attempt
                continue;
            }
            write(buffer);
            JfrBufferAccess.reinitialize(buffer);
            JfrBufferAccess.release(buffer);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void traverseList(JfrBufferNodeLinkedList linkedList, boolean java, boolean safepoint) {

        boolean firstIteration = true;
        JfrBufferNode node = linkedList.getAndLockHead();
        JfrBufferNode prev = WordFactory.nullPointer();

        while (node.isNonNull()) {
            try {
                JfrBufferNode next = node.getNext();
                JfrBuffer buffer = node.getValue();
                VMError.guarantee(buffer.isNonNull(), "JFR buffer should exist if we have not already removed its respective node.");

                // Try to get BUFFER with one attempt
                if (!JfrBufferAccess.acquire(buffer)) {
                    prev = node;
                    node = next;
                    continue;
                }
                write(buffer);

                JfrBufferAccess.release(buffer);

                if (!node.getAlive()) {
                    linkedList.removeNode(node, prev);
                    // if removed current node, should not update prev.
                } else {
                    // Only notify java event writer if thread is still alive and we are at an epoch change.
                    if (safepoint && java) {
                        JfrThreadLocal.notifyEventWriter(node.getThread());
                    }
                    prev = node;
                }
                node = next;
            } finally {
                if (firstIteration) {
                    linkedList.releaseList(); // hold onto lock until done with head.
                    firstIteration = false;
                }
            }
        }

        // we may never have entered the while loop if the list is empty.
        if (firstIteration) {
            linkedList.releaseList(); // hold onto lock until done with head.
        }
    }

    public long getChunkStartNanos() {
        return chunkStartNanos;
    }

    public void markChunkFinal() {
        assert lock.isOwner();
        isFinal = true;
    }
}
