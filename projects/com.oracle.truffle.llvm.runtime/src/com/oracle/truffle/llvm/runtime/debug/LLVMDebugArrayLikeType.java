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
package com.oracle.truffle.llvm.runtime.debug;

import java.util.function.Supplier;

public final class LLVMDebugArrayLikeType extends LLVMDebugType {

    private Supplier<LLVMDebugType> baseType;
    private Supplier<Long> length;

    public LLVMDebugArrayLikeType(long size, long align, long offset) {
        this(LLVMDebugType.UNKNOWN_TYPE::getName, size, align, offset, () -> LLVMDebugType.UNKNOWN_TYPE, () -> -1L);
    }

    private LLVMDebugArrayLikeType(Supplier<String> name, long size, long align, long offset, Supplier<LLVMDebugType> baseType, Supplier<Long> length) {
        super(size, align, offset);
        setName(name);
        this.baseType = baseType;
        this.length = length;
    }

    public LLVMDebugType getBaseType() {
        return baseType.get();
    }

    public void setBaseType(Supplier<LLVMDebugType> baseType) {
        this.baseType = baseType;
    }

    public long getLength() {
        return length.get();
    }

    public void setLength(long length) {
        this.length = () -> length;
    }

    @Override
    public LLVMDebugType getOffset(long newOffset) {
        return new LLVMDebugArrayLikeType(this::getName, getSize(), getAlign(), newOffset, this::getBaseType, length);
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public int getElementCount() {
        return (int) getLength();
    }

    @Override
    public String getElementName(long i) {
        if (0 <= i && i < getLength()) {
            return String.valueOf(i);
        }
        return null;
    }

    @Override
    public LLVMDebugType getElementType(long i) {
        if (0 <= i && i < getLength()) {
            final LLVMDebugType resolvedBaseType = baseType.get();
            // TODO element type alignment
            return resolvedBaseType.getOffset(getOffset() + i * resolvedBaseType.getSize());
        }
        return null;
    }

    @Override
    public LLVMDebugType getElementType(String name) {
        int i;
        try {
            i = Integer.parseInt(name);
        } catch (NumberFormatException nfe) {
            return null;
        }
        return getElementType(i);
    }
}
