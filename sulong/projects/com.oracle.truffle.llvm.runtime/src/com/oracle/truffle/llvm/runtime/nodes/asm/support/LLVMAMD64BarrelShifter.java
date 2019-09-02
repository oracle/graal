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
package com.oracle.truffle.llvm.runtime.nodes.asm.support;

import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public class LLVMAMD64BarrelShifter {
    private static final int MASK_8 = 0x07;
    private static final int MASK_16 = 0x0f;
    private static final int MASK_32 = 0x1f;
    private static final int MASK_64 = 0x3f;

    public static byte rol(byte x, byte n) {
        int shift = n & MASK_8;
        return (byte) ((x << shift) | ((x & LLVMExpressionNode.I8_MASK) >>> (LLVMExpressionNode.I8_SIZE_IN_BITS - shift)));
    }

    public static short rol(short x, short n) {
        int shift = n & MASK_16;
        return (short) ((x << shift) | ((x & LLVMExpressionNode.I16_MASK) >>> (LLVMExpressionNode.I16_SIZE_IN_BITS - shift)));
    }

    public static int rol(int x, int n) {
        int shift = n & MASK_32;
        return (x << shift) | (x >>> (LLVMExpressionNode.I32_SIZE_IN_BITS - shift));
    }

    public static long rol(long x, long n) {
        int shift = (int) (n & MASK_64);
        return ((x << shift) | (x >>> (LLVMExpressionNode.I64_SIZE_IN_BITS - shift)));
    }

    public static byte ror(byte x, byte n) {
        int shift = n & MASK_8;
        return (byte) (((x & LLVMExpressionNode.I8_MASK) >>> shift) | (x << (LLVMExpressionNode.I8_SIZE_IN_BITS - shift)));
    }

    public static short ror(short x, short n) {
        int shift = n & MASK_16;
        return (short) (((x & LLVMExpressionNode.I16_MASK) >>> shift) | (x << (LLVMExpressionNode.I16_SIZE_IN_BITS - shift)));
    }

    public static int ror(int x, int n) {
        int shift = n & MASK_32;
        return ((x >>> shift) | (x << (LLVMExpressionNode.I32_SIZE_IN_BITS - shift)));
    }

    public static long ror(long x, long n) {
        int shift = (int) (n & MASK_64);
        return ((x >>> shift) | (x << (LLVMExpressionNode.I64_SIZE_IN_BITS - shift)));
    }
}
