/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.interop.LLVMAddressMessageResolutionForeign;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;

@ValueType
public final class LLVMAddress implements LLVMObjectNativeLibrary.Provider, TruffleObject {

    public static final int WORD_LENGTH_BIT = 64;

    private final long val;

    private LLVMAddress(long val) {
        this.val = val;
    }

    public static LLVMAddress nullPointer() {
        return new LLVMAddress(0);
    }

    public static LLVMAddress fromLong(long val) {
        return new LLVMAddress(val);
    }

    public long getVal() {
        return val;
    }

    public LLVMAddress increment(long incr) {
        return new LLVMAddress(val + incr);
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
        return unsignedLessThan(val2.val);
    }

    private boolean unsignedLessThan(long val2) {
        return Long.compareUnsigned(val, val2) < 0;
    }

    public boolean unsignedGreaterEquals(LLVMAddress val2) {
        return Long.compareUnsigned(val, val2.val) >= 0;
    }

    public boolean unsignedLessEquals(LLVMAddress val2) {
        return Long.compareUnsigned(val, val2.val) <= 0;
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

    @Override
    public LLVMObjectNativeLibrary createLLVMObjectNativeLibrary() {
        return new LLVMAddressNativeLibrary();
    }

    private static class LLVMAddressNativeLibrary extends LLVMObjectNativeLibrary {

        @Override
        public boolean guard(Object obj) {
            return obj instanceof LLVMAddress;
        }

        @Override
        public boolean isPointer(VirtualFrame frame, Object obj) {
            return true;
        }

        @Override
        public long asPointer(VirtualFrame frame, Object obj) throws InteropException {
            return ((LLVMAddress) obj).getVal();
        }

        @Override
        public LLVMAddress toNative(VirtualFrame frame, Object obj) throws InteropException {
            return (LLVMAddress) obj;
        }
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof LLVMTruffleAddress;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LLVMAddressMessageResolutionForeign.ACCESS;
    }
}
