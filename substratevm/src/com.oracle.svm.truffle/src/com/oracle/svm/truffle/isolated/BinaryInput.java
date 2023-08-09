/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.truffle.isolated;

import static com.oracle.svm.truffle.isolated.BinaryOutput.ARRAY;
import static com.oracle.svm.truffle.isolated.BinaryOutput.BOOLEAN;
import static com.oracle.svm.truffle.isolated.BinaryOutput.BYTE;
import static com.oracle.svm.truffle.isolated.BinaryOutput.CHAR;
import static com.oracle.svm.truffle.isolated.BinaryOutput.DOUBLE;
import static com.oracle.svm.truffle.isolated.BinaryOutput.FLOAT;
import static com.oracle.svm.truffle.isolated.BinaryOutput.INT;
import static com.oracle.svm.truffle.isolated.BinaryOutput.LARGE_STRING_TAG;
import static com.oracle.svm.truffle.isolated.BinaryOutput.LONG;
import static com.oracle.svm.truffle.isolated.BinaryOutput.NULL;
import static com.oracle.svm.truffle.isolated.BinaryOutput.SHORT;
import static com.oracle.svm.truffle.isolated.BinaryOutput.STRING;
import static com.oracle.svm.truffle.isolated.BinaryOutput.bufferSize;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * Copy from native-bridge to avoid depenency from now. Keep in sync with native-bridge.
 */
abstract class BinaryInput {

    private static final int EOF = -1;

    private byte[] tempEncodingByteBuffer;
    private char[] tempEncodingCharBuffer;
    protected final int length;
    protected int pos;

    private BinaryInput(int length) {
        this.length = length;
    }

    /**
     * Reads a single byte and returns {@code true} if that byte is non-zero, {@code false} if that
     * byte is zero.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     */
    public final boolean readBoolean() throws IndexOutOfBoundsException {
        int b = read();
        if (b < 0) {
            throw new IndexOutOfBoundsException();
        }
        return b != 0;
    }

    /**
     * Reads and returns a single byte.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     */
    public final byte readByte() throws IndexOutOfBoundsException {
        int b = read();
        if (b < 0) {
            throw new IndexOutOfBoundsException();
        }
        return (byte) b;
    }

    /**
     * Reads two bytes and returns a {@code short} value.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     */
    public final short readShort() throws IndexOutOfBoundsException {
        int b1 = read();
        int b2 = read();
        if ((b1 | b2) < 0) {
            throw new IndexOutOfBoundsException();
        }
        return packShort(b1, b2);
    }

    /**
     * Creates a Java {@code short} from given unsigned bytes, where {@code b1} is the most
     * significant byte {@code byte} and {@code b2} is the least significant {@code byte}.
     */
    private static short packShort(int b1, int b2) {
        return (short) ((b1 << 8) + b2);
    }

    /**
     * Reads two bytes and returns a {@code char} value.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     */
    public final char readChar() throws IndexOutOfBoundsException {
        int b1 = read();
        int b2 = read();
        if ((b1 | b2) < 0) {
            throw new IndexOutOfBoundsException();
        }
        return packChar(b1, b2);
    }

    /**
     * Creates a Java {@code char} from given unsigned bytes, where {@code b1} is the most
     * significant byte {@code byte} and {@code b2} is the least significant {@code byte}.
     */
    private static char packChar(int b1, int b2) {
        return (char) ((b1 << 8) + b2);
    }

    /**
     * Reads four bytes and returns an {@code int} value.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     */
    public final int readInt() throws IndexOutOfBoundsException {
        int b1 = read();
        int b2 = read();
        int b3 = read();
        int b4 = read();
        if ((b1 | b2 | b3 | b4) < 0) {
            throw new IndexOutOfBoundsException();
        }
        return packInt(b1, b2, b3, b4);
    }

