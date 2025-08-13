/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.ffi.memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.oracle.truffle.api.memory.ByteArraySupport;

public class ProtoChunkedMemoryImpl extends ChunkedNativeMemory<byte[]> {

    private static final ByteArraySupport BYTES = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                    ? ByteArraySupport.littleEndian()
                    : ByteArraySupport.bigEndian();

    private static void validateAccess(int length, int byteIndex, int accessByteSize) {
        if (byteIndex < 0 || byteIndex > length - accessByteSize) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public void copyMemory(long srcBase,
                    long destBase,
                    long bytes, MemoryAccessMode accessMode) {
        copyBytes(srcBase, destBase, bytes);
    }

    @Override
    public void setMemory(long address, long bytes, byte value) {
        byte[] chunk = getChunk(address);
        // I think the accessByteSize should be 0 instead of 1 as we access exactly bytes many bytes
        // in chunk
        validateAccess(chunk.length, Math.toIntExact(bytes), 0);
        Arrays.fill(chunk, 0, Math.toIntExact(bytes), value);
    }

    @Override
    public void putBoolean(long address, boolean value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        switch (accessMode) {
            case PLAIN -> BYTES.putByte(chunk, chunkOffset, value ? (byte) 1 : (byte) 0);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                BYTES.putByteVolatile(chunk, chunkOffset, value ? (byte) 1 : (byte) 0);
        }
    }

    @Override
    public void putByte(long address, byte value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Byte.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putByte(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putByteVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putChar(long address, char value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Character.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putShort(chunk, chunkOffset, (short) value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putShortVolatile(chunk, chunkOffset, (short) value);
        }
    }

    @Override
    public void putShort(long address, short value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Short.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putShort(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putShortVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putInt(long address, int value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Integer.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putInt(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putIntVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putFloat(long address, float value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Float.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putFloat(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putIntVolatile(chunk, chunkOffset, Float.floatToRawIntBits(value));
        }
    }

    @Override
    public void putDouble(long address, double value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Double.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putDouble(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putLongVolatile(chunk, chunkOffset, Double.doubleToRawLongBits(value));
        }
    }

    @Override
    public void putLong(long address, long value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Long.BYTES);
        switch (accessMode) {
            case PLAIN -> BYTES.putLong(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putLongVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public boolean getBoolean(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Byte.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getByte(chunk, chunkOffset) != 0;
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getByteVolatile(chunk, chunkOffset) != 0;
        };
    }

    @Override
    public byte getByte(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Byte.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getByte(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getByteVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public char getChar(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Character.BYTES);
        return switch (accessMode) {
            case PLAIN -> (char) BYTES.getShort(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> (char) BYTES.getShortVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public short getShort(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Short.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getShort(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getShortVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public int getInt(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Integer.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getInt(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getIntVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public float getFloat(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Float.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getFloat(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> Float.intBitsToFloat(BYTES.getIntVolatile(chunk, chunkOffset));
        };
    }

    @Override
    public double getDouble(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Double.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getDouble(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> Double.longBitsToDouble(BYTES.getLongVolatile(chunk, chunkOffset));
        };
    }

    @Override
    public boolean compareAndSetLong(long address, long expected, long newValue) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        return BYTES.compareAndExchangeLong(chunk, chunkOffset, expected, newValue) == expected;
    }

    @Override
    public boolean compareAndSetInt(long address, int expected, int newValue) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        return BYTES.compareAndExchangeInt(chunk, chunkOffset, expected, newValue) == expected;
    }

    @Override
    public long getLong(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Long.BYTES);
        return switch (accessMode) {
            case PLAIN -> BYTES.getLong(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getLongVolatile(chunk, chunkOffset);
        };
    }

    @Override
    protected byte[] allocateChunk(long bytes) {
        return new byte[Math.toIntExact(bytes)];
    }

    @Override
    protected long getChunkSize(long address) {
        return getChunk(address).length;
    }

    @Override
    public ByteBuffer getDirectBuffer(long address, long bytes) {
        int intByteSize = Math.toIntExact(bytes);
        byte[] fromChunk = getChunk(address);
        int fromOffset = Math.toIntExact(getChunkOffset(address));
        validateAccess(fromChunk.length, fromOffset, intByteSize);
        return ByteBuffer.wrap(fromChunk, fromOffset, intByteSize);
    }

    @Override
    public void writeMemory(long address, long bytes, ByteBuffer buf) {
        int intByteSize = Math.toIntExact(bytes);
        int fromOffset = Math.toIntExact(getChunkOffset(address));
        byte[] fromChunk = getChunk(address);
        validateAccess(fromChunk.length, fromOffset, intByteSize);
        buf.get(fromChunk, fromOffset, intByteSize);
    }

    @Override
    public void readMemory(long address, long bytes, ByteBuffer buf) {
        int intByteSize = Math.toIntExact(bytes);
        int fromOffset = Math.toIntExact(getChunkOffset(address));
        byte[] fromChunk = getChunk(address);
        validateAccess(fromChunk.length, fromOffset, intByteSize);
        buf.put(fromChunk, fromOffset, intByteSize);
    }

    @Override
    protected void copyBytes(long fromAddress, long toAddress, long byteSize) {
        int intByteSize = Math.toIntExact(byteSize);

        byte[] fromChunk = getChunk(fromAddress);
        int fromOffset = Math.toIntExact(getChunkOffset(fromAddress));
        validateAccess(fromChunk.length, fromOffset, intByteSize);

        byte[] toChunk = getChunk(toAddress);
        int toOffset = Math.toIntExact(getChunkOffset(fromAddress));
        validateAccess(toChunk.length, toOffset, intByteSize);

        System.arraycopy(fromChunk, fromOffset, toChunk, toOffset, intByteSize);
    }

    @Override
    public boolean isDirectBufferSupported() {
        return true;
    }
}
