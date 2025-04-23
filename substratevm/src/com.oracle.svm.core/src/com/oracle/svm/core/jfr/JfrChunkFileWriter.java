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

import static com.oracle.svm.core.jfr.JfrThreadLocal.getJavaBufferList;
import static com.oracle.svm.core.jfr.JfrThreadLocal.getNativeBufferList;

import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jfr.oldobject.JfrOldObjectRepository;
import com.oracle.svm.core.jfr.sampler.JfrExecutionSampler;
import com.oracle.svm.core.jfr.sampler.JfrRecurringCallbackExecutionSampler;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileAccessMode;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.sampler.SamplerBuffersAccess;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.word.Word;

/**
 * This class is used when writing the in-memory JFR data to a file. For all operations, except
 * those listed in {@link JfrUnlockedChunkWriter}, it is necessary to acquire the {@link #lock}
 * before invoking the operation.
 *
 * <p>
 * If an operation needs both a safepoint and the lock, then it is necessary to acquire the lock
 * outside of the safepoint. Otherwise, this will result in deadlocks as other threads may hold the
 * lock while they are paused at a safepoint.
 * </p>
 */
public final class JfrChunkFileWriter implements JfrChunkWriter {
    public static final byte[] FILE_MAGIC = {'F', 'L', 'R', '\0'};
    public static final short JFR_VERSION_MAJOR = 2;
    public static final short JFR_VERSION_MINOR = 1;
    private static final int CHUNK_SIZE_OFFSET = 8;
    private static final int FILE_STATE_OFFSET = 64;
    private static final byte COMPLETE = 0;
    private static final short FLAG_COMPRESSED_INTS = 0b01;
    private static final short FLAG_CHUNK_FINAL = 0b10;

    private final VMMutex lock;
    private final JfrGlobalMemory globalMemory;
    private final JfrMetadata metadata;
    private final JfrRepository[] flushCheckpointRepos;
    private final JfrRepository[] threadCheckpointRepos;
    private final boolean compressedInts;

    private long notificationThreshold;

