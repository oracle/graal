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

import com.oracle.nfi.NativeFunctionInterfaceRuntime;
import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.nfi.api.NativeFunctionInterface;
import com.oracle.truffle.llvm.runtime.LLVMAddress;

public final class LLVMHeap extends LLVMMemory {

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
        long allocateMemory = (long) mallocHandle.call(size);
        return LLVMAddress.fromLong(allocateMemory);
    }

    public static LLVMAddress allocateZeroedMemory(long l) {
        long allocateMemory = allocateMemory(l).getVal();
        UNSAFE.setMemory(allocateMemory, l, (byte) 0);
        return LLVMAddress.fromLong(allocateMemory);
    }

    public static void freeMemory(LLVMAddress addr) {
        freeHandle.call(addr.getVal());
    }

    public static void memCopy(LLVMAddress target, LLVMAddress source, long length) {
        long targetAddress = extractAddrNullPointerAllowed(target);
        long sourceAddress = extractAddrNullPointerAllowed(source);
        assert length == 0 || targetAddress != 0 && sourceAddress != 0;
        memCopyHandle.call(targetAddress, sourceAddress, length);
    }

    public static void memCopy(LLVMAddress target, LLVMAddress source, long length, @SuppressWarnings("unused") int align, @SuppressWarnings("unused") boolean isVolatile) {
        memCopy(target, source, length);
    }

    public static void memSet(LLVMAddress target, int value, long length) {
        long targetAddress = LLVMMemory.extractAddr(target);
        memSetHandle.call(targetAddress, value, length);
    }

    public static void memSet(LLVMAddress target, byte value, long length, @SuppressWarnings("unused") int align, @SuppressWarnings("unused") boolean isVolatile) {
        memSet(target, value, length);
    }

    private static final NativeFunctionHandle memMoveHandle;
    private static final NativeFunctionHandle memSetHandle;
    private static final NativeFunctionHandle memCopyHandle;
    private static final NativeFunctionHandle freeHandle;
    private static final NativeFunctionHandle mallocHandle;

    static {
        final NativeFunctionInterface nfi = NativeFunctionInterfaceRuntime.getNativeFunctionInterface();
        memMoveHandle = nfi.getFunctionHandle("memmove", void.class, long.class, long.class, long.class);
        memCopyHandle = nfi.getFunctionHandle("memcpy", void.class, long.class, long.class, long.class);
        memSetHandle = nfi.getFunctionHandle("memset", void.class, long.class, int.class, long.class);
        freeHandle = nfi.getFunctionHandle("free", void.class, long.class);
        mallocHandle = nfi.getFunctionHandle("malloc", long.class, long.class);
    }

    public static void memMove(LLVMAddress dest, LLVMAddress source, long length) {
        memMoveHandle.call(dest.getVal(), source.getVal(), length);
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
