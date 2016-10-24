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

import java.util.Objects;

import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock;
import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;

public class VectorType implements AggregateType {

    private final Type elementType;

    private final int length;

    private MetadataReference metadata = MetadataBlock.voidRef;

    public VectorType(Type type, int length) {
        this.elementType = type;
        this.length = length;
    }

    @Override
    public int getBits() {
        return elementType.getBits() * length;
    }

    public Type getElementType() {
        return elementType;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public Type getElementType(int index) {
        return elementType;
    }

    @Override
    public LLVMBaseType getLLVMBaseType() {
        final LLVMBaseType llvmBaseType = this.elementType.getLLVMBaseType();
        switch (llvmBaseType) {
            case I1:
                return LLVMBaseType.I1_VECTOR;
            case I8:
                return LLVMBaseType.I8_VECTOR;
            case I16:
                return LLVMBaseType.I16_VECTOR;
            case I32:
                return LLVMBaseType.I32_VECTOR;
            case I64:
                return LLVMBaseType.I64_VECTOR;
            case FLOAT:
                return LLVMBaseType.FLOAT_VECTOR;
            case DOUBLE:
                return LLVMBaseType.DOUBLE_VECTOR;
            default:
                throw new UnsupportedOperationException("Unsupported Vector Element Type: " + elementType);
        }
    }

    @Override
    public LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        switch (elementType.getRuntimeType()) {
            case I1:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I1_VECTOR;
            case I8:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I8_VECTOR;
            case I16:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I16_VECTOR;
            case I32:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I32_VECTOR;
            case I64:
                return LLVMFunctionDescriptor.LLVMRuntimeType.I64_VECTOR;
            case FLOAT:
                return LLVMFunctionDescriptor.LLVMRuntimeType.FLOAT_VECTOR;
            case DOUBLE:
                return LLVMFunctionDescriptor.LLVMRuntimeType.DOUBLE_VECTOR;
            default:
                throw new UnsupportedOperationException("Unsupported Vector Element Type: " + elementType);
        }
    }

    @Override
    public int getAlignment(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return elementType.getAlignment(targetDataLayout);
    }

    @Override
    public int getSize(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return elementType.getSize(targetDataLayout) * length;
    }

    @Override
    public int getIndexOffset(int index, DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return elementType.getSize(targetDataLayout) * index;
    }

    @Override
    public void setMetadataReference(MetadataReference metadata) {
        this.metadata = metadata;
    }

    @Override
    public MetadataReference getMetadataReference() {
        return metadata;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.elementType);
        hash = 59 * hash + this.length;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VectorType) {
            VectorType other = (VectorType) obj;
            return length == other.length && elementType.equals(other.getElementType());
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("<%d x %s>", getLength(), getElementType());
    }
}
