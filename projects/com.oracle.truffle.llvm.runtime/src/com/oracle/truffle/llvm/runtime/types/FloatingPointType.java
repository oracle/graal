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
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public enum FloatingPointType implements Type {

    HALF(16, 2, LLVMBaseType.HALF),
    FLOAT(32, 4, LLVMBaseType.FLOAT),
    DOUBLE(64, 8, LLVMBaseType.DOUBLE),
    X86_FP80(80, 16, LLVMBaseType.X86_FP80),
    FP128(128, 16, LLVMBaseType.F128),
    PPC_FP128(128, 16, LLVMBaseType.PPC_FP128);

    private final int alignment;

    private final int width;

    private final LLVMBaseType llvmBaseType;

    FloatingPointType(int width, int alignment, LLVMBaseType llvmBaseType) {
        this.alignment = alignment;
        this.width = width;
        this.llvmBaseType = llvmBaseType;
    }

    @Override
    public LLVMBaseType getLLVMBaseType() {
        return llvmBaseType;
    }

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public FrameSlotKind getFrameSlotKind() {
        switch (this) {
            case FLOAT:
                return FrameSlotKind.Float;
            case DOUBLE:
                return FrameSlotKind.Double;
            default:
                return FrameSlotKind.Object;
        }
    }

    @Override
    public LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        switch (this) {
            case HALF:
                return LLVMFunctionDescriptor.LLVMRuntimeType.HALF;
            case FLOAT:
                return LLVMFunctionDescriptor.LLVMRuntimeType.FLOAT;
            case DOUBLE:
                return LLVMFunctionDescriptor.LLVMRuntimeType.DOUBLE;
            case X86_FP80:
                return LLVMFunctionDescriptor.LLVMRuntimeType.X86_FP80;
            default:
                throw new UnsupportedOperationException("Unsupported FloatingPointType: " + this);
        }
    }

    @Override
    public int getBits() {
        return width;
    }

    @Override
    public int getAlignment(DataSpecConverter targetDataLayout) {
        if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(llvmBaseType) / Byte.SIZE;
        } else {
            return alignment;
        }
    }

    @Override
    public int getSize(DataSpecConverter targetDataLayout) {
        return Math.max(1, width / Byte.SIZE);
    }

    public int width() {
        return width;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }

}
