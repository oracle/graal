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
    public int getAlignment() {
        return elementType.getAlignment();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ArrayType) {
            ArrayType other = (ArrayType) obj;
            return length == other.length && elementType.equals(other.elementType);
        }
        return false;
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
    public int getAlignmentByte(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return elementType.getAlignmentByte(targetDataLayout);
    }

    @Override
    public int getSizeByte(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return elementType.getSizeByte(targetDataLayout) * length;
    }

    @Override
    public int getIndexOffsetByte(int index, DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return elementType.getSizeByte(targetDataLayout) * index;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.elementType);
        hash = 67 * hash + this.length;
        return hash;
    }

    @Override
    public int sizeof() {
        return length * elementType.sizeof();
    }

    @Override
    public int sizeof(int alignment) {
        return length * elementType.sizeof(alignment);
    }

    @Override
    public String toString() {
        return String.format("[%d x %s]", getLength(), getElementType());
    }

    @Override
    public void setMetadataReference(MetadataReference metadata) {
        this.metadata = metadata;
    }

    @Override
    public MetadataReference getMetadataReference() {
        return metadata;
    }
}
