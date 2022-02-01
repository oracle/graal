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

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import java.io.Closeable;
import java.io.UTFDataFormatException;
import java.util.Arrays;

public abstract class BinaryOutput implements Closeable {

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

    public static final byte CUSTOM_TAG_START = ARRAY + 1;

    private byte[] byteBuffer;
    protected int pos;

    private BinaryOutput() {
    }

    public final void writeBoolean(boolean value) {
        write(value ? 1 : 0);
    }

    public final void writeByte(int value) {
        write(value);
    }

    public final void writeShort(int value) {
        write((value >>> 8) & 0xff);
        write(value & 0xff);
    }

    public final void writeChar(int value) {
        write((value >>> 8) & 0xff);
        write(value & 0xff);
    }

    public final void writeInt(int value) {
        write((value >>> 24) & 0xff);
        write((value >>> 16) & 0xff);
        write((value >>> 8) & 0xff);
        write(value & 0xff);
    }

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

    public final void writeFloat(float value) {
        writeInt(Float.floatToIntBits(value));
    }

    public final void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    public abstract void write(int b);

    public abstract void write(byte[] b, int off, int len);

    public abstract CCharPointer getAddress();

    public abstract byte[] getArray();

    public final void writeUTF(String string) throws UTFDataFormatException {
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
            throw new UTFDataFormatException("String too long to encode, " + utfLen + " bytes");
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

    public int getPosition() {
        return pos;
    }

    @Override
    public void close() {
    }

    public final void writeTypedValue(Object value) throws UTFDataFormatException {
        boolean res = writeTypedValueImpl(value);
        if (!res) {
            throw new IllegalArgumentException(String.format("Unsupported type %s", value.getClass()));
        }
    }

    public final boolean tryWriteTypedValue(Object value) throws UTFDataFormatException {
        return writeTypedValueImpl(value);
    }

    private boolean writeTypedValueImpl(Object value) throws UTFDataFormatException {
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
            return false;
        }
        return true;
    }

    private void ensureBufferSize(int headerSize, int dataSize) {
        if (byteBuffer == null || byteBuffer.length < (headerSize + dataSize)) {
            byteBuffer = new byte[bufferSize(headerSize, dataSize)];
        }
    }

    public static BinaryOutput create() {
        return new ByteArrayBinaryOutput();
    }

    public static BinaryOutput create(byte[] initialBuffer) {
        return initialBuffer == null ? new ByteArrayBinaryOutput() : new ByteArrayBinaryOutput(initialBuffer);
    }

    public static BinaryOutput create(CCharPointer address, int length, boolean unmanaged) {
        return new CCharPointerBinaryOutput(address, length, unmanaged);
    }

    static int bufferSize(int headerSize, int dataSize) {
        return headerSize + (dataSize <= MAX_SHORT_LENGTH ? dataSize << 1 : dataSize);
    }

    private static final class ByteArrayBinaryOutput extends BinaryOutput {

        private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
        private static final int INITIAL_SIZE = 32;

        private byte[] buffer;

        private ByteArrayBinaryOutput() {
            buffer = new byte[INITIAL_SIZE];
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
        public byte[] getArray() {
            return buffer;
        }

        @Override
        public CCharPointer getAddress() {
            throw new UnsupportedOperationException("GetAddress is not supported on the array based output.");
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
    }

    private static final class CCharPointerBinaryOutput extends BinaryOutput {

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

        @Override
        public CCharPointer getAddress() {
            checkClosed();
            return address;
        }

        @Override
        public byte[] getArray() {
            throw new UnsupportedOperationException("GetArray is not supported on the direct memory based output.");
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
    }
}
