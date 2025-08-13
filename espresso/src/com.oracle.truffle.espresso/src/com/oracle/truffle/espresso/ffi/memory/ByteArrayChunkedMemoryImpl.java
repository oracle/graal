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

import java.util.Arrays;

import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public class ByteArrayChunkedMemoryImpl extends ChunkedNativeMemory<byte[]> {

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    private static void validateAccess(int length, int byteIndex, int accessByteSize) {
        if (byteIndex < 0 || byteIndex > length - accessByteSize) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public void setMemory(long address, long bytes, byte value) {
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(bytes), 0);
        Arrays.fill(chunk, 0, Math.toIntExact(bytes), value);
    }

    @Override
    public void putByte(long address, byte value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Byte.BYTES);
        switch (accessMode) {
            case PLAIN -> UNSAFE.putByte(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.putByteVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
        }
    }

    @Override
    public void putShort(long address, short value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Short.BYTES);
        switch (accessMode) {
            case PLAIN -> UNSAFE.putShort(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.putShortVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
        }
    }

    @Override
    public void putInt(long address, int value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Integer.BYTES);
        switch (accessMode) {
            case PLAIN -> UNSAFE.putInt(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.putIntVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
        }
    }

    @Override
    public byte getByte(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Byte.BYTES);
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getByte(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getByteVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
        };
    }

    @Override
    public short getShort(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Short.BYTES);
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getShort(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getShortVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
        };
    }

    @Override
    public int getInt(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Integer.BYTES);
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getInt(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getIntVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
        };
    }

    @Override
    public void putLong(long address, long value, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Long.BYTES);
        switch (accessMode) {
            case PLAIN -> UNSAFE.putLong(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.putLongVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
        }
    }

    @Override
    public double getDouble(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Double.BYTES);
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getDouble(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getDoubleVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
        };
    }

    @Override
    public boolean compareAndSetLong(long address, long expected, long newValue) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        return UNSAFE.compareAndSwapLong(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, expected, newValue);
    }

    @Override
    public boolean compareAndSetInt(long address, int expected, int newValue) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        return UNSAFE.compareAndSwapInt(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, expected, newValue);
    }

    @Override
    public long getLong(long address, MemoryAccessMode accessMode) {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk.length, Math.toIntExact(chunkOffset), Long.BYTES);
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getLong(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getLongVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
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
}
