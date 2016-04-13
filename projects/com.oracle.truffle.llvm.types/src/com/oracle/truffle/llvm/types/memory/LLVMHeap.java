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
package com.oracle.truffle.llvm.types.memory;

import com.oracle.truffle.llvm.types.LLVMAddress;

public class LLVMHeap extends LLVMMemory {

    public static LLVMAddress allocateCString(String string) {
        LLVMAddress baseAddress = LLVMHeap.allocateMemory(string.length() + 1);
        LLVMAddress currentAddress = baseAddress;
        for (int i = 0; i < string.length(); i++) {
            byte c = (byte) string.charAt(i);
            LLVMMemory.putI8(currentAddress, c);
            currentAddress = currentAddress.increment(1);
        }
        LLVMMemory.putI8(currentAddress, (byte) 0);
        return baseAddress;
    }

    public static LLVMAddress allocateMemory(long size) {
        long allocateMemory = UNSAFE.allocateMemory(size);
        return LLVMAddress.fromLong(allocateMemory);
    }

    public static LLVMAddress allocateZeroedMemory(long l) {
        long allocateMemory = UNSAFE.allocateMemory(l);
        UNSAFE.setMemory(allocateMemory, l, (byte) 0);
        return LLVMAddress.fromLong(allocateMemory);
    }

    public static void freeMemory(LLVMAddress addr) {
        UNSAFE.freeMemory(extractAddrNullPointerAllowed(addr));
    }

    public static void memCopy(LLVMAddress target, LLVMAddress source, long length) {
        long targetAddress = extractAddrNullPointerAllowed(target);
        long sourceAddress = extractAddrNullPointerAllowed(source);
        assert length == 0 || targetAddress != 0 && sourceAddress != 0;
        UNSAFE.copyMemory(sourceAddress, targetAddress, length);
    }

    public static void memCopy(LLVMAddress target, LLVMAddress source, long length, @SuppressWarnings("unused") int align, @SuppressWarnings("unused") boolean isVolatile) {
        memCopy(target, source, length);
    }

    public static void memSet(LLVMAddress target, byte value, long length) {
        long targetAddress = LLVMMemory.extractAddr(target);
        UNSAFE.setMemory(targetAddress, length, value);
    }

    public static void memSet(LLVMAddress target, byte value, long length, @SuppressWarnings("unused") int align, @SuppressWarnings("unused") boolean isVolatile) {
        memSet(target, value, length);
    }

    public static void memMove(LLVMAddress dest, LLVMAddress source, long length, @SuppressWarnings("unused") int align, @SuppressWarnings("unused") boolean isVolatile) {
        memMove(dest, source, length);
    }

    private static void memMove(LLVMAddress dest, LLVMAddress source, long length) {
        byte[] bytes = new byte[(int) length];
        LLVMAddress currentSourceAddress = source;
        for (int i = 0; i < length; i++) {
            bytes[i] = LLVMMemory.getI8(currentSourceAddress);
            currentSourceAddress.increment(1);
        }
        LLVMAddress currentTargetAddress = dest;
        for (int i = 0; i < length; i++) {
            LLVMMemory.putI8(currentTargetAddress, bytes[i]);
            currentTargetAddress.increment(Byte.SIZE);
        }
    }

    // current hack: we cannot directly store the LLVMFunction in the native memory due to GC
    public static final int FUNCTION_PTR_SIZE_BYTE = 8;

    public static void putFunctionIndex(LLVMAddress address, int functionIndex) {
        UNSAFE.putInt(LLVMMemory.extractAddr(address), functionIndex);
    }

    public static int getFunctionIndex(LLVMAddress addr) {
        int functionIndex = UNSAFE.getInt(LLVMMemory.extractAddr(addr));
        return functionIndex;
    }

}
