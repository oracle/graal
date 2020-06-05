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
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.types.visitors.TypeVisitor;

public final class PointerType extends AggregateType {
    public static final PointerType I8 = new PointerType(PrimitiveType.I8);
    public static final PointerType VOID = new PointerType(VoidType.INSTANCE);

    private Type pointeeType;

    public PointerType(Type pointeeType) {
        this.pointeeType = pointeeType;
    }

    public Type getPointeeType() {
        CompilerAsserts.neverPartOfCompilation();
        return pointeeType;
    }

    public void setPointeeType(Type type) {
        CompilerAsserts.neverPartOfCompilation();
        verifyCycleFree(type);
        this.pointeeType = type;
    }

    @Override
    public int getAlignment(DataLayout targetDataLayout) {
        if (targetDataLayout != null) {
            return targetDataLayout.getBitAlignment(this) / Byte.SIZE;
        } else {
            return Long.BYTES;
        }
    }

    @Override
    public long getSize(DataLayout targetDataLayout) {
        return LLVMNode.ADDRESS_SIZE_IN_BYTES;
    }

    @Override
    public long getOffsetOf(long index, DataLayout targetDataLayout) throws TypeOverflowException {
        // For a pointer, the index can be negative
        return multiplySignedExact(getPointeeType().getSize(targetDataLayout), index);
    }

    @Override
    public Type getElementType(long index) {
        return getPointeeType();
    }

    @Override
    public long getNumberOfElements() {
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException();
    }

    @Override
    public long getBitSize() {
        return Long.SIZE;
    }

    @Override
    public void accept(TypeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return String.format("%s*", getPointeeType());
    }

    @Override
    public int hashCode() {
        return PointerType.class.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PointerType;
    }
}
