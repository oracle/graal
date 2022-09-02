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

import org.graalvm.nativebridge.JNIConfig.Builder;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Objects;

/**
 * A buffer used by the {@link BinaryMarshaller} to marshall parameters and results passed by value.
 *
 * @see BinaryInput
 * @see BinaryMarshaller
 * @see Builder#registerMarshaller(Class, BinaryMarshaller)
 */
public abstract class BinaryOutput {

    /**
     * Maximum string length for string encoded by 4 bytes length followed be content.
     */
    private static final int MAX_LENGTH = Integer.MAX_VALUE - Integer.BYTES;
    /**
     * Maximum string length for string encoded by 2 bytes length followed be content.
     */
    private static final int MAX_SHORT_LENGTH = Short.MAX_VALUE;
    /**
     * Tag to distinguish between long and short string. The tag is set in the first string length
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

    private byte[] byteBuffer;
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
            byteBuffer[count++] = (byte) ((LARGE_STRING_TAG | (utfLen >>> 24)) & 0xff);
            byteBuffer[count++] = (byte) ((utfLen >>> 16) & 0xFF);
        } else {
            headerSize = Short.BYTES;
            ensureBufferSize(headerSize, utfLen);
        }
        byteBuffer[count++] = (byte) ((utfLen >>> 8) & 0xFF);
        byteBuffer[count++] = (byte) (utfLen & 0xFF);

        int i = 0;
        for (; i < len; i++) {
            c = string.charAt(i);
            if (!((c >= 0x0001) && (c <= 0x007F))) {
                break;
            }
            byteBuffer[count++] = (byte) c;
        }

        for (; i < len; i++) {
            c = string.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F)) {
                byteBuffer[count++] = (byte) c;
            } else if (c > 0x07FF) {
                byteBuffer[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                byteBuffer[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                byteBuffer[count++] = (byte) (0x80 | (c & 0x3F));
            } else {
                byteBuffer[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                byteBuffer[count++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        write(byteBuffer, 0, headerSize + utfLen);
    }

    /**
     * Returns this buffer's position.
     */
    public int getPosition() {
        return pos;
    }

    /**
     * Writes the value that is represented by the given object, together with information on the
     * value's data type. Supported types are boxed Java primitive types, {@link String},
     * {@code null}, and arrays of these types.
     *
     * @throws IllegalArgumentException when the {@code value} type is not supported or the
     *             {@code value} is a string which cannot be encoded using modified UTF-8 encoding.
     */
    public final void writeTypedValue(Object value) throws IllegalArgumentException {
        if (value instanceof Object[]) {
            Object[] arr = (Object[]) value;
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

    private void ensureBufferSize(int headerSize, int dataSize) {
        if (byteBuffer == null || byteBuffer.length < (headerSize + dataSize)) {
            byteBuffer = new byte[bufferSize(headerSize, dataSize)];
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

        private CCharPointer address;
        private int length;
        private boolean unmanaged;

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
                throw new IndexOutOfBoundsException("offset: " + off + ", length: " + len + ", array length: " + b.length);
            }
            ensureCapacity(pos + len);
            for (int i = 0; i < len; i++) {
                address.write(pos + i, b[off + i]);
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
