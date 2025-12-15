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

import java.io.Serial;
import java.nio.ByteBuffer;

/**
 * The native memory abstraction layer for Espresso. All guest off-heap memory operations in
 * Espresso now flow through this NativeMemory layer.
 * <p>
 * When you call {@link #allocateMemory(long)}, it reserves a contiguous block of memory of the
 * specified size. Any location within the block should be accessible by adding an offset (between 0
 * and the size) to the base address.
 * </p>
 * <p>
 * <b>Maximum allocation size in No-Native Mode:</b>
 * <p>
 * In no-native mode, the default implementation ({@link TruffleByteArrayChunkedMemoryImpl}) uses
 * Java byte arrays as the backing memory. As a result, each continuous memory region can only hold
 * up to {@link Integer#MAX_VALUE} bytes. If you try to allocate more than that, an
 * {@link MemoryAllocationException} is thrown.
 * </p>
 *
 * <p>
 * If you need to use memory regions larger than {@link Integer#MAX_VALUE} (up to
 * {@link Long#MAX_VALUE}), you can specify "MemorySegmentChunkedMemory" as the nativeMemory
 * implementation in {@link com.oracle.truffle.espresso.EspressoOptions#NativeMemory}.
 * </p>
 * <p>
 * <b>Exceptions</b>
 * <p>
 * All implementations throw {@link MemoryAllocationException} if an error occurs during allocation
 * (e.g., out of memory, exceeding allocation limits). {@link IllegalMemoryAccessException} is
 * thrown only if all memory accesses are fully bounds-checked. This is currently true for all
 * subclasses of {@link ChunkedNativeMemory}. {@link UnsafeNativeMemory}, in contrast, does
 * <b>not</b> check memory accesses and therefore does not throw
 * {@link IllegalMemoryAccessException}, instead, violating memory boundaries will most likely cause
 * a JVM crash (e.g., segmentation fault).
 * </p>
 */
public interface NativeMemory {
    /**
     * Reserves a contiguous block of memory of the specified size and returns the base address. Any
     * location within the block should be accessible by adding an offset (between 0 and
     * {@code bytes}) to the base address.
     * <p>
     * Even if {@code bytes == 0} a valid memory address is returned.
     *
     * @param bytes the number of bytes to allocate
     * @return the base address of the newly allocated memory block.
     * @throws MemoryAllocationException if out of memory or the implementation's maximum allocation
     *             size is exceeded (see class level documentation).
     */
    long allocateMemory(long bytes) throws MemoryAllocationException;

    /**
     * Reallocates a previously allocated block of memory to a new size. The contents of the new
     * block will be the same as the original block, up to the minimum of the old and new sizes. As
     * specified in {@link NativeMemory#allocateMemory} the new block should be continuously
     * accessible and explicitly freed.
     * <p>
     * Even if {@code bytes == 0} a valid memory address is returned.
     * <p>
     * Calling this method with {@code address == 0} will directly call
     * {@link NativeMemory#allocateMemory(long)}
     *
     * @param address the address of the previous allocated block
     * @param bytes the new size of the block in bytes
     * @return the base address of the newly allocated memory block.
     * @throws MemoryAllocationException if out of memory or the implementation's maximum allocation
     *             size is exceeded (see class level documentation).
     * @throws IllegalMemoryAccessException if the address provided is non-zero and was not
     *             previously returned by {@link NativeMemory#allocateMemory(long)} memory.
     */
    long reallocateMemory(long address, long bytes) throws MemoryAllocationException, IllegalMemoryAccessException;

    /**
     * Frees a previously allocated block of memory. Immediately returns if {@code address == 0}.
     *
     * @param address the base address of an allocated block
     * @throws IllegalMemoryAccessException if the address provided is non-zero and was not
     *             previously returned by {@link NativeMemory#allocateMemory(long)} memory.
     */
    void freeMemory(long address) throws IllegalMemoryAccessException;

