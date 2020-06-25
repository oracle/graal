/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class StructureType extends AggregateType {

    private final String name;
    private final boolean isPacked;
    private final boolean isNamed;
    @CompilationFinal(dimensions = 1) private final Type[] types;
    private long size = -1;

    private StructureType(String name, boolean isPacked, boolean isNamed, Type[] types) {
        this.name = name;
        this.isPacked = isPacked;
        this.isNamed = isNamed;
        this.types = types;
    }

    /**
     * Creates a named structure type with one element type.
     */
    public static StructureType createNamed(String name, boolean isPacked, Type type0) {
        return new StructureType(name, isPacked, true, new Type[]{type0});
    }

    /**
     * Creates a named structure type with two element types.
     */
    public static StructureType createNamed(String name, boolean isPacked, Type type0, Type type1) {
        return new StructureType(name, isPacked, true, new Type[]{type0, type1});
    }

    /**
     * Creates a named structure type with known element types.
     */
    public static StructureType createNamedFromList(String name, boolean isPacked, ArrayList<Type> types) {
        return new StructureType(name, isPacked, true, types.toArray(Type.EMPTY_ARRAY));
    }

    /**
     * Creates an unnamed structure type with one element type.
     */
    public static StructureType createUnnamed(boolean isPacked, Type type0) {
        return new StructureType(LLVMIdentifier.UNKNOWN, isPacked, false, new Type[]{type0});
    }

    /**
     * Creates an unnamed structure type with two element types.
     */
    public static StructureType createUnnamed(boolean isPacked, Type type0, Type type1) {
        return new StructureType(LLVMIdentifier.UNKNOWN, isPacked, false, new Type[]{type0, type1});
    }

    /**
     * Creates an unnamed structure type with three element types.
     */
    public static StructureType createUnnamed(boolean isPacked, Type type0, Type type1, Type type2) {
        return new StructureType(LLVMIdentifier.UNKNOWN, isPacked, false, new Type[]{type0, type1, type2});
    }

    public StructureType(String name, boolean isPacked, int numElements) {
        this(name, isPacked, true, new Type[numElements]);
    }

    public StructureType(boolean isPacked, int numElements) {
        this(LLVMIdentifier.UNKNOWN, isPacked, false, new Type[numElements]);
    }

    public void setElementType(int idx, Type type) {
        verifyCycleFree(type);
        types[idx] = type;
    }

    public boolean isPacked() {
        return isPacked;
    }

    public String getName() {
        return name;
    }

    public boolean isNamed() {
        return isNamed;
    }

    @Override
    public long getBitSize() throws TypeOverflowException {
        if (isPacked) {
            try {
                return Arrays.stream(types).mapToLong(Type::getBitSizeUnchecked).reduce(0, Type::addUnsignedExactUnchecked);
            } catch (TypeOverflowExceptionUnchecked e) {
                throw e.getCause();
            }
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new UnsupportedOperationException("TargetDataLayout is necessary to compute Padding information!");
        }
    }

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public long getNumberOfElements() {
        return types.length;
    }

    public int getNumberOfElementsInt() {
        return types.length;
    }

    @Override
    public Type getElementType(long index) {
        assert index == (int) index;
        return types[(int) index];
    }

    @Override
    public int getAlignment(DataLayout targetDataLayout) {
        return isPacked ? 1 : getLargestAlignment(targetDataLayout);
    }

    @Override
    public long getSize(DataLayout targetDataLayout) throws TypeOverflowException {
        if (size != -1) {
            return size;
        }
        long sumByte = 0;
        for (final Type elementType : types) {
            if (!isPacked) {
                sumByte = addUnsignedExact(sumByte, Type.getPadding(sumByte, elementType, targetDataLayout));
            }
            sumByte = addUnsignedExact(sumByte, elementType.getSize(targetDataLayout));
        }

        long padding = 0;
        if (!isPacked && sumByte != 0) {
            padding = Type.getPadding(sumByte, getAlignment(targetDataLayout));
        }
        size = Math.addExact(sumByte, padding);
        return size;
    }

    @Override
    public long getOffsetOf(long index, DataLayout targetDataLayout) throws TypeOverflowException {
        long offset = 0;
        for (int i = 0; i < index; i++) {
            final Type elementType = types[i];
            if (!isPacked) {
                offset = addUnsignedExact(offset, Type.getPadding(offset, elementType, targetDataLayout));
            }
            offset = addUnsignedExact(offset, elementType.getSize(targetDataLayout));
        }
        if (!isPacked && getSize(targetDataLayout) > offset) {
            assert index == (int) index;
            offset = Math.addExact(offset, Type.getPadding(offset, types[(int) index], targetDataLayout));
        }
        return offset;
    }

    private int getLargestAlignment(DataLayout targetDataLayout) {
        int largestAlignment = 0;
        for (final Type elementType : types) {
            largestAlignment = Math.max(largestAlignment, elementType.getAlignment(targetDataLayout));
        }
        return largestAlignment;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        if (!isNamed()) {
            return Arrays.stream(types).map(String::valueOf).collect(Collectors.joining(", ", "%{", "}"));
        } else {
            return name;
        }
    }

    @Override
    @TruffleBoundary
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isPacked ? 1231 : 1237);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + Arrays.hashCode(types);
        return result;
    }

    @Override
    @TruffleBoundary
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
        return Arrays.equals(types, other.types);
    }

}
