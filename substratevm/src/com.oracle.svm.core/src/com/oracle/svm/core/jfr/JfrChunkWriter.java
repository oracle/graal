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
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;
import org.graalvm.nativeimage.IsolateThread;

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
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.locks.VMMutex;

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
    public static final short JFR_VERSION_MINOR = 1;
    private static final int CHUNK_SIZE_OFFSET = 8;
    private static final int FILE_STATE_OFFSET = 64;
    private static final byte COMPLETE = 0;
    private static final short FLAG_COMPRESSED_INTS = 0b01;
    private static final short FLAG_CHUNK_FINAL = 0b10;
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
    private SignedWord lastCheckpointOffset;
    private boolean newChunk;
    private boolean isFinal;
    private long lastMetadataId;
    private SignedWord metadataPosition;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrChunkWriter(JfrGlobalMemory globalMemory) {
        this.lock = new VMMutex("JfrChunkWriter");
        this.compressedInts = true;
        this.globalMemory = globalMemory;
        this.metadata = new JfrMetadata(null);
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
        lastMetadataId = -1;
        metadataPosition = WordFactory.signed(-1);
        chunkStartNanos = JfrTicks.currentTimeNanos();
        chunkStartTicks = JfrTicks.elapsedTicks();
        filename = outputFile;
        fd = getFileSupport().open(filename, RawFileOperationSupport.FileAccessMode.READ_WRITE);
        writeFileHeader();
        lastCheckpointOffset = WordFactory.signed(-1); // must reset this on new chunk
        return true;
    }

    @Uninterruptible(reason = "Prevent safepoints as those could change the flushed position.")
    public boolean write(JfrBuffer buffer) {
        assert JfrBufferAccess.isLockedByCurrentThread(buffer) || VMOperation.isInProgressAtSafepoint() || buffer.getBufferType() == JfrBufferType.C_HEAP;
        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(buffer);
        if (unflushedSize.equal(0)) {
            return false;
        }

        boolean success = getFileSupport().write(fd, JfrBufferAccess.getFlushedPos(buffer), unflushedSize);

        JfrBufferAccess.increaseFlushedPos(buffer, unflushedSize);

        if (!success) {
            // We lost some data because the write failed.
            return false;
        }
        return getFileSupport().position(fd).greaterThan(WordFactory.signed(notificationThreshold));
    }

    /**
     * Write all the in-memory data to the file.
     */
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

        lastCheckpointOffset = SubstrateJVM.getThreadRepo().maybeWrite(this, false, lastCheckpointOffset);

        SignedWord constantPoolPosition = writeCheckpointEvent(false);
        writeMetadataEvent();
        patchFileHeader(constantPoolPosition, false);
        getFileSupport().close(fd);

        filename = null;
        fd = WordFactory.nullPointer();
    }

    public void flush() {
        assert lock.isOwner();
        flushStorage(true);

        lastCheckpointOffset = SubstrateJVM.getThreadRepo().maybeWrite(this, true, lastCheckpointOffset);
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
        assert getFileSupport().position(fd).equal(FILE_STATE_OFFSET);
        getFileSupport().writeByte(fd, nextGeneration()); // A 1 byte generation is written
        getFileSupport().writeByte(fd, (byte) 0); // A 1 byte padding
        getFileSupport().writeShort(fd, computeHeaderFlags());
    }

    private void patchFileHeader(SignedWord constantPoolPosition, boolean flush) {
        assert lock.isOwner();
        SignedWord currentPos = getFileSupport().position(fd);
        long chunkSize = getFileSupport().position(fd).rawValue();
        long durationNanos = JfrTicks.currentTimeNanos() - chunkStartNanos;
        getFileSupport().seek(fd, WordFactory.signed(CHUNK_SIZE_OFFSET));
        getFileSupport().writeLong(fd, chunkSize);
        getFileSupport().writeLong(fd, constantPoolPosition.rawValue());
        assert metadataPosition.greaterThan(0);
        getFileSupport().writeLong(fd, metadataPosition.rawValue());
        getFileSupport().writeLong(fd, chunkStartNanos);
        getFileSupport().writeLong(fd, durationNanos);
        getFileSupport().seek(fd, WordFactory.signed(FILE_STATE_OFFSET));
        if (flush) {
            // chunk is not finished
            getFileSupport().writeByte(fd, nextGeneration());
        } else {
            getFileSupport().writeByte(fd, COMPLETE);
        }
        getFileSupport().writeByte(fd, (byte) 0);
        getFileSupport().writeShort(fd, computeHeaderFlags());

        // need to move pointer back to correct position for next write
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

    private byte nextGeneration() {
        if (generation == Byte.MAX_VALUE) {
            // Restart counter if required.
            generation = 1;
            return Byte.MAX_VALUE;
        }
        return generation++;
    }

    private SignedWord writeCheckpointEvent(boolean flush) {
        assert lock.isOwner();
        SignedWord start = beginEvent();

        if (lastCheckpointOffset.lessThan(0)) {
            lastCheckpointOffset = start;
        }
        writeCompressedLong(JfrReservedEvent.EVENT_CHECKPOINT.getId());
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(lastCheckpointOffset.subtract(start).rawValue()); // deltaToNext
        writeByte(JfrCheckpointType.Flush.getId());

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
        count += SubstrateJVM.getStackTraceRepo().write(this, flush);
        count += SubstrateJVM.getMethodRepo().write(this, flush);
        count += SubstrateJVM.getTypeRepository().write(this, flush);
        count += SubstrateJVM.getSymbolRepository().write(this, flush);
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

    public void setMetadata(byte[] bytes) {
        metadata.setDescriptor(bytes);
    }

    private void writeMetadataEvent() {
        assert lock.isOwner();
        // always write metadata on a new chunk!
        if (lastMetadataId == metadata.getCurrentMetadataId()) {
            return;
        }
        SignedWord start = beginEvent();
        writeCompressedLong(JfrReservedEvent.EVENT_METADATA.getId());
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(metadata.getCurrentMetadataId()); // metadata id
        writeBytes(metadata.getDescriptor()); // payload
        endEvent(start);
        metadataPosition = start;
        lastMetadataId = metadata.getCurrentMetadataId();
    }

    public boolean shouldRotateDisk() {
        assert lock.isOwner();
        return getFileSupport().isValid(fd) && getFileSupport().size(fd).greaterThan(WordFactory.signed(notificationThreshold));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public SignedWord beginEvent() {
        SignedWord start = getFileSupport().position(fd);
        // Write a placeholder for the size. Will be patched by endEvent,
        getFileSupport().writeInt(fd, 0);
        return start;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void endEvent(SignedWord start) {
        SignedWord end = getFileSupport().position(fd);
        SignedWord writtenBytes = end.subtract(start);
        getFileSupport().seek(fd, start);
        getFileSupport().writeInt(fd, makePaddedInt(writtenBytes.rawValue()));
        getFileSupport().seek(fd, end);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeBoolean(boolean value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        writeByte((byte) (value ? 1 : 0));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeByte(byte value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        getFileSupport().writeByte(fd, value);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeBytes(byte[] values) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        getFileSupport().write(fd, values);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeCompressedInt(int value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        writeCompressedLong(value & 0xFFFFFFFFL);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeInt(int value) {
        assert lock.isOwner() || VMOperationControl.isDedicatedVMOperationThread() && lock.hasOwner();
        getFileSupport().writeInt(fd, makePaddedInt(value));
    }

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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int makePaddedInt(long sizeWritten) {
        return JfrNativeEventWriter.makePaddedInt(safeToInt(sizeWritten));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static int safeToInt(long v) {
        assert isInt(v);
        return (int) v;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInt(long l) {
        return (int) l == l;
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

            flushStorage(false);
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
    private void flushStorage(boolean flush) {
        /*
         * Write unflushed data from the thread-local event buffers to the output file. We do *not*
         * reinitialize the thread-local buffers as the individual threads will handle space
         * reclamation on their own time.
         */
        traverseList(getJavaBufferList(), flush);
        traverseList(getNativeBufferList(), flush);

        JfrBuffers buffers = globalMemory.getBuffers();
        for (int i = 0; i < globalMemory.getBufferCount(); i++) {
            JfrBuffer buffer = buffers.addressOf(i).read();
            if (!JfrBufferAccess.tryLock(buffer)) { // one attempt
                assert flush;
                continue;
            }
            write(buffer);
            JfrBufferAccess.reinitialize(buffer);
            JfrBufferAccess.unlock(buffer);
        }
    }

    @Uninterruptible(reason = "Prevent pollution of the current thread's thread local JFR buffer. Locks linked list with no transition. ")
    private void traverseList(JfrBufferNodeLinkedList linkedList, boolean flush) {

        // hold onto lock until done with the head node.
        JfrBufferNode node = linkedList.getHead();
        JfrBufferNode prev = WordFactory.nullPointer();

        while (node.isNonNull()) {
            JfrBufferNode next = node.getNext();
            JfrBuffer buffer = node.getValue();
            assert buffer.isNonNull();

            if (!node.getAlive()) {
                // It is safe to free without acquiring because the owning thread is gone.
                assert !JfrBufferAccess.isLockedByCurrentThread(buffer);
                JfrBufferAccess.free(buffer);
                linkedList.removeNode(node, prev);
                // if removed current node, should not update prev.
                node = next;
                continue;
            }

            if (flush) {
                /*
                 * I/O operations may be slow, so this flushes to the global buffers instead of
                 * writing to disk directly. This mitigates the risk of acquiring the TLBs for too
                 * long.
                 */
                JfrThreadLocal.flushNoReset(buffer);
            } else {
                /*
                 * Buffer should not be locked when entering a safepoint. Lock buffer here to
                 * satisfy assertion checks.
                 */
                if (!JfrBufferAccess.tryLock(buffer)) {
                    assert false;
                }
                try {
                    write(buffer);
                } finally {
                    JfrBufferAccess.unlock(buffer);
                }
            }
            prev = node;
            node = next;
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