    private String filename;
    private RawFileDescriptor fd;
    private long chunkStartTicks;
    private long chunkStartNanos;
    private byte nextGeneration;
    private boolean newChunk;
    private boolean isFinal;
    private long lastMetadataId;
    private long metadataPosition;
    private long lastCheckpointOffset;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrChunkFileWriter(JfrGlobalMemory globalMemory, JfrStackTraceRepository stackTraceRepo, JfrMethodRepository methodRepo, JfrTypeRepository typeRepo, JfrSymbolRepository symbolRepo,
                    JfrThreadRepository threadRepo, JfrOldObjectRepository oldObjectRepo) {
        this.lock = new VMMutex("jfrChunkWriter");
        this.globalMemory = globalMemory;
        this.metadata = new JfrMetadata(null);
        this.compressedInts = true;

        /*
         * Repositories earlier in the write order may reference entries of repositories later in
         * the write order. This ordering is required to prevent races during flushing without
         * changing epoch.
         */
        this.flushCheckpointRepos = new JfrRepository[]{stackTraceRepo, methodRepo, oldObjectRepo, typeRepo, symbolRepo};
        this.threadCheckpointRepos = new JfrRepository[]{threadRepo};
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

    @Override
    public void unlock() {
        lock.unlock();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean hasOpenFile() {
        return getFileSupport().isValid(fd);
    }

    @Override
    public long getChunkStartNanos() {
        return chunkStartNanos;
    }

    @Override
    public void setFilename(String filename) {
        assert lock.isOwner();
        this.filename = filename;
    }

    @Override
    public void maybeOpenFile() {
        assert lock.isOwner();
        if (filename != null) {
            openFile(filename);
        }
    }

    @Override
    public void openFile(String outputFile) {
        assert lock.isOwner();
        filename = outputFile;
        fd = getFileSupport().create(filename, FileCreationMode.CREATE_OR_REPLACE, FileAccessMode.READ_WRITE);

        chunkStartTicks = JfrTicks.elapsedTicks();
        chunkStartNanos = JfrTicks.currentTimeNanos();
        nextGeneration = 1;
        newChunk = true;
        isFinal = false;
        lastMetadataId = -1;
        metadataPosition = -1;
        lastCheckpointOffset = -1;

        writeFileHeader();
    }

    @Override
    @Uninterruptible(reason = "Prevent safepoints as those could change the flushed position.")
    public void write(JfrBuffer buffer) {
        assert lock.isOwner();
        assert buffer.isNonNull();
        assert buffer.getBufferType() == JfrBufferType.C_HEAP || VMOperation.isInProgressAtSafepoint() || JfrBufferNodeAccess.isLockedByCurrentThread(buffer.getNode());

        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(buffer);
        if (unflushedSize.equal(0)) {
            return;
        }

        boolean success = getFileSupport().write(fd, JfrBufferAccess.getFlushedPos(buffer), unflushedSize);
        if (success) {
            JfrBufferAccess.increaseFlushedPos(buffer, unflushedSize);
        }
    }

    @Override
    public void flush() {
        assert lock.isOwner();

        flushStorage(true);

        writeThreadCheckpoint(true);
        writeFlushCheckpoint(true);
        writeMetadataEvent();
        patchFileHeader(true);

        newChunk = false;
    }

    @Override
    public void markChunkFinal() {
        assert lock.isOwner();
        isFinal = true;
    }

    /**
     * Write all the in-memory data to the file.
     */
    @Override
    public void closeFile() {
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

        writeThreadCheckpoint(false);
        writeFlushCheckpoint(false);
        writeMetadataEvent();
        patchFileHeader(false);

        getFileSupport().close(fd);
        filename = null;
        fd = Word.nullPointer();
    }

    private void writeFileHeader() {
        /* Write the header - some data gets patched later on. */
        getFileSupport().write(fd, FILE_MAGIC);
        getFileSupport().writeShort(fd, JFR_VERSION_MAJOR);
        getFileSupport().writeShort(fd, JFR_VERSION_MINOR);
        assert getFileSupport().position(fd) == CHUNK_SIZE_OFFSET;
        getFileSupport().writeLong(fd, 0L); // chunk size
        getFileSupport().writeLong(fd, 0L); // last checkpoint offset
        getFileSupport().writeLong(fd, 0L); // metadata position
        getFileSupport().writeLong(fd, chunkStartNanos);
        getFileSupport().writeLong(fd, 0L); // durationNanos
        getFileSupport().writeLong(fd, chunkStartTicks);
        getFileSupport().writeLong(fd, JfrTicks.getTicksFrequency());
        assert getFileSupport().position(fd) == FILE_STATE_OFFSET;
        getFileSupport().writeByte(fd, getAndIncrementGeneration());
        getFileSupport().writeByte(fd, (byte) 0); // padding
        getFileSupport().writeShort(fd, computeHeaderFlags());
    }

    private void patchFileHeader(boolean flushpoint) {
        assert lock.isOwner();
        assert metadataPosition > 0;
        assert lastCheckpointOffset > 0;

        byte generation = flushpoint ? getAndIncrementGeneration() : COMPLETE;
        long currentPos = getFileSupport().position(fd);
        long durationNanos = JfrTicks.currentTimeNanos() - chunkStartNanos;

        getFileSupport().seek(fd, CHUNK_SIZE_OFFSET);
        getFileSupport().writeLong(fd, currentPos);
        getFileSupport().writeLong(fd, lastCheckpointOffset);
        getFileSupport().writeLong(fd, metadataPosition);
        getFileSupport().writeLong(fd, chunkStartNanos);
        getFileSupport().writeLong(fd, durationNanos);

        getFileSupport().seek(fd, FILE_STATE_OFFSET);
        getFileSupport().writeByte(fd, generation);
        getFileSupport().writeByte(fd, (byte) 0);
        getFileSupport().writeShort(fd, computeHeaderFlags());

        /* Move pointer back to correct position for next write. */
        getFileSupport().seek(fd, currentPos);
    }

    private short computeHeaderFlags() {
        short flags = 0;
        if (compressedInts) {
            flags |= FLAG_COMPRESSED_INTS;
        }
        if (isFinal) {
            flags |= FLAG_CHUNK_FINAL;
        }
        return flags;
    }

    private byte getAndIncrementGeneration() {
        if (nextGeneration == Byte.MAX_VALUE) {
            // Restart counter if required.
            nextGeneration = 1;
            return Byte.MAX_VALUE;
        }
        return nextGeneration++;
    }

    private void writeFlushCheckpoint(boolean flushpoint) {
        writeCheckpointEvent(JfrCheckpointType.Flush, flushCheckpointRepos, newChunk, flushpoint);
    }

    private void writeThreadCheckpoint(boolean flushpoint) {
        assert threadCheckpointRepos.length == 1 && threadCheckpointRepos[0] == SubstrateJVM.getThreadRepo();
        /* The code below is only atomic enough because the epoch can't change while flushing. */
        if (SubstrateJVM.getThreadRepo().hasUnflushedData()) {
            writeCheckpointEvent(JfrCheckpointType.Threads, threadCheckpointRepos, false, flushpoint);
        } else if (!flushpoint) {
            /* After an epoch change, the previous epoch data must be completely clear. */
            SubstrateJVM.getThreadRepo().clearPreviousEpoch();
        }
    }

    private void writeCheckpointEvent(JfrCheckpointType type, JfrRepository[] repositories, boolean writeSerializers, boolean flushpoint) {
        assert lock.isOwner();

        long start = beginEvent();
        writeCompressedLong(JfrReservedEvent.CHECKPOINT.getId());
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(getDeltaToLastCheckpoint(start));
        writeByte(type.getId());

        long poolCountPos = getFileSupport().position(fd);
        getFileSupport().writeInt(fd, 0); // pool count (patched below)

        int poolCount = writeSerializers ? writeSerializers() : 0;
        poolCount += writeConstantPools(repositories, flushpoint);

        long currentPos = getFileSupport().position(fd);
        getFileSupport().seek(fd, poolCountPos);
        writePaddedInt(poolCount);

        getFileSupport().seek(fd, currentPos);
        endEvent(start);

        lastCheckpointOffset = start;
    }

    private long getDeltaToLastCheckpoint(long startOfNewCheckpoint) {
        if (lastCheckpointOffset < 0) {
            return 0L;
        }
        return lastCheckpointOffset - startOfNewCheckpoint;
    }

    private int writeSerializers() {
        JfrSerializer[] serializers = JfrSerializerSupport.get().getSerializers();
        for (JfrSerializer serializer : serializers) {
            serializer.write(this);
        }
        return serializers.length;
    }

    private int writeConstantPools(JfrRepository[] repositories, boolean flushpoint) {
        int poolCount = 0;
        for (JfrRepository repo : repositories) {
            poolCount += repo.write(this, flushpoint);
        }
        return poolCount;
    }

    @Override
    public void setMetadata(byte[] bytes) {
        metadata.setDescriptor(bytes);
    }

    private void writeMetadataEvent() {
        assert lock.isOwner();

        /* Only write the metadata if this is a new chunk or if it changed in the meanwhile. */
        long currentMetadataId = metadata.getCurrentMetadataId();
        if (lastMetadataId == currentMetadataId) {
            return;
        }

        long start = beginEvent();
        writeCompressedLong(JfrReservedEvent.METADATA.getId());
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(currentMetadataId);
        writeBytes(metadata.getDescriptor()); // payload
        endEvent(start);

        metadataPosition = start;
        lastMetadataId = currentMetadataId;
    }

    @Override
    public boolean shouldRotateDisk() {
        assert lock.isOwner();
        return getFileSupport().isValid(fd) && getFileSupport().size(fd) > notificationThreshold;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long beginEvent() {
        long start = getFileSupport().position(fd);
        // Write a placeholder for the size. Will be patched by endEvent,
        getFileSupport().writeInt(fd, 0);
        return start;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void endEvent(long start) {
        long end = getFileSupport().position(fd);
        long writtenBytes = end - start;
        assert (int) writtenBytes == writtenBytes;

        getFileSupport().seek(fd, start);
        writePaddedInt(writtenBytes);
        getFileSupport().seek(fd, end);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeBoolean(boolean value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        writeByte((byte) (value ? 1 : 0));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeByte(byte value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        getFileSupport().writeByte(fd, value);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeBytes(byte[] values) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        getFileSupport().write(fd, values);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeCompressedInt(int value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        writeCompressedLong(value & 0xFFFFFFFFL);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writePaddedInt(long value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        assert (int) value == value;
        getFileSupport().writeInt(fd, JfrNativeEventWriter.makePaddedInt((int) value));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeCompressedLong(long value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
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

    @Override
    public void writeString(String str) {
        if (str.isEmpty()) {
            getFileSupport().writeByte(fd, StringEncoding.EMPTY_STRING.getValue());
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            getFileSupport().writeByte(fd, StringEncoding.UTF8_BYTE_ARRAY.getValue());
            writeCompressedInt(bytes.length);
            getFileSupport().write(fd, bytes);
        }
    }

    @Uninterruptible(reason = "Prevent pollution of the current thread's thread local JFR buffer.")
    private void flushStorage(boolean flushpoint) {
        traverseThreadLocalBuffers(getJavaBufferList(), flushpoint);
        traverseThreadLocalBuffers(getNativeBufferList(), flushpoint);

        flushGlobalMemory(flushpoint);
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private void traverseThreadLocalBuffers(JfrBufferList list, boolean flushpoint) {
        JfrBufferNode node = list.getHead();
        JfrBufferNode prev = Word.nullPointer();

        while (node.isNonNull()) {
            JfrBufferNode next = node.getNext();
            boolean lockAcquired = JfrBufferNodeAccess.tryLock(node);
            if (lockAcquired) {
                JfrBuffer buffer = JfrBufferNodeAccess.getBuffer(node);
                if (buffer.isNull()) {
                    list.removeNode(node, prev);
                    JfrBufferNodeAccess.free(node);
                    node = next;
                    continue;
                }

                try {
                    if (flushpoint) {
                        /*
                         * I/O operations may be slow, so this flushes to the global buffers instead
                         * of writing to disk directly. This mitigates the risk of acquiring the
                         * thread-local buffers for too long.
                         */
                        SubstrateJVM.getGlobalMemory().write(buffer, true);
                    } else {
                        write(buffer);
                    }
                    /*
                     * The flushed position is modified in the calls above. We do *not* reinitialize
                     * the thread-local buffers as the individual threads will handle space
                     * reclamation on their own time.
                     */
                } finally {
                    JfrBufferNodeAccess.unlock(node);
                }
            }

            assert lockAcquired || flushpoint;
            prev = node;
            node = next;
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private void flushGlobalMemory(boolean flushpoint) {
        JfrBufferList buffers = globalMemory.getBuffers();
        JfrBufferNode node = buffers.getHead();
        while (node.isNonNull()) {
            boolean lockAcquired = JfrBufferNodeAccess.tryLock(node);
            if (lockAcquired) {
                try {
                    JfrBuffer buffer = JfrBufferNodeAccess.getBuffer(node);
                    write(buffer);
                    JfrBufferAccess.reinitialize(buffer);
                } finally {
                    JfrBufferNodeAccess.unlock(node);
                }
            }
            assert lockAcquired || flushpoint;
            node = node.getNext();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isLockedByCurrentThread() {
        return lock.isOwner();
    }

    public enum StringEncoding {
        NULL(0),
        EMPTY_STRING(1),
        CONSTANT_POOL(2),
        UTF8_BYTE_ARRAY(3),
        CHAR_ARRAY(4),
        LATIN1_BYTE_ARRAY(5);

        private final byte value;

        StringEncoding(int value) {
            this.value = NumUtil.safeToByte(value);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public byte getValue() {
            return value;
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
            flushStorage(false);

            /* Notify all event writers that the epoch changed. */
            for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                JfrThreadLocal.notifyEventWriter(thread);
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
        private static void processSamplerBuffers() {
            assert VMOperation.isInProgressAtSafepoint();
            assert RecurringCallbackSupport.isCallbackUnsupportedOrTimerSuspended();

            JfrExecutionSampler.singleton().disallowThreadsInSamplerCode();
            try {
                processSamplerBuffers0();
            } finally {
                JfrExecutionSampler.singleton().allowThreadsInSamplerCode();
            }
        }

        @Uninterruptible(reason = "Prevent JFR recording.")
        private static void processSamplerBuffers0() {
            SamplerBuffersAccess.processActiveBuffers();
            SamplerBuffersAccess.processFullBuffers(false);
        }
    }
}
