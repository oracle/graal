/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.debug.LLDBSupport;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugTypeConstants;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;

final class LLDBBoxedPrimitive implements LLVMDebugValue {

    private final Object boxedValue;

    LLDBBoxedPrimitive(Object boxedValue) {
        this.boxedValue = boxedValue;
    }

    private LLVMDebugValue unbox() {
        return CommonNodeFactory.createDebugValueBuilder().build(boxedValue);
    }

    private static boolean isMatchingSize(Object value, long bitSize) {
        if (value instanceof Boolean && bitSize != LLVMDebugTypeConstants.BOOLEAN_SIZE) {
            return false;
        }

        if (value instanceof Byte && bitSize != LLVMDebugTypeConstants.BYTE_SIZE) {
            return false;
        }

        if (value instanceof Short && bitSize != LLVMDebugTypeConstants.SHORT_SIZE) {
            return false;
        }

        if (value instanceof Integer && bitSize != LLVMDebugTypeConstants.INTEGER_SIZE) {
            return false;
        }

        if (value instanceof Long && bitSize != LLVMDebugTypeConstants.LONG_SIZE) {
            return false;
        }

        if (value instanceof Float && bitSize != LLVMDebugTypeConstants.FLOAT_SIZE) {
            return false;
        }

        if (value instanceof Double && bitSize != LLVMDebugTypeConstants.DOUBLE_SIZE) {
            return false;
        }

        return true;
    }

    @Override
    @TruffleBoundary
    public String describeValue(long bitOffset, int bitSize) {
        if (bitOffset == 0 && isMatchingSize(boxedValue, bitSize)) {
            return "<boxed value: " + boxedValue + ">";
        } else if (bitSize == 1) {
            return "<bit at offset " + LLDBSupport.toSizeString(bitOffset) + " in boxed value: " + boxedValue + ">";
        } else {
            return "<" + LLDBSupport.toSizeString(bitSize) + " at offset " + LLDBSupport.toSizeString(bitOffset) + " in boxed value: " + boxedValue + ">";
        }
    }

    @Override
    public boolean canRead(long bitOffset, int bits) {
        return true;
    }

    @Override
    public Object readBoolean(long bitOffset) {
        return unbox().readBoolean(bitOffset);
    }

    @Override
    public Object readFloat(long bitOffset) {
        return unbox().readFloat(bitOffset);
    }

    @Override
    public Object readDouble(long bitOffset) {
        return unbox().readDouble(bitOffset);
    }

    @Override
    public Object read80BitFloat(long bitOffset) {
        return unbox().read80BitFloat(bitOffset);
    }

    @Override
    public Object readAddress(long bitOffset) {
        if (bitOffset == 0) {
            return "<boxed value: " + boxedValue + ">";
        } else {
            return "<offset " + LLDBSupport.toSizeString(bitOffset) + " in boxed value: " + boxedValue + ">";
        }
    }

    @Override
    public Object readUnknown(long bitOffset, int bitSize) {
        return unbox().readUnknown(bitOffset, bitSize);
    }

    @Override
    public Object computeAddress(long bitOffset) {
        return readAddress(bitOffset);
    }

    @Override
    public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
        return unbox().readBigInteger(bitOffset, bitSize, signed);
    }

    @Override
    public LLVMDebugValue dereferencePointer(long bitOffset) {
        return bitOffset == 0 ? unbox() : null;
    }

    @Override
    public boolean isInteropValue() {
        return false;
    }

    @Override
    public Object asInteropValue() {
        return null;
    }
}
