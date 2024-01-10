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

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

/**
 * Copy from native-bridge to avoid depenency from now. Keep in sync with native-bridge.
 */
abstract class BinaryOutput {

    /**
     * Maximum string length for string encoded by 4 bytes length followed be content.
     */
    private static final int MAX_LENGTH = Integer.MAX_VALUE - Integer.BYTES;
    /**
     * Maximum string length for string encoded by 2 bytes length followed be content.
     */
    private static final int MAX_SHORT_LENGTH = Short.MAX_VALUE;
    /**
     * byte.
     */
    static final int LARGE_STRING_TAG = 1 << 7;

    // Type tags used by writeTypedValue
    static final byte NULL = 0;
    static final byte BOOLEAN = NULL + 1;
    static final byte BYTE = BOOLEAN + 1;
    static final byte SHORT = BYTE + 1;
    static final byte CHAR = SHORT + 1;
    static final byte INT = CHAR + 1;
    static final byte LONG = INT + 1;
    static final byte FLOAT = LONG + 1;
    static final byte DOUBLE = FLOAT + 1;
    static final byte STRING = DOUBLE + 1;
    static final byte ARRAY = STRING + 1;

    private byte[] tempDecodingBuffer;
    protected int pos;

    private BinaryOutput() {
    }

    /**
     * Writes a {@code boolean} as a single byte value. The value {@code true} is written as the
     * value {@code (byte)1}, the value {@code false} is written as the value {@code (byte)0}. The
     * buffer position is incremented by {@code 1}.
     */
    public final void writeBoolean(boolean value) {
        write(value ? 1 : 0);
    }

    /**
     * Writes a {@code byte} as a single byte value. The buffer position is incremented by
     * {@code 1}.
     */
    public final void writeByte(int value) {
        write(value);
    }

    /**
     * Writes a {@code short} as two bytes, high byte first. The buffer position is incremented by
     * {@code 2}.
     */
    public final void writeShort(int value) {
        write((value >>> 8) & 0xff);
        write(value & 0xff);
    }

    /**
     * Writes a {@code char} as two bytes, high byte first. The buffer position is incremented by
     * {@code 2}.
     */
    public final void writeChar(int value) {
        write((value >>> 8) & 0xff);
        write(value & 0xff);
    }

    /**
     * Writes an {@code int} as four bytes, high byte first. The buffer position is incremented by
     * {@code 4}.
     */
    public final void writeInt(int value) {
        write((value >>> 24) & 0xff);
        write((value >>> 16) & 0xff);
        write((value >>> 8) & 0xff);
        write(value & 0xff);
    }

    /**
     * Writes a {@code long} as eight bytes, high byte first. The buffer position is incremented by
     * {@code 8}.
     */
    public final void writeLong(long value) {
        write((int) ((value >>> 56) & 0xff));
        write((int) ((value >>> 48) & 0xff));
        write((int) ((value >>> 40) & 0xff));
        write((int) ((value >>> 32) & 0xff));
        write((int) ((value >>> 24) & 0xff));
        write((int) ((value >>> 16) & 0xff));
        write((int) ((value >>> 8) & 0xff));
        write((int) (value & 0xff));
    }

