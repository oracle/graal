/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.value;

import java.math.BigInteger;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.NodeInterface;

public interface LLVMDebugValue {

    String UNAVAILABLE_VALUE = "<unavailable>";

    LLVMDebugValue UNAVAILABLE = new LLVMDebugValue() {
        @Override
        public String describeValue(long bitOffset, int bitSize) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object cannotInterpret(String intendedType, long bitOffset, int bitSize) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object unavailable(long bitOffset, int bitSize) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public boolean canRead(long bitOffset, int bits) {
            return false;
        }

        @Override
        public Object readBoolean(long bitOffset) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object readFloat(long bitOffset) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object readDouble(long bitOffset) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object read80BitFloat(long bitOffset) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object readAddress(long bitOffset) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object readUnknown(long bitOffset, int bitSize) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object computeAddress(long bitOffset) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public Object readBigInteger(long bitOffset, int bitSize, boolean signed) {
            return UNAVAILABLE_VALUE;
        }

        @Override
        public LLVMDebugValue dereferencePointer(long bitOffset) {
            return null;
        }

        @Override
        public boolean isInteropValue() {
            return false;
        }

        @Override
        public Object asInteropValue() {
            return null;
        }
    };

    @TruffleBoundary
    static String toHexString(BigInteger value) {
        final byte[] bytes = value.toByteArray();
        final StringBuilder builder = new StringBuilder(bytes.length * 2 + 2);
        builder.append("0x");
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    interface Builder extends NodeInterface {

        LLVMDebugValue build(Object irValue);

    }

    String describeValue(long bitOffset, int bitSize);

    @TruffleBoundary
    default Object cannotInterpret(String intendedType, long bitOffset, int bitSize) {
        return String.format("<cannot interpret as %s: %s>", intendedType, describeValue(bitOffset, bitSize));
    }

    @TruffleBoundary
    default Object unavailable(long bitOffset, int bitSize) {
        return String.format("<unavailable: %s>", describeValue(bitOffset, bitSize));
    }

    boolean canRead(long bitOffset, int bits);

    Object readBoolean(long bitOffset);

    Object readFloat(long bitOffset);

    Object readDouble(long bitOffset);

    Object read80BitFloat(long bitOffset);

    Object readAddress(long bitOffset);

    Object readUnknown(long bitOffset, int bitSize);

    Object computeAddress(long bitOffset);

    Object readBigInteger(long bitOffset, int bitSize, boolean signed);

    default boolean isAlwaysSafeToDereference(@SuppressWarnings("unused") long bitOffset) {
        return false;
    }

    LLVMDebugValue dereferencePointer(long bitOffset);

    boolean isInteropValue();

    Object asInteropValue();

    default boolean isManagedPointer() {
        return false;
    }

    default Object getManagedPointerBase() {
        return null;
    }

    default long getManagedPointerOffset() {
        return 0;
    }
}
