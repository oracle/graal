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
package com.oracle.truffle.llvm.nodes.intrinsics.llvm.debug;

import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValueProvider;

final class LLVMUnavailableDebugValueProvider implements LLVMDebugValueProvider {

    static LLVMUnavailableDebugValueProvider INSTANCE = new LLVMUnavailableDebugValueProvider();

    private LLVMUnavailableDebugValueProvider() {
    }

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
    public LLVMDebugValueProvider dereferencePointer(long bitOffset) {
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
}
