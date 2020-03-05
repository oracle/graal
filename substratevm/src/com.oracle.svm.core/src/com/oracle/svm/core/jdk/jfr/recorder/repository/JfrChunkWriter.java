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

package com.oracle.svm.core.jdk.jfr.recorder.repository;

import static com.oracle.svm.core.jdk.jfr.utilities.JfrCheckpointType.FLUSH;
import static com.oracle.svm.core.jdk.jfr.utilities.JfrCheckpointType.HEADER;
import static com.oracle.svm.core.jdk.jfr.utilities.JfrTime.invalidTime;
import static com.oracle.svm.core.jdk.jfr.utilities.JfrTypes.ReservedEvent.EVENT_CHECKPOINT;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import com.oracle.svm.core.jdk.jfr.JfrOptions;
import com.oracle.svm.core.jdk.jfr.utilities.JfrTicks;
import com.oracle.svm.core.jdk.jfr.writers.JfrEncodingWriter;
import com.oracle.svm.core.jdk.jfr.writers.JfrEncodingWriter.Encoder;
import com.oracle.svm.core.jdk.jfr.writers.JfrWriter;
import com.oracle.svm.core.thread.VMOperation;

public class JfrChunkWriter {
    final JfrChunk chunk;

    private final ByteBuffer data;
    private RandomAccessFile file;
    private long streamPos;

    static final long MAGIC_OFFSET = 0;
    static final long MAGIC_LEN = 4;
    static final long VERSION_OFFSET = MAGIC_LEN;
    static final long SIZE_OFFSET = 8;
    static final long SLOT_SIZE = 8;
    static final long CHECKPOINT_OFFSET = SIZE_OFFSET + SLOT_SIZE;
    static final long METADATA_OFFSET = CHECKPOINT_OFFSET + SLOT_SIZE;
    static final long START_NANOS_OFFSET = METADATA_OFFSET + SLOT_SIZE;
    static final long DURATION_NANOS_OFFSET = START_NANOS_OFFSET + SLOT_SIZE;
    static final long START_TICKS_OFFSET = DURATION_NANOS_OFFSET + SLOT_SIZE;
    static final long CPU_FREQUENCY_OFFSET = START_TICKS_OFFSET + SLOT_SIZE;
    static final long GENERATION_OFFSET = CPU_FREQUENCY_OFFSET + SLOT_SIZE;
    static final long FLAG_OFFSET = GENERATION_OFFSET + 2;
    static final long HEADER_SIZE = FLAG_OFFSET + 2;

    public static final int sizeSafetyCushion = 1;

    public JfrChunkWriter() {
        chunk = new JfrChunk();
        data = ByteBuffer.allocate(1024 * 1024);
    }

    public void setTimeStamp() {
        assert chunk != null;
        chunk.setTimeStamp();
    }

    public long getSizeWritten() {
        return isValid() ? getCurrentOffset() : 0;
    }

    public long getLastCheckpointOffset() {
        assert chunk != null;
        return chunk.getLastCheckpointOffset();
    }

    public void setLastCheckpointOffset(long offset) {
        assert chunk != null;
        chunk.setLastCheckpointOffset(offset);
    }

    public void setLastMetadataOffset(long offset) {
        assert chunk != null;
        chunk.setLastMetadataOffset(offset);
    }

    public boolean hasMetadata() {
        assert chunk != null;
        return chunk.hasMetadata();
    }

    public boolean isValid() {
        return file != null;
    }

    long getCurrentChunkStartNanos() {
        assert chunk != null;
        return isValid() ? chunk.getStartNanos() : invalidTime;
    }

    void setPath(Path path) {
        chunk.setPath(path);
    }

    void markChunkFinal() {
        assert chunk != null;
        chunk.markFinal();
    }

    long flushChunk(boolean flushpoint) throws IOException {
        assert chunk != null;
        long szWritten = writeChunkHeaderCheckpoint(flushpoint);
        assert getSizeWritten() == szWritten;
        JfrChunkHeadWriter head = new JfrChunkHeadWriter(this, SIZE_OFFSET);
        head.flush(szWritten, !flushpoint);
        return szWritten;
    }

    boolean open() throws IOException {
        assert chunk != null;
        reset(openChunk(chunk.getPath()));
        boolean isOpen = isValid();
        if (isOpen) {
            assert 0 == getCurrentOffset();
            chunk.reset();
            // Constructor side effects are important here
            JfrChunkHeadWriter head = new JfrChunkHeadWriter(this, HEADER_SIZE);
        }
        return isOpen;
    }

