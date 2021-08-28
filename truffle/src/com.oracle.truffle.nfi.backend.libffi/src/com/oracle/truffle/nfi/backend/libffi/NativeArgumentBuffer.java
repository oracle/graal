/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.libffi;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.memory.ByteArraySupport;

import sun.misc.Unsafe;

abstract class NativeArgumentBuffer {

    final int[] patches;
    final Object[] objects;

    private int objIdx;

    enum TypeTag {
        // keep this in sync with the code in com.oracle.truffle.nfi.native/src/internal.h
        OBJECT,
        STRING,
        KEEPALIVE,
        ENV,

        BOOLEAN_ARRAY,
        BYTE_ARRAY,
        CHAR_ARRAY,
        SHORT_ARRAY,
        INT_ARRAY,
        LONG_ARRAY,
        FLOAT_ARRAY,
        DOUBLE_ARRAY;

        private static final TypeTag[] VALUES = values();

        int encode(int offset) {
            int encoded = (offset << 4) | (ordinal() & 0x0F);
            assert getTag(encoded) == this && getOffset(encoded) == offset : "error encoding type tag, maybe offset is too big?";
            return encoded;
        }

        static TypeTag getTag(int encoded) {
            return VALUES[encoded & 0x0F];
        }

        static int getOffset(int encoded) {
            return encoded >>> 4;
        }
    }

    static final class Array extends NativeArgumentBuffer {
        private static final ByteArraySupport byteArraySupport;

        static {
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                byteArraySupport = ByteArraySupport.bigEndian();
            } else {
                byteArraySupport = ByteArraySupport.littleEndian();
            }
        }

        final byte[] prim;
        private int position;

        Array(int primSize, int objCount) {
            super(objCount);
            this.prim = new byte[primSize];
            this.position = 0;
        }

        @Override
        public int position() {
            return position;
        }

        @Override
        public void position(int newPosition) {
            position = newPosition;
        }

        @Override
        public byte getInt8() {
            byte v = byteArraySupport.getByte(prim, position);
            position += Byte.BYTES;
            return v;
        }

        @Override
        public void putInt8(byte b) {
            byteArraySupport.putByte(prim, position, b);
            position += Byte.BYTES;
        }

        @Override
        public short getInt16() {
            short v = byteArraySupport.getShort(prim, position);
            position += Short.BYTES;
            return v;
        }

        @Override
        public void putInt16(short s) {
            byteArraySupport.putShort(prim, position, s);
            position += Short.BYTES;
        }

        @Override
        public int getInt32() {
            int v = byteArraySupport.getInt(prim, position);
            position += Integer.BYTES;
            return v;
        }

        @Override
        public void putInt32(int i) {
            byteArraySupport.putInt(prim, position, i);
            position += Integer.BYTES;
        }

        @Override
        public long getInt64() {
            long v = byteArraySupport.getLong(prim, position);
            position += Long.BYTES;
            return v;
        }

        @Override
        public void putInt64(long l) {
            byteArraySupport.putLong(prim, position, l);
            position += Long.BYTES;
        }

        @Override
        public float getFloat() {
            float v = byteArraySupport.getFloat(prim, position);
            position += Float.BYTES;
            return v;
        }

        @Override
        public void putFloat(float f) {
            byteArraySupport.putFloat(prim, position, f);
            position += Float.BYTES;
        }

        @Override
        public double getDouble() {
            double v = byteArraySupport.getDouble(prim, position);
            position += Double.BYTES;
            return v;
        }

