/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.nfi.CallSignatureNode.CachedCallSignatureNode;
import com.oracle.truffle.nfi.api.NativePointerLibrary;
import com.oracle.truffle.nfi.backend.spi.BackendNativePointerLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativePointerLibrary.class, useForAOT = true, useForAOTPriority = 1)
final class NFISymbol implements TruffleObject {

    static Object createBound(Object nativeSymbol, NFISignature signature) {
        return new NFISymbol(nativeSymbol, signature);
    }

    final Object nativeSymbol;
    final NFISignature signature;

    private NFISymbol(Object nativeSymbol, NFISignature signature) {
        assert signature != null;
        this.nativeSymbol = nativeSymbol;
        this.signature = signature;
    }

    // executing

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] args,
                    @Cached CachedCallSignatureNode call) throws ArityException, UnsupportedTypeException, UnsupportedMessageException {
        return call.execute(signature, nativeSymbol, args);
    }

    // reexports

    @ExportMessage
    boolean isNull(@CachedLibrary("this.nativeSymbol") InteropLibrary library) {
        return library.isNull(nativeSymbol);
    }

    @ExportMessage(name = "isPointer", library = InteropLibrary.class)
    boolean isPointerInterop(@CachedLibrary("this.nativeSymbol") InteropLibrary library) {
        return library.isPointer(nativeSymbol);
    }

    @ExportMessage(name = "isPointer", library = NativePointerLibrary.class)
    boolean isPointerNFI(@CachedLibrary(limit = "1") BackendNativePointerLibrary library) {
        return library.isPointer(nativeSymbol);
    }

    @ExportMessage(name = "asPointer", library = InteropLibrary.class)
    long asPointerInterop(@CachedLibrary("this.nativeSymbol") InteropLibrary library) throws UnsupportedMessageException {
        return library.asPointer(nativeSymbol);
    }

    @ExportMessage(name = "asPointer", library = NativePointerLibrary.class)
    long asPointerNFI(@CachedLibrary(limit = "1") BackendNativePointerLibrary library) throws UnsupportedMessageException {
        return library.asPointer(nativeSymbol);
    }

    @ExportMessage
    void toNative(@CachedLibrary("this.nativeSymbol") InteropLibrary library) {
        library.toNative(nativeSymbol);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return NFILanguage.class;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Native Symbol";
    }

}