    static RandomAccessFile openChunk(Path path) throws IOException {
        return path != null ? new RandomAccessFile(path.toFile(), "rw") : null;
    }

    void seek(long offset) throws IOException {
        flush();
        assert 0 == getUsedSize() : "can only seek from beginning";
        file.seek(offset);
        streamPos = offset;
    }

    public void reset(RandomAccessFile file) {
        assert !isValid();
        this.file = file;
        streamPos = 0;
        // hardReset();
    }

    public void flush() throws IOException {
        if (isValid()) {
            int used = getUsedSize();
            if (used > 0) {
                flush(used);
            }
        }
    }

    protected void flush(int size) throws IOException {
        assert size > 0;
        assert isValid();
        data.flip();

        while (data.hasRemaining()) {
            file.write(data.get());
            streamPos++;
        }
        data.clear();
        assert 0 == getUsedSize();
    }

    long close() throws IOException {
        assert isValid();
        long sizeWritten = flushChunk(false);
        file.close();
        file = null;
        assert !isValid();
        return sizeWritten;
    }

    long prepareChunkHeaderConstantPool(long eventOffset, boolean flushpoint) throws IOException {
        long delta = getLastCheckpointOffset() == 0 ? 0 : getLastCheckpointOffset() - eventOffset;
        int checkpointType = flushpoint ? FLUSH.id | HEADER.id : HEADER.id;
        reserve(Integer.BYTES);
        JfrWriter w = encoded();
        w.writeLong(EVENT_CHECKPOINT.id);
        w.writeLong(JfrTicks.now());
        w.writeLong(0L); // duration
        w.writeLong(delta); // to previous checkpoint
        w.writeInt(checkpointType);
        w.writeInt(0); // pool count
        // w.writeLong(TYPE_CHUNKHEADER.id);
        // w.writeInt(1); // count
        // w.writeLong(1L); // key
        // w.writeInt((int) HEADER_SIZE); // length of byte array
        return getCurrentOffset();
    }

    long writeChunkHeaderCheckpoint(boolean flushpoint) throws IOException {
        assert isValid();
        long eventSizeOffset = getCurrentOffset();
        long headerContentPos = prepareChunkHeaderConstantPool(eventSizeOffset, flushpoint);
        // JfrChunkHeadWriter head = new JfrChunkHeadWriter(this, headerContentPos, false);
        // head.writeMagic();
        // head.writeVersion();
        // long chunkSizeOffset = reserve(Long.BYTES); // size to be decided when we are done
        // be().writeLong(eventSizeOffset); // last checkpoint offset will be this checkpoint
        // head.writeMetadata();
        // head.writeTime(false);
        // head.writeCpuFrequency();
        // head.writeNextGeneration();
        // head.writeFlags();
        // assert getCurrentOffset() - headerContentPos == HEADER_SIZE;
        int checkpointSize = (int) (getCurrentOffset() - eventSizeOffset);
        padded().writeInt(checkpointSize, eventSizeOffset);
        setLastCheckpointOffset(eventSizeOffset);
        long szWritten = getSizeWritten();
        // be().writeLong(szWritten, chunkSizeOffset);
        return szWritten;
    }

    public long reserve(int size) throws IOException {
        if (ensureSize(size) >= 0) {
            long pos = this.getCurrentOffset();
            data.position(data.position() + size);
            return pos;
        }
        // cancel();
        return 0;
    }

    int ensureSize(int requested) throws IOException {
        if (!isValid()) {
            // cancelled
            return -1;
        }
        if (data.remaining() < requested + sizeSafetyCushion) {
            flush();
            // if (!accommodate(getUsedSize(), requested + sizeSafetyCushion)) {
            // assert !isValid();
            // return -1;
            // }
        }
        assert requested + sizeSafetyCushion <= data.remaining();
        return data.position();
    }

    public long getCurrentOffset() {
        return getUsedSize() + streamPos;
    }

    public int getUsedSize() {
        return data.position();
    }

    public int getAvailableSize() {
        return data.remaining();
    }

    public void writeBytes(byte[] buf) throws IOException {
        writeBytes(buf, buf.length);
    }

    public void writeBytes(byte[] buf, int len) throws IOException {
        if (len > getAvailableSize()) {
            writeUnbuffered(buf, len);
        } else {
            data.put(buf, 0, len);
        }
    }

    public int writeUnbuffered(byte[] src, int len) throws IOException {
        flush();
        assert 0 == getUsedSize() : "can only seek from beginning";
        file.write(src, 0, len);
        streamPos += len;
        return len;
    }

