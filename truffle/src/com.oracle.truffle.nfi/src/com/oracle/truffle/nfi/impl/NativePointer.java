/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.nfi.impl.LibFFIType.Direction;
import com.oracle.truffle.nfi.spi.NativeSymbolLibrary;
import com.oracle.truffle.nfi.spi.types.NativeSignature;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(SerializeArgumentLibrary.class)
@ExportLibrary(NativeSymbolLibrary.class)
@SuppressWarnings("unused")
class NativePointer implements TruffleObject {

    final long nativePointer;

    static Object create(NFILanguageImpl language, long nativePointer) {
        return language.getTools().createBindableSymbol(new NativePointer(nativePointer));
    }

    static Object createBound(NFILanguageImpl language, long nativePointer, LibFFISignature signature) {
        return language.getTools().createBoundSymbol(new NativePointer(nativePointer), signature);
    }

    NativePointer(long nativePointer) {
        this.nativePointer = nativePointer;
    }

    @Override
    public String toString() {
        return String.valueOf(nativePointer);
    }

    @ExportMessage
    boolean isPointer() {
        return true;
    }

    @ExportMessage
    long asPointer() {
        return nativePointer;
    }

    @ExportMessage
    boolean isNull() {
        return nativePointer == 0;
    }

    @ExportMessage
    void putPointer(NativeArgumentBuffer buffer, int ptrSize) {
        buffer.putPointer(nativePointer, ptrSize);
    }

    @ExportMessage
    boolean isBindable() {
        return nativePointer != 0;
    }

    @ExportMessage
    @TruffleBoundary
    Object prepareSignature(NativeSignature signature,
                    @CachedContext(NFILanguageImpl.class) NFIContext ctx) {
        LibFFISignature ret = LibFFISignature.create(ctx, signature);
        if (ret.getAllowedCallDirection() == Direction.NATIVE_TO_JAVA_ONLY) {
            throw new IllegalArgumentException("signature is only valid for native to Java callbacks");
        }
        return ret;
    }

    @ExportMessage
    Object call(Object signature, Object[] args,
                    @Cached FunctionExecuteNode execute) throws ArityException, UnsupportedTypeException {
        if (!(signature instanceof LibFFISignature)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.create(new Object[]{signature});
        }

        return execute.execute(this, (LibFFISignature) signature, args);
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return NFILanguageImpl.class;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "NativePointer(" + nativePointer + ")";
    }
}
