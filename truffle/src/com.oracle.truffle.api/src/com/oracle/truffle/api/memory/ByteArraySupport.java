/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oracle.truffle.api.memory;

/**
 * Utility methods to write to and read from arrays of bytes. Singletons of this class are obtained
 * from {@link #littleEndian()} or {@link #bigEndian()} respectively to access byte arrays in
 * little-endian or big-endian order.
 *
 * <h2>Thread safety</h2>
 * <p>
 * The methods of this class are <em>not</em> safe for use by multiple concurrent threads, unless
 * otherwise stated. If a byte array is to be used by more than one thread then access to the byte
 * array should be controlled by appropriate synchronization.
 *
 * <h2>Alignment</h2>
 * <p>
 * Unaligned accesses are allowed, but will not constant-fold during partial evaluation. If constant
 * folding is desired, consider using the unaligned methods (e.g., {@link #getIntUnaligned}) to read
 * immutable data from unaligned offsets.
 *
 * @since 20.3
 */
public abstract class ByteArraySupport {
    /**
     * Constructor is package private. Use provided accessors in {@link ByteArraySupports} instead.
     *
     * @since 20.3
     */
    ByteArraySupport() {
    }

    /**
     * Enables accessing multibyte Java primitives from byte arrays in little-endian order.
     * <p>
     * Example usage:
     *
     * <pre>
     * <code>byte[] buffer = new byte[]{0, 0, 0, 0};
     * ByteArraySupport.littleEndian().putShort(buffer, 2, (short) 1);
     * // buffer[2] == (byte) 1
     * // buffer[3] == (byte) 0
     * ByteArraySupport.littleEndian().putShort(buffer, 3, (short) 1);
     * // throws IndexOutOfBoundsException exception</code>
     * </pre>
     *
     * @since 20.3
     */
    public static ByteArraySupport littleEndian() {
        return ByteArraySupports.LITTLE_ENDIAN;
    }

    /**
     * Enables accessing multibyte Java primitives from byte arrays in big-endian order.
     * <p>
     * Example usage:
     *
     * <pre>
     * <code>byte[] buffer = new byte[]{0, 0, 0, 0};
     * ByteArraySupport.bigEndian().putShort(buffer, 0, (short) 1);
     * // buffer[0] == (byte) 0
     * // buffer[1] == (byte) 1</code>
     * </pre>
     *
     * @since 20.3
     */
    public static ByteArraySupport bigEndian() {
        return ByteArraySupports.BIG_ENDIAN;
    }

    static ByteArraySupport nativeUnsafe() {
        return ByteArraySupports.NATIVE_UNSAFE;
    }

    static ByteArraySupport nativeChecked() {
        return ByteArraySupports.NATIVE_CHECKED;
    }

    /**
     * Checks if an access is in bounds of the given buffer.
     * <p>
     * If out-of-bounds accesses are expected to happen frequently, it is faster (~1.2x in
     * interpreter mode) to use this method to check for them than catching
     * {@link IndexOutOfBoundsException}s.
     *
     * @param buffer the byte array
     * @param startByteOffset the start byte offset of the access
     * @param length the number of bytes accessed
     * @return True if the access is in bounds, false otherwise
     * @since 20.3
     */
    @SuppressWarnings("static-method")
    public final boolean inBounds(byte[] buffer, int startByteOffset, int length) {
        return length >= 1 && startByteOffset >= 0 && startByteOffset <= buffer.length - length;
    }

    /**
     * Checks if an access is in bounds of the given buffer.
     * <p>
     * If out-of-bounds accesses are expected to happen frequently, it is faster (~1.2x in
     * interpreter mode) to use this method to check for them than catching
     * {@link IndexOutOfBoundsException}s.
     *
     * @param buffer the byte array
     * @param startByteOffset the start byte offset of the access
     * @param length the number of bytes accessed
     * @return True if the access is in bounds, false otherwise
     * @since 22.2
     */
    @SuppressWarnings("static-method")
    public final boolean inBounds(byte[] buffer, long startByteOffset, long length) {
        return length >= 1L && startByteOffset >= 0L && startByteOffset <= buffer.length - length;
    }

