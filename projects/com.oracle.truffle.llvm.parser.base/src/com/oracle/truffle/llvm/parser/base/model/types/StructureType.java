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

import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.base.datalayout.DataLayoutConverter;
import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock;
import com.oracle.truffle.llvm.parser.base.model.blocks.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.parser.base.model.symbols.ValueSymbol;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;

import java.util.Arrays;

public final class StructureType implements AggregateType, ValueSymbol {

    private String name = ValueSymbol.UNKNOWN;

    private final boolean isPacked;

    private final Type[] types;

    private MetadataReference metadata = MetadataBlock.voidRef;

    public StructureType(boolean isPacked, Type[] types) {
        this.isPacked = isPacked;
        this.types = types;
    }

    @Override
    public int getBits() {
        if (isPacked) {
            return Arrays.stream(types).mapToInt(Type::getBits).sum();
        } else {
            throw new UnsupportedOperationException("TargetDataLayout is necessary to compute Padding information!");
        }
    }

    @Override
    public Type getElementType(int index) {
        return types[index];
    }

    @Override
    public int getLength() {
        return types.length;
    }

    @Override
    public LLVMBaseType getLLVMBaseType() {
        return LLVMBaseType.STRUCT;
    }

    @Override
    public LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        return LLVMFunctionDescriptor.LLVMRuntimeType.STRUCT;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getAlignment(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        return getLargestAlignment(targetDataLayout);
    }

    @Override
    public int getSize(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        int sumByte = 0;
        for (final Type elementType : types) {
            if (!isPacked) {
                sumByte += Type.getPadding(sumByte, elementType, targetDataLayout);
            }
            sumByte += elementType.getSize(targetDataLayout);
        }

        int padding = 0;
        if (!isPacked && sumByte != 0) {
            padding = Type.getPadding(sumByte, getLargestAlignment(targetDataLayout));
        }

        return sumByte + padding;
    }

    @Override
    public int getIndexOffset(int index, DataLayoutConverter.DataSpecConverter targetDataLayout) {
        int offset = 0;
        for (int i = 0; i < index; i++) {
            final Type elementType = types[i];
            offset += elementType.getSize(targetDataLayout);
            if (!isPacked) {
                offset += Type.getPadding(offset, elementType, targetDataLayout);
            }
        }
        if (!isPacked && getSize(targetDataLayout) > offset) {
            offset += Type.getPadding(offset, types[index], targetDataLayout);
        }
        return offset;
    }

    private int getLargestAlignment(DataLayoutConverter.DataSpecConverter targetDataLayout) {
        int largestAlignment = 0;
        for (final Type elementType : types) {
            largestAlignment = Math.max(largestAlignment, elementType.getAlignment(targetDataLayout));
        }
        return largestAlignment;
    }

    @Override
    public Type getType() {
        return this;
    }

    public boolean isPacked() {
        return isPacked;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    private String toDeclarationString() {
        StringBuilder str = new StringBuilder();
        if (isPacked) {
            str.append("<{ ");
        } else {
            str.append("{ ");
        }

        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            if (i > 0) {
                str.append(", ");
            }
            if (type instanceof PointerType && ((PointerType) type).getPointeeType() == this) {
                str.append("%").append(getName()).append("*");
            } else {
                str.append(type);
            }
        }

        if (isPacked) {
            str.append(" }>");
        } else {
            str.append(" }");
        }

        return str.toString();
    }

    @Override
    public String toString() {
        if (name.equals(ValueSymbol.UNKNOWN)) {
            return toDeclarationString();
        } else {
            return "%" + name;
        }
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
