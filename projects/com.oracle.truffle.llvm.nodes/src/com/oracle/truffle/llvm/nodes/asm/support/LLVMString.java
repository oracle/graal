/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.asm.support;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

import sun.misc.SharedSecrets;

// All methods except getBuffer can be used without @TruffleBoundary
public class LLVMString {
    public static ByteBuffer getBuffer(LLVMAddress address, int size) {
        CompilerAsserts.neverPartOfCompilation();
        return SharedSecrets.getJavaNioAccess().newDirectByteBuffer(address.getVal(), size, null);
    }

    public static byte[] memcpy(LLVMAddress address, int size) {
        byte[] out = new byte[size];
        LLVMAddress ptr = address;
        for (int i = 0; i < size; i++) {
            out[i] = LLVMMemory.getI8(ptr);
            ptr = ptr.increment(1);
        }
        return out;
    }

    public static void memcpy(byte[] src, LLVMAddress dst, long size) {
        int min = src.length;
        if (min > size) {
            min = (int) size;
        }
        LLVMAddress ptr = dst;
        for (int i = 0; i < min; i++) {
            LLVMMemory.putI8(ptr, src[i]);
            ptr = ptr.increment(1);
        }
    }

    @TruffleBoundary
    private static byte[] getBytes(String str) {
        return str.getBytes();
    }

    @TruffleBoundary
    private static String toString(byte[] bytes) {
        return new String(bytes);
    }

    public static void strcpy(LLVMAddress dst, String src) {
        memcpy(getBytes(src), dst, src.length());
        LLVMAddress zero = dst.increment(src.length());
        LLVMMemory.putI8(zero, (byte) 0);
    }

    public static void strncpy(LLVMAddress dst, String src, long size) {
        memcpy(getBytes(src), dst, size);
        if (src.length() < size) {
            LLVMAddress zero = dst.increment(src.length());
            LLVMMemory.putI8(zero, (byte) 0);
        }
    }

    public static long strlen(LLVMAddress address) {
        LLVMAddress ptr = address;
        while (LLVMMemory.getI8(ptr) != 0) {
            ptr = ptr.increment(1);
        }
        return ptr.getVal() - address.getVal();
    }

    public static String cstr(LLVMAddress address) {
        long len = strlen(address);
        byte[] bytes = memcpy(address, (int) len);
        return toString(bytes);
    }
}
