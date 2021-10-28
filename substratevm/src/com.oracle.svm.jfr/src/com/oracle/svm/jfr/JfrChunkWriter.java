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
package com.oracle.svm.jfr;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.jfr.traceid.JfrTraceIdEpoch;

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
    private static final byte[] FILE_MAGIC = {'F', 'L', 'R', '\0'};
    private static final short JFR_VERSION_MAJOR = 2;
    private static final short JFR_VERSION_MINOR = 0;
    private static final int CHUNK_SIZE_OFFSET = 8;

    private static final long METADATA_TYPE_ID = 0;
    private static final long CONSTANT_POOL_TYPE_ID = 1;

    private final JfrGlobalMemory globalMemory;
    private final ReentrantLock lock;
    private final boolean compressedInts;
    private long notificationThreshold;

    private String filename;
    private RawFileOperationSupport.RawFileDescriptor fd;
    private long chunkStartTicks;
    private long chunkStartNanos;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrChunkWriter(JfrGlobalMemory globalMemory) {
        this.lock = new ReentrantLock();
        this.compressedInts = true;
        this.globalMemory = globalMemory;
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
        assert lock.isHeldByCurrentThread();
        this.filename = filename;
    }

    public void maybeOpenFile() {
        assert lock.isHeldByCurrentThread();
        if (filename != null) {
            openFile(filename);
        }
    }

    public boolean openFile(String outputFile) {
        assert lock.isHeldByCurrentThread();
        chunkStartNanos = JfrTicks.currentTimeNanos();
        chunkStartTicks = JfrTicks.elapsedTicks();
        filename = outputFile;
        fd = getFileSupport().open(filename, RawFileOperationSupport.FileAccessMode.READ_WRITE);
        writeFileHeader();
        return true;
    }

    @Uninterruptible(reason = "Prevent safepoints as those could change the top pointer.")
    public boolean write(JfrBuffer buffer) {
        assert (JfrBufferAccess.isAcquired(buffer) || VMOperation.isInProgressAtSafepoint());
        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(buffer);
        if (unflushedSize.equal(0)) {
            return false;
        }

        boolean success = getFileSupport().write(fd, buffer.getTop(), unflushedSize);
        JfrBufferAccess.increaseTop(buffer, unflushedSize);
        if (!success) {
            // We lost some data because the write failed.
            return false;
        }
        return getFileSupport().position(fd).greaterThan(WordFactory.signed(notificationThreshold));
    }

    /**
     * We are writing all the in-memory data to the file. However, even though we are at a
     * safepoint, further JFR events can still be triggered by the current thread at any time. This
     * includes allocation and GC events. Therefore, it is necessary that we switch to a new epoch
     * in uninterruptible code at a safepoint.
     */
    public void closeFile(byte[] metadataDescriptor, JfrConstantPool[] repositories) {
        assert lock.isHeldByCurrentThread();
        JfrCloseFileOperation op = new JfrCloseFileOperation();
        op.enqueue();

        // JfrCloseFileOperation will switch to a new epoch so data for the old epoch will not
        // be modified by other threads and can be written without a safepoint

        SignedWord constantPoolPosition = writeCheckpointEvent(repositories);
        SignedWord metadataPosition = writeMetadataEvent(metadataDescriptor);
        patchFileHeader(constantPoolPosition, metadataPosition);
        getFileSupport().close(fd);

        filename = null;
        fd = WordFactory.nullPointer();
    }

    private void writeFileHeader() {
        // Write the header - some of the data gets patched later on.
        getFileSupport().write(fd, FILE_MAGIC);
        getFileSupport().writeShort(fd, JFR_VERSION_MAJOR);
        getFileSupport().writeShort(fd, JFR_VERSION_MINOR);
        assert getFileSupport().position(fd).equal(CHUNK_SIZE_OFFSET);
        getFileSupport().writeLong(fd, 0L); // chunk size
        getFileSupport().writeLong(fd, 0L); // last checkpoint offset
        getFileSupport().writeLong(fd, 0L); // metadata position
        getFileSupport().writeLong(fd, 0L); // startNanos
        getFileSupport().writeLong(fd, 0L); // durationNanos
        getFileSupport().writeLong(fd, chunkStartTicks);
        getFileSupport().writeLong(fd, JfrTicks.getTicksFrequency());
        getFileSupport().writeInt(fd, compressedInts ? 1 : 0);
    }

    public void patchFileHeader(SignedWord constantPoolPosition, SignedWord metadataPosition) {
        long chunkSize = getFileSupport().position(fd).rawValue();
        long durationNanos = JfrTicks.currentTimeNanos() - chunkStartNanos;
        getFileSupport().seek(fd, WordFactory.signed(CHUNK_SIZE_OFFSET));
        getFileSupport().writeLong(fd, chunkSize);
        getFileSupport().writeLong(fd, constantPoolPosition.rawValue());
        getFileSupport().writeLong(fd, metadataPosition.rawValue());
        getFileSupport().writeLong(fd, chunkStartNanos);
        getFileSupport().writeLong(fd, durationNanos);
    }

    private SignedWord writeCheckpointEvent(JfrConstantPool[] repositories) {
        SignedWord start = beginEvent();
        writeCompressedLong(CONSTANT_POOL_TYPE_ID);
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(0); // deltaToNext
        writeBoolean(true); // flush

        SignedWord poolCountPos = getFileSupport().position(fd);
        getFileSupport().writeInt(fd, 0); // We'll patch this later.
        JfrConstantPool[] serializers = JfrSerializerSupport.get().getSerializers();
        int poolCount = writeConstantPools(serializers) + writeConstantPools(repositories);
        SignedWord currentPos = getFileSupport().position(fd);
        getFileSupport().seek(fd, poolCountPos);
        getFileSupport().writeInt(fd, makePaddedInt(poolCount));
        getFileSupport().seek(fd, currentPos);
        endEvent(start);

        return start;
    }

    private int writeConstantPools(JfrConstantPool[] constantPools) {
        int count = 0;
        for (JfrConstantPool constantPool : constantPools) {
            int poolCount = constantPool.write(this);
            count += poolCount;
        }
        return count;
    }

    private SignedWord writeMetadataEvent(byte[] metadataDescriptor) {
        SignedWord start = beginEvent();
        writeCompressedLong(METADATA_TYPE_ID);
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(0); // metadata id
        writeBytes(metadataDescriptor); // payload
        endEvent(start);
        return start;
    }

    public boolean shouldRotateDisk() {
        assert lock.isHeldByCurrentThread();
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

    public void writeBoolean(boolean value) {
        assert lock.isHeldByCurrentThread() || VMOperationControl.isDedicatedVMOperationThread() && lock.isLocked();
        writeByte((byte) (value ? 1 : 0));
    }

    public void writeByte(byte value) {
        assert lock.isHeldByCurrentThread() || VMOperationControl.isDedicatedVMOperationThread() && lock.isLocked();
        getFileSupport().writeByte(fd, value);
    }

    public void writeBytes(byte[] values) {
        assert lock.isHeldByCurrentThread() || VMOperationControl.isDedicatedVMOperationThread() && lock.isLocked();
        getFileSupport().write(fd, values);
    }

    public void writeCompressedInt(int value) {
        assert lock.isHeldByCurrentThread() || VMOperationControl.isDedicatedVMOperationThread() && lock.isLocked();
        writeCompressedLong(value & 0xFFFFFFFFL);
    }

    public void writeCompressedLong(long value) {
        assert lock.isHeldByCurrentThread() || VMOperationControl.isDedicatedVMOperationThread() && lock.isLocked();
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
            getFileSupport().writeByte(fd, StringEncoding.UTF8_BYTE_ARRAY.byteValue);
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
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

    private class JfrCloseFileOperation extends JavaVMOperation {
        protected JfrCloseFileOperation() {
            // Some of the JDK code that deals with files uses Java synchronization. So, we need to
            // allow Java synchronization for this VM operation.
            super("JFR close file", SystemEffect.SAFEPOINT);
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
            // Write unflushed data from the thread local buffers but do *not* reinitialize them
            // The thread local code will handle space reclamation on their own time
            for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                JfrBuffer buffer = JfrThreadLocal.getJavaBuffer(thread);
                if (buffer.isNonNull()) {
                    write(buffer);
                    JfrThreadLocal.notifyEventWriter(thread);
                }
                buffer = JfrThreadLocal.getNativeBuffer(thread);
                if (buffer.isNonNull()) {
                    write(buffer);
                }
            }

            JfrBuffers buffers = globalMemory.getBuffers();
            for (int i = 0; i < globalMemory.getBufferCount(); i++) {
                JfrBuffer buffer = buffers.addressOf(i).read();
                assert !JfrBufferAccess.isAcquired(buffer);
                write(buffer);
                JfrBufferAccess.reinitialize(buffer);
            }
            JfrTraceIdEpoch.getInstance().changeEpoch();
        }
    }

    public long getChunkStartNanos() {
        return chunkStartNanos;
    }
}
