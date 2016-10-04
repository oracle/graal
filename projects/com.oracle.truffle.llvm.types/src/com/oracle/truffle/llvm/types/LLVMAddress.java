/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.types;

import com.oracle.truffle.api.CompilerDirectives.ValueType;

@ValueType
public final class LLVMAddress {

    public static final int WORD_LENGTH_BIT = 64;

    public static final LLVMAddress NULL_POINTER = fromLong(0);

    private final long val;

    private LLVMAddress(long val) {
        this.val = val;
    }

    public static LLVMAddress fromLong(long val) {
        return new LLVMAddress(val);
    }

    public long getVal() {
        return val;
    }

    public LLVMAddress increment(int incr) {
        return this.increment((long) incr);
    }

    public LLVMAddress increment(long incr) {
        return new LLVMAddress(val + incr);
    }

    public LLVMAddress decrement(int decr) {
        return new LLVMAddress(val - decr);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof LLVMAddress) && ((LLVMAddress) obj).val == val;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(val);
    }

    public boolean unsignedGreaterThan(LLVMAddress val2) {
        return Long.compareUnsigned(val, val2.val) > 0;
    }

    public boolean unsignedLessThan(LLVMAddress val2) {
        return Long.compareUnsigned(val, val2.val) < 0;
    }

    public boolean unsignedGreaterEquals(LLVMAddress val2) {
        return Long.compareUnsigned(val, val2.val) >= 0;
    }

    public boolean unsignedLessEquals(LLVMAddress val2) {
        return Long.compareUnsigned(val, val2.val) <= 0;
    }

    public static LLVMAddress createUndefinedAddress() {
        return new LLVMAddress(-1);
    }

    @Override
    public String toString() {
        return String.format("0x%x", getVal());
    }

    public boolean signedLessThan(LLVMAddress val2) {
        return val < val2.getVal();
    }

    public boolean signedLessEquals(LLVMAddress val2) {
        return val < val2.getVal();
    }

    public boolean signedGreaterThan(LLVMAddress val2) {
        return val > val2.getVal();
    }

    public boolean signedGreaterEquals(LLVMAddress val2) {
        return val > val2.getVal();
    }

    public LLVMAddress copy() {
        return new LLVMAddress(val);
    }

}
