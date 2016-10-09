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
package com.oracle.truffle.llvm.parser.base.model.types;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;

public final class IntegerType implements Type {

    private static final int BOOLEAN_BITS = 1;
    private static final int BYTE_BITS = 8;
    private static final int SHORT_BITS = 16;
    private static final int INTEGER_BITS = 32;
    private static final int LONG_BITS = 64;

    public static final IntegerType BOOLEAN = new IntegerType(BOOLEAN_BITS);

    public static final IntegerType BYTE = new IntegerType(BYTE_BITS);

    public static final IntegerType SHORT = new IntegerType(SHORT_BITS);

    public static final IntegerType INTEGER = new IntegerType(INTEGER_BITS);

    public static final IntegerType LONG = new IntegerType(LONG_BITS);

    private final int bits;

    public IntegerType(int bits) {
        super();
        this.bits = bits;
    }

    @Override
    public int getAlignment() {
        if (bits <= Byte.SIZE) {
            return Byte.BYTES;
        } else if (bits <= Short.SIZE) {
            return Short.BYTES;
        } else if (bits <= Integer.SIZE) {
            return Integer.BYTES;
        }
        return Long.BYTES;
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
        switch (bits) {
            case BOOLEAN_BITS:
                return FrameSlotKind.Boolean;
            case BYTE_BITS:
                return FrameSlotKind.Byte;
            case SHORT_BITS:
            case INTEGER_BITS:
                return FrameSlotKind.Int;
            case LONG_BITS:
                return FrameSlotKind.Long;
            default:
                return FrameSlotKind.Object;
        }
    }

    @Override
    public LLVMBaseType getLLVMBaseType() {
        switch (bits) {
            case BOOLEAN_BITS:
                return LLVMBaseType.I1;
            case BYTE_BITS:
                return LLVMBaseType.I8;
            case SHORT_BITS:
                return LLVMBaseType.I16;
            case INTEGER_BITS:
                return LLVMBaseType.I32;
            case LONG_BITS:
                return LLVMBaseType.I64;
            default:
                return LLVMBaseType.I_VAR_BITWIDTH;
        }
    }

    @Override
    public LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        switch (bits) {
            case BOOLEAN_BITS:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I1;
            case BYTE_BITS:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I8;
            case SHORT_BITS:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I16;
            case INTEGER_BITS:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I32;
            case LONG_BITS:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I64;
            default:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I_VAR_BITWIDTH;
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof IntegerType && bits == ((IntegerType) obj).bits;
    }

    @Override
    public int getBits() {
        return bits;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + this.bits;
        return hash;
    }

    @Override
    public int sizeof() {
        int sizeof = bits / Byte.SIZE;
        return (bits % Byte.SIZE) == 0 ? sizeof : sizeof + 1;
    }

    @Override
    public String toString() {
        return String.format("i%d", bits);
    }

    @Override
    public int getAlignmentByte(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(getLLVMBaseType()) / Byte.SIZE;

        } else if (bits <= Byte.SIZE) {
            return Byte.BYTES;

        } else if (bits <= Short.SIZE) {
            return Short.BYTES;

        } else if (bits <= Integer.SIZE) {
            return Integer.BYTES;

        } else {
            return Long.BYTES;
        }
    }

    @Override
    public int getSizeByte(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return Math.max(1, bits / Byte.SIZE);
    }
}
