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
package com.oracle.truffle.llvm.runtime.memory;

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerAsserts;

import sun.misc.Unsafe;

@SuppressWarnings("static-method")
public final class UnsafeIntArrayAccess {
    private static final Unsafe unsafe = getUnsafe();
    private static final int intArrayBaseOffset = unsafe.arrayBaseOffset(int[].class);

    private static final UnsafeIntArrayAccess INSTANCE = new UnsafeIntArrayAccess();

    public static UnsafeIntArrayAccess getInstance() {
        return INSTANCE;
    }

    private UnsafeIntArrayAccess() {
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
        unsafe.putByte(arr, intArrayBaseOffset + offset, (byte) (value ? 1 : 0));
    }

    public boolean getI1(int[] arr, long offset) {
        return unsafe.getByte(arr, intArrayBaseOffset + offset) != 0;
    }

    public void writeI8(int[] arr, long offset, byte value) {
        unsafe.putByte(arr, intArrayBaseOffset + offset, value);
    }

    public byte getI8(int[] arr, long offset) {
        return unsafe.getByte(arr, intArrayBaseOffset + offset);
    }

    public void writeI16(int[] arr, long offset, short value) {
        unsafe.putShort(arr, intArrayBaseOffset + offset, value);
    }

    public short getI16(int[] arr, long offset) {
        return unsafe.getShort(arr, intArrayBaseOffset + offset);
    }

    public void writeI32(int[] arr, long offset, int value) {
        unsafe.putInt(arr, intArrayBaseOffset + offset, value);
    }

    public int getI32(int[] arr, long offset) {
        return unsafe.getInt(arr, intArrayBaseOffset + offset);
    }

    public void writeI64(int[] arr, long offset, long value) {
        unsafe.putLong(arr, intArrayBaseOffset + offset, value);
    }

    public long getI64(int[] arr, long offset) {
        return unsafe.getLong(arr, intArrayBaseOffset + offset);
    }

    public void writeFloat(int[] arr, long offset, float value) {
        unsafe.putFloat(arr, intArrayBaseOffset + offset, value);
    }

    public float getFloat(int[] arr, long offset) {
        return unsafe.getFloat(arr, intArrayBaseOffset + offset);
    }

    public void writeDouble(int[] arr, long offset, double value) {
        unsafe.putDouble(arr, intArrayBaseOffset + offset, value);
    }

    public double getDouble(int[] arr, long offset) {
        return unsafe.getDouble(arr, intArrayBaseOffset + offset);
    }
}
