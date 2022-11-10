/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMReadStringNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen.LLVMI16OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen.LLVMI32OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen.LLVMI64OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen.LLVMI8OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNodeGen.LLVMPointerOffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen.LLVMI16OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen.LLVMI32OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen.LLVMI64OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen.LLVMI8OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Various utility functions that can be used to read pointers to aid debugging.
 */
public final class LLVMDebugPointer {

    private LLVMPointer pointer;

    public static LLVMDebugPointer of(LLVMPointer pointer) {
        return new LLVMDebugPointer(pointer);
    }

    public static LLVMDebugPointer of(long pointer) {
        return of(LLVMNativePointer.create(pointer));
    }

    public LLVMDebugPointer(LLVMPointer pointer) {
        this.pointer = pointer;
    }

    public String readAsciiString() {
        return LLVMReadStringNodeGen.create().executeWithTarget(pointer);
    }

    private static char charOfByte(byte v) {
        if (v < 32 || v > 126) {
            return '.';
        } else {
            return (char) v;
        }
    }

    public LLVMPointer getPointer() {
        return pointer;
    }

    public byte readI8() {
        return readI8(0);
    }

    public byte readI8(long offset) {
        return LLVMI8OffsetLoadNodeGen.getUncached().executeWithTarget(pointer, offset);
    }

    public short readI16() {
        return readI16(0);
    }

    public short readI16(long offset) {
        return LLVMI16OffsetLoadNodeGen.getUncached().executeWithTarget(pointer, offset);
    }

    public int readI32() {
        return readI32(0);
    }

    public int readI32(long offset) {
        return LLVMI32OffsetLoadNodeGen.getUncached().executeWithTarget(pointer, offset);
    }

    public long readI64() throws UnexpectedResultException {
        return readI64(0);
    }

    public long readI64(long offset) throws UnexpectedResultException {
        return LLVMI64OffsetLoadNodeGen.getUncached().executeWithTarget(pointer, offset);
    }

    public void writeI8(long offset, byte value) {
        LLVMI8OffsetStoreNodeGen.getUncached().executeWithTarget(pointer, offset, value);
    }

    public void writeI8(byte value) {
        writeI8(0, value);
    }

    public void writeI16(long offset, short value) {
        LLVMI16OffsetStoreNodeGen.getUncached().executeWithTarget(pointer, offset, value);
    }

    public void writeI16(short value) {
        writeI16(0, value);
    }

    public void writeI32(long offset, int value) {
        LLVMI32OffsetStoreNodeGen.getUncached().executeWithTarget(pointer, offset, value);
    }

    public void writeI32(int value) {
        writeI32(0, value);
    }

    public void writeI64(long offset, long value) {
        LLVMI64OffsetStoreNodeGen.getUncached().executeWithTarget(pointer, offset, value);
    }

    public void writeI64(long value) {
        writeI64(0, value);
    }

    public LLVMPointer readPointer() {
        return readPointer(0);
    }

    public LLVMPointer readPointer(long offset) {
        return LLVMPointerOffsetLoadNodeGen.getUncached().executeWithTarget(pointer, offset);
    }

    public String asHex() {
        return String.format("%08x", LLVMNativePointer.cast(pointer).asNative());
    }

    public String readHex(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i += 16) {
            StringBuilder asciiOut = new StringBuilder();
            sb.append(String.format("%08x  ", i));
            int remaining = Math.min(length - i, 16);
            for (int j = 0; j < remaining; j++) {
                byte v = readI8(i + j);
                sb.append(String.format("%02x ", v));
                asciiOut.append(charOfByte(v));
            }
            for (int j = remaining; j < 16; j++) {
                sb.append("   ");
            }
            sb.append(' ');
            sb.append(asciiOut.toString());
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    public LLVMDebugPointer deref() {
        return deref(0);
    }

    public LLVMDebugPointer deref(long offset) {
        return of(readPointer(offset));
    }

    public LLVMDebugPointer increment(long offset) {
        return of(pointer.increment(offset));
    }

    @Override
    public String toString() {
        return pointer.toString();
    }
}