    /**
     * Creates a ByteBuffer view of the native memory region starting at the specified address. It
     * is assumed the address points to a valid, accessible memory region with at least
     * {@code bytes} bytes allocated.
     *
     * <p>
     * <b>Important Lifetime Notes:</b>
     * <ul>
     * <li>The returned ByteBuffer is a <i>view</i> of existing native memory, not an
     * allocation</li>
     * <li>The MemoryRegion's lifetime is <b>not</b> tied to the returned ByteBuffer</li>
     * <li>The underlying memory may be freed while the ByteBuffer is still referenced</li>
     * <li>Garbage collecting the ByteBuffer does <b>not</b> free the underlying memory</li>
     * </ul>
     * To create a buffer backed by newly allocated memory with explicit lifetime control, use
     * {@link NativeMemory#allocateMemoryBuffer(int)} instead.
     *
     * @param address the starting address of the native memory region
     * @param bytes the size of the memory region in bytes
     * @return a direct ByteBuffer view of the native memory
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if address plus bytes exceeds the
     *             bounds of the allocated memory region
     */
    @SuppressWarnings("unused")
    ByteBuffer wrapNativeMemory(long address, int bytes) throws IllegalMemoryAccessException;

    /**
     * Allocates a MemoryBuffer with the given size. The MemoryBuffer needs to freed explicitly
     * using {@link MemoryBuffer#close()}.
     *
     * @param bytes the number of bytes to allocate
     * @return a {@link MemoryBuffer} containing the ByteBuffer and the Address of the underlying
     *         memory region.
     * @throws MemoryAllocationException if out of memory.
     */
    default MemoryBuffer allocateMemoryBuffer(int bytes) throws MemoryAllocationException {
        return new MemoryBuffer(bytes, this);
    }

    enum MemoryAccessMode {
        PLAIN,
        OPAQUE,
        RELEASE_ACQUIRE,
        VOLATILE
    }

