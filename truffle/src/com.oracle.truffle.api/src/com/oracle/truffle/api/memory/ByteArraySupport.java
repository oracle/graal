/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
     * @param buffer The byte array
     * @param startIndex The start index of the access
     * @param length The number of bytes accessed
     * @return True if if the access is in bounds, false otherwise
     * @since 20.3
     */
    public final boolean inBounds(byte[] buffer, int startIndex, int length) {
        return length >= 1 && startIndex >= 0 && startIndex <= buffer.length - length;
    }

    /**
     * Reads the byte at the given index.
     *
     * @param buffer The byte array to read from
     * @param index The index from which the byte will be read
     * @return The byte at the given index
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size
     * @since 20.3
     */
    public abstract byte getByte(byte[] buffer, int index) throws IndexOutOfBoundsException;

    /**
     * Writes the given byte at the given index.
     *
     * @param buffer The byte array to write in
     * @param index The index at which the byte will be written
     * @param value The byte value to be written
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size
     * @since 20.3
     */
    public abstract void putByte(byte[] buffer, int index, byte value) throws IndexOutOfBoundsException;

    /**
     * Reads the short at the given index.
     *
     * @param buffer The byte array to read from
     * @param index The index from which the short will be read
     * @return The short at the given index
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus one
     * @since 20.3
     */
    public abstract short getShort(byte[] buffer, int index) throws IndexOutOfBoundsException;

    /**
     * Writes the given short at the given index.
     *
     * @param buffer The byte array to write in
     * @param index The index at which the short will be written
     * @param value The short value to be written
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus one
     * @since 20.3
     */
    public abstract void putShort(byte[] buffer, int index, short value) throws IndexOutOfBoundsException;

    /**
     * Reads the int at the given index.
     *
     * @param buffer The byte array to read from
     * @param index The index from which the int will be read
     * @return The int at the given index
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus three
     * @since 20.3
     */
    public abstract int getInt(byte[] buffer, int index) throws IndexOutOfBoundsException;

    /**
     * Writes the given int at the given index.
     *
     * @param buffer The byte array to write in
     * @param index The index at which the int will be written
     * @param value The int value to be written
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus three
     * @since 20.3
     */
    public abstract void putInt(byte[] buffer, int index, int value) throws IndexOutOfBoundsException;

    /**
     * Reads the int at the given index.
     *
     * @param buffer The byte array to read from
     * @param index The index from which the int will be read
     * @return The int at the given index
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus seven
     * @since 20.3
     */
    public abstract long getLong(byte[] buffer, int index) throws IndexOutOfBoundsException;

    /**
     * Writes the given int at the given index.
     *
     * @param buffer The byte array to write in
     * @param index The index at which the int will be written
     * @param value The int value to be written
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus seven
     * @since 20.3
     */
    public abstract void putLong(byte[] buffer, int index, long value) throws IndexOutOfBoundsException;

    /**
     * Reads the float at the given index.
     *
     * @param buffer The byte array to read from
     * @param index The index from which the float will be read
     * @return The float at the given index
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus three
     * @since 20.3
     */
    public abstract float getFloat(byte[] buffer, int index) throws IndexOutOfBoundsException;

    /**
     * Writes the given float at the given index.
     *
     * @param buffer The byte array to write in
     * @param index The index at which the float will be written
     * @param value The float value to be written
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus three
     * @since 20.3
     */
    public abstract void putFloat(byte[] buffer, int index, float value) throws IndexOutOfBoundsException;

    /**
     * Reads the double at the given index.
     *
     * @param buffer The byte array to read from
     * @param index The index from which the double will be read
     * @return The double at the given index
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus seven
     * @since 20.3
     */
    public abstract double getDouble(byte[] buffer, int index) throws IndexOutOfBoundsException;

    /**
     * Writes the given double at the given index.
     *
     * @param buffer The byte array to write in
     * @param index The index at which the double will be written
     * @param value The double value to be written
     * @throws IndexOutOfBoundsException If <tt>index</tt> is negative or not smaller than the
     *             buffer's size minus seven
     * @since 20.3
     */
    public abstract void putDouble(byte[] buffer, int index, double value) throws IndexOutOfBoundsException;
}