    /**
     * Reads the byte at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the byte will be read
     * @return the byte at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 20.3
     */
    public abstract byte getByte(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the byte at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the byte will be read
     * @return the byte at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 22.2
     */
    public abstract byte getByte(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Writes the given byte at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset at which the byte will be written
     * @param value the byte value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 20.3
     */
    public abstract void putByte(byte[] buffer, int byteOffset, byte value) throws IndexOutOfBoundsException;

    /**
     * Writes the given byte at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset at which the byte will be written
     * @param value the byte value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 22.2
     */
    public abstract void putByte(byte[] buffer, long byteOffset, byte value) throws IndexOutOfBoundsException;

    /**
     * Reads the short at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the short will be read
     * @return the short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 20.3
     */
    public abstract short getShort(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the short at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the short will be read
     * @return the short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 22.2
     */
    public abstract short getShort(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Writes the given short at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the short will be written
     * @param value the short value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 20.3
     */
    public abstract void putShort(byte[] buffer, int byteOffset, short value) throws IndexOutOfBoundsException;

    /**
     * Writes the given short at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the short will be written
     * @param value the short value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 22.2
     */
    public abstract void putShort(byte[] buffer, long byteOffset, short value) throws IndexOutOfBoundsException;

    /**
     * Reads the int at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the int will be read
     * @return the int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 20.3
     */
    public abstract int getInt(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the int at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the int will be read
     * @return the int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 20.3
     */
    public abstract int getInt(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Writes the given int at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the int will be written
     * @param value the int value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 20.3
     */
    public abstract void putInt(byte[] buffer, int byteOffset, int value) throws IndexOutOfBoundsException;

    /**
     * Writes the given int at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the int will be written
     * @param value the int value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 22.2
     */
    public abstract void putInt(byte[] buffer, long byteOffset, int value) throws IndexOutOfBoundsException;

    /**
     * Reads the long at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the int will be read
     * @return the int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 20.3
     */
    public abstract long getLong(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the long at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the int will be read
     * @return the int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 22.2
     */
    public abstract long getLong(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Writes the given long at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the int will be written
     * @param value the int value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 20.3
     */
    public abstract void putLong(byte[] buffer, int byteOffset, long value) throws IndexOutOfBoundsException;

    /**
     * Writes the given long at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the int will be written
     * @param value the int value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 22.2
     */
    public abstract void putLong(byte[] buffer, long byteOffset, long value) throws IndexOutOfBoundsException;

    /**
     * Reads the float at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the float will be read
     * @return the float at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 20.3
     */
    public abstract float getFloat(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the float at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the float will be read
     * @return the float at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 22.2
     */
    public abstract float getFloat(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Writes the given float at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the float will be written
     * @param value the float value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 20.3
     */
    public abstract void putFloat(byte[] buffer, int byteOffset, float value) throws IndexOutOfBoundsException;

    /**
     * Writes the given float at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the float will be written
     * @param value the float value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 22.2
     */
    public abstract void putFloat(byte[] buffer, long byteOffset, float value) throws IndexOutOfBoundsException;

    /**
     * Reads the double at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the double will be read
     * @return the double at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 20.3
     */
    public abstract double getDouble(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the double at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the double will be read
     * @return the double at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 22.2
     */
    public abstract double getDouble(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Writes the given double at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the double will be written
     * @param value the double value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 20.3
     */
    public abstract void putDouble(byte[] buffer, int byteOffset, double value) throws IndexOutOfBoundsException;

    /**
     * Writes the given double at the given byte offset from the start of the buffer.
     *
     * @param buffer the byte array to write in
     * @param byteOffset the byte offset from which the double will be written
     * @param value the double value to be written
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 22.2
     */
    public abstract void putDouble(byte[] buffer, long byteOffset, double value) throws IndexOutOfBoundsException;

    /**
     * Reads the short at the given byte offset from the start of the buffer.
     *
     * Unlike {@link #getShort(byte[], int)}, the byte offset does not need to be short-aligned. The
     * platform may not support atomic unaligned reads, so this method should not be used with
     * shared mutable data.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the short will be read
     * @return the short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 24.2
     */
    public abstract short getShortUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the short at the given byte offset from the start of the buffer.
     *
     * Unlike {@link #getShort(byte[], long)}, the byte offset does not need to be short-aligned.
     * The platform may not support atomic unaligned reads, so this method should not be used with
     * shared mutable data.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the short will be read
     * @return the short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 24.2
     */
    public abstract short getShortUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the int at the given byte offset from the start of the buffer.
     *
     * Unlike {@link #getInt(byte[], int)}, the byte offset does not need to be int-aligned. The
     * platform may not support atomic unaligned reads, so this method should not be used with
     * shared mutable data.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the int will be read
     * @return the int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 24.2
     */
    public abstract int getIntUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the int at the given byte offset from the start of the buffer.
     *
     * Unlike {@link #getInt(byte[], long)}, the byte offset does not need to be int-aligned. The
     * platform may not support atomic unaligned reads, so this method should not be used with
     * shared mutable data.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the int will be read
     * @return the int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 24.2
     */
    public abstract int getIntUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the long at the given byte offset from the start of the buffer.
     *
     * Unlike {@link #getLong(byte[], int)}, the byte offset does not need to be long-aligned. The
     * platform may not support atomic unaligned reads, so this method should not be used with
     * shared mutable data.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the long will be read
     * @return the long at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 24.2
     */
    public abstract long getLongUnaligned(byte[] buffer, int byteOffset) throws IndexOutOfBoundsException;

    /**
     * Reads the long at the given byte offset from the start of the buffer.
     *
     * Unlike {@link #getLong(byte[], long)}, the byte offset does not need to be long-aligned. The
     * platform may not support atomic unaligned reads, so this method should not be used with
     * shared mutable data.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset from which the long will be read
     * @return the long at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 24.2
     */
    public abstract long getLongUnaligned(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Volatile version of {@link #getByte(byte[], long)}.
     *
     * @since 23.1
     */
    public abstract byte getByteVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Volatile version of {@link #putByte(byte[], long, byte)}.
     *
     * @since 23.1
     */
    public abstract void putByteVolatile(byte[] buffer, long byteOffset, byte value) throws IndexOutOfBoundsException;

    /**
     * Volatile version of {@link #getShort(byte[], long)}.
     *
     * @since 23.1
     */
    public abstract short getShortVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Volatile version of {@link #putShort(byte[], long, short)}.
     *
     * @since 23.1
     */
    public abstract void putShortVolatile(byte[] buffer, long byteOffset, short value) throws IndexOutOfBoundsException;

    /**
     * Volatile version of {@link #getInt(byte[], long)}.
     *
     * @since 23.1
     */
    public abstract int getIntVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Volatile version of {@link #putInt(byte[], long, int)}.
     *
     * @since 23.1
     */
    public abstract void putIntVolatile(byte[] buffer, long byteOffset, int value) throws IndexOutOfBoundsException;

    /**
     * Volatile version of {@link #getLong(byte[], long)}.
     *
     * @since 23.1
     */
    public abstract long getLongVolatile(byte[] buffer, long byteOffset) throws IndexOutOfBoundsException;

    /**
     * Volatile version of {@link #putLong(byte[], long, long)}.
     *
     * @since 23.1
     */
    public abstract void putLongVolatile(byte[] buffer, long byteOffset, long value) throws IndexOutOfBoundsException;

    /**
     * Atomically adds the given byte to the current byte at the given byte offset from the start of
     * the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the byte will be read and written to
     * @param delta the byte value to add
     * @return the previous byte at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 23.1
     */
    public abstract byte getAndAddByte(byte[] buffer, long byteOffset, byte delta) throws IndexOutOfBoundsException;

    /**
     * Atomically adds the given short to the current short at the given byte offset from the start
     * of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the short will be read and written to
     * @param delta the short value to add
     * @return the previous short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 23.1
     */
    public abstract short getAndAddShort(byte[] buffer, long byteOffset, short delta) throws IndexOutOfBoundsException;

    /**
     * Atomically adds the given int to the current int at the given byte offset from the start of
     * the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the int will be read and written to
     * @param delta the int value to add
     * @return the previous int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 23.1
     */
    public abstract int getAndAddInt(byte[] buffer, long byteOffset, int delta) throws IndexOutOfBoundsException;

    /**
     * Atomically adds the given long to the current long at the given byte offset from the start of
     * the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the long will be read and written to
     * @param delta the long value to add
     * @return the previous long at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 23.1
     */
    public abstract long getAndAddLong(byte[] buffer, long byteOffset, long delta) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-ANDs the given byte to the current byte at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the byte will be read and written to
     * @param mask the byte value to bitwise-AND
     * @return the previous byte at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 23.1
     */
    public abstract byte getAndBitwiseAndByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-ANDs the given short to the current short at the given byte offset from
     * the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the short will be read and written to
     * @param mask the short value to bitwise-AND
     * @return the previous short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 23.1
     */
    public abstract short getAndBitwiseAndShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-ANDs the given int to the current int at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the int will be read and written to
     * @param mask the int value to bitwise-AND
     * @return the previous int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 23.1
     */
    public abstract int getAndBitwiseAndInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-ANDs the given long to the current long at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the long will be read and written to
     * @param mask the long value to bitwise-AND
     * @return the previous long at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 23.1
     */
    public abstract long getAndBitwiseAndLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-ORs the given byte to the current byte at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the byte will be read and written to
     * @param mask the byte value to bitwise-OR
     * @return the previous byte at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 23.1
     */
    public abstract byte getAndBitwiseOrByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-ORs the given short to the current short at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the short will be read and written to
     * @param mask the short value to bitwise-OR
     * @return the previous short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 23.1
     */
    public abstract short getAndBitwiseOrShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-ORs the given int to the current int at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the int will be read and written to
     * @param mask the int value to bitwise-OR
     * @return the previous int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 23.1
     */
    public abstract int getAndBitwiseOrInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-ORs the given long to the current long at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the long will be read and written to
     * @param mask the long value to bitwise-OR
     * @return the previous long at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 23.1
     */
    public abstract long getAndBitwiseOrLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-XORs the given byte to the current byte at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the byte will be read and written to
     * @param mask the byte value to bitwise-XOR
     * @return the previous byte at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 23.1
     */
    public abstract byte getAndBitwiseXorByte(byte[] buffer, long byteOffset, byte mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-XORs the given short to the current short at the given byte offset from
     * the start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the short will be read and written to
     * @param mask the short value to bitwise-XOR
     * @return the previous short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 23.1
     */
    public abstract short getAndBitwiseXorShort(byte[] buffer, long byteOffset, short mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-XORs the given int to the current int at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the int will be read and written to
     * @param mask the int value to bitwise-XOR
     * @return the previous int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 23.1
     */
    public abstract int getAndBitwiseXorInt(byte[] buffer, long byteOffset, int mask) throws IndexOutOfBoundsException;

    /**
     * Atomically bitwise-XORs the given long to the current long at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the long will be read and written to
     * @param mask the long value to bitwise-XOR
     * @return the previous long at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 23.1
     */
    public abstract long getAndBitwiseXorLong(byte[] buffer, long byteOffset, long mask) throws IndexOutOfBoundsException;

    /**
     * Atomically exchanges the given byte with the current byte at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the byte will be read and written to
     * @param newValue the new byte value
     * @return the previous byte at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 23.1
     */
    public abstract byte getAndSetByte(byte[] buffer, long byteOffset, byte newValue) throws IndexOutOfBoundsException;

    /**
     * Atomically exchanges the given short with the current short at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the short will be read and written to
     * @param newValue the new short value
     * @return the previous short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 23.1
     */
    public abstract short getAndSetShort(byte[] buffer, long byteOffset, short newValue) throws IndexOutOfBoundsException;

    /**
     * Atomically exchanges the given int with the current int at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the int will be read and written to
     * @param newValue the new int value
     * @return the previous int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 23.1
     */
    public abstract int getAndSetInt(byte[] buffer, long byteOffset, int newValue) throws IndexOutOfBoundsException;

    /**
     * Atomically exchanges the given long with the current long at the given byte offset from the
     * start of the buffer.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the long will be read and written to
     * @param newValue the new long value
     * @return the previous long at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 23.1
     */
    public abstract long getAndSetLong(byte[] buffer, long byteOffset, long newValue) throws IndexOutOfBoundsException;

    /**
     * Atomically exchanges the given byte with the current byte at the given byte offset from the
     * start of the buffer, if and only if the current byte equals the expected byte.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the byte will be read and written to
     * @param expected the expected byte value
     * @param x the replacement byte value
     * @return the previous byte at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length}
     * @since 23.1
     */
    public abstract byte compareAndExchangeByte(byte[] buffer, long byteOffset, byte expected, byte x) throws IndexOutOfBoundsException;

    /**
     * Atomically exchanges the given short with the current short at the given byte offset from the
     * start of the buffer, if and only if the current short equals the expected short.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the short will be read and written to
     * @param expected the expected short value
     * @param x the replacement short value
     * @return the previous short at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 1}
     * @since 23.1
     */
    public abstract short compareAndExchangeShort(byte[] buffer, long byteOffset, short expected, short x) throws IndexOutOfBoundsException;

    /**
     * Atomically exchanges the given int with the current int at the given byte offset from the
     * start of the buffer, if and only if the current int equals the expected int.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the int will be read and written to
     * @param expected the expected int value
     * @param x the replacement int value
     * @return the previous int at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 3}
     * @since 23.1
     */
    public abstract int compareAndExchangeInt(byte[] buffer, long byteOffset, int expected, int x) throws IndexOutOfBoundsException;

    /**
     * Atomically exchanges the given long with the current long at the given byte offset from the
     * start of the buffer, if and only if the current long equals the expected long.
     *
     * @param buffer the byte array to read from
     * @param byteOffset the byte offset at which the long will be read and written to
     * @param expected the expected long value
     * @param x the replacement long value
     * @return the previous long at the given byte offset from the start of the buffer
     * @throws IndexOutOfBoundsException if and only if
     *             {@code byteOffset < 0 || byteOffset >= buffer.length - 7}
     * @since 23.1
     */
    public abstract long compareAndExchangeLong(byte[] buffer, long byteOffset, long expected, long x) throws IndexOutOfBoundsException;
}
