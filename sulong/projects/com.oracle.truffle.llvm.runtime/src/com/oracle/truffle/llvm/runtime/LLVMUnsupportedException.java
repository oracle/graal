/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.except.LLVMException;

public final class LLVMUnsupportedException extends LLVMException {

    private static final long serialVersionUID = 1L;

    public enum UnsupportedReason {
        /**
         * Inline assembler calls.
         */
        INLINE_ASSEMBLER("inline assembler"),
        /**
         * setjmp and longjmp intrinsic.
         */
        SET_JMP_LONG_JMP("setjmp/longjmp"),
        PARSER_ERROR_VOID_SLOT("parser error void slot"),
        UNSUPPORTED_SYSCALL("unsupported syscall"),
        /**
         * Indicates that a value is valid in terms of LLVM language spec, but it is unsupported by
         * this implementation.
         */
        UNSUPPORTED_VALUE_RANGE("unsupported value range");

        private final String description;

        UnsupportedReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public LLVMUnsupportedException(Node location, UnsupportedReason reason) {
        super(location, reason.getDescription());
    }

    public LLVMUnsupportedException(Node location, UnsupportedReason reason, String details) {
        super(location, reason.getDescription() + ": " + details);
    }

    public LLVMUnsupportedException(Node location, UnsupportedReason reason, Throwable cause) {
        super(location, reason.getDescription() + ": " + cause.getMessage(), cause);
    }

}