    /**
     * Writes a byte value to the specified memory address in the given {@link MemoryAccessMode}.
     * 
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed byte is outside the
     *             boundaries of the allocated memory region.
     */
    void putByte(long address, byte value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException;

    /**
     * Writes a short value to the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    void putShort(long address, short value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException;

    /**
     * Writes an int value to the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    void putInt(long address, int value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException;

    /**
     * Writes a long value to the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    void putLong(long address, long value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException;

    /**
     * Writes a boolean value to the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed byte is outside the
     *             boundaries of the allocated memory region.
     */
    default void putBoolean(long address, boolean value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        putByte(address, value ? (byte) 1 : (byte) 0, accessMode);
    }

    /**
     * Writes a char value to the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default void putChar(long address, char value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        putShort(address, (short) value, accessMode);
    }

    /**
     * Writes a float value to the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default void putFloat(long address, float value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        putInt(address, Float.floatToRawIntBits(value), accessMode);
    }

    /**
     * Writes a byte double to the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default void putDouble(long address, double value, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        putLong(address, Double.doubleToRawLongBits(value), accessMode);
    }

    /**
     * Reads a byte value from the specified memory address in the given {@link MemoryAccessMode}.
     * 
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed byte is outside the
     *             boundaries of the allocated memory region.
     */
    byte getByte(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException;

    /**
     * Reads a short value from the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    short getShort(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException;

    /**
     * Reads an int value from the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    int getInt(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException;

    /**
     * Reads a long value from the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    long getLong(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException;

    /**
     * Reads a boolean value from the specified memory address in the given
     * {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed byte is outside the
     *             boundaries of the allocated memory region.
     */
    default boolean getBoolean(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        return getByte(address, accessMode) != 0;
    }

    /**
     * Reads a char value from the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default char getChar(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        return (char) getShort(address, accessMode);
    }

    /**
     * Reads a float value from the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default float getFloat(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        return Float.intBitsToFloat(getInt(address, accessMode));
    }

    /**
     * Reads a double value from the specified memory address in the given {@link MemoryAccessMode}.
     *
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default double getDouble(long address, MemoryAccessMode accessMode) throws IllegalMemoryAccessException {
        return Double.longBitsToDouble(getLong(address, accessMode));
    }

    /**
     * Atomically sets a long value at the specified address if its current value equals the
     * expected value.
     * 
     * @return true if the value was updated, false otherwise.
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    boolean compareAndSetLong(long address, long expected, long newValue) throws IllegalMemoryAccessException;

    /**
     * Atomically sets an int value at the specified address if its current value equals the
     * expected value.
     *
     * @return true if the value was updated, false otherwise.
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    boolean compareAndSetInt(long address, int expected, int newValue) throws IllegalMemoryAccessException;

    /**
     * Atomically sets a long value at the specified address if its current value equals the
     * expected value.
     *
     * @return the value read at the given address.
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default long compareAndExchangeLong(long address, long expected, long newValue) throws IllegalMemoryAccessException {
        long previous;
        do {
            previous = getLong(address, MemoryAccessMode.VOLATILE);
            if (previous != expected) {
                return previous;
            }
        } while (!compareAndSetLong(address, expected, newValue));
        return previous;
    }

    /**
     * Atomically sets an int value at the specified address if its current value equals the
     * expected value.
     *
     * @return the value read at the given address.
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default int compareAndExchangeInt(long address, int expected, int newValue) throws IllegalMemoryAccessException {
        int previous;
        do {
            previous = getInt(address, MemoryAccessMode.VOLATILE);
            if (previous != expected) {
                return previous;
            }
        } while (!compareAndSetInt(address, expected, newValue));
        return previous;
    }

    /**
     * Sets {@code bytes} bytes in the memory region starting at {@code address} to the specified
     * {@code value}.
     *
     * @param address the base address of the memory region to set
     * @param bytes the number of bytes to set to the given value
     * @param value the byte value to write throughout the specified region
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    void setMemory(long address, long bytes, byte value) throws IllegalMemoryAccessException;

    /**
     * Copies bytes-many bytes from the source address to the destination address. This method works
     * in plain accessMode.
     *
     * @param srcBase The source memory address.
     * @param destBase The destination memory address.
     * @param bytes Number of bytes copied.
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default void copyMemory(long srcBase,
                    long destBase,
                    long bytes) throws IllegalMemoryAccessException {
        if (srcBase < destBase && srcBase + bytes > destBase) {
            // Overlap detected --> copy from end to beginning
            for (long offset = bytes - 1; offset >= 0; offset--) {
                putByte(destBase + offset, getByte(srcBase + offset, MemoryAccessMode.PLAIN), MemoryAccessMode.PLAIN);
            }
        } else {
            // No overlap or dest is before src --> copy normally
            for (long offset = 0; offset < bytes; offset++) {
                putByte(destBase + offset, getByte(srcBase + offset, MemoryAccessMode.PLAIN), MemoryAccessMode.PLAIN);
            }
        }
    }

    /**
     * Reads bytes from the memory address into the given buffer until its completely filled. This
     * method works in plain accessMode.
     *
     * @param addr Memory address where we read bytes from.
     * @param buf The buffer where we write the bytes to.
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default void readMemory(long addr, byte[] buf) throws IllegalMemoryAccessException {
        for (int offset = 0; offset < buf.length; offset++) {
            buf[offset] = getByte(addr + offset, MemoryAccessMode.PLAIN);
        }
    }

    /**
     * Writes all bytes from the given buffer to the memory address. This method works in plain
     * accessMode.
     *
     * @param addr Memory address where we write bytes from.
     * @param buf The buffer where we read the bytes into.
     * @throws IllegalMemoryAccessException if the provided address was not returned by
     *             {@link NativeMemory#allocateMemory(long)}, or if the accessed bytes are outside
     *             the boundaries of the allocated memory region.
     */
    default void writeMemory(long addr, byte[] buf) throws IllegalMemoryAccessException {
        for (int offset = 0; offset < buf.length; offset++) {
            putByte(addr + offset, buf[offset], MemoryAccessMode.PLAIN);
        }
    }

    interface Provider {
        /**
         * @return the name of the nativeMemory
         */
        String id();

        /**
         * @return an instance of the nativeMemory
         */
        NativeMemory create();

        /**
         * @return whether the underlying NativeMemory needs native access. This should return false
         *         iff. the implementation is safe to use when native access is not allowed
         *         ({@code env.isNativeAccessAllowed() == false}).
         */
        boolean needsNativeAccess();
    }

    /**
     * Exception class for illegal memory accesses.
     * <p>
     * When accessing memory, you must always use an address returned by
     * {@link NativeMemory#allocateMemory(long)} and a valid offset. An offset is valid if it does
     * not exceed the boundaries of the allocated memory region. If these constraints are violated
     * this exception is thrown.
     */
    class IllegalMemoryAccessException extends Exception {
        @Serial private static final long serialVersionUID = 1L;

        public IllegalMemoryAccessException(String message) {
            super(message);
        }
    }

    /**
     * Exception class for failures when allocating memory.
     */
    class MemoryAllocationException extends Exception {
        @Serial private static final long serialVersionUID = 1L;

        public MemoryAllocationException(String message) {
            super(message);
        }

        public MemoryAllocationException(Throwable cause) {
            super("Could not allocate memory: ", cause);
        }
    }
}
