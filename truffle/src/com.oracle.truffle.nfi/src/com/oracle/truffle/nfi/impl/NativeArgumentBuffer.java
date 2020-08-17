/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CompilerDirectives;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

abstract class NativeArgumentBuffer {

    final int[] patches;
    final Object[] objects;

    private int objIdx;

    enum TypeTag {
        // keep this in sync with the code in com.oracle.truffle.nfi.native/src/internal.h
        OBJECT,
        STRING,
        CLOSURE,
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

        private static final Class<? extends ByteBuffer> heapByteBuffer = ByteBuffer.wrap(new byte[0]).getClass();

        final byte[] prim;
        private final ByteBuffer primBuffer;

        Array(int primSize, int objCount) {
            super(objCount);
            this.prim = new byte[primSize];
            this.primBuffer = ByteBuffer.wrap(prim).order(ByteOrder.nativeOrder());
        }

        @Override
        protected ByteBuffer getPrimBuffer() {
            return CompilerDirectives.castExact(primBuffer, heapByteBuffer);
        }

        @Override
        public byte getInt8() {
            return CompilerDirectives.castExact(primBuffer, heapByteBuffer).get();
        }

        @Override
        public void putInt8(byte b) {
            CompilerDirectives.castExact(primBuffer, heapByteBuffer).put(b);
        }

        @Override
        public short getInt16() {
            return CompilerDirectives.castExact(primBuffer, heapByteBuffer).getShort();
        }

        @Override
        public void putInt16(short s) {
            CompilerDirectives.castExact(primBuffer, heapByteBuffer).putShort(s);
        }

        @Override
        public int getInt32() {
            return CompilerDirectives.castExact(primBuffer, heapByteBuffer).getInt();
        }

        @Override
        public void putInt32(int i) {
            CompilerDirectives.castExact(primBuffer, heapByteBuffer).putInt(i);
        }

        @Override
        public long getInt64() {
            return CompilerDirectives.castExact(primBuffer, heapByteBuffer).getLong();
        }

        @Override
        public void putInt64(long l) {
            CompilerDirectives.castExact(primBuffer, heapByteBuffer).putLong(l);
        }

        @Override
        public float getFloat() {
            return CompilerDirectives.castExact(primBuffer, heapByteBuffer).getFloat();
        }

        @Override
        public void putFloat(float f) {
            CompilerDirectives.castExact(primBuffer, heapByteBuffer).putFloat(f);
        }

        @Override
        public double getDouble() {
            return CompilerDirectives.castExact(primBuffer, heapByteBuffer).getDouble();
        }

        @Override
        public void putDouble(double d) {
            CompilerDirectives.castExact(primBuffer, heapByteBuffer).putDouble(d);
        }
    }

    static final class Direct extends NativeArgumentBuffer {

        private static final Class<? extends ByteBuffer> directByteBuffer = ByteBuffer.allocateDirect(0).getClass();

        private final ByteBuffer primBuffer;

        Direct(ByteBuffer primBuffer, int objCount) {
            super(objCount);
            this.primBuffer = CompilerDirectives.castExact(primBuffer, directByteBuffer).slice().order(ByteOrder.nativeOrder());
        }

        @Override
        protected ByteBuffer getPrimBuffer() {
            return CompilerDirectives.castExact(primBuffer, directByteBuffer);
        }

        @Override
        public byte getInt8() {
            return CompilerDirectives.castExact(primBuffer, directByteBuffer).get();
        }

        @Override
        public void putInt8(byte b) {
            CompilerDirectives.castExact(primBuffer, directByteBuffer).put(b);
        }

        @Override
        public short getInt16() {
            return CompilerDirectives.castExact(primBuffer, directByteBuffer).getShort();
        }

        @Override
        public void putInt16(short s) {
            CompilerDirectives.castExact(primBuffer, directByteBuffer).putShort(s);
        }

        @Override
        public int getInt32() {
            return CompilerDirectives.castExact(primBuffer, directByteBuffer).getInt();
        }

        @Override
        public void putInt32(int i) {
            CompilerDirectives.castExact(primBuffer, directByteBuffer).putInt(i);
        }

        @Override
        public long getInt64() {
            return CompilerDirectives.castExact(primBuffer, directByteBuffer).getLong();
        }

        @Override
        public void putInt64(long l) {
            CompilerDirectives.castExact(primBuffer, directByteBuffer).putLong(l);
        }

        @Override
        public float getFloat() {
            return CompilerDirectives.castExact(primBuffer, directByteBuffer).getFloat();
        }

        @Override
        public void putFloat(float f) {
            CompilerDirectives.castExact(primBuffer, directByteBuffer).putFloat(f);
        }

        @Override
        public double getDouble() {
            return CompilerDirectives.castExact(primBuffer, directByteBuffer).getDouble();
        }

        @Override
        public void putDouble(double d) {
            CompilerDirectives.castExact(primBuffer, directByteBuffer).putDouble(d);
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
        int pos = getPrimBuffer().position();
        if (pos % alignment != 0) {
            pos += alignment - (pos % alignment);
            getPrimBuffer().position(pos);
        }
    }

    protected abstract ByteBuffer getPrimBuffer();

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
        int pos = getPrimBuffer().position();
        getPrimBuffer().position(pos + size);
        CompilerDirectives.transferToInterpreter();
        throw new AssertionError("passing TruffleObject from native back to Truffle not yet supported");
    }

    public void putObject(TypeTag tag, Object o, int size) {
        int pos = getPrimBuffer().position();
        int idx = objIdx++;

        patches[idx] = tag.encode(pos);
        objects[idx] = o;

        getPrimBuffer().position(pos + size);
    }
}
