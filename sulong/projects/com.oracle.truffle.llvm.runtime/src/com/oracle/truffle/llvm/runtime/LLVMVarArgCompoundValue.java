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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerDirectives.ValueType;

@ValueType
public final class LLVMVarArgCompoundValue {
    private final Object addr;
    private final long size;
    private final int alignment;

    private LLVMVarArgCompoundValue(Object val, long size, int alignment) {
        this.addr = val;
        this.size = size;
        this.alignment = alignment;
    }

    public static LLVMVarArgCompoundValue create(Object val, long size, int alignment) {
        return new LLVMVarArgCompoundValue(val, size, alignment);
    }

    public Object getAddr() {
        return addr;
    }

    public long getSize() {
        return size;
    }

    public int getAlignment() {
        return alignment;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LLVMVarArgCompoundValue) {
            LLVMVarArgCompoundValue curObj = (LLVMVarArgCompoundValue) obj;
            return curObj.addr.equals(addr) && curObj.size == size && curObj.alignment == alignment;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return addr.hashCode() + 11 * Long.hashCode(size) + 23 * Integer.hashCode(alignment);
    }

    @Override
    public String toString() {
        return String.format("%s (%d align %d)", getAddr(), getSize(), getAlignment());
    }
}