    /**
     * Creates a Java {@code int} from given unsigned bytes, where {@code b1} is the most
     * significant byte {@code byte} and {@code b4} is the least significant {@code byte}.
     */
    private static int packInt(int b1, int b2, int b3, int b4) {
        return (b1 << 24) + (b2 << 16) + (b3 << 8) + b4;
    }

    /**
     * Reads eight bytes and returns a {@code long} value.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     */
    public final long readLong() throws IndexOutOfBoundsException {
        int b1 = read();
        int b2 = read();
        int b3 = read();
        int b4 = read();
        int b5 = read();
        int b6 = read();
        int b7 = read();
        int b8 = read();
        if ((b1 | b2 | b3 | b4 | b5 | b6 | b7 | b8) < 0) {
            throw new IndexOutOfBoundsException();
        }
        return packLong(b1, b2, b3, b4, b5, b6, b7, b8);
    }

    /**
     * Creates a Java {@code long} from given unsigned bytes, where {@code b1} is the most
     * significant byte {@code byte} and {@code b8} is the least significant {@code byte}.
     */
    private static long packLong(int b1, int b2, int b3, int b4, int b5, int b6, int b7, int b8) {
        return ((long) b1 << 56) + ((long) b2 << 48) + ((long) b3 << 40) + ((long) b4 << 32) +
                        ((long) b5 << 24) + ((long) b6 << 16) + ((long) b7 << 8) + b8;
    }

