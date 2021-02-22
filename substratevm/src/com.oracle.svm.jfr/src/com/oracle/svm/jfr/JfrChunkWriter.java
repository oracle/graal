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

import static jdk.jfr.internal.LogLevel.ERROR;
import static jdk.jfr.internal.LogTag.JFR_SYSTEM;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.Target_java_nio_DirectByteBuffer;
import com.oracle.svm.core.thread.JavaVMOperation;

import jdk.jfr.internal.Logger;

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
    private static final int JFR_VERSION_MAJOR = 2;
    private static final int JFR_VERSION_MINOR = 0;
    private static final int CHUNK_SIZE_OFFSET = 8;

    private final ReentrantLock lock;
    private final boolean compressedInts;
    private long notificationThreshold;

    private String filename;
    private RandomAccessFile file;
    private long chunkStartTicks;
    private long chunkStartNanos;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrChunkWriter() {
        this.lock = new ReentrantLock();
        this.compressedInts = true;
    }

    @Override
    public void initialize(long maxChunkSize) {
        this.notificationThreshold = maxChunkSize;
    }

    @Override
    public JfrChunkWriter lock() {
        lock.lock();
        return this;
    }

    public void unlock() {
        lock.unlock();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean hasOpenFile() {
        return file != null;
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
        filename = outputFile;
        chunkStartNanos = JfrTicks.currentTimeNanos();
        chunkStartTicks = JfrTicks.elapsedTicks();
        try {
            file = new RandomAccessFile(outputFile, "rw");
            writeFileHeader();
            // TODO: this should probably also write all live threads
            return true;
        } catch (IOException e) {
            Logger.log(JFR_SYSTEM, ERROR, "Error while writing file " + filename + ": " + e.getMessage());
            return false;
        }
    }

    public boolean write(JfrBuffer buffer) {
        assert lock.isHeldByCurrentThread();
        int capacity = NumUtil.safeToInt(JfrBufferAccess.getUnflushedSize(buffer).rawValue());
        Target_java_nio_DirectByteBuffer bb = new Target_java_nio_DirectByteBuffer(JfrBufferAccess.getDataStart(buffer).rawValue(), capacity);
        FileChannel fc = file.getChannel();
        try {
            fc.write(SubstrateUtil.cast(bb, ByteBuffer.class));
            return file.getFilePointer() > notificationThreshold;
        } catch (IOException e) {
            Logger.log(JFR_SYSTEM, ERROR, "Error while writing file " + filename + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * We are writing all the in-memory data to the file. However, even though we are at a
     * safepoint, further JFR events can still be triggered by the current thread at any time. This
     * includes allocation and GC events. Therefore, it is necessary that our whole JFR
     * infrastructure is epoch-based. So, we can uninterruptibly switch to a new epoch before we
     * start writing out the data of the old epoch.
     */
    // TODO: add more logic to all JfrRepositories so that it is possible to switch the epoch. The
    // global JFR memory must also support different epochs.
    public void closeFile(byte[] metadataDescriptor, JfrRepository[] repositories) {
        assert lock.isHeldByCurrentThread();
        JfrCloseFileOperation op = new JfrCloseFileOperation(metadataDescriptor, repositories);
        op.enqueue();
    }

    private void writeFileHeader() throws IOException {
        // Write the header - some of the data gets patched later on.
        file.write(FILE_MAGIC);
        file.writeShort(JFR_VERSION_MAJOR);
        file.writeShort(JFR_VERSION_MINOR);
        assert file.getFilePointer() == CHUNK_SIZE_OFFSET;
        file.writeLong(0L); // chunk size
        file.writeLong(0L); // last checkpoint offset
        file.writeLong(0L); // metadata position
        file.writeLong(0L); // startNanos
        file.writeLong(0L); // durationNanos
        file.writeLong(chunkStartTicks);
        file.writeLong(JfrTicks.getTicksFrequency());
        file.writeInt(compressedInts ? 1 : 0);
    }

    private void patchFileHeader(long constantPoolPosition, long metadataPosition) throws IOException {
        long chunkSize = file.getFilePointer();
        long durationNanos = JfrTicks.currentTimeNanos() - chunkStartNanos;
        file.seek(CHUNK_SIZE_OFFSET);
        file.writeLong(chunkSize);
        file.writeLong(constantPoolPosition);
        file.writeLong(metadataPosition);
        file.writeLong(chunkStartNanos);
        file.writeLong(durationNanos);
    }

    private long writeCheckpointEvent(JfrRepository[] repositories) throws IOException {
        JfrSerializer[] serializers = JfrSerializerSupport.get().getSerializers();

        // TODO: Write the global buffers of the previous epoch to disk. Assert that none of the
        // buffers from the previous epoch is acquired (all operations on the buffers must have
        // finished before the safepoint).

        long start = beginEvent();
        writeCompressedLong(JfrEvents.CheckpointEvent.getId());
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(0); // deltaToNext
        file.writeBoolean(true); // flush
        writeCompressedInt(serializers.length + repositories.length); // pools size
        writeSerializers(serializers);
        writeRepositories(repositories);
        endEvent(start);

        return start;
    }

    private void writeSerializers(JfrSerializer[] serializers) throws IOException {
        for (int i = 0; i < serializers.length; i++) {
            serializers[i].write(this);
        }
    }

    private void writeRepositories(JfrRepository[] constantPools) throws IOException {
        for (int i = 0; i < constantPools.length; i++) {
            constantPools[i].write(this);
        }
    }

    private long writeMetadataEvent(byte[] metadataDescriptor) throws IOException {
        long start = beginEvent();
        writeCompressedLong(JfrEvents.MetadataEvent.getId());
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(0); // metadata id
        file.write(metadataDescriptor); // payload
        endEvent(start);
        return start;
    }

    public boolean shouldRotateDisk() {
        assert lock.isHeldByCurrentThread();
        try {
            return file != null && file.length() > notificationThreshold;
        } catch (IOException ex) {
            Logger.log(JFR_SYSTEM, ERROR, "Could not check file size to determine chunk rotation: " + ex.getMessage());
            return false;
        }
    }

    private long beginEvent() throws IOException {
        long start = file.getFilePointer();
        // Write a placeholder for the size. Will be patched by endEvent,
        file.writeInt(0);
        return start;
    }

    private void endEvent(long start) throws IOException {
        long end = file.getFilePointer();
        long writtenBytes = end - start;
        file.seek(start);
        file.writeInt(makePaddedInt(writtenBytes));
        file.seek(end);
    }

    public void writeBoolean(boolean value) throws IOException {
        assert lock.isHeldByCurrentThread();
        writeCompressedInt(value ? 1 : 0);
    }

    public void writeCompressedInt(int value) throws IOException {
        assert lock.isHeldByCurrentThread();
        writeCompressedLong(value & 0xFFFFFFFFL);
    }

    public void writeCompressedLong(long value) throws IOException {
        assert lock.isHeldByCurrentThread();
        long v = value;
        if ((v & ~0x7FL) == 0L) {
            file.write((byte) v); // 0-6
            return;
        }
        file.write((byte) (v | 0x80L)); // 0-6
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            file.write((byte) v); // 7-13
            return;
        }
        file.write((byte) (v | 0x80L)); // 7-13
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            file.write((byte) v); // 14-20
            return;
        }
        file.write((byte) (v | 0x80L)); // 14-20
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            file.write((byte) v); // 21-27
            return;
        }
        file.write((byte) (v | 0x80L)); // 21-27
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            file.write((byte) v); // 28-34
            return;
        }
        file.write((byte) (v | 0x80L)); // 28-34
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            file.write((byte) v); // 35-41
            return;
        }
        file.write((byte) (v | 0x80L)); // 35-41
        v >>>= 7;
        if ((v & ~0x7FL) == 0L) {
            file.write((byte) v); // 42-48
            return;
        }
        file.write((byte) (v | 0x80L)); // 42-48
        v >>>= 7;

        if ((v & ~0x7FL) == 0L) {
            file.write((byte) v); // 49-55
            return;
        }
        file.write((byte) (v | 0x80L)); // 49-55
        file.write((byte) (v >>> 7)); // 56-63, last byte as is.
    }

    private static int makePaddedInt(long sizeWritten) {
        return JfrNativeEventWriter.makePaddedInt(NumUtil.safeToInt(sizeWritten));
    }

    private class JfrCloseFileOperation extends JavaVMOperation {
        private final byte[] metadataDescriptor;
        private final JfrRepository[] repositories;

        protected JfrCloseFileOperation(byte[] metadataDescriptor, JfrRepository[] repositories) {
            // Some of the JDK code that deals with files uses Java synchronization. So, we need to
            // allow Java synchronization for this VM operation.
            super("JFR close file", SystemEffect.SAFEPOINT, true);
            this.metadataDescriptor = metadataDescriptor;
            this.repositories = repositories;
        }

        @Override
        protected void operate() {
            changeEpoch();
            try {

                long constantPoolPosition = writeCheckpointEvent(repositories);
                long metadataPosition = writeMetadataEvent(metadataDescriptor);
                patchFileHeader(constantPoolPosition, metadataPosition);
                file.close();
            } catch (IOException e) {
                Logger.log(JFR_SYSTEM, ERROR, "Error while writing file " + filename + ": " + e.getMessage());
            }

            filename = null;
            file = null;
        }

        @Uninterruptible(reason = "Prevent pollution of the current thread's thread local JFR buffer.")
        private void changeEpoch() {
            // TODO: We need to ensure that all JFR events that are triggered by the current thread
            // are recorded for the next epoch. Otherwise, those JFR events could pollute the data
            // that we currently try to persist. To ensure that, we must execute the following steps
            // uninterruptibly:
            //
            // - Flush all thread-local buffers (native & Java) to global JFR memory.
            // - Set all Java EventWriter.notified values
            // - Change the epoch.
        }
    }
}
