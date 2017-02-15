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

import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock;
import com.oracle.truffle.llvm.runtime.types.metadata.MetadataBlock.MetadataReference;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.ValueSymbol;

public class StructureType implements AggregateType, ValueSymbol {

    private String name = LLVMIdentifier.UNKNOWN;

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
    public int getAlignment(DataSpecConverter targetDataLayout) {
        return getLargestAlignment(targetDataLayout);
    }

    @Override
    public int getSize(DataSpecConverter targetDataLayout) {
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
    public int getIndexOffset(int index, DataSpecConverter targetDataLayout) {
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

    private int getLargestAlignment(DataSpecConverter targetDataLayout) {
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
        this.name = LLVMIdentifier.toTypeIdentifier(name);
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
    public void setMetadataReference(MetadataReference metadata) {
        this.metadata = metadata;
    }

    @Override
    public MetadataReference getMetadataReference() {
        return metadata;
    }

    @Override
    public int hashCode() {
        int hash = 11;
        hash = 23 * hash + (isPacked ? 1231 : 1237);
        for (Type type : types) {
            /*
             * Those types could create cycles, so we ignore them for hashCode() calculation.
             */
            if (type instanceof AggregateType || type instanceof PointerType || type instanceof FunctionType) {
                hash = 23 * hash + 47;
                continue;
            }
            hash = 23 * hash + type.hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;

        } else if (obj instanceof StructureType) {
            final StructureType other = (StructureType) obj;
            return Objects.equals(name, other.name) && isPacked == other.isPacked && Arrays.equals(types, other.types);

        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        if (name.equals(LLVMIdentifier.UNKNOWN)) {
            return toDeclarationString();
        } else {
            return name;
        }
    }
}
