/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import org.graalvm.nativeimage.c.type.CCharPointer;

import static org.graalvm.nativebridge.BinaryOutput.LARGE_STRING_TAG;
import static org.graalvm.nativebridge.BinaryOutput.NULL;
import static org.graalvm.nativebridge.BinaryOutput.BYTE;
import static org.graalvm.nativebridge.BinaryOutput.BOOLEAN;
import static org.graalvm.nativebridge.BinaryOutput.SHORT;
import static org.graalvm.nativebridge.BinaryOutput.CHAR;
import static org.graalvm.nativebridge.BinaryOutput.INT;
import static org.graalvm.nativebridge.BinaryOutput.LONG;
import static org.graalvm.nativebridge.BinaryOutput.FLOAT;
import static org.graalvm.nativebridge.BinaryOutput.DOUBLE;
import static org.graalvm.nativebridge.BinaryOutput.STRING;
import static org.graalvm.nativebridge.BinaryOutput.ARRAY;
import static org.graalvm.nativebridge.BinaryOutput.bufferSize;

/**
 * A buffer used by the {@link BinaryMarshaller} to unmarshal parameters and results passed by
 * value.
 *
 * @see BinaryOutput
 * @see BinaryMarshaller
 * @see JNIConfig.Builder#registerMarshaller(Class, BinaryMarshaller)
 */
public abstract class BinaryInput {

    private static final int EOF = -1;

    private byte[] byteBuffer;
    private char[] charBuffer;
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
     * Reads up to {@code len} bytes into a byte array starting at offset {@code off}.
     */
    public abstract int read(byte[] b, int off, int len);

    /**
     * Reads {@code len} bytes into a byte array starting at offset {@code off}.
     *
     * @throws IndexOutOfBoundsException if there are not enough bytes to read
     */
    public final void readFully(byte[] b, int off, int len) throws IndexOutOfBoundsException {
        if (len < 0) {
            throw new IllegalArgumentException(String.format("Len must be non negative but was %d", len));
        }
        int n = 0;
        while (n < len) {
            int count = read(b, off + n, len - n);
            if (count < 0) {
                throw new IndexOutOfBoundsException();
            }
            n += count;
        }
    }

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
        if (byteBuffer == null || byteBuffer.length < len) {
            int bufSize = Math.max(bufferSize(0, len), 80);
            byteBuffer = new byte[bufSize];
            charBuffer = new char[bufSize];
        }

        int c1;
        int c2;
        int c3;
        int byteCount = 0;
        int charCount = 0;

        readFully(byteBuffer, 0, len);

        while (byteCount < len) {
            c1 = byteBuffer[byteCount] & 0xff;
            if (c1 > 127) {
                break;
            }
            byteCount++;
            charBuffer[charCount++] = (char) c1;
        }

        while (byteCount < len) {
            c1 = byteBuffer[byteCount] & 0xff;
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
                    charBuffer[charCount++] = (char) c1;
                    break;
                case 12:
                case 13:
                    /* 110x xxxx 10xx xxxx */
                    byteCount += 2;
                    if (byteCount > len) {
                        throw new IllegalArgumentException("Partial character at end");
                    }
                    c2 = byteBuffer[byteCount - 1];
                    if ((c2 & 0xC0) != 0x80) {
                        throw new IllegalArgumentException("malformed input around byte " + byteCount);
                    }
                    charBuffer[charCount++] = (char) (((c1 & 0x1F) << 6) | (c2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx 10xx xxxx 10xx xxxx */
                    byteCount += 3;
                    if (byteCount > len) {
                        throw new IllegalArgumentException("malformed input: partial character at end");
                    }
                    c2 = byteBuffer[byteCount - 2];
                    c3 = byteBuffer[byteCount - 1];
                    if (((c2 & 0xC0) != 0x80) || ((c3 & 0xC0) != 0x80)) {
                        throw new IllegalArgumentException("malformed input around byte " + (byteCount - 1));
                    }
                    charBuffer[charCount++] = (char) (((c1 & 0x0F) << 12) | ((c2 & 0x3F) << 6) | (c3 & 0x3F));
                    break;
                default:
                    /* 10xx xxxx, 1111 xxxx */
                    throw new IllegalArgumentException("malformed input around byte " + byteCount);
            }
        }
        // The number of chars produced may be less than len
        return new String(charBuffer, 0, charCount);
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
     * Creates a new buffer backed by a byte array.
     */
    public static BinaryInput create(byte[] buffer) {
        return new ByteArrayBinaryInput(buffer);
    }

    /**
     * Creates a new buffer wrapping an off-heap memory segment starting at an {@code address}
     * having {@code length} bytes.
     */
    public static BinaryInput create(CCharPointer address, int length) {
        return new CCharPointerInput(address, length);
    }

    private static final class ByteArrayBinaryInput extends BinaryInput {

        private final byte[] buffer;

        ByteArrayBinaryInput(byte[] buffer) {
            super(buffer.length);
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
        public int read(byte[] b, int off, int len) {
            if (pos >= length) {
                return EOF;
            }
            int toRead = Math.min(len, length - pos);
            System.arraycopy(buffer, pos, b, off, toRead);
            pos += toRead;
            return toRead;
        }
    }

    private static final class CCharPointerInput extends BinaryInput {

        private final CCharPointer address;

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
        public int read(byte[] b, int off, int len) {
            if (pos >= length) {
                return EOF;
            }
            int i = 0;
            for (int j = pos; i < len && j < length; i++, j++) {
                b[off + i] = address.read(j);
            }
            pos += i;
            return i;
        }
    }
}
