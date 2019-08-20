/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class VectorType extends AggregateType {

    @CompilationFinal private Assumption elementTypeAssumption;
    @CompilationFinal private Type elementType;
    private final int length;

    public VectorType(Type elementType, int length) {
        if (elementType != null && !(elementType instanceof PrimitiveType || elementType instanceof PointerType)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AssertionError("Invalid ElementType of Vector: " + elementType);
        }
        this.elementTypeAssumption = Truffle.getRuntime().createAssumption("VectorType.elementType");
        this.elementType = elementType;
        this.length = length;
    }

    public Type getElementType() {
        if (!elementTypeAssumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
        }
        return elementType;
    }

    @Override
    public int getBitSize() {
        return getElementType().getBitSize() * length;
    }

    @Override
    public int getNumberOfElements() {
        return length;
    }

    public void setElementType(Type elementType) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (elementType == null || !(elementType instanceof PrimitiveType || elementType instanceof PointerType)) {
            throw new AssertionError("Invalid ElementType of Vector: " + elementType);
        }
        this.elementTypeAssumption.invalidate();
        this.elementType = elementType;
        this.elementTypeAssumption = Truffle.getRuntime().createAssumption("VectorType.elementType");
    }

    @Override
    public Type getElementType(long index) {
        if (index >= length) {
            CompilerDirectives.transferToInterpreter();
            throw new ArrayIndexOutOfBoundsException();
        }
        return getElementType();
    }

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public int getAlignment(DataLayout targetDataLayout) {
        return getElementType().getAlignment(targetDataLayout);
    }

    @Override
    public int getSize(DataLayout targetDataLayout) {
        return getElementType().getSize(targetDataLayout) * length;
    }

    @Override
    public long getOffsetOf(long index, DataLayout targetDataLayout) {
        return getElementType().getSize(targetDataLayout) * index;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.format("< %d x %s >", getNumberOfElements(), getElementType());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getElementType() == null) ? 0 : getElementType().hashCode());
        result = prime * result + length;
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
        VectorType other = (VectorType) obj;
        if (getElementType() == null) {
            if (other.getElementType() != null) {
                return false;
            }
        } else if (!getElementType().equals(other.getElementType())) {
            return false;
        }
        if (length != other.length) {
            return false;
        }
        return true;
    }
}
