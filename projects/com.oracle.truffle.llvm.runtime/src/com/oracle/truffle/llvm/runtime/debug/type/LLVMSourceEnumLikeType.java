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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class LLVMSourceEnumLikeType extends LLVMSourceType {

    private final Map<Long, String> values;

    @TruffleBoundary
    public LLVMSourceEnumLikeType(Supplier<String> nameSupplier, long size, long align, long offset, final LLVMSourceLocation location) {
        this(nameSupplier, size, align, offset, new HashMap<>(), location);
    }

    private LLVMSourceEnumLikeType(Supplier<String> nameSupplier, long size, long align, long offset, Map<Long, String> values, LLVMSourceLocation location) {
        super(nameSupplier, size, align, offset, location);
        this.values = values;
    }

    public void addValue(long id, String representation) {
        CompilerAsserts.neverPartOfCompilation();
        values.put(id, representation);
    }

    @Override
    @TruffleBoundary
    public String getElementName(long i) {
        return values.get(i);
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        return new LLVMSourceEnumLikeType(this::getName, getSize(), getAlign(), getOffset(), values, getLocation());
    }

    @Override
    public boolean isEnum() {
        return true;
    }
}