        @Override
        public void putDouble(double d) {
            byteArraySupport.putDouble(prim, position, d);
            position += Double.BYTES;
        }
    }

    // allocated by native code
    static final class Pointer {

        long pointer;

        Pointer(long pointer) {
            this.pointer = pointer;
        }
    }

    static final class Direct extends NativeArgumentBuffer {
        private static final Unsafe UNSAFE;

        static {
            Field unsafeField;
            try {
                unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }

            try {
                UNSAFE = (Unsafe) unsafeField.get(null);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        private final long buffer;
        private int position;

        Direct(Pointer pointer, int objCount) {
            super(objCount);
            this.buffer = pointer.pointer;
            this.position = 0;
        }

        @Override
        public int position() {
            return position;
        }

        @Override
        public void position(int newPosition) {
            position = newPosition;
        }

        @Override
        public byte getInt8() {
            byte v = UNSAFE.getByte(buffer + position);
            position += Byte.BYTES;
            return v;
        }

        @Override
        public void putInt8(byte b) {
            UNSAFE.putByte(buffer + position, b);
            position += Byte.BYTES;
        }

        @Override
        public short getInt16() {
            short v = UNSAFE.getShort(buffer + position);
            position += Short.BYTES;
            return v;
        }

        @Override
        public void putInt16(short s) {
            UNSAFE.putShort(buffer + position, s);
            position += Short.BYTES;
        }

        @Override
        public int getInt32() {
            int v = UNSAFE.getInt(buffer + position);
            position += Integer.BYTES;
            return v;
        }

        @Override
        public void putInt32(int i) {
            UNSAFE.putInt(buffer + position, i);
            position += Integer.BYTES;
        }

        @Override
        public long getInt64() {
            long v = UNSAFE.getLong(buffer + position);
            position += Long.BYTES;
            return v;
        }

        @Override
        public void putInt64(long l) {
            UNSAFE.putLong(buffer + position, l);
            position += Long.BYTES;
        }

        @Override
        public float getFloat() {
            float v = UNSAFE.getFloat(buffer + position);
            position += Float.BYTES;
            return v;
        }

        @Override
        public void putFloat(float f) {
            UNSAFE.putFloat(buffer + position, f);
            position += Float.BYTES;
        }

        @Override
        public double getDouble() {
            double v = UNSAFE.getDouble(buffer + position);
            position += Double.BYTES;
            return v;
        }

        @Override
        public void putDouble(double d) {
            UNSAFE.putDouble(buffer + position, d);
            position += Double.BYTES;
        }
    }

    int getPatchCount() {
        return objIdx;
    }

    protected NativeArgumentBuffer(int objCount) {
        if (objCount > 0) {
            patches = new int[objCount];
            objects = new Object[objCount];
        } else {
            patches = null;
            objects = null;
        }
        objIdx = 0;
    }

    public void align(int alignment) {
        assert alignment >= 1;
        int pos = position();
        if (pos % alignment != 0) {
            pos += alignment - (pos % alignment);
            position(pos);
        }
    }

    public abstract int position();

    public abstract void position(int newPosition);

    public abstract byte getInt8();

    public abstract void putInt8(byte b);

    public abstract short getInt16();

    public abstract void putInt16(short s);

    public abstract int getInt32();

    public abstract void putInt32(int i);

    public abstract long getInt64();

    public abstract void putInt64(long l);

    public abstract float getFloat();

    public abstract void putFloat(float f);

    public abstract double getDouble();

    public abstract void putDouble(double d);

    public long getPointer(int size) {
        switch (size) {
            case 4:
                return getInt32();
            case 8:
                return getInt64();
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("unexpected pointer size " + size);
        }
    }

    public void putPointer(long ptr, int size) {
        switch (size) {
            case 4:
                putInt32((int) ptr);
                break;
            case 8:
                putInt64(ptr);
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("unexpected pointer size " + size);
        }
    }

    public Object getObject(int size) {
        int pos = position();
        position(pos + size);
        throw CompilerDirectives.shouldNotReachHere("passing TruffleObject from native back to Truffle not yet supported");
    }

    public void putObject(TypeTag tag, Object o, int size) {
        int pos = position();
        int idx = objIdx++;

        patches[idx] = tag.encode(pos);
        objects[idx] = o;

        position(pos + size);
    }

    public void putPointerKeepalive(Object o, long ptr, int size) {
        putObject(TypeTag.KEEPALIVE, o, 0);
        putPointer(ptr, size);
    }
}
