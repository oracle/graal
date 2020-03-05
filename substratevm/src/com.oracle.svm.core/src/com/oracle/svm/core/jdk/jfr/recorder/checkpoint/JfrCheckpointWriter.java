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

import java.io.IOException;

import com.oracle.svm.core.jdk.jfr.JfrOptions;
import com.oracle.svm.core.jdk.jfr.recorder.storage.JfrBuffer;
import com.oracle.svm.core.jdk.jfr.utilities.JfrCheckpointType;
import com.oracle.svm.core.jdk.jfr.utilities.JfrTicks;
import com.oracle.svm.core.jdk.jfr.writers.JfrEncodingWriter;
import com.oracle.svm.core.jdk.jfr.writers.JfrEncodingWriter.Encoder;
import com.oracle.svm.core.jdk.jfr.writers.JfrWriter;

public class JfrCheckpointWriter {
    public static final int jfrCheckpointEntrySize = 32; // 3 longs and 2 ints
    private static final int sizeSafetyCushion = 1;

    private final JfrCheckpointType type;
    private final long startTime;
    private final JfrCheckpointClient callback;
    private final Thread thread;

    private JfrBuffer buffer;
    private int count;
    private int startPosition;

    public JfrCheckpointWriter(JfrBuffer b, Thread thread, JfrCheckpointClient callback) {
        this(b, thread, JfrCheckpointType.GENERIC, callback);
    }

    public JfrCheckpointWriter(JfrBuffer b, Thread thread, JfrCheckpointType type, JfrCheckpointClient callback) {
        this.buffer = b;
        this.thread = thread;
        this.startTime = JfrTicks.now();
        this.type = type;
        this.callback = callback;
    }

    public void open() {
        try {
            this.startPosition = getCurrentOffset();
            reserve(jfrCheckpointEntrySize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (count() == 0) {
            assert (usedSize() == jfrCheckpointEntrySize);
            this.buffer.getBuffer().position(startPosition);
            this.buffer.release();
            return;
        }

        assert (usedSize() > jfrCheckpointEntrySize);
        try {
            writeCheckpointHeader();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.buffer.setCommittedPosition(getCurrentOffset());
        this.buffer.release();
    }

    private void writeCheckpointHeader() throws IOException {
        long size = usedSize();
        int oldPos = getCurrentOffset();

        this.buffer.getBuffer().position(startPosition);
        this.buffer.getBuffer().putLong(size);
        this.buffer.getBuffer().putLong(startTime);
        this.buffer.getBuffer().putLong(JfrTicks.now() - startTime);
        this.buffer.getBuffer().putInt(type.id);
        this.buffer.getBuffer().putInt(count());

        this.buffer.getBuffer().position(oldPos);
    }

    public int usedSize() {
        // JFR.TODO
        return this.buffer.getBuffer().position() - this.buffer.getCommittedPosition();
    }

    int ensureSize(int requested) {
        if (this.buffer.getBuffer().remaining() < requested + sizeSafetyCushion) {
            this.buffer = callback.flush(this.buffer, this.usedSize(), requested, this.thread);
        }
        assert requested + sizeSafetyCushion <= this.buffer.getBuffer().remaining();
        return this.buffer.getBuffer().position();
    }

    public int getCurrentOffset() {
        return this.buffer.getBuffer().position();
    }

    public int count() {
        return this.count;
    }

    public int reserve(int size) throws IOException {
        if (ensureSize(size) >= 0) {
            int pos = getCurrentOffset();
            this.buffer.getBuffer().position(getCurrentOffset() + size);
            return pos;
        }
        // cancel();
        return 0;
    }

    // JFR.TODO : Need not throw Exception, but interface is used elsewhere where it
    // does
    class EncodingWriter implements JfrWriter {
        private final Encoder encoder;

        EncodingWriter(Encoder encoder) {
            this.encoder = encoder;
        }

        @Override
        public int writeByte(byte value) throws IOException {
            if (ensureSize(Byte.BYTES) >= 0) {
                return this.encoder.encode(buffer.getBuffer(), Byte.BYTES, value);
            } else {
                return 0;
            }
        }

        @Override
        public int writeShort(short value) throws IOException {
            if (ensureSize(Short.BYTES) >= 0) {
                return this.encoder.encode(buffer.getBuffer(), Short.BYTES, value);
            } else {
                return 0;
            }
        }

        @Override
        public int writeInt(int value) throws IOException {
            if (ensureSize(Integer.BYTES) >= 0) {
                return this.encoder.encode(buffer.getBuffer(), Integer.BYTES, value);
            } else {
                return 0;
            }
        }

        @Override
        public int writeLong(long value) throws IOException {
            if (ensureSize(Long.BYTES) >= 0) {
                return this.encoder.encode(buffer.getBuffer(), Long.BYTES, value);
            } else {
                return 0;
            }
        }

        @Override
        public int writeByte(byte value, long offset) throws IOException {
            if (offset < buffer.getBuffer().limit()) {
                int old = buffer.getBuffer().position();
                buffer.getBuffer().position((int) offset);
                int amount = writeByte(value);
                buffer.getBuffer().position(old);
                return amount;
            }

            return 0;
        }

        @Override
        public int writeShort(short value, long offset) throws IOException {
            if (offset < buffer.getBuffer().limit()) {
                int old = buffer.getBuffer().position();
                buffer.getBuffer().position((int) offset);
                int amount = writeShort(value);
                buffer.getBuffer().position(old);
                return amount;
            }

            return 0;
        }

        @Override
        public int writeInt(int value, long offset) throws IOException {
            if (offset < buffer.getBuffer().limit()) {
                int old = buffer.getBuffer().position();
                buffer.getBuffer().position((int) offset);
                int amount = writeInt(value);
                buffer.getBuffer().position(old);
                return amount;
            }

            return 0;
        }

        @Override
        public int writeLong(long value, long offset) throws IOException {
            if (offset < buffer.getBuffer().limit()) {
                int old = buffer.getBuffer().position();
                buffer.getBuffer().position((int) offset);
                int amount = writeLong(value);
                buffer.getBuffer().position(old);
                return amount;
            }

            return 0;
        }
    }

    private final JfrWriter beWriter = new EncodingWriter(JfrEncodingWriter::writeBE);

    public JfrWriter be() {
        return this.beWriter;
    }

    private final JfrWriter compressedWriter = new EncodingWriter(JfrEncodingWriter::writeCompressed);

    public JfrWriter encoded() {
        if (JfrOptions.compressedIntegers()) {
            return this.compressedWriter;
        } else {
            return this.beWriter;
        }
    }

    private final JfrWriter paddedCompressedWriter = new EncodingWriter(JfrEncodingWriter::writePaddedCompressed);

    public JfrWriter padded() {
        if (JfrOptions.compressedIntegers()) {
            return this.paddedCompressedWriter;
        } else {
            return this.beWriter;
        }
    }

    public void setContext(int offset, int count) {
        this.buffer.getBuffer().position(offset);
        this.count = count;
    }

    public void increment() {
        this.count++;
    }

    public void writeString(String s) throws IOException {
        if (s == null) {
            encoded().writeInt(0);
        } else if (s.length() == 0) {
            encoded().writeInt(1);
        } else {
            byte UTF16 = 4;
            encoded().writeByte(UTF16);
            encoded().writeInt(s.length());
            for (byte b : s.getBytes()) {
                encoded().writeByte(b);
            }
        }
    }
}
