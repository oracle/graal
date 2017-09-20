/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

import sun.misc.Unsafe;

@ValueType
public final class LLVMVirtualAllocationAddress {
    private static final Unsafe UNSAFE = getUnsafe();
    private static final long intArrayBaseOffset = getUnsafe().arrayBaseOffset(int[].class);

    @SuppressWarnings("restriction")
    private static Unsafe getUnsafe() {
        CompilerAsserts.neverPartOfCompilation();
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }

    private final int[] object;
    private final long offset;

    public LLVMVirtualAllocationAddress(long size, long offset) {
        long s = (size + 3) / 4;
        this.object = new int[(int) s];
        this.offset = offset;
    }

    private LLVMVirtualAllocationAddress(int[] arr, long offset) {
        this.object = arr;
        this.offset = offset;
    }

    public long getOffset() {
        return offset;
    }

    public int[] getObject() {
        return object;
    }

    public boolean isNull() {
        return object == null;
    }

    public LLVMVirtualAllocationAddress increment(long value) {
        return new LLVMVirtualAllocationAddress(this.object, this.offset + value);
    }

    public void writeI1(boolean value) {
        UNSAFE.putByte(object, intArrayBaseOffset + offset, (byte) (value ? 1 : 0));
    }

    public boolean getI1() {
        return UNSAFE.getByte(object, intArrayBaseOffset + offset) != 0;
    }

    public void writeI8(byte value) {
        UNSAFE.putByte(object, intArrayBaseOffset + offset, value);
    }

    public byte getI8() {
        return UNSAFE.getByte(object, intArrayBaseOffset + offset);
    }

    public void writeI16(short value) {
        UNSAFE.putShort(object, intArrayBaseOffset + offset, value);
    }

    public short getI16() {
        return UNSAFE.getShort(object, intArrayBaseOffset + offset);
    }

    public void writeI32(int value) {
        UNSAFE.putInt(object, intArrayBaseOffset + offset, value);
    }

    public int getI32() {
        return UNSAFE.getInt(object, intArrayBaseOffset + offset);
    }

    public void writeI64(long value) {
        UNSAFE.putLong(object, intArrayBaseOffset + offset, value);
    }

    public long getI64() {
        return UNSAFE.getLong(object, intArrayBaseOffset + offset);
    }

    public void writeFloat(float value) {
        UNSAFE.putFloat(object, intArrayBaseOffset + offset, value);
    }

    public float getFloat() {
        return UNSAFE.getFloat(object, intArrayBaseOffset + offset);
    }

    public void writeDouble(double value) {
        UNSAFE.putDouble(object, intArrayBaseOffset + offset, value);
    }

    public double getDouble() {
        return UNSAFE.getDouble(object, intArrayBaseOffset + offset);
    }

    public LLVMVirtualAllocationAddress copy() {
        return new LLVMVirtualAllocationAddress(this.object, this.offset);
    }

    public static final class LLVMVirtualAllocationAddressTruffleObject implements TruffleObject {
        private final LLVMVirtualAllocationAddress object;

        public LLVMVirtualAllocationAddressTruffleObject(LLVMVirtualAllocationAddress object) {
            this.object = object;
        }

        public LLVMVirtualAllocationAddress getObject() {
            return object.copy();
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return null;
        }
    }

}
