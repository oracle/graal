/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.util.function.Function;

public class LLVMSourceForeignType extends LLVMSourceDecoratorType {

    public static final String VALUE_KEY = "Unindexed Interop Value";
    public static final String[] KEYS = new String[]{VALUE_KEY};

    public LLVMSourceForeignType(LLVMSourceType wrappedType) {
        super(0, 0, 0, Function.identity(), wrappedType.getLocation());
        setBaseType(wrappedType);
    }

    @Override
    public int getElementCount() {
        return KEYS.length;
    }

    @Override
    @TruffleBoundary
    public String getElementName(long i) {
        if (0 <= i && i < getElementCount()) {
            return IndexedTypeBounds.toKey(i);
        }
        return null;
    }

    @Override
    public LLVMSourceType getElementType(long i) {
        if (0 <= i && i < getElementCount()) {
            return getBaseType();
        }
        return null;
    }

    @Override
    @TruffleBoundary
    public LLVMSourceType getElementType(String key) {
        return getElementType(IndexedTypeBounds.toIndex(key));
    }

    @Override
    public LLVMSourceLocation getElementDeclaration(long i) {
        return getLocation();
    }

    @Override
    public LLVMSourceLocation getElementDeclaration(String name) {
        return getLocation();
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        return this;
    }
}
