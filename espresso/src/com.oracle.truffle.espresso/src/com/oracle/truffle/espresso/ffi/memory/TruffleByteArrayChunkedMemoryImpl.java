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

import java.nio.ByteOrder;

import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.espresso.substitutions.Collect;

/**
 * An implementation of a chunked byte-array native memory. It uses Truffle's
 * {@link ByteArraySupport} for accessing the byte arrays.
 */
public class TruffleByteArrayChunkedMemoryImpl extends ByteArrayChunkedMemory {

    private static final ByteArraySupport BYTES = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                    ? ByteArraySupport.littleEndian()
                    : ByteArraySupport.bigEndian();

    @Override
    public void putByteImpl(byte[] chunk, long chunkOffset, byte value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> BYTES.putByte(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putByteVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putShortImpl(byte[] chunk, long chunkOffset, short value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> BYTES.putShort(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putShortVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putIntImpl(byte[] chunk, long chunkOffset, int value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> BYTES.putInt(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putIntVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public void putLongImpl(byte[] chunk, long chunkOffset, long value, MemoryAccessMode accessMode) {
        switch (accessMode) {
            case PLAIN -> BYTES.putLong(chunk, chunkOffset, value);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.putLongVolatile(chunk, chunkOffset, value);
        }
    }

    @Override
    public byte getByteImpl(byte[] chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> BYTES.getByte(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getByteVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public short getShortImpl(byte[] chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> BYTES.getShort(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getShortVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public int getIntImpl(byte[] chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> BYTES.getInt(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getIntVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public long getLongImpl(byte[] chunk, long chunkOffset, MemoryAccessMode accessMode) {
        return switch (accessMode) {
            case PLAIN -> BYTES.getLong(chunk, chunkOffset);
            case OPAQUE, RELEASE_ACQUIRE, VOLATILE -> BYTES.getLongVolatile(chunk, chunkOffset);
        };
    }

    @Override
    public boolean compareAndSetIntImpl(byte[] chunk, long chunkOffset, int expected, int newValue) {
        return BYTES.compareAndExchangeInt(chunk, chunkOffset, expected, newValue) == expected;
    }

    @Override
    public boolean compareAndSetLongImpl(byte[] chunk, long chunkOffset, long expected, long newValue) {
        return BYTES.compareAndExchangeLong(chunk, chunkOffset, expected, newValue) == expected;
    }

    // Override of default method in NativeMemory for efficiency
    @Override
    public int compareAndExchangeInt(long address, int expected, int newValue) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Integer.BYTES);
        return BYTES.compareAndExchangeInt(chunk, chunkOffset, expected, newValue);
    }

    // Override of default method in NativeMemory for efficiency
    @Override
    public long compareAndExchangeLong(long address, long expected, long newValue) throws IllegalMemoryAccessException {
        long chunkOffset = getChunkOffset(address);
        byte[] chunk = getChunk(address);
        validateAccess(chunk, chunkOffset, Long.BYTES);
        return BYTES.compareAndExchangeLong(chunk, chunkOffset, expected, newValue);
    }

    @Collect(NativeMemory.class)
    public static final class Provider implements NativeMemory.Provider {

        public static final String ID = "TruffleByteArrayChunkedMemory";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public NativeMemory create() {
            return new TruffleByteArrayChunkedMemoryImpl();
        }

        @Override
        public boolean needsNativeAccess() {
            return false;
        }
    }
}
