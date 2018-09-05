/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.memory;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;

import sun.misc.Unsafe;

@SuppressWarnings("static-method")
public final class UnsafeArrayAccess {
    public static final int ARRAY_BOOLEAN_BASE_OFFSET = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
    public static final int ARRAY_BYTE_BASE_OFFSET = Unsafe.ARRAY_BYTE_BASE_OFFSET;
    public static final int ARRAY_CHAR_BASE_OFFSET = Unsafe.ARRAY_CHAR_BASE_OFFSET;
    public static final int ARRAY_SHORT_BASE_OFFSET = Unsafe.ARRAY_SHORT_BASE_OFFSET;
    public static final int ARRAY_INT_BASE_OFFSET = Unsafe.ARRAY_INT_BASE_OFFSET;
    public static final int ARRAY_LONG_BASE_OFFSET = Unsafe.ARRAY_LONG_BASE_OFFSET;
    public static final int ARRAY_FLOAT_BASE_OFFSET = Unsafe.ARRAY_FLOAT_BASE_OFFSET;
    public static final int ARRAY_DOUBLE_BASE_OFFSET = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
    public static final int ARRAY_OBJECT_BASE_OFFSET = Unsafe.ARRAY_OBJECT_BASE_OFFSET;

    private static final Unsafe unsafe = getUnsafe();

    private static final UnsafeArrayAccess INSTANCE = new UnsafeArrayAccess();

    /**
     * @deprecated "This method should not be called directly. Use
     *             {@link LLVMLanguage#getCapability(Class)} instead."
     */
    @Deprecated
    public static UnsafeArrayAccess getInstance() {
        return INSTANCE;
    }

    private UnsafeArrayAccess() {
    }

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

    public void writeI1(int[] arr, long offset, boolean value) {
        writeI1(arr, ARRAY_INT_BASE_OFFSET, offset, value);
    }

    public boolean getI1(int[] arr, long offset) {
        return getI1(arr, ARRAY_INT_BASE_OFFSET, offset);
    }

    public void writeI8(int[] arr, long offset, byte value) {
        writeI8(arr, ARRAY_INT_BASE_OFFSET, offset, value);
    }

    public byte getI8(int[] arr, long offset) {
        return getI8(arr, ARRAY_INT_BASE_OFFSET, offset);
    }

    public void writeI16(int[] arr, long offset, short value) {
        writeI16(arr, ARRAY_INT_BASE_OFFSET, offset, value);
    }

    public short getI16(int[] arr, long offset) {
        return getI16(arr, ARRAY_INT_BASE_OFFSET, offset);
    }

    public void writeI32(int[] arr, long offset, int value) {
        writeI32(arr, ARRAY_INT_BASE_OFFSET, offset, value);
    }

    public int getI32(int[] arr, long offset) {
        return getI32(arr, ARRAY_INT_BASE_OFFSET, offset);
    }

    public void writeI64(int[] arr, long offset, long value) {
        writeI64(arr, ARRAY_INT_BASE_OFFSET, offset, value);
    }

    public long getI64(int[] arr, long offset) {
        return getI64(arr, ARRAY_INT_BASE_OFFSET, offset);
    }

    public void writeFloat(int[] arr, long offset, float value) {
        writeFloat(arr, ARRAY_INT_BASE_OFFSET, offset, value);
    }

    public float getFloat(int[] arr, long offset) {
        return getFloat(arr, ARRAY_INT_BASE_OFFSET, offset);
    }

    public void writeDouble(int[] arr, long offset, double value) {
        writeDouble(arr, ARRAY_INT_BASE_OFFSET, offset, value);
    }

    public double getDouble(int[] arr, long offset) {
        return getDouble(arr, ARRAY_INT_BASE_OFFSET, offset);
    }

    public void writeI1(long[] arr, long offset, boolean value) {
        writeI1(arr, ARRAY_LONG_BASE_OFFSET, offset, value);
    }

