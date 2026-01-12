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

import com.oracle.truffle.espresso.substitutions.Collect;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

/**
 * An implementation of a chunked byte-array native memory. It uses Unsafe for accessing the byte
 * arrays.
 */
public class UnsafeByteArrayChunkedMemoryImpl extends ByteArrayChunkedMemory {

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    @Override
    public void putByteImpl(byte[] chunk, long chunkOffset, byte value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putByte(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.putByteVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
        }
    }

    @Override
    public void putShortImpl(byte[] chunk, long chunkOffset, short value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putShort(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.putShortVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
        }
    }

    @Override
    public void putIntImpl(byte[] chunk, long chunkOffset, int value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putInt(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.putIntVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
        }
    }

    @Override
    public void putLongImpl(byte[] chunk, long chunkOffset, long value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> UNSAFE.putLong(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.putLongVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, value);
        }
    }

    @Override
    public byte getByteImpl(byte[] chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getByte(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getByteVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
        };
    }

    @Override
    public short getShortImpl(byte[] chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getShort(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getShortVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
        };
    }

    @Override
    public int getIntImpl(byte[] chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getInt(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getIntVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
        };
    }

    @Override
    public long getLongImpl(byte[] chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> UNSAFE.getLong(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE ->
                UNSAFE.getLongVolatile(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset);
        };
    }

    @Override
    public double getDouble(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
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
    public boolean compareAndSetLongImpl(byte[] chunk, long chunkOffset, long expected, long newValue) {
        return UNSAFE.compareAndSwapLong(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, expected, newValue);
    }

    @Override
    public boolean compareAndSetIntImpl(byte[] chunk, long chunkOffset, int expected, int newValue) {
        return UNSAFE.compareAndSwapInt(chunk, Unsafe.ARRAY_BYTE_BASE_OFFSET + chunkOffset, expected, newValue);
    }

    @Collect(NativeMemory.class)
    public static final class Provider implements NativeMemory.Provider {

        public static final String ID = "UnsafeByteArrayChunkedMemory";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public NativeMemory create() {
            return new UnsafeByteArrayChunkedMemoryImpl();
        }

        @Override
        public boolean needsNativeAccess() {
            return false;
        }
    }
}
