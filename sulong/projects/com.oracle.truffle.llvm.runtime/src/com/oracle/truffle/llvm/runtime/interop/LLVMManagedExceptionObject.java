/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(LLVMManagedReadLibrary.class)
public class LLVMManagedExceptionObject extends LLVMInternalTruffleObject {
    private final Object exceptionObject;
    /*
     * 0x504c594754455843 = (char-encoded) PLYGTEXC = polyglot exception. Denotes that the exception
     * has not been thrown by LLVM itself, but via foreign language and the polyglot interop API.
     * See com.oracle.truffle.llvm.libraries.bitcode/libsulongxx/exception_support.cpp
     */
    public static final long magicExceptionNumber = 0x504c594754455843L;

    public LLVMManagedExceptionObject(Object exceptionObject) {
        this.exceptionObject = exceptionObject;
    }

    public Object getExceptionObject() {
        return exceptionObject;
    }

    @ExportMessage
    public LLVMPointer readPointer(long offset) {
        if (offset == 8) {
            return LLVMManagedPointer.create(exceptionObject);
        }
        return LLVMNativePointer.createNull();
    }

    @ExportMessage
    final Object readGenericI64(long offset) {
        if (offset == 0) {
            return magicExceptionNumber;
        } else if (offset == 8) {
            return LLVMManagedPointer.create(exceptionObject);
        } else {
            return 0L;
        }
    }

    @ExportMessage
    public int readI32(long offset) {
        return 0;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean isReadable() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final byte readI8(long offset) {
        return (byte) 0;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final short readI16(long offset) {
        return (short) 0;
    }

}
