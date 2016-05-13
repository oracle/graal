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
package com.oracle.truffle.llvm.runtime;

public final class LLVMUnsupportedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum UnsupportedReason {
        OTHER_TYPE_NOT_IMPLEMENTED,
        /**
         * We cannot let Truffle LLVM function pointeres escape to native functions.
         */
        FUNCTION_POINTER_ESCAPES_TO_NATIVE,
        /**
         * Inline assembler calls.
         */
        INLINE_ASSEMBLER,
        /**
         * "@llvm.va_start" and other intrinsic.
         */
        VA_COPY,
        /**
         * Clang fails to produce the correct IR.
         */
        CLANG_ERROR,
        /**
         * Vector cast.
         */
        VECTOR_CAST,
        /**
         * setjmp and longjmp intrinsic.
         */
        SET_JMP_LONG_JMP,
        FLOAT_OTHER_TYPE_NOT_IMPLEMENTED,
        CONSTANT_EXPRESSION,
        PARSER_ERROR_VOID_SLOT,
        MULTITHREADING,
        VOID_NOT_VOID_FUNCTION_CALL_MISMATCH,
    }

    private final UnsupportedReason reason;

    public LLVMUnsupportedException(UnsupportedReason reason) {
        this.reason = reason;
    }

    public UnsupportedReason getReason() {
        return reason;
    }

}