    /**
     * Reads four bytes and returns a {@code float} value. It does this by reading an {@code int}
     * value and converting the {@code int} value to a {@code float} using
     * {@link Float#intBitsToFloat(int)}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     */
    public final float readFloat() throws IndexOutOfBoundsException {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * Reads eight bytes and returns a {@code double} value. It does this by reading a {@code long}
     * value and converting the {@code long} value to a {@code double} using
     * {@link Double#longBitsToDouble(long)}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     */
    public final double readDouble() throws IndexOutOfBoundsException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * Reads a single byte. The byte value is returned as an {@code int} in the range {@code 0} to
     * {@code 255}. If no byte is available because the end of the stream has been reached, the
     * value {@code -1} is returned.
     */
    public abstract int read();

    /**
     * Reads {@code len} bytes into a byte array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public abstract void read(byte[] b, int off, int len) throws IndexOutOfBoundsException;

    /**
     * Reads a string using a modified UTF-8 encoding in a machine-independent manner.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     * @throws IllegalArgumentException if the bytes do not represent a valid modified UTF-8
     *             encoding of a string.
     */
    public final String readUTF() throws IndexOutOfBoundsException, IllegalArgumentException {
        int len;
        int b1 = read();
        int b2 = read();
        if ((b1 | b2) < 0) {
            throw new IndexOutOfBoundsException();
        }
        if ((b1 & LARGE_STRING_TAG) == LARGE_STRING_TAG) {
            int b3 = read();
            int b4 = read();
            if ((b3 | b4) < 0) {
                throw new IndexOutOfBoundsException();
            }
            len = ((b1 & ~LARGE_STRING_TAG) << 24) + (b2 << 16) + (b3 << 8) + b4;
        } else {
            len = (b1 << 8) + b2;
        }
        ensureBufferSize(len);
        if (tempEncodingCharBuffer == null || tempEncodingCharBuffer.length < len) {
            tempEncodingCharBuffer = new char[Math.max(bufferSize(0, len), 80)];
        }

        int c1;
        int c2;
        int c3;
        int byteCount = 0;
        int charCount = 0;

        read(tempEncodingByteBuffer, 0, len);

        while (byteCount < len) {
            c1 = tempEncodingByteBuffer[byteCount] & 0xff;
            if (c1 > 127) {
                break;
            }
            byteCount++;
            tempEncodingCharBuffer[charCount++] = (char) c1;
        }

        while (byteCount < len) {
            c1 = tempEncodingByteBuffer[byteCount] & 0xff;
            switch (c1 >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    /* 0xxxxxxx */
                    byteCount++;
                    tempEncodingCharBuffer[charCount++] = (char) c1;
                    break;
                case 12:
                case 13:
                    /* 110x xxxx 10xx xxxx */
                    byteCount += 2;
                    if (byteCount > len) {
                        throw new IllegalArgumentException("Partial character at end");
                    }
                    c2 = tempEncodingByteBuffer[byteCount - 1];
                    if ((c2 & 0xC0) != 0x80) {
                        throw new IllegalArgumentException("Malformed input around byte " + byteCount);
                    }
                    tempEncodingCharBuffer[charCount++] = (char) (((c1 & 0x1F) << 6) | (c2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx 10xx xxxx 10xx xxxx */
                    byteCount += 3;
                    if (byteCount > len) {
                        throw new IllegalArgumentException("Malformed input: partial character at end");
                    }
                    c2 = tempEncodingByteBuffer[byteCount - 2];
                    c3 = tempEncodingByteBuffer[byteCount - 1];
                    if (((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80)) {
                        throw new IllegalArgumentException("Malformed input around byte " + (byteCount - 1));
                    }
                    tempEncodingCharBuffer[charCount++] = (char) (((c1 & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F));
                    break;
                default:
                    /* 10xx xxxx, 1111 xxxx */
                    throw new IllegalArgumentException("Malformed input around byte " + byteCount);
            }
        }
        // The number of chars produced may be less than len
        return new String(tempEncodingCharBuffer, 0, charCount);
    }

    /**
     * Reads a single value, using the data type encoded in the marshalled data.
     *
     * @return The read value, such as a boxed Java primitive, a {@link String}, a {@code null}, or
     *         an array of these types.
     * @throws IndexOutOfBoundsException if there are not enough bytes to read.
     * @throws IllegalArgumentException when the marshaled type is not supported or if the bytes do
     *             not represent a valid modified UTF-8 encoding of a string.
     */
    public final Object readTypedValue() throws IndexOutOfBoundsException, IllegalArgumentException {
        byte tag = readByte();
        switch (tag) {
            case ARRAY:
                int len = readInt();
                Object[] arr = new Object[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = readTypedValue();
                }
                return arr;
            case NULL:
                return null;
            case BOOLEAN:
                return readBoolean();
            case BYTE:
                return readByte();
            case SHORT:
                return readShort();
            case CHAR:
                return readChar();
            case INT:
                return readInt();
            case LONG:
                return readLong();
            case FLOAT:
                return readFloat();
            case DOUBLE:
                return readDouble();
            case STRING:
                return readUTF();
            default:
                throw new IllegalArgumentException(String.format("Unknown tag %d", tag));
        }
    }

    /**
     * Reads {@code len} bytes into a boolean array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public final void read(boolean[] b, int off, int len) {
        ensureBufferSize(len);
        read(tempEncodingByteBuffer, 0, len);
        int limit = off + len;
        for (int i = off, j = 0; i < limit; i++) {
            b[i] = tempEncodingByteBuffer[j++] != 0;
        }
    }

    /**
     * Reads {@code len} shorts into a short array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public final void read(short[] b, int off, int len) {
        int size = len * Short.BYTES;
        ensureBufferSize(size);
        read(tempEncodingByteBuffer, 0, size);
        int limit = off + len;
        for (int i = off, j = 0; i < limit; i++) {
            int b1 = (tempEncodingByteBuffer[j++] & 0xff);
            int b2 = (tempEncodingByteBuffer[j++] & 0xff);
            b[i] = packShort(b1, b2);
        }
    }

    /**
     * Reads {@code len} chars into a char array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public final void read(char[] b, int off, int len) {
        int size = len * Character.BYTES;
        ensureBufferSize(size);
        read(tempEncodingByteBuffer, 0, size);
        int limit = off + len;
        for (int i = off, j = 0; i < limit; i++) {
            int b1 = (tempEncodingByteBuffer[j++] & 0xff);
            int b2 = (tempEncodingByteBuffer[j++] & 0xff);
            b[i] = packChar(b1, b2);
        }
    }

    /**
     * Reads {@code len} ints into an int array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public final void read(int[] b, int off, int len) {
        int size = len * Integer.BYTES;
        ensureBufferSize(size);
        read(tempEncodingByteBuffer, 0, size);
        int limit = off + len;
        for (int i = off, j = 0; i < limit; i++) {
            int b1 = (tempEncodingByteBuffer[j++] & 0xff);
            int b2 = (tempEncodingByteBuffer[j++] & 0xff);
            int b3 = (tempEncodingByteBuffer[j++] & 0xff);
            int b4 = (tempEncodingByteBuffer[j++] & 0xff);
            b[i] = packInt(b1, b2, b3, b4);
        }
    }

    /**
     * Reads {@code len} longs into a long array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public final void read(long[] b, int off, int len) {
        int size = len * Long.BYTES;
        ensureBufferSize(size);
        read(tempEncodingByteBuffer, 0, size);
        int limit = off + len;
        for (int i = off, j = 0; i < limit; i++) {
            int b1 = (tempEncodingByteBuffer[j++] & 0xff);
            int b2 = (tempEncodingByteBuffer[j++] & 0xff);
            int b3 = (tempEncodingByteBuffer[j++] & 0xff);
            int b4 = (tempEncodingByteBuffer[j++] & 0xff);
            int b5 = (tempEncodingByteBuffer[j++] & 0xff);
            int b6 = (tempEncodingByteBuffer[j++] & 0xff);
            int b7 = (tempEncodingByteBuffer[j++] & 0xff);
            int b8 = (tempEncodingByteBuffer[j++] & 0xff);
            b[i] = packLong(b1, b2, b3, b4, b5, b6, b7, b8);
        }
    }

    /**
     * Reads {@code len} floats into a float array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public final void read(float[] b, int off, int len) {
        int size = len * Float.BYTES;
        ensureBufferSize(size);
        read(tempEncodingByteBuffer, 0, size);
        int limit = off + len;
        for (int i = off, j = 0; i < limit; i++) {
            int b1 = (tempEncodingByteBuffer[j++] & 0xff);
            int b2 = (tempEncodingByteBuffer[j++] & 0xff);
            int b3 = (tempEncodingByteBuffer[j++] & 0xff);
            int b4 = (tempEncodingByteBuffer[j++] & 0xff);
            b[i] = Float.intBitsToFloat(packInt(b1, b2, b3, b4));
        }
    }

    /**
     * Reads {@code len} doubles into a double array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public final void read(double[] b, int off, int len) {
        int size = len * Double.BYTES;
        ensureBufferSize(size);
        read(tempEncodingByteBuffer, 0, size);
        int limit = off + len;
        for (int i = off, j = 0; i < limit; i++) {
            int b1 = (tempEncodingByteBuffer[j++] & 0xff);
            int b2 = (tempEncodingByteBuffer[j++] & 0xff);
            int b3 = (tempEncodingByteBuffer[j++] & 0xff);
            int b4 = (tempEncodingByteBuffer[j++] & 0xff);
            int b5 = (tempEncodingByteBuffer[j++] & 0xff);
            int b6 = (tempEncodingByteBuffer[j++] & 0xff);
            int b7 = (tempEncodingByteBuffer[j++] & 0xff);
            int b8 = (tempEncodingByteBuffer[j++] & 0xff);
            b[i] = Double.longBitsToDouble(packLong(b1, b2, b3, b4, b5, b6, b7, b8));
        }
    }

    /**
     * Returns a read only {@link ByteBuffer} backed by the {@link BinaryInput} internal buffer. The
     * content of the buffer will start at the {@link BinaryInput}'s current position. The buffer's
     * capacity and limit will be {@code len}, its position will be zero, its mark will be
     * undefined, and its byte order will be {@link ByteOrder#BIG_ENDIAN BIG_ENDIAN}. After a
     * successful call, the {@link BinaryInput}'s current position is incremented by the
     * {@code len}.
     *
     * @throws IndexOutOfBoundsException if the BinaryInput has not enough remaining bytes.
     */
    public abstract ByteBuffer asByteBuffer(int len);

    /**
     * Creates a new buffer backed by a byte array.
     */
    public static BinaryInput create(byte[] buffer) {
        return new ByteArrayBinaryInput(buffer);
    }

    /**
     * Creates a new buffer backed by a byte array only up to a given length.
     */
    public static BinaryInput create(byte[] buffer, int length) {
        return new ByteArrayBinaryInput(buffer, length);
    }

    /**
     * Creates a new buffer wrapping an off-heap memory segment starting at an {@code address}
     * having {@code length} bytes.
     */
    public static BinaryInput create(CCharPointer address, int length) {
        return new CCharPointerInput(address, length);
    }

    private void ensureBufferSize(int len) {
        if (tempEncodingByteBuffer == null || tempEncodingByteBuffer.length < len) {
            tempEncodingByteBuffer = new byte[Math.max(bufferSize(0, len), 80)];
        }
    }

    private static final class ByteArrayBinaryInput extends BinaryInput {

        private final byte[] buffer;

        ByteArrayBinaryInput(byte[] buffer) {
            super(buffer.length);
            this.buffer = buffer;
        }

        ByteArrayBinaryInput(byte[] buffer, int length) {
            super(length);
            this.buffer = buffer;
        }

        @Override
        public int read() {
            if (pos >= length) {
                return EOF;
            }
            return (buffer[pos++] & 0xff);
        }

        @Override
        public void read(byte[] b, int off, int len) {
            if (len < 0) {
                throw new IllegalArgumentException(String.format("Len must be non negative but was %d", len));
            }
            if (pos + len > length) {
                throw new IndexOutOfBoundsException();
            }
            System.arraycopy(buffer, pos, b, off, len);
            pos += len;
        }

        @Override
        public ByteBuffer asByteBuffer(int len) {
            ByteBuffer result = ByteBuffer.wrap(buffer, pos, len).slice().asReadOnlyBuffer();
            pos += len;
            return result;
        }
    }

    private static final class CCharPointerInput extends BinaryInput {

        /**
         * Represents the point at which the average cost of a JNI call exceeds the expense of an
         * element by element copy. See {@code java.nio.Bits#JNI_COPY_TO_ARRAY_THRESHOLD}.
         */
        private static final int BYTEBUFFER_COPY_TO_ARRAY_THRESHOLD = 6;

        private final CCharPointer address;
        /**
         * ByteBuffer view of this {@link CCharPointerInput} direct memory. The ByteBuffer is used
         * for bulk data transfers, where the bulk ByteBuffer operations outperform element by
         * element copying by an order of magnitude.
         */
        private ByteBuffer byteBufferView;

        CCharPointerInput(CCharPointer address, int length) {
            super(length);
            this.address = address;
        }

        @Override
        public int read() {
            if (pos >= length) {
                return EOF;
            }
            return (address.read(pos++) & 0xff);
        }

        @Override
        public void read(byte[] b, int off, int len) {
            if (len < 0) {
                throw new IllegalArgumentException(String.format("Len must be non negative but was %d", len));
            }
            if (pos + len > length) {
                throw new IndexOutOfBoundsException();
            }
            if (len > BYTEBUFFER_COPY_TO_ARRAY_THRESHOLD) {
                if (byteBufferView == null) {
                    byteBufferView = CTypeConversion.asByteBuffer(address, length);
                }
                byteBufferView.position(pos);
                byteBufferView.get(b, off, len);
            } else {
                for (int i = 0, j = pos; i < len; i++, j++) {
                    b[off + i] = address.read(j);
                }
            }
            pos += len;
        }

        @Override
        public ByteBuffer asByteBuffer(int len) {
            ByteBuffer result = CTypeConversion.asByteBuffer(address.addressOf(pos), len).order(ByteOrder.BIG_ENDIAN).asReadOnlyBuffer();
            pos += len;
            return result;
        }
    }
}
