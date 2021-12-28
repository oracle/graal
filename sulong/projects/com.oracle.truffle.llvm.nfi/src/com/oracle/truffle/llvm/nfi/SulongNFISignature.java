/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nfi;

import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureBuilderLibrary;
import com.oracle.truffle.nfi.backend.spi.NFIBackendSignatureLibrary;

@ExportLibrary(value = NFIBackendSignatureLibrary.class, useForAOT = false)
@SuppressWarnings("static-method")
final class SulongNFISignature {

    static final SignatureBuilder BUILDER = new SignatureBuilder();

    @ExportMessage(limit = "1")
    @GenerateAOT.Exclude
    static Object call(@SuppressWarnings("unused") SulongNFISignature self, Object function, Object[] args,
                    @CachedLibrary("function") InteropLibrary interop) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        return interop.execute(function, args);
    }

    @ExportMessage
    Object createClosure(Object executable) {
        return executable;
    }

    @ExportLibrary(value = NFIBackendSignatureBuilderLibrary.class, useForAOT = false)
    static final class SignatureBuilder {

        @ExportMessage
        void setReturnType(@SuppressWarnings("unused") Object type) {
            // no need for type information, we already have it in bitcode
        }

        @ExportMessage
        void addArgument(@SuppressWarnings("unused") Object type) {
            // no need for type information, we already have it in bitcode
        }

        @ExportMessage
        Object build() {
            return new SulongNFISignature();
        }
    }
}