    public int writeUnbuffered(ByteBuffer buf) throws IOException {
        flush();
        assert 0 == getUsedSize() : "can only seek from beginning";
        int written = 0;
        if (VMOperation.isInProgressAtSafepoint()) {
            byte[] b = new byte[buf.remaining()];
            buf.get(b);
            file.write(b);
            written += b.length;
        } else {
            FileChannel fc = file.getChannel();
            written += fc.write(buf);
        }

        streamPos += written;
        return written;
    }

    class EncodingWriter implements JfrWriter {
        private final Encoder encoder;

        private final ByteBuffer buf;

        EncodingWriter(Encoder encoder) {
            this.encoder = encoder;
            buf = ByteBuffer.allocate(9);
        }

        @Override
        public int writeByte(byte value) throws IOException {
            if (ensureSize(Byte.BYTES) >= 0) {
                return encoder.encode(data, Byte.BYTES, value);
            } else {
                return 0;
            }
        }

        @Override
        public int writeShort(short value) throws IOException {
            if (ensureSize(Short.BYTES) >= 0) {
                return encoder.encode(data, Short.BYTES, value);
            } else {
                return 0;
            }
        }

        @Override
        public int writeInt(int value) throws IOException {
            if (ensureSize(Integer.BYTES) >= 0) {
                return encoder.encode(data, Integer.BYTES, value);
            } else {
                return 0;
            }
        }

        @Override
        public int writeLong(long value) throws IOException {
            if (ensureSize(Long.BYTES) >= 0) {
                return encoder.encode(data, Long.BYTES, value);
            } else {
                return 0;
            }
        }

        @Override
        public int writeByte(byte value, long offset) throws IOException {
            int res;
            if (offset < file.length()) {
                buf.clear();
                res = encoder.encode(buf, Byte.BYTES, value);
                buf.flip();

                long oldPos = file.getFilePointer();
                file.seek(offset);
                while (buf.hasRemaining()) {
                    file.write(buf.get());
                }
                file.seek(oldPos);
            } else {
                int p = data.position();
                data.position((int) (offset - file.length()));
                res = encoder.encode(data, Byte.BYTES, value);
                data.position(p);
            }
            return res;
        }

        @Override
        public int writeShort(short value, long offset) throws IOException {
            int res;
            if (offset < file.length()) {
                buf.clear();
                res = encoder.encode(buf, Short.BYTES, value);
                buf.flip();

                long oldPos = file.getFilePointer();
                file.seek(offset);
                while (buf.hasRemaining()) {
                    file.write(buf.get());
                }
                file.seek(oldPos);
            } else {
                int p = data.position();
                data.position((int) (offset - file.length()));
                res = encoder.encode(data, Short.BYTES, value);
                data.position(p);
            }
            return res;
        }

        @Override
        public int writeInt(int value, long offset) throws IOException {
            int res;
            if (offset < file.length()) {
                buf.clear();
                res = encoder.encode(buf, Integer.BYTES, value);
                buf.flip();

                long oldPos = file.getFilePointer();
                file.seek(offset);
                while (buf.hasRemaining()) {
                    file.write(buf.get());
                }
                file.seek(oldPos);
            } else {
                int p = data.position();
                data.position((int) (offset - file.length()));
                res = encoder.encode(data, Integer.BYTES, value);
                data.position(p);
            }
            return res;
        }

        @Override
        public int writeLong(long value, long offset) throws IOException {
            int res;
            if (offset < file.length()) {
                buf.clear();
                res = encoder.encode(buf, Long.BYTES, value);
                buf.flip();

                long oldPos = file.getFilePointer();
                file.seek(offset);
                while (buf.hasRemaining()) {
                    file.write(buf.get());
                }
                file.seek(oldPos);
            } else {
                int p = data.position();
                data.position((int) (offset - file.length()));
                res = encoder.encode(data, Long.BYTES, value);
                data.position(p);
            }
            return res;
        }
    }

    private final JfrWriter beWriter = new EncodingWriter(JfrEncodingWriter::writeBE);

    public JfrWriter be() {
        return beWriter;
    }

    private final JfrWriter compressedWriter = new EncodingWriter(JfrEncodingWriter::writeCompressed);

    public JfrWriter encoded() {
        if (JfrOptions.compressedIntegers()) {
            return compressedWriter;
        } else {
            return beWriter;
        }
    }

    private final JfrWriter paddedCompressedWriter = new EncodingWriter(JfrEncodingWriter::writePaddedCompressed);

    public JfrWriter padded() {
        if (JfrOptions.compressedIntegers()) {
            return paddedCompressedWriter;
        } else {
            return beWriter;
        }
    }


}
