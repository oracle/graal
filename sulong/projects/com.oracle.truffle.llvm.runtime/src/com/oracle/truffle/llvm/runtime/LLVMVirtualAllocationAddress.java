/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;

@ValueType
@ExportLibrary(InteropLibrary.class)
public final class LLVMVirtualAllocationAddress implements LLVMInternalTruffleObject {

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

    @ExportMessage
    public boolean isNull() {
        return object == null;
    }

    public LLVMVirtualAllocationAddress increment(long value) {
        return new LLVMVirtualAllocationAddress(this.object, this.offset + value);
    }

    public void writeI1(UnsafeArrayAccess memory, boolean value) {
        memory.writeI1(object, offset, value);
    }

    public boolean getI1(UnsafeArrayAccess memory) {
        return memory.getI1(object, offset);
    }

    public void writeI8(UnsafeArrayAccess memory, byte value) {
        memory.writeI8(object, offset, value);
    }

    public byte getI8(UnsafeArrayAccess memory) {
        return memory.getI8(object, offset);
    }

    public void writeI16(UnsafeArrayAccess memory, short value) {
        memory.writeI16(object, offset, value);
    }

    public short getI16(UnsafeArrayAccess memory) {
        return memory.getI16(object, offset);
    }

    public void writeI32(UnsafeArrayAccess memory, int value) {
        memory.writeI32(object, offset, value);
    }

    public int getI32(UnsafeArrayAccess memory) {
        return memory.getI32(object, offset);
    }

    public void writeI64(UnsafeArrayAccess memory, long value) {
        memory.writeI64(object, offset, value);
    }

    public long getI64(UnsafeArrayAccess memory) {
        return memory.getI64(object, offset);
    }

    public void writeFloat(UnsafeArrayAccess memory, float value) {
        memory.writeFloat(object, offset, value);
    }

    public float getFloat(UnsafeArrayAccess memory) {
        return memory.getFloat(object, offset);
    }

    public void writeDouble(UnsafeArrayAccess memory, double value) {
        memory.writeDouble(object, offset, value);
    }

    public double getDouble(UnsafeArrayAccess memory) {
        return memory.getDouble(object, offset);
    }

    public LLVMVirtualAllocationAddress copy() {
        return new LLVMVirtualAllocationAddress(this.object, this.offset);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LLVMVirtualAllocationAddress) {
            LLVMVirtualAllocationAddress other = (LLVMVirtualAllocationAddress) obj;
            return this.object == other.object && this.offset == other.offset;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + Objects.hashCode(object);
        result = 31 * result + Long.hashCode(offset);
        return result;
    }
}
