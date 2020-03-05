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

import static com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunk.COMPLETE;
import static com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunk.GUARD;
import static com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunk.PAD;
import static com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkWriter.GENERATION_OFFSET;
import static com.oracle.svm.core.jdk.jfr.recorder.repository.JfrChunkWriter.HEADER_SIZE;

import java.io.IOException;

public class JfrChunkHeadWriter {
    private final JfrChunkWriter writer;
    private final JfrChunk chunk;

    public JfrChunkHeadWriter(JfrChunkWriter writer, long offset) throws IOException {
        this(writer, offset, true);
    }

    public JfrChunkHeadWriter(JfrChunkWriter writer, long offset, boolean guard) throws IOException {
        assert writer != null;
        assert writer.isValid();
        assert writer.chunk != null;
        this.writer = writer;
        this.chunk = writer.chunk;
        if (0 == writer.getCurrentOffset()) {
            assert HEADER_SIZE == offset;
            initialize();
        } else {
            if (guard) {
                writer.seek(GENERATION_OFFSET);
                writeGuard();
                writer.seek(offset);
            } else {
                chunk.updateCurrentNanos();
            }
        }
//        DEBUG_ONLY(assert_writer_position(_writer, offset);)
    }

    void initialize() throws IOException {
        assert writer.isValid();
        assert chunk != null;
//        DEBUG_ONLY(assert_writer_position(_writer, 0);)
        writeMagic();
        writeVersion();
        writeSizeToGeneration(HEADER_SIZE, false);
        writeFlags();
//        DEBUG_ONLY(assert_writer_position(_writer, HEADER_SIZE);)
        writer.flush();
    }

    public void writeMagic() throws IOException {
        writer.writeBytes(chunk.getMagic().getBytes());
    }

    public void writeVersion() throws IOException {
        writer.be().writeShort(chunk.getMajorVersion());
        writer.be().writeShort(chunk.getMinorVersion());
    }

    public void writeSize(long size) throws IOException {
        writer.be().writeLong(size);
    }

    public void writeCheckpoint() throws IOException {
        writer.be().writeLong(chunk.getLastCheckpointOffset());
    }

    public void writeMetadata() throws IOException {
        writer.be().writeLong(chunk.getLastMetadataOffset());
    }

    public void writeTime(boolean finalize) throws IOException {
        if (finalize) {
            writer.be().writeLong(chunk.getPreviousStartNanos());
            writer.be().writeLong(chunk.getLastChunkDuration());
            writer.be().writeLong(chunk.getPreviousStartTicks());
            return;
        }
        writer.be().writeLong(chunk.getStartNanos());
        writer.be().writeLong(chunk.getDuration());
        writer.be().writeLong(chunk.getStartTicks());
    }

    public void writeCpuFrequency() throws IOException {
        writer.be().writeLong(chunk.getCpuFrequency());
    }

    public void writeGeneration(boolean finalize) throws IOException {
        writer.be().writeByte(finalize ? COMPLETE : (byte) chunk.getGeneration());
        writer.be().writeByte(PAD);
    }

    public void writeNextGeneration() throws IOException {
        writer.be().writeByte((byte) chunk.getNextGeneration());
        writer.be().writeByte(PAD);
    }

    public void writeGuard() throws IOException {
        writer.be().writeByte((byte) GUARD);
        writer.be().writeByte(PAD);
    }

    public void writeFlags() throws IOException {
        writer.be().writeShort(chunk.getFlags());
    }

    public void writeSizeToGeneration(long size, boolean finalize) throws IOException {
        writeSize(size);
        writeCheckpoint();
        writeMetadata();
        writeTime(finalize);
        writeCpuFrequency();
        writeGeneration(finalize);
    }

    void flush(long size, boolean finalize) throws IOException {
        assert writer.isValid();
        assert chunk != null;
//        DEBUG_ONLY(assert_writer_position(_writer, SIZE_OFFSET);)
        writeSizeToGeneration(size, finalize);
        writeFlags();
        writer.seek(size); // implicit flush
    }
}
