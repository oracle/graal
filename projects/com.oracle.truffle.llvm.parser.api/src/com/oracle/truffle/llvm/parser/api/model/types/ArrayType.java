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
package com.oracle.truffle.llvm.parser.api.model.types;

import java.util.Objects;

import com.oracle.truffle.llvm.parser.api.LLVMBaseType;
import com.oracle.truffle.llvm.parser.api.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.api.model.blocks.MetadataBlock;
import com.oracle.truffle.llvm.parser.api.model.blocks.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;

public class ArrayType implements AggregateType {

    private final Type elementType;

    private final int length;

    private MetadataReference metadata = MetadataBlock.voidRef;

    public ArrayType(Type type, int size) {
        super();
        this.elementType = type;
        this.length = size;
    }

    @Override
    public int getBits() {
        return getElementType().getBits() * length;
    }

    public Type getElementType() {
        return elementType;
    }

    @Override
    public Type getElementType(int idx) {
        return getElementType();
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public LLVMBaseType getLLVMBaseType() {
        return LLVMBaseType.ARRAY;
    }

    @Override
    public LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        return LLVMFunctionDescriptor.LLVMRuntimeType.ARRAY;
    }

    @Override
    public int getAlignment(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return getElementType().getAlignment(targetDataLayout);
    }

    @Override
    public int getSize(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return getElementType().getSize(targetDataLayout) * length;
    }

    @Override
    public int getIndexOffset(int index, DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return getElementType().getSize(targetDataLayout) * index;
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
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.getElementType());
        hash = 67 * hash + this.length;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;

        } else if (obj instanceof ArrayType) {
            final ArrayType other = (ArrayType) obj;
            return length == other.length && Objects.equals(getElementType(), other.getElementType());

        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("[%d x %s]", getLength(), getElementType());
    }
}
