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
package com.oracle.truffle.llvm.runtime.types;

import java.util.Arrays;

import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class StructureType extends AggregateType {

    private String name = LLVMIdentifier.UNKNOWN;
    private final boolean isPacked;
    private final Type[] types;

    public StructureType(boolean isPacked, Type[] types) {
        this.isPacked = isPacked;
        this.types = types;
    }

    public Type[] getElementTypes() {
        return types;
    }

    public boolean isPacked() {
        return isPacked;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getBitSize() {
        if (isPacked) {
            return Arrays.stream(types).mapToInt(Type::getBitSize).sum();
        } else {
            throw new UnsupportedOperationException("TargetDataLayout is necessary to compute Padding information!");
        }
    }

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int getNumberOfElements() {
        return types.length;
    }

    @Override
    public Type getElementType(int index) {
        return types[index];
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
    public int getOffsetOf(int index, DataSpecConverter targetDataLayout) {
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
    public String toString() {
        if (name.equals(LLVMIdentifier.UNKNOWN)) {
            return toDeclarationString();
        } else {
            return name;
        }
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isPacked ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + Arrays.hashCode(types);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        StructureType other = (StructureType) obj;
        if (isPacked != other.isPacked) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (!Arrays.equals(types, other.types)) {
            return false;
        }
        return true;
    }

}
