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
package com.oracle.truffle.llvm.runtime.types;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public enum MetaType implements Type {

    UNKNOWN,
    VOID,
    OPAQUE,
    LABEL,
    TOKEN,
    METADATA,
    X86_MMX;

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

    @Override
    public LLVMBaseType getLLVMBaseType() {
        switch (this) {
            case VOID:
                return LLVMBaseType.VOID;
            case OPAQUE:
                return LLVMBaseType.ADDRESS;
            default:
                throw new AssertionError("Cannot resolve to LLVMBaseType: " + this);
        }
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
        switch (this) {
            case VOID:
                throw new LLVMUnsupportedException(LLVMUnsupportedException.UnsupportedReason.PARSER_ERROR_VOID_SLOT);
            default:
                return FrameSlotKind.Object;
        }
    }

    @Override
    public LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        switch (this) {
            case VOID:
                return LLVMFunctionDescriptor.LLVMRuntimeType.VOID;
            case OPAQUE:
                return LLVMFunctionDescriptor.LLVMRuntimeType.ADDRESS;
            default:
                throw new UnsupportedOperationException("Cannot resolve to Runtime Type: " + this);
        }
    }

    @Override
    public int getBits() {
        return 0;
    }

    @Override
    public int getAlignment(DataSpecConverter targetDataLayout) {
        return Long.BYTES;
    }

    @Override
    public int getSize(DataSpecConverter targetDataLayout) {
        return 0;
    }
}
