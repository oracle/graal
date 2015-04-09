/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.compiler.common.util;

import static com.oracle.graal.compiler.common.util.TypeConversion.*;

import java.nio.*;

import sun.misc.*;

import com.oracle.graal.compiler.common.*;

/**
 * Provides low-level sequential write access to a byte[] array for signed and unsigned values of
 * size 1, 2, 4, and 8 bytes. To avoid copying an array when the buffer size is no longer
 * sufficient, the buffer is split into chunks of a fixed size.
 */
public class UnsafeArrayTypeWriter implements TypeWriter {

    private static final int CHUNK_SIZE = 4000;

    static class Chunk {
        protected final byte[] data = new byte[CHUNK_SIZE];
        protected int size;
        protected Chunk next;
    }

    private Chunk firstChunk;
    private Chunk writeChunk;
    private int totalSize;

    public UnsafeArrayTypeWriter() {
        firstChunk = new Chunk();
        writeChunk = firstChunk;
    }

    @Override
    public long getBytesWritten() {
        return totalSize;
    }

    /**
     * Copies the buffer into the provided byte[] array of length {@link #getBytesWritten()}.
     */
    public byte[] toArray(byte[] result) {
        assert result.length == totalSize;
        int resultIdx = 0;
        for (Chunk cur = firstChunk; cur != null; cur = cur.next) {
            System.arraycopy(cur.data, 0, result, resultIdx, cur.size);
            resultIdx += cur.size;
        }
        assert resultIdx == totalSize;
        return result;
    }

    @Override
    public void putS1(long value) {
        long offset = writeOffset(Byte.BYTES);
        UnsafeAccess.unsafe.putByte(writeChunk.data, offset, asS1(value));
        commitWrite(Byte.BYTES);
    }

    @Override
    public void putU1(long value) {
        long offset = writeOffset(Byte.BYTES);
        UnsafeAccess.unsafe.putByte(writeChunk.data, offset, asU1(value));
        commitWrite(Byte.BYTES);
    }

    @Override
    public void putS2(long value) {
        long offset = writeOffset(Short.BYTES);
        if (offset % Short.BYTES == 0) {
            UnsafeAccess.unsafe.putShort(writeChunk.data, offset, asS2(value));
            commitWrite(Short.BYTES);
        } else {
            ByteBuffer buf = ByteBuffer.wrap(new byte[Short.BYTES]);
            buf.putShort(asS2(value));
            putS1(buf.get(0));
            putS1(buf.get(Byte.BYTES));
        }
    }

    @Override
    public void putU2(long value) {
        putS2(asU2(value));
    }

    @Override
    public void putS4(long value) {
        long offset = writeOffset(Integer.BYTES);
        if (offset % Integer.BYTES == 0) {
            UnsafeAccess.unsafe.putInt(writeChunk.data, offset, asS4(value));
            commitWrite(Integer.BYTES);
        } else {
            ByteBuffer buf = ByteBuffer.wrap(new byte[Integer.BYTES]);
            buf.putInt(asS4(value));
            if (offset % Short.BYTES == 0) {
                putS2(buf.getShort(0));
                putS2(buf.getShort(2));
            } else {
                putS1(buf.get(0));
                putS2(buf.getShort(1));
                putS1(buf.get(3));
            }
        }
    }

    @Override
    public void putU4(long value) {
        putS4(asU4(value));
    }

    @Override
    public void putS8(long value) {
        long offset = writeOffset(Long.BYTES);
        if (offset % Long.BYTES == 0) {
            UnsafeAccess.unsafe.putLong(writeChunk.data, offset, value);
            commitWrite(Long.BYTES);
        } else {
            ByteBuffer buf = ByteBuffer.wrap(new byte[Long.BYTES]);
            buf.putLong(value);
            if (offset % Integer.BYTES == 0) {
                putS4(buf.getInt(0));
                putS4(buf.getInt(4));
            } else {
                putS2(buf.getShort(0));
                putS4(buf.getInt(2));
                putS2(buf.getShort(6));
            }
        }
    }

    private long writeOffset(int writeBytes) {
        if (writeChunk.size + writeBytes >= writeChunk.data.length) {
            Chunk newChunk = new Chunk();
            writeChunk.next = newChunk;
            writeChunk = newChunk;
        }

        assert Unsafe.ARRAY_BYTE_INDEX_SCALE == 1;
        long result = writeChunk.size + Unsafe.ARRAY_BYTE_BASE_OFFSET;

        return result;
    }

    private void commitWrite(int writeBytes) {
        totalSize += writeBytes;
        writeChunk.size += writeBytes;
        assert writeChunk.size <= writeChunk.data.length;
    }
}