    /**
     * Converts a {@code float} value to an {@code int} using the
     * {@link Float#floatToIntBits(float)}, and then writes that {@code int} as four bytes, high
     * byte first. The buffer position is incremented by {@code 4}.
     */
    public final void writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
    }

    /**
     * Converts a {@code double} value to a {@code long} using the
     * {@link Double#doubleToLongBits(double)}, and then writes that {@code long} as eight bytes,
     * high byte first. The buffer position is incremented by {@code 8}.
     */
    public final void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    /**
     * Writes the lowest byte of the argument as a single byte value. The buffer position is
     * incremented by {@code 1}.
     */
    public abstract void write(int b);

    /**
     * Writes {@code len} bytes from the byte {@code array} starting at offset {@code off}. The
     * buffer position is incremented by {@code len}.
     */
    public abstract void write(byte[] array, int off, int len);

    /**
     * Reserves a buffer space. The reserved space can be used for out parameters.
     *
     * @param numberOfBytes number of bytes to reserve.
     */
    public abstract void skip(int numberOfBytes);

    /**
     * Writes a string using a modified UTF-8 encoding in a machine-independent manner.
     *
     * @throws IllegalArgumentException if the {@code string} cannot be encoded using modified UTF-8
     *             encoding.
     */
    public final void writeUTF(String string) throws IllegalArgumentException {
        int len = string.length();
        int utfLen = 0;
        int c;
        int count = 0;

        for (int i = 0; i < len; i++) {
            c = string.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                utfLen++;
            } else if (c > 0x07FF) {
                utfLen += 3;
            } else {
                utfLen += 2;
            }
        }

        if (utfLen > MAX_LENGTH) {
            throw new IllegalArgumentException("String too long to encode, " + utfLen + " bytes");
        }
        int headerSize;
        if (utfLen > MAX_SHORT_LENGTH) {
            headerSize = Integer.BYTES;
            ensureBufferSize(headerSize, utfLen);
            tempDecodingBuffer[count++] = (byte) ((LARGE_STRING_TAG | (utfLen >>> 24)) & 0xff);
            tempDecodingBuffer[count++] = (byte) ((utfLen >>> 16) & 0xFF);
        } else {
            headerSize = Short.BYTES;
            ensureBufferSize(headerSize, utfLen);
        }
        tempDecodingBuffer[count++] = (byte) ((utfLen >>> 8) & 0xFF);
        tempDecodingBuffer[count++] = (byte) (utfLen & 0xFF);

        int i = 0;
        for (; i < len; i++) {
            c = string.charAt(i);
            if (!((c >= 0x0001) && (c <= 0x007F))) {
                break;
            }
            tempDecodingBuffer[count++] = (byte) c;
        }

        for (; i < len; i++) {
            c = string.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                tempDecodingBuffer[count++] = (byte) c;
            } else if (c > 0x07FF) {
                tempDecodingBuffer[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                tempDecodingBuffer[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                tempDecodingBuffer[count++] = (byte) (0x80 | (c & 0x3F));
            } else {
                tempDecodingBuffer[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                tempDecodingBuffer[count++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        write(tempDecodingBuffer, 0, headerSize + utfLen);
    }

    /**
     * Returns this buffer's position.
     */
    public int getPosition() {
        return pos;
    }

    /**
     * Returns <code>true</code> if a value is a typed value writable using
     * {@link #writeTypedValue(Object)}, else <code>false</code>.
     */
    public static boolean isTypedValue(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof Object[] || value instanceof Boolean || value instanceof Byte ||
                        value instanceof Short || value instanceof Character || value instanceof Integer ||
                        value instanceof Long || value instanceof Float || value instanceof Double || value instanceof String;
    }

    /**
     * Writes the value that is represented by the given object, together with information on the
     * value's data type. Supported types are boxed Java primitive types, {@link String},
     * {@code null}, and arrays of these types.
     *
     * @throws IllegalArgumentException when the {@code value} type is not supported or the
     *             {@code value} is a string which cannot be encoded using modified UTF-8 encoding.
     * @see #isTypedValue(Object) to find out whether a value can be serialized.
     */
    public final void writeTypedValue(Object value) throws IllegalArgumentException {
        if (value instanceof Object[] arr) {
            writeByte(ARRAY);
            writeInt(arr.length);
            for (Object arrElement : arr) {
                writeTypedValue(arrElement);
            }
        } else if (value == null) {
            writeByte(NULL);
        } else if (value instanceof Boolean) {
            writeByte(BOOLEAN);
            writeBoolean((boolean) value);
        } else if (value instanceof Byte) {
            writeByte(BYTE);
            writeByte((byte) value);
        } else if (value instanceof Short) {
            writeByte(SHORT);
            writeShort((short) value);
        } else if (value instanceof Character) {
            writeByte(CHAR);
            writeChar((char) value);
        } else if (value instanceof Integer) {
            writeByte(INT);
            writeInt((int) value);
        } else if (value instanceof Long) {
            writeByte(LONG);
            writeLong((long) value);
        } else if (value instanceof Float) {
            writeByte(FLOAT);
            writeFloat((float) value);
        } else if (value instanceof Double) {
            writeByte(DOUBLE);
            writeDouble((double) value);
        } else if (value instanceof String) {
            writeByte(STRING);
            writeUTF((String) value);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported type %s", value.getClass()));
        }
    }

    /**
     * Writes {@code len} bytes from the boolean {@code array} starting at offset {@code off}. The
     * value {@code true} is written as the value {@code (byte)1}, the value {@code false} is
     * written as the value {@code (byte)0}. The buffer position is incremented by {@code len}.
     */
    public final void write(boolean[] array, int off, int len) {
        ensureBufferSize(0, len);
        for (int i = 0, j = 0; i < len; i++, j++) {
            tempDecodingBuffer[j] = (byte) (array[off + i] ? 1 : 0);
        }
        write(tempDecodingBuffer, 0, len);
    }

    /**
     * Writes {@code len} shorts from the {@code array} starting at offset {@code off}. The buffer
     * position is incremented by {@code 2 * len}.
     */
    public final void write(short[] array, int off, int len) {
        int size = len * Short.BYTES;
        ensureBufferSize(0, size);
        for (int i = 0, j = 0; i < len; i++) {
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 8) & 0xff);
            tempDecodingBuffer[j++] = (byte) (array[off + i] & 0xff);
        }
        write(tempDecodingBuffer, 0, size);
    }

    /**
     * Writes {@code len} chars from the {@code array} starting at offset {@code off}. The buffer
     * position is incremented by {@code 2 * len}.
     */
    public final void write(char[] array, int off, int len) {
        int size = len * Character.BYTES;
        ensureBufferSize(0, size);
        for (int i = 0, j = 0; i < len; i++) {
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 8) & 0xff);
            tempDecodingBuffer[j++] = (byte) (array[off + i] & 0xff);
        }
        write(tempDecodingBuffer, 0, size);
    }

    /**
     * Writes {@code len} ints from the {@code array} starting at offset {@code off}. The buffer
     * position is incremented by {@code 4 * len}.
     */
    public final void write(int[] array, int off, int len) {
        int size = len * Integer.BYTES;
        ensureBufferSize(0, size);
        for (int i = 0, j = 0; i < len; i++) {
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 24) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 16) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 8) & 0xff);
            tempDecodingBuffer[j++] = (byte) (array[off + i] & 0xff);
        }
        write(tempDecodingBuffer, 0, size);
    }

    /**
     * Writes {@code len} longs from the {@code array} starting at offset {@code off}. The buffer
     * position is incremented by {@code 8 * len}.
     */
    public final void write(long[] array, int off, int len) {
        int size = len * Long.BYTES;
        ensureBufferSize(0, size);
        for (int i = 0, j = 0; i < len; i++) {
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 56) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 48) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 40) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 32) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 24) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 16) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((array[off + i] >>> 8) & 0xff);
            tempDecodingBuffer[j++] = (byte) (array[off + i] & 0xff);
        }
        write(tempDecodingBuffer, 0, size);
    }

    /**
     * Writes {@code len} floats from the {@code array} starting at offset {@code off}. Each
     * {@code float} value is converted to an {@code int} using the
     * {@link Float#floatToIntBits(float)} and written as an int. The buffer position is incremented
     * by {@code 4 * len}.
     */
    public final void write(float[] array, int off, int len) {
        int size = len * Float.BYTES;
        ensureBufferSize(0, size);
        for (int i = 0, j = 0; i < len; i++) {
            int bits = Float.floatToIntBits(array[off + i]);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 24) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 16) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 8) & 0xff);
            tempDecodingBuffer[j++] = (byte) (bits & 0xff);
        }
        write(tempDecodingBuffer, 0, size);
    }

    /**
     * Writes {@code len} doubles from the {@code array} starting at offset {@code off}. Each
     * {@code double} value is converted to an {@code lang} using the
     * {@link Double#doubleToLongBits(double)} and written as a long. The buffer position is
     * incremented by {@code 8 * len}.
     */
    public final void write(double[] array, int off, int len) {
        int size = len * Double.BYTES;
        ensureBufferSize(Integer.BYTES, size);
        for (int i = 0, j = 0; i < len; i++) {
            long bits = Double.doubleToLongBits(array[off + i]);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 56) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 48) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 40) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 32) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 24) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 16) & 0xff);
            tempDecodingBuffer[j++] = (byte) ((bits >>> 8) & 0xff);
            tempDecodingBuffer[j++] = (byte) (bits & 0xff);
        }
        write(tempDecodingBuffer, 0, size);
    }

    private void ensureBufferSize(int headerSize, int dataSize) {
        if (tempDecodingBuffer == null || tempDecodingBuffer.length < (headerSize + dataSize)) {
            tempDecodingBuffer = new byte[bufferSize(headerSize, dataSize)];
        }
    }

    /**
     * Creates a new buffer backed by a byte array.
     */
    public static ByteArrayBinaryOutput create() {
        return new ByteArrayBinaryOutput(ByteArrayBinaryOutput.INITIAL_SIZE);
    }

    /**
     * Creates a new buffer wrapping the {@code initialBuffer}. If the {@code initialBuffer}
     * capacity is not sufficient for writing the data, a new array is allocated. Always use
     * {@link ByteArrayBinaryOutput#getArray()} to obtain the marshaled data.
     */
    public static ByteArrayBinaryOutput create(byte[] initialBuffer) {
        Objects.requireNonNull(initialBuffer, "InitialBuffer must be non null.");
        return new ByteArrayBinaryOutput(initialBuffer);
    }

    /**
     * Creates a new buffer wrapping an off-heap memory segment starting at {@code address} having
     * {@code length} bytes. If the capacity of an off-heap memory segment is not sufficient for
     * writing the data, a new off-heap memory is allocated. Always use
     * {@link CCharPointerBinaryOutput#getAddress()} to obtain the marshaled data.
     *
     * @param address the off-heap memory address
     * @param length the off-heap memory size
     * @param dynamicallyAllocated {@code true} if the memory was dynamically allocated and should
     *            be freed when the buffer is closed; {@code false} for the stack allocated memory.
     */
    public static CCharPointerBinaryOutput create(CCharPointer address, int length, boolean dynamicallyAllocated) {
        return new CCharPointerBinaryOutput(address, length, dynamicallyAllocated);
    }

    static int bufferSize(int headerSize, int dataSize) {
        return headerSize + (dataSize <= MAX_SHORT_LENGTH ? dataSize << 1 : dataSize);
    }

    /**
     * A {@link BinaryOutput} backed by a byte array.
     */
    public static final class ByteArrayBinaryOutput extends BinaryOutput {

        private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
        static final int INITIAL_SIZE = 32;

        private byte[] buffer;

        private ByteArrayBinaryOutput(int size) {
            buffer = new byte[size];
        }

        private ByteArrayBinaryOutput(byte[] initialBuffer) {
            buffer = initialBuffer;
        }

        @Override
        public void write(int b) {
            ensureCapacity(pos + 1);
            buffer[pos] = (byte) b;
            pos += 1;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            ensureCapacity(pos + len);
            System.arraycopy(b, off, buffer, pos, len);
            pos += len;
        }

        @Override
        public void skip(int numberOfBytes) {
            ensureCapacity(pos + numberOfBytes);
            pos += numberOfBytes;
        }

        /**
         * Returns the byte array containing the marshalled data.
         */
        public byte[] getArray() {
            return buffer;
        }

        private void ensureCapacity(int neededCapacity) {
            if (neededCapacity - buffer.length > 0) {
                int newCapacity = buffer.length << 1;
                if (newCapacity - neededCapacity < 0) {
                    newCapacity = neededCapacity;
                }
                if (newCapacity - MAX_ARRAY_SIZE > 0) {
                    throw new OutOfMemoryError();
                }
                buffer = Arrays.copyOf(buffer, newCapacity);
            }
        }

        /**
         * Creates a new buffer backed by a byte array. The buffer initial size is
         * {@code initialSize}.
         */
        public static ByteArrayBinaryOutput create(int initialSize) {
            return new ByteArrayBinaryOutput(initialSize);
        }
    }

    /**
     * A {@link BinaryOutput} backed by an off-heap memory.
     */
    public static final class CCharPointerBinaryOutput extends BinaryOutput implements Closeable {

        /**
         * Represents the point at which the average cost of a JNI call exceeds the expense of an
         * element by element copy. See {@code java.nio.Bits#JNI_COPY_FROM_ARRAY_THRESHOLD}.
         */
        private static final int BYTEBUFFER_COPY_FROM_ARRAY_THRESHOLD = 6;

        private CCharPointer address;
        private int length;
        private boolean unmanaged;
        /**
         * ByteBuffer view of this {@link CCharPointerBinaryOutput} direct memory. The ByteBuffer is
         * used for bulk data transfers, where the bulk ByteBuffer operations outperform element by
         * element copying by an order of magnitude.
         */
        private ByteBuffer byteBufferView;

        private CCharPointerBinaryOutput(CCharPointer address, int length, boolean unmanaged) {
            this.address = address;
            this.length = length;
            this.unmanaged = unmanaged;
        }

        @Override
        public int getPosition() {
            checkClosed();
            return super.getPosition();
        }

        /**
         * Returns an address of an off-heap memory segment containing the marshalled data.
         */
        public CCharPointer getAddress() {
            checkClosed();
            return address;
        }

        @Override
        public void write(int b) {
            checkClosed();
            ensureCapacity(pos + 1);
            address.write(pos++, (byte) b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            checkClosed();
            if ((off | len | b.length) < 0 || b.length - off < len) {
                throw new IndexOutOfBoundsException("Offset: " + off + ", length: " + len + ", array length: " + b.length);
            }
            ensureCapacity(pos + len);
            if (len > BYTEBUFFER_COPY_FROM_ARRAY_THRESHOLD) {
                if (byteBufferView == null) {
                    byteBufferView = CTypeConversion.asByteBuffer(address, length);
                }
                byteBufferView.position(pos);
                byteBufferView.put(b, off, len);
            } else {
                for (int i = 0; i < len; i++) {
                    address.write(pos + i, b[off + i]);
                }
            }
            pos += len;
        }

        @Override
        public void skip(int numberOfBytes) {
            ensureCapacity(pos + numberOfBytes);
            pos += numberOfBytes;
        }

        /**
         * Closes the buffer and frees off-heap allocated resources.
         */
        @Override
        public void close() {
            if (unmanaged) {
                UnmanagedMemory.free(address);
                byteBufferView = null;
                address = WordFactory.nullPointer();
                length = 0;
                unmanaged = false;
                pos = Integer.MIN_VALUE;
            }
        }

        private void checkClosed() {
            if (pos == Integer.MIN_VALUE) {
                throw new IllegalStateException("Already closed");
            }
        }

        private void ensureCapacity(int neededCapacity) {
            if (neededCapacity - length > 0) {
                byteBufferView = null;
                int newCapacity = length << 1;
                if (newCapacity - neededCapacity < 0) {
                    newCapacity = neededCapacity;
                }
                if (newCapacity - Integer.MAX_VALUE > 0) {
                    throw new OutOfMemoryError();
                }
                if (unmanaged) {
                    address = UnmanagedMemory.realloc(address, WordFactory.unsigned(newCapacity));
                } else {
                    CCharPointer newAddress = UnmanagedMemory.malloc(newCapacity);
                    memcpy(newAddress, address, pos);
                    address = newAddress;
                }
                length = newCapacity;
                unmanaged = true;
            }
        }

        private static void memcpy(CCharPointer dst, CCharPointer src, int len) {
            for (int i = 0; i < len; i++) {
                dst.write(i, src.read(i));
            }
        }

        /**
         * Creates a new buffer backed by an off-heap memory segment. The buffer initial size is
         * {@code initialSize}.
         */
        public static CCharPointerBinaryOutput create(int initialSize) {
            return new CCharPointerBinaryOutput(UnmanagedMemory.malloc(initialSize), initialSize, true);
        }
    }
}
