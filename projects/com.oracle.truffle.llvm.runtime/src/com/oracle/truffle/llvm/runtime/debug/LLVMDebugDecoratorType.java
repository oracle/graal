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
package com.oracle.truffle.llvm.runtime.debug;

import java.util.function.Function;
import java.util.function.Supplier;

public final class LLVMDebugDecoratorType extends LLVMDebugType {

    private Supplier<LLVMDebugType> baseType;

    private final Function<String, String> nameDecorator;

    public LLVMDebugDecoratorType(long size, long align, long offset, Function<String, String> nameDecorator) {
        super(size, align, offset);
        this.nameDecorator = nameDecorator;
        this.baseType = () -> LLVMDebugType.UNKNOWN_TYPE;
    }

    private LLVMDebugDecoratorType(Supplier<String> nameSupplier, long size, long align, long offset, Supplier<LLVMDebugType> baseType, Function<String, String> nameDecorator) {
        super(nameSupplier, size, align, offset);
        this.baseType = baseType;
        this.nameDecorator = nameDecorator;
    }

    public void setBaseType(Supplier<LLVMDebugType> baseType) {
        this.baseType = baseType;
    }

    public LLVMDebugType getTrueBaseType() {
        final LLVMDebugType resolvedBaseType = baseType.get();
        if (resolvedBaseType instanceof LLVMDebugDecoratorType) {
            return ((LLVMDebugDecoratorType) resolvedBaseType).getTrueBaseType();
        } else {
            return resolvedBaseType;
        }
    }

    @Override
    public String getName() {
        return nameDecorator.apply(baseType.get().getName());
    }

    @Override
    public void setName(Supplier<String> nameSupplier) {
        baseType.get().setName(nameSupplier);
    }

    @Override
    public long getSize() {
        return baseType.get().getSize();
    }

    @Override
    public long getAlign() {
        return baseType.get().getAlign();
    }

    @Override
    public long getOffset() {
        return baseType.get().getOffset();
    }

    @Override
    public boolean isPointer() {
        return baseType.get().isPointer();
    }

    @Override
    public boolean isAggregate() {
        return baseType.get().isAggregate();
    }

    @Override
    public boolean isEnum() {
        return baseType.get().isEnum();
    }

    @Override
    public int getElementCount() {
        return baseType.get().getElementCount();
    }

    @Override
    public String getElementName(long i) {
        return baseType.get().getElementName(i);
    }

    @Override
    public LLVMDebugType getElementType(long i) {
        return baseType.get().getElementType(i);
    }

    @Override
    public LLVMDebugType getElementType(String name) {
        return baseType.get().getElementType(name);
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public LLVMDebugType getOffset(long newOffset) {
        final LLVMDebugType offsetBaseType = baseType.get().getOffset(newOffset);
        return new LLVMDebugDecoratorType(this::getName, getSize(), getAlign(), getOffset(), () -> offsetBaseType, nameDecorator);
    }
}
