/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ValueType
@ExportLibrary(InteropLibrary.class)
public final class LLVMNegatedForeignObject extends LLVMInternalTruffleObject {

    final Object foreign;

    public static Object negate(Object obj) {
        if (obj instanceof LLVMNegatedForeignObject) {
            return ((LLVMNegatedForeignObject) obj).getForeign();
        } else {
            return new LLVMNegatedForeignObject(obj);
        }
    }

    private LLVMNegatedForeignObject(Object foreign) {
        this.foreign = foreign;
    }

    public Object getForeign() {
        return foreign;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LLVMNegatedForeignObject) {
            LLVMNegatedForeignObject other = (LLVMNegatedForeignObject) obj;
            return foreign.equals(other.foreign);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return -foreign.hashCode();
    }

    @ExportMessage
    boolean isNull(@CachedLibrary("this.foreign") InteropLibrary interop) {
        return interop.isNull(getForeign());
    }

    @ExportMessage
    boolean isPointer(@CachedLibrary("this.foreign") InteropLibrary interop) {
        return interop.isPointer(getForeign());
    }

    @ExportMessage
    long asPointer(@CachedLibrary("this.foreign") InteropLibrary interop) throws UnsupportedMessageException {
        return -interop.asPointer(getForeign());
    }

    @ExportMessage
    void toNative(@CachedLibrary("this.foreign") InteropLibrary interop) {
        interop.toNative(getForeign());
    }

}
