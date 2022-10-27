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
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.svm.core.heap.VMOperationInfos;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;

import com.oracle.svm.core.jfr.JfrThreadLocal.JfrBufferNode;
import com.oracle.svm.core.util.VMError;

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

    private final JfrGlobalMemory globalMemory;
    private final ReentrantLock lock;
    private final boolean compressedInts;
    private long notificationThreshold;

    private String filename;
    private RawFileOperationSupport.RawFileDescriptor fd;
    private long chunkStartTicks;
    private long chunkStartNanos;
    private byte generation;
    private static final byte COMPLETE = 0;
    private static final byte MAX_BYTE = 127;

    public long lastCheckpointOffset = 0;

    private int lastMetadataId = 0;
    private int currentMetadataId = 0;
    private boolean staticConstantsSerialized = false;
    private boolean newChunk = true;

    public void setCurrentMetadataId(){
        currentMetadataId++;
    }

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
        generation = 1;
        newChunk = true;
        System.out.println("*** ChunkWriter openfile");
        chunkStartNanos = JfrTicks.currentTimeNanos();
        chunkStartTicks = JfrTicks.elapsedTicks();
        filename = outputFile;
        fd = getFileSupport().open(filename, RawFileOperationSupport.FileAccessMode.READ_WRITE);
        writeFileHeader();
        lastCheckpointOffset = -1;// must reset this on new chunk
        return true;
    }
    @Uninterruptible(reason = "Prevent safepoints as those could change the top pointer.") // *** top pointer (of the buffer) like if it gets reinit.
    public boolean write(JfrBuffer buffer) {
        return write(buffer, true);
    }
    @Uninterruptible(reason = "Prevent safepoints as those could change the top pointer.") // *** top pointer (of the buffer) like if it gets reinit.
    public boolean write(JfrBuffer buffer, boolean reset) {
        assert JfrBufferAccess.isAcquired(buffer) || VMOperation.isInProgressAtSafepoint() || buffer.getBufferType() == JfrBufferType.C_HEAP;
        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(buffer);
        if (unflushedSize.equal(0)) {
            return false;
        }

        boolean success = getFileSupport().write(fd, buffer.getTop(), unflushedSize);
        if(reset) {
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
    public void closeFile(byte[] metadataDescriptor, JfrConstantPool[] repositories, JfrThreadRepository threadRepo) {
        assert lock.isHeldByCurrentThread();
        System.out.println("*** rotating chunk: closeFile");
        /*
         * Switch to a new epoch. This is done at a safepoint to ensure that we end up with
         * consistent data, even if multiple threads have JFR events in progress.
         */
        System.out.println("*** safepoint start");
        JfrChangeEpochOperation op = new JfrChangeEpochOperation(false);
        op.enqueue();
        System.out.println("*** safepoint end");

        /*
         * After changing the epoch, all subsequently triggered JFR events will be recorded into the
         * data structures of the new epoch. This guarantees that the data in the old epoch can be
         * persisted to a file without a safepoint.
         */
        if (threadRepo.isDirty(false)){
            writeThreadCheckpointEvent(threadRepo, false);
        }
        SignedWord constantPoolPosition = writeCheckpointEvent(repositories, false);
        SignedWord metadataPosition = writeMetadataEvent(metadataDescriptor);
//        _constantPoolPosition = constantPoolPosition;
        _metadataPosition = metadataPosition;
        patchFileHeader(constantPoolPosition, metadataPosition); // write of header doesn't have to be uninterruptible because closefile() already has the lock. It can get interrupted by safepoint but it'll just resume later.
        getFileSupport().close(fd);

        filename = null;
        fd = WordFactory.nullPointer();
    }
    private SignedWord _constantPoolPosition;
    private SignedWord _metadataPosition;
    public void flush(byte[] metadataDescriptor, JfrConstantPool[] repositories, JfrThreadRepository threadRepo) {
        assert lock.isHeldByCurrentThread();// fd should always be correct because its cleared and set within locked critical section

//        JfrChangeEpochOperation op = new JfrChangeEpochOperation(true);
//        op.enqueue();
        flushStorage();

        if (threadRepo.isDirty(true)){
            writeThreadCheckpointEvent(threadRepo, true);
        }
        SignedWord constantPoolPosition = writeCheckpointEvent(repositories, true); // WILL get written again when the chunk closes and overwrite what we write here. In that case we shouldn't wipe the repos right? How does hotspot handle it?
        SignedWord metadataPosition = writeMetadataEvent(metadataDescriptor);


//        patchFileHeader(_constantPoolPosition, metadataPosition, true);
        patchFileHeader(constantPoolPosition, metadataPosition, true);

        // unlike rotate chunk, don't close file.

    }

    private void writeFileHeader() {
        // Write the header - some data gets patched later on.
        getFileSupport().write(fd, FILE_MAGIC); //magic
        getFileSupport().writeShort(fd, JFR_VERSION_MAJOR); // version
        getFileSupport().writeShort(fd, JFR_VERSION_MINOR);
        assert getFileSupport().position(fd).equal(CHUNK_SIZE_OFFSET);
        getFileSupport().writeLong(fd, 0L); // chunk size
        getFileSupport().writeLong(fd, 0L); // last checkpoint offset
        getFileSupport().writeLong(fd, 0L); // metadata position
        getFileSupport().writeLong(fd, 0L); // startNanos
        getFileSupport().writeLong(fd, 0L); // durationNanos
        getFileSupport().writeLong(fd, chunkStartTicks); // *** only changed after a chunk rotation is complete (after header is patched)
        getFileSupport().writeLong(fd, JfrTicks.getTicksFrequency());
        getFileSupport().writeByte(fd, nextGeneration()); // in hotspot a 1 byte generation is written
        getFileSupport().writeByte(fd, (byte)  0 ); // in hotspot 1 byte PAD padding
        getFileSupport().writeShort(fd, compressedInts ? (short) 1 : 0 ); // seems like only 2 bytes of the flags are written after the 1 byte generation

//        getFileSupport().writeInt(fd, compressedInts ? 1 : 0);
//        getFileSupport().seek(fd, WordFactory.signed(FILE_STATE_OFFSET));
//        getFileSupport().writeByte(fd, nextGeneration());
    }

    public void patchFileHeader(SignedWord constantPoolPosition, SignedWord metadataPosition) {
        patchFileHeader(constantPoolPosition, metadataPosition, false);
    }

    private void patchFileHeader(SignedWord constantPoolPosition, SignedWord metadataPosition, boolean flushpoint) {
        SignedWord currentPos = getFileSupport().position(fd);
        long chunkSize = getFileSupport().position(fd).rawValue();
        long durationNanos = JfrTicks.currentTimeNanos() - chunkStartNanos;
        getFileSupport().seek(fd, WordFactory.signed(CHUNK_SIZE_OFFSET));
        getFileSupport().writeLong(fd, chunkSize);
        getFileSupport().writeLong(fd, constantPoolPosition.rawValue());
        getFileSupport().writeLong(fd, metadataPosition.rawValue());
        getFileSupport().writeLong(fd, chunkStartNanos);
        getFileSupport().writeLong(fd, durationNanos);
        // *** i guess they didn't write anything else because nothing else changes
        getFileSupport().seek(fd, WordFactory.signed(FILE_STATE_OFFSET));
        if (flushpoint) {
            //chunk is not finished
            getFileSupport().writeByte(fd, nextGeneration()); // there are 4 bytes at the end. The first byte is the finished flag.
        } else {
            getFileSupport().writeByte(fd, COMPLETE);
        }
        //need to move pointer back to correct position for next write
        getFileSupport().seek(fd,currentPos);
    }

    private byte nextGeneration(){
        if (generation==MAX_BYTE){
            // similar to Hotspot, restart counter if required.
            generation = 1;
            return MAX_BYTE;
        }
        return generation++;
    }

    private SignedWord writeThreadCheckpointEvent(JfrConstantPool threadRepo, boolean flush) {

        SignedWord start = beginEvent();

        if (lastCheckpointOffset < 0) {
            lastCheckpointOffset = start.rawValue();
        }
        writeCompressedLong(CONSTANT_POOL_TYPE_ID);
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(lastCheckpointOffset - start.rawValue()); // deltaToNext
        writeCompressedLong(8); // *** Threads

        SignedWord poolCountPos = getFileSupport().position(fd);
        getFileSupport().writeInt(fd, 0); // We'll patch this later.

        int poolCount = threadRepo.write(this, flush);

        SignedWord currentPos = getFileSupport().position(fd);
        getFileSupport().seek(fd, poolCountPos); // *** write number of constant pools written
        getFileSupport().writeInt(fd, makePaddedInt(poolCount));
        getFileSupport().seek(fd, currentPos);
        endEvent(start);
        lastCheckpointOffset = start.rawValue();

        return start;
    }
    private SignedWord writeCheckpointEvent(JfrConstantPool[] repositories, boolean flush) {
//        Exception e = new Exception();
//        e.printStackTrace();
        System.out.println("*** Checkpoint");
        SignedWord start = beginEvent();

        if (lastCheckpointOffset < 0) {
            lastCheckpointOffset = start.rawValue();
        }
        writeCompressedLong(CONSTANT_POOL_TYPE_ID);
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(lastCheckpointOffset - start.rawValue()); // deltaToNext  //*** need to implement this!
        writeBoolean(true); // flush

        SignedWord poolCountPos = getFileSupport().position(fd);
        getFileSupport().writeInt(fd, 0); // We'll patch this later.
        JfrConstantPool[] serializers = JfrSerializerSupport.get().getSerializers();

        int poolCount;
        if (!staticConstantsSerialized) {
            poolCount = writeConstantPools(serializers, flush) + writeConstantPools(repositories, flush);
            staticConstantsSerialized = true;
        } else {
            poolCount = writeConstantPools(repositories, flush);
        }
//        int poolCount = writeConstantPools(serializers, flush) + writeConstantPools(repositories, flush);
        SignedWord currentPos = getFileSupport().position(fd);
        getFileSupport().seek(fd, poolCountPos); // *** write number of constant pools written
        getFileSupport().writeInt(fd, makePaddedInt(poolCount));
        getFileSupport().seek(fd, currentPos);
        endEvent(start);
        lastCheckpointOffset = start.rawValue();

        return start;
    }

    private int writeConstantPools(JfrConstantPool[] constantPools, boolean flush) {
        int count = 0;
        for (JfrConstantPool constantPool : constantPools) {
//            if (constantPool instanceof com.oracle.svm.core.jfr.JfrThreadRepository) {
//                System.out.println("*** Skipping thread repo");
//                continue;
//            }
            int poolCount = constantPool.write(this, flush);
            count += poolCount;
        }
        return count;
    }

    private SignedWord writeMetadataEvent(byte[] metadataDescriptor) {
        // *** works to prevent duplicate metadata from being written to disk
        if (currentMetadataId != lastMetadataId || newChunk) {
            lastMetadataId = currentMetadataId;
            newChunk = false; //always write metadata on a new chunk!
        } else {
            return _metadataPosition;
        }
        SignedWord start = beginEvent();
        writeCompressedLong(METADATA_TYPE_ID);
        writeCompressedLong(JfrTicks.elapsedTicks());
        writeCompressedLong(0); // duration
        writeCompressedLong(0); // metadata id
        writeBytes(metadataDescriptor); // payload
        endEvent(start);
        _metadataPosition = start;
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
        private boolean flush;
        protected JfrChangeEpochOperation(boolean flush) {
            super(VMOperationInfos.get(JfrChangeEpochOperation.class, "JFR change epoch", SystemEffect.SAFEPOINT));
            this.flush = flush;
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
//            for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
//                JfrBuffer buffer = JfrThreadLocal.getJavaBuffer(thread);
//                if (buffer.isNonNull()) {
//                    write(buffer);
//                    JfrThreadLocal.notifyEventWriter(thread);
//                }
//                buffer = JfrThreadLocal.getNativeBuffer(thread);
//                if (buffer.isNonNull()) {
//                    write(buffer);
//                }
//            }

            JfrBufferNodeLinkedList javaBuffers = com.oracle.svm.core.jfr.JfrThreadLocal.getJavaBufferList();
            JfrBufferNodeLinkedList nativeBuffers = com.oracle.svm.core.jfr.JfrThreadLocal.getNativeBufferList();

            traverseList(javaBuffers, true, true);

            traverseList(nativeBuffers, false, true);

            JfrBuffers buffers = globalMemory.getBuffers();
            for (int i = 0; i < globalMemory.getBufferCount(); i++) {
                JfrBuffer buffer = buffers.addressOf(i).read();
                VMError.guarantee(!JfrBufferAccess.isAcquired(buffer), "^^^6");//assert !JfrBufferAccess.isAcquired(buffer);
                write(buffer);
                JfrBufferAccess.reinitialize(buffer);
            }
            if (!flush) {
                JfrTraceIdEpoch.getInstance().changeEpoch();

                // Now that the epoch changed, re-register all running threads for the new epoch.
                SubstrateJVM.getThreadRepo().registerRunningThreads();
            }
        }
    }

    @Uninterruptible(reason = "Prevent pollution of the current thread's thread local JFR buffer.")
    private void flushStorage() {
        JfrBufferNodeLinkedList javaBuffers = com.oracle.svm.core.jfr.JfrThreadLocal.getJavaBufferList();
        JfrBufferNodeLinkedList nativeBuffers = com.oracle.svm.core.jfr.JfrThreadLocal.getNativeBufferList();
//        int count = javaBuffers.getSize(); // in case other threads are adding nodes

        traverseList(javaBuffers, true, false);
        traverseList(nativeBuffers, false, false);

        JfrBuffers buffers = globalMemory.getBuffers();
        for (int i = 0; i < globalMemory.getBufferCount(); i++) {
            JfrBuffer buffer = buffers.addressOf(i).read();
            if(!JfrBufferAccess.acquire(buffer)){ // one attempt
                continue;
            }
//            assert !JfrBufferAccess.isAcquired(buffer); // *** need to deal with this too
            write(buffer);
            JfrBufferAccess.reinitialize(buffer);
            JfrBufferAccess.release(buffer);
        }
    }
//    @Uninterruptible(reason = "Called from uninterruptible code.")
//    private void traverseList(JfrBufferNodeLinkedList linkedList, boolean java, boolean safepoint) {
//        // Try to lock list
//        if (!safepoint) {
//            for (int retry = 0; retry < 100; retry++) {
//                if (linkedList.acquire()) {
//                    break;
//                }
//            }
//            if (!linkedList.isAcquired()) {
//                VMError.guarantee(!safepoint, "^^^4");//assert !safepoint; // if safepoint, no one else should hold the lock on the LL.
//                return; // wasn't able to get the lock
//            }
//        }
//
//        JfrBufferNode node = linkedList.getHead();
//        JfrBufferNode prev = WordFactory.nullPointer();
//        int count = 0;
//
//        while (node.isNonNull()) {
//            count++;
//            VMError.guarantee(count < 1000, "^^^26");
//
//            // An optimization
//            if (linkedList.isAcquired()) { // evaluate this first
//                if (node != linkedList.getHead()) {
//                    // only need lock when dealing with head. Because other threads add nodes in direction opposite to traversal.
//                    VMError.guarantee(prev.isNonNull(), "^^^2");//assert prev.isNonNull();
//                    linkedList.release();
//                }
//            }
//
//            JfrBufferNode next = node.getNext();
//
//            // Try to get node if not in safepoint
//            if (!safepoint && !JfrBufferNodeLinkedList.acquire(node)) { //make one attempt
//                VMError.guarantee(!safepoint, "^^^1"); //if safepoint, no one else should hold the lock on the LL node. TODO: causes error when checked at safepoint (common)
//                prev = node;
//                node = next;
//                continue;
//            }
//
//            // Try to write to disk. (If thread doesn't flush at death, this is always safe to do because we remove nodes afterward)
//            JfrBuffer buffer = node.getValue();
//            VMError.guarantee(buffer.isNonNull(), "^^^3");//assert buffer.isNonNull();
//            if (!safepoint && JfrBufferAccess.acquire(buffer)) {
//                VMError.guarantee(JfrBufferAccess.isAcquired(buffer), "^^^5"); // TODO: causes error when checked at safepoint
//                write(buffer);
//                JfrBufferAccess.release(buffer);
//            }else {
//                write(buffer);
//            }
//            if (java) {
//                VMError.guarantee(node.getThread().isNonNull(), "^^^20");
//                JfrThreadLocal.notifyEventWriter(node.getThread());
//            }
//
//            if (!safepoint) {
//                JfrBufferNodeLinkedList.release(node);
//            }
//
//            // Try to remove if needed (If thread doesn't flush at death, this must be after flushing to disk block)
//            if (!node.getAlive()){
//                linkedList.removeNode(prev, node);
//                // don't update previous here!
//                node = next;
//                continue;
//            }
//
//            prev = node; // prev is always the last node still in the list before the current node. Prev may not be alive.
//            node = next;
//        }
//
//        if (linkedList.isAcquired()) {
//            linkedList.release();
//        }
//    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private void traverseList(JfrBufferNodeLinkedList linkedList, boolean java, boolean safepoint) {
    // Try to lock list
    if (!safepoint) {
        for (int retry = 0; retry < 100; retry++) {
            if (linkedList.acquire()) {
                break;
            }
        }
        if (!linkedList.isAcquired()) {
            VMError.guarantee(!safepoint, "^^^4");//assert !safepoint; // if safepoint, no one else should hold the lock on the LL.
            return; // wasn't able to get the lock
        }
    }

    JfrBufferNode node = linkedList.getHead();
    JfrBufferNode prev = WordFactory.nullPointer();
    int count = 0;

    while (node.isNonNull()) {
        count++;
        VMError.guarantee(count < 1000, "^^^26");

        // An optimization
        if (linkedList.isAcquired()) { // evaluate this first
            if (node != linkedList.getHead()) {
                // only need lock when dealing with head. Because other threads add nodes in direction opposite to traversal.
                VMError.guarantee(prev.isNonNull(), "^^^2");//assert prev.isNonNull();
                linkedList.release();
            }
        }

        JfrBufferNode next = node.getNext();
        JfrBuffer buffer = node.getValue();
        VMError.guarantee(buffer.isNonNull(), "^^^3");//assert buffer.isNonNull();

        // Try to get BUFFER if not in safepoint
        if (!safepoint && !JfrBufferAccess.acquire(buffer)) { //make one attempt
            VMError.guarantee(!safepoint, "^^^1"); //if safepoint, no one else should hold the lock on the LL node. TODO: causes error when checked at safepoint (common)
            prev = node;
            node = next;
            continue;
        }
        VMError.guarantee(JfrBufferAccess.isAcquired(buffer) || safepoint, "^^^5");
        write(buffer);

        // Try to write to disk. (If thread doesn't flush at death, this is always safe to do because we remove nodes afterward)
        if (!safepoint) {
            JfrBufferAccess.release(buffer);
        }

        if (java) {
            VMError.guarantee(node.getThread().isNonNull(), "^^^20");
            JfrThreadLocal.notifyEventWriter(node.getThread());
        }


        // Try to remove if needed (If thread doesn't flush at death, this must be after flushing to disk block)
        if (!node.getAlive()){
            linkedList.removeNode(prev, node);
            // don't update previous here!
            node = next;
            continue;
        }

        prev = node; // prev is always the last node still in the list before the current node. Prev may not be alive.
        node = next;
    }

    if (linkedList.isAcquired()) {
        linkedList.release();
    }
}
    public long getChunkStartNanos() {
        return chunkStartNanos;
    }
}
