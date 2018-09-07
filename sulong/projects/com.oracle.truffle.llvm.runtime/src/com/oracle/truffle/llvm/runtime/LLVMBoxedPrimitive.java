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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectNativeLibrary;

/**
 * This object is used to wrap primitive values that enter LLVM code via interop at points where a
 * pointer is expected. Semantically this behaves similar to pointer tagging. The resulting pointer
 * can not be dereferenced, and in pointer comparisons, the same primitive value will result in the
 * same pointer value.
 */
public final class LLVMBoxedPrimitive implements LLVMObjectNativeLibrary.Provider {

    private final Object value;

    public LLVMBoxedPrimitive(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return "<boxed value: " + getValue() + ">";
    }

    @Override
    public LLVMObjectNativeLibrary createLLVMObjectNativeLibrary() {
        return new LLVMBoxedPrimitiveNativeLibrary();
    }

    private static final class LLVMBoxedPrimitiveNativeLibrary extends LLVMObjectNativeLibrary {

        @Child private ForeignToLLVM toLLVM;

        @Override
        public boolean guard(Object obj) {
            return obj instanceof LLVMBoxedPrimitive;
        }

        @Override
        public boolean isPointer(Object obj) {
            return true;
        }

        @Override
        public long asPointer(Object obj) throws InteropException {
            if (toLLVM == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toLLVM = insert(getNodeFactory().createForeignToLLVM(ForeignToLLVMType.I64));
            }
            LLVMBoxedPrimitive boxed = (LLVMBoxedPrimitive) obj;
            return (long) toLLVM.executeWithTarget(boxed.getValue());
        }

        @Override
        public LLVMBoxedPrimitive toNative(Object obj) throws InteropException {
            return (LLVMBoxedPrimitive) obj;
        }
    }
}
