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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class LLVMDebugMemberType extends LLVMDebugType {

    @CompilationFinal private LLVMDebugType elementType;

    public LLVMDebugMemberType(String name, long size, long align, long offset) {
        this(name, size, align, offset, LLVMDebugType.UNKNOWN_TYPE);
    }

    private LLVMDebugMemberType(String name, long size, long align, long offset, LLVMDebugType elementType) {
        super(() -> name, size, align, offset);
        this.elementType = elementType;
    }

    public LLVMDebugType getElementType() {
        return elementType;
    }

    public void setElementType(LLVMDebugType elementType) {
        CompilerAsserts.neverPartOfCompilation();
        this.elementType = elementType;
    }

    /**
     * Return the element type with the offset of this type.
     *
     * @return the element type with the offset of this type
     */
    LLVMDebugType getOffsetElementType() {
        return elementType != null ? elementType.getOffset(getOffset()) : null;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.format("%s: %s", getName(), elementType != null ? elementType.getName() : null);
    }

    @Override
    public LLVMDebugType getOffset(long newOffset) {
        return this;
    }

    @Override
    public boolean isAggregate() {
        return true;
    }

    @Override
    public int getElementCount() {
        return 1;
    }

    @Override
    public String getElementName(long i) {
        if (i == 0) {
            return getName();
        }
        return null;
    }

    @Override
    public LLVMDebugType getElementType(long i) {
        if (i == 0) {
            return getOffsetElementType();
        }
        return null;
    }

    @Override
    public LLVMDebugType getElementType(String name) {
        if (name != null && name.equals(getName())) {
            return getOffsetElementType();
        }
        return null;
    }
}