    public boolean getI1(long[] arr, long offset) {
        return getI1(arr, ARRAY_LONG_BASE_OFFSET, offset);
    }

    public void writeI8(long[] arr, long offset, byte value) {
        writeI8(arr, ARRAY_LONG_BASE_OFFSET, offset, value);
    }

    public byte getI8(long[] arr, long offset) {
        return getI8(arr, ARRAY_LONG_BASE_OFFSET, offset);
    }

    public void writeI16(long[] arr, long offset, short value) {
        writeI16(arr, ARRAY_LONG_BASE_OFFSET, offset, value);
    }

    public short getI16(long[] arr, long offset) {
        return getI16(arr, ARRAY_LONG_BASE_OFFSET, offset);
    }

    public void writeI32(long[] arr, long offset, int value) {
        writeI32(arr, ARRAY_LONG_BASE_OFFSET, offset, value);
    }

    public int getI32(long[] arr, long offset) {
        return getI32(arr, ARRAY_LONG_BASE_OFFSET, offset);
    }

    public void writeI64(long[] arr, long offset, long value) {
        writeI64(arr, ARRAY_LONG_BASE_OFFSET, offset, value);
    }

    public long getI64(long[] arr, long offset) {
        return getI64(arr, ARRAY_LONG_BASE_OFFSET, offset);
    }

    public void writeFloat(long[] arr, long offset, float value) {
        writeFloat(arr, ARRAY_LONG_BASE_OFFSET, offset, value);
    }

    public float getFloat(long[] arr, long offset) {
        return getFloat(arr, ARRAY_LONG_BASE_OFFSET, offset);
    }

    public void writeDouble(long[] arr, long offset, double value) {
        writeDouble(arr, ARRAY_LONG_BASE_OFFSET, offset, value);
    }

    public double getDouble(long[] arr, long offset) {
        return getDouble(arr, ARRAY_LONG_BASE_OFFSET, offset);
    }

    public void writeI1(Object arr, long baseOffset, long offset, boolean value) {
        unsafe.putByte(arr, baseOffset + offset, (byte) (value ? 1 : 0));
    }

    public boolean getI1(Object arr, long baseOffset, long offset) {
        return unsafe.getByte(arr, baseOffset + offset) != 0;
    }

    public void writeI8(Object arr, long baseOffset, long offset, byte value) {
        unsafe.putByte(arr, baseOffset + offset, value);
    }

    public byte getI8(Object arr, long baseOffset, long offset) {
        return unsafe.getByte(arr, baseOffset + offset);
    }

    public void writeI16(Object arr, long baseOffset, long offset, short value) {
        unsafe.putShort(arr, baseOffset + offset, value);
    }

    public short getI16(Object arr, long baseOffset, long offset) {
        return unsafe.getShort(arr, baseOffset + offset);
    }

    public void writeI32(Object arr, long baseOffset, long offset, int value) {
        unsafe.putInt(arr, baseOffset + offset, value);
    }

    public int getI32(Object arr, long baseOffset, long offset) {
        return unsafe.getInt(arr, baseOffset + offset);
    }

    public void writeI64(Object arr, long baseOffset, long offset, long value) {
        unsafe.putLong(arr, baseOffset + offset, value);
    }

    public long getI64(Object arr, long baseOffset, long offset) {
        return unsafe.getLong(arr, baseOffset + offset);
    }

    public void writeFloat(Object arr, long baseOffset, long offset, float value) {
        unsafe.putFloat(arr, baseOffset + offset, value);
    }

    public float getFloat(Object arr, long baseOffset, long offset) {
        return unsafe.getFloat(arr, baseOffset + offset);
    }

    public void writeDouble(Object arr, long baseOffset, long offset, double value) {
        unsafe.putDouble(arr, baseOffset + offset, value);
    }

    public double getDouble(Object arr, long baseOffset, long offset) {
        return unsafe.getDouble(arr, baseOffset + offset);
    }
}
