/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.nfi.spi.NativeSymbolLibrary;
import com.oracle.truffle.nfi.spi.types.NativeSignature;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeSymbolLibrary.class)
final class SulongNFIFunction implements TruffleObject {

    final Object function;

    SulongNFIFunction(Object function) {
        this.function = function;
    }

    @ExportMessage
    boolean isNull(@CachedLibrary("this.function") InteropLibrary interop) {
        return interop.isNull(function);
    }

    @ExportMessage
    boolean isPointer(@CachedLibrary("this.function") InteropLibrary interop) {
        return interop.isPointer(function);
    }

    @ExportMessage
    long asPointer(@CachedLibrary("this.function") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asPointer(function);
    }

    @ExportMessage
    void toNative(@CachedLibrary("this.function") InteropLibrary interop) {
        interop.toNative(function);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBindable() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object prepareSignature(@SuppressWarnings("unused") NativeSignature signature) {
        // for now, Sulong ignores the signature
        return null;
    }

    @ExportMessage
    Object call(@SuppressWarnings("unused") Object signature, Object[] args,
                    @CachedLibrary("this.function") InteropLibrary interop) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        return interop.execute(function, args);
    }
}
