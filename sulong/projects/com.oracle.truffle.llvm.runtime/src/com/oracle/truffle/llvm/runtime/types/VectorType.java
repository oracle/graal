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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class VectorType extends AggregateType {

    private Type elementType;
    /**
     * Length of the vector. The value is interpreted as an unsigned 32 bit integer value.
     */
    private final int length;

    public VectorType(Type elementType, int length) {
        if (elementType != null && !(elementType instanceof PrimitiveType || elementType instanceof PointerType)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new AssertionError("Invalid ElementType of Vector: " + elementType.getClass().getSimpleName());
        }
        this.elementType = elementType;
        this.length = length;
    }

    public Type getElementType() {
        CompilerAsserts.neverPartOfCompilation();
        return elementType;
    }

    @Override
    public long getBitSize() throws TypeOverflowException {
        return multiplyUnsignedExact(getElementType().getBitSize(), length);
    }

    @Override
    public long getNumberOfElements() {
        return Integer.toUnsignedLong(length);
    }

    public int getNumberOfElementsInt() {
        return length;
    }

    public void setElementType(Type elementType) {
        CompilerAsserts.neverPartOfCompilation();
        if (!(elementType instanceof PrimitiveType || elementType instanceof PointerType)) {
            throw new AssertionError("Invalid ElementType of Vector: " + (elementType == null ? "null" : elementType.getClass().getSimpleName()));
        }
        verifyCycleFree(elementType);
        this.elementType = elementType;
    }

    @Override
    public Type getElementType(long index) {
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
    public long getSize(DataLayout targetDataLayout) throws TypeOverflowException {
        return multiplyUnsignedExact(getElementType().getSize(targetDataLayout), Integer.toUnsignedLong(length));
    }

    @Override
    public long getOffsetOf(long index, DataLayout targetDataLayout) throws TypeOverflowException {
        return multiplyUnsignedExact(getElementType().getSize(targetDataLayout), index);
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
