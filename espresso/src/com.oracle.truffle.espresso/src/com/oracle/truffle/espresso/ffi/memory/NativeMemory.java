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

/**
 * The native memory abstraction layer for Espresso. It is assumed when you call
 * {@link #allocateMemory(long)} it reserves a continuous block of memory of the specified size. Any
 * location within the allocated block should be accessible by adding an offset (between 0 and size)
 * to the base address.
 */
public interface NativeMemory {
    long reallocateMemory(long address, long bytes);

    long allocateMemory(long bytes);

    void freeMemory(long bytes);

    void setMemory(long address, long bytes, byte value);

    enum MemoryAccessMode {
        PLAIN,
        OPAQUE,
        RELEASE_ACQUIRE,
        VOLATILE
    }

    void putByte(long address, byte value, MemoryAccessMode accessMode);

    void putShort(long address, short value, MemoryAccessMode accessMode);

    void putInt(long address, int value, MemoryAccessMode accessMode);

    void putLong(long address, long value, MemoryAccessMode accessMode);

    default void putBoolean(long address, boolean value, MemoryAccessMode accessMode) {
        putByte(address, value ? (byte) 1 : (byte) 0, accessMode);
    }

    default void putChar(long address, char value, MemoryAccessMode accessMode) {
        putShort(address, (short) value, accessMode);
    }

    default void putFloat(long address, float value, MemoryAccessMode accessMode) {
        putInt(address, Float.floatToRawIntBits(value), accessMode);
    }

    default void putDouble(long address, double value, MemoryAccessMode accessMode) {
        putLong(address, Double.doubleToRawLongBits(value), accessMode);
    }

    byte getByte(long address, MemoryAccessMode accessMode);

    short getShort(long address, MemoryAccessMode accessMode);

    int getInt(long address, MemoryAccessMode accessMode);

    long getLong(long address, MemoryAccessMode accessMode);

    default boolean getBoolean(long address, MemoryAccessMode accessMode) {
        return getByte(address, accessMode) != 0;
    }

    default char getChar(long address, MemoryAccessMode accessMode) {
        return (char) getShort(address, accessMode);
    }

    default float getFloat(long address, MemoryAccessMode accessMode) {
        return Float.intBitsToFloat(getInt(address, accessMode));
    }

    default double getDouble(long address, MemoryAccessMode accessMode) {
        return Double.longBitsToDouble(getLong(address, accessMode));
    }

    boolean compareAndSetLong(long address, long expected, long newValue);

    boolean compareAndSetInt(long address, int expected, int newValue);

    default long compareAndExchangeLong(long address, long expected, long newValue) {
        long previous;
        do {
            previous = getLong(address, MemoryAccessMode.VOLATILE);
            if (previous != expected) {
                return previous;
            }
        } while (!compareAndSetLong(address, expected, newValue));
        return previous;
    }

    default int compareAndExchangeInt(long address, int expected, int newValue) {
        int previous;
        do {
            previous = getInt(address, MemoryAccessMode.VOLATILE);
            if (previous != expected) {
                return previous;
            }
        } while (!compareAndSetInt(address, expected, newValue));
        return previous;
    }

    default void copyMemory(long srcBase,
                    long destBase,
                    long bytes, MemoryAccessMode accessMode) {
        for (int offset = 0; offset < bytes; offset++) {
            putByte(destBase + offset, getByte(srcBase + offset, accessMode), accessMode);
        }
    }

    default void readMemory(long addr, long bytes, ByteBuffer buf) {
        for (long offset = 0; offset < bytes; offset++) {
            buf.put(getByte(addr + offset, MemoryAccessMode.PLAIN));
        }
    }

    default void readMemory(long addr, long bytes, byte[] buf) {
        readMemory(addr, bytes, ByteBuffer.wrap(buf));
    }

    default void writeMemory(long addr, long bytes, ByteBuffer buf) {
        for (long offset = 0; offset < bytes; offset++) {
            putByte(addr + offset, buf.get(), MemoryAccessMode.PLAIN);
        }
    }

    default void writeMemory(long addr, long bytes, byte[] buf) {
        writeMemory(addr, bytes, ByteBuffer.wrap(buf));
    }

    /*
     * Should be overwritten if a direct way to access Memory can be provided which does not involve
     * copying.
     */
    default ByteBuffer getDirectBuffer(long address, long bytes) {
        throw new UnsupportedOperationException("DirectMemory Access is not supported");
    }

    default boolean isDirectBufferSupported() {
        return false;
    }
}
