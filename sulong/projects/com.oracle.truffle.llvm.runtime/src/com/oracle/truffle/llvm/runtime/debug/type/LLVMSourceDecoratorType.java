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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.util.function.Function;
import java.util.function.Supplier;

public class LLVMSourceDecoratorType extends LLVMSourceType {

    private final Function<String, String> nameDecorator;

    @CompilationFinal private LLVMSourceType baseType;

    @CompilationFinal private long size;

    public LLVMSourceDecoratorType(long size, long align, long offset, Function<String, String> nameDecorator, LLVMSourceLocation location) {
        super(size, align, offset, location);
        this.nameDecorator = nameDecorator;
        this.baseType = LLVMSourceType.UNKNOWN;
        this.size = size;
    }

    private LLVMSourceDecoratorType(Supplier<String> nameSupplier, long size, long align, long offset, LLVMSourceType baseType, Function<String, String> nameDecorator, LLVMSourceLocation location) {
        super(nameSupplier, size, align, offset, location);
        this.baseType = baseType;
        this.nameDecorator = nameDecorator;
        this.size = size;
    }

    public void setBaseType(LLVMSourceType baseType) {
        CompilerAsserts.neverPartOfCompilation();
        this.baseType = baseType;
    }

    public LLVMSourceType getBaseType() {
        return baseType;
    }

    @Override
    public LLVMSourceType getActualType() {
        return baseType.getActualType();
    }

    @Override
    @TruffleBoundary
    public String getName() {
        return nameDecorator.apply(baseType.getName());
    }

    @Override
    public void setName(Supplier<String> nameSupplier) {
        CompilerAsserts.neverPartOfCompilation();
        baseType.setName(nameSupplier);
    }

    public void setSize(long size) {
        CompilerAsserts.neverPartOfCompilation();
        this.size = size;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getAlign() {
        return baseType.getAlign();
    }

    @Override
    public long getOffset() {
        return baseType.getOffset();
    }

    @Override
    public boolean isPointer() {
        return baseType.isPointer();
    }

    @Override
    public boolean isReference() {
        return baseType.isReference();
    }

    @Override
    public boolean isAggregate() {
        return baseType.isAggregate();
    }

    @Override
    public boolean isEnum() {
        return baseType.isEnum();
    }

    @Override
    public int getElementCount() {
        return baseType.getElementCount();
    }

    @Override
    public String getElementName(long i) {
        return baseType.getElementName(i);
    }

    @Override
    public LLVMSourceType getElementType(long i) {
        return baseType.getElementType(i);
    }

    @Override
    public LLVMSourceType getElementType(String name) {
        return baseType.getElementType(name);
    }

    @Override
    public LLVMSourceLocation getElementDeclaration(long i) {
        return baseType.getElementDeclaration(i);
    }

    @Override
    public LLVMSourceLocation getElementDeclaration(String name) {
        return baseType.getElementDeclaration(name);
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        final LLVMSourceType offsetBaseType = baseType.getOffset(newOffset);
        return new LLVMSourceDecoratorType(this::getName, getSize(), getAlign(), getOffset(), offsetBaseType, nameDecorator, getLocation());
    }
}
