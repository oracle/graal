/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.except;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Used for implementing try catch blocks within LLVM bitcode (e.g., when executing __cxa_throw).
 */
@ExportLibrary(value = InteropLibrary.class, delegateTo = "unwindHeader")
public class LLVMUserException extends LLVMException {

    private static final long serialVersionUID = 1L;

    // transient to shut up JDK19 warnings (this should never be serialized anyway)
    final transient LLVMPointer unwindHeader; // or throw info

    public LLVMUserException(Node location, LLVMPointer unwindHeader) {
        super(location);
        this.unwindHeader = unwindHeader;
    }

    public LLVMPointer getUnwindHeader() {
        return unwindHeader;
    }

    @Override
    public String getMessage() {
        return "LLVMException:" + unwindHeader.toString();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isException() {
        return true;
    }

    @ExportMessage
    RuntimeException throwException() {
        throw this;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    ExceptionType getExceptionType() {
        return ExceptionType.RUNTIME_ERROR;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasExceptionMessage() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    String getExceptionMessage() {
        return getMessage();
    }

    public static final class LLVMUserExceptionWindows extends LLVMUserException {

        private static final long serialVersionUID = 1L;

        final transient LLVMPointer imageBase;
        final transient LLVMPointer exceptionObject;
        final transient long stackOffset;

        public LLVMUserExceptionWindows(Node location, LLVMPointer imageBase, LLVMPointer exceptionObject, LLVMPointer throwInfo, long stackOffset) {
            super(location, throwInfo);
            this.exceptionObject = exceptionObject;
            this.imageBase = imageBase;
            this.stackOffset = stackOffset;
        }

        public LLVMPointer getThrowInfo() {
            return unwindHeader;
        }

        public LLVMPointer getImageBase() {
            return imageBase;
        }

        public LLVMPointer getExceptionObject() {
            return exceptionObject;
        }

        public long getStackPointer() {
            return stackOffset;
        }
    }
}
