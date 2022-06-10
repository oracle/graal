/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * The methods of this class are <em>not</em> safe for use by multiple concurrent threads. If a byte
 * array is to be used by more than one thread then access to the byte array should be controlled by
 * appropriate synchronization.
 *
 * <h2>Alignment</h2>
 * <p>
 * Unaligned accesses are allowed.
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
}
