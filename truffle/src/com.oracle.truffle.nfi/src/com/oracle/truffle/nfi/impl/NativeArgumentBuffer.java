/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
    }

    int getPatchCount() {
        return objIdx;
    }

    private NativeArgumentBuffer(int objCount) {
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

    public byte getInt8() {
        return getPrimBuffer().get();
    }

    public void putInt8(byte b) {
        getPrimBuffer().put(b);
    }

    public short getInt16() {
        return getPrimBuffer().getShort();
    }

    public void putInt16(short s) {
        getPrimBuffer().putShort(s);
    }

    public int getInt32() {
        return getPrimBuffer().getInt();
    }

    public void putInt32(int i) {
        getPrimBuffer().putInt(i);
    }

    public long getInt64() {
        return getPrimBuffer().getLong();
    }

    public void putInt64(long l) {
        getPrimBuffer().putLong(l);
    }

    public float getFloat() {
        return getPrimBuffer().getFloat();
    }

    public void putFloat(float f) {
        getPrimBuffer().putFloat(f);
    }

    public double getDouble() {
        return getPrimBuffer().getDouble();
    }

    public void putDouble(double d) {
        getPrimBuffer().putDouble(d);
    }

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
