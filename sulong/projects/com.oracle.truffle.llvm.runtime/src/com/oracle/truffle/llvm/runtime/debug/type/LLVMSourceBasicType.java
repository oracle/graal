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

import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

public final class LLVMSourceBasicType extends LLVMSourceType {

    private final Kind kind;

    public LLVMSourceBasicType(String name, long size, long align, long offset, Kind kind, LLVMSourceLocation location) {
        super(() -> name, size, align, offset, location);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public LLVMSourceType getOffset(long newOffset) {
        return new LLVMSourceBasicType(getName(), getSize(), getAlign(), newOffset, kind, getLocation());
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean isAggregate() {
        return false;
    }

    @Override
    public int getElementCount() {
        return 0;
    }

    @Override
    public String getElementName(long i) {
        return null;
    }

    @Override
    public LLVMSourceType getElementType(long i) {
        return null;
    }

    @Override
    public LLVMSourceType getElementType(String name) {
        return null;
    }

    public enum Kind {
        UNKNOWN,
        ADDRESS,
        BOOLEAN,
        FLOATING,
        SIGNED,
        SIGNED_CHAR,
        UNSIGNED,
        UNSIGNED_CHAR;
    }
}
