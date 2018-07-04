/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.type;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.util.function.Supplier;

public final class LLVMSourcePointerType extends LLVMSourceType {

    private final boolean isReference;

    private final boolean isSafeToDereference;

    @CompilationFinal private LLVMSourceType baseType;

    public LLVMSourcePointerType(long size, long align, long offset, boolean isSafeToDereference, boolean isReference, LLVMSourceLocation location) {
        this(LLVMSourceType.UNKNOWN::getName, size, align, offset, LLVMSourceType.UNKNOWN, isSafeToDereference, isReference, location);
    }

    private LLVMSourcePointerType(Supplier<String> nameSupplier, long size, long align, long offset, LLVMSourceType baseType, boolean isSafeToDereference, boolean isReference,
                    LLVMSourceLocation location) {
        super(nameSupplier, size, align, offset, location);
        this.baseType = baseType;
        this.isSafeToDereference = isSafeToDereference | isReference;
        this.isReference = isReference;
    }

    @Override
    public boolean isReference() {
        // references, in contrast to pointers that are known to be safe to dereference, should be
        // displayed as values of the basetype to users
        return isReference;
    }

    public boolean isSafeToDereference() {
        return isSafeToDereference;
    }

    public LLVMSourceType getBaseType() {
        return baseType;
    }

    public void setBaseType(LLVMSourceType baseType) {
        CompilerAsserts.neverPartOfCompilation();
        this.baseType = baseType;
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        return new LLVMSourcePointerType(this::getName, getSize(), getAlign(), newOffset, baseType, isSafeToDereference, isReference, getLocation());
    }

    @Override
    public boolean isPointer() {
        return true;
    }

    @Override
    public boolean isAggregate() {
        return false;
    }

    @Override
    public int getElementCount() {
        return 1;
    }

    @Override
    public String getElementName(long i) {
        return getBaseType().getElementName(i);
    }

    @Override
    public LLVMSourceType getElementType(long i) {
        return getBaseType().getElementType(i);
    }

    @Override
    public LLVMSourceType getElementType(String name) {
        return getBaseType().getElementType(name);
    }
}
