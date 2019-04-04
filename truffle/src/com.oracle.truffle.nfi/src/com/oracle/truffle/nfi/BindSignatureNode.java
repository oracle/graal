/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.spi.NativeSymbolLibrary;
import com.oracle.truffle.nfi.spi.types.NativeSignature;

@GenerateUncached
abstract class BindSignatureNode extends Node {

    abstract Object execute(Object symbol, Object signature) throws UnsupportedMessageException, UnsupportedTypeException;

    static String asString(InteropLibrary interop, Object signature) throws UnsupportedTypeException {
        try {
            return interop.asString(signature);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.create(new Object[]{signature});
        }
    }

    static Object parseSignature(Object symbol, Object signature, InteropLibrary interop, NativeSymbolLibrary symbolLibrary) throws UnsupportedMessageException, UnsupportedTypeException {
        String sigString = asString(interop, signature);
        NativeSignature parsed = parseSignature(sigString);
        return symbolLibrary.prepareSignature(symbol, parsed);
    }

    @TruffleBoundary
    static NativeSignature parseSignature(String signature) {
        return Parser.parseSignature(signature);
    }

    @Specialization(limit = "5", guards = "signature == cachedSignature")
    @SuppressWarnings("unused")
    static Object doCachedSignature(Object symbol, Object signature,
                    @Cached("signature") Object cachedSignature,
                    @CachedLibrary("cachedSignature") InteropLibrary interop,
                    @CachedLibrary("symbol") NativeSymbolLibrary symbolLibrary,
                    @Cached("parseSignature(symbol, cachedSignature, interop, symbolLibrary)") Object parsedSignature) {
        return NFISymbol.createBound(symbol, parsedSignature);
    }

    @Specialization(limit = "5", guards = "cachedSignature.equals(asString(interop, signature))", replaces = "doCachedSignature")
    @SuppressWarnings("unused")
    static Object doCachedSignatureString(Object symbol, Object signature,
                    @CachedLibrary("signature") InteropLibrary interop,
                    @Cached("asString(interop, signature)") String cachedSignature,
                    @CachedLibrary("symbol") NativeSymbolLibrary symbolLibrary,
                    @Cached("parseSignature(symbol, signature, interop, symbolLibrary)") Object parsedSignature) {
        return NFISymbol.createBound(symbol, parsedSignature);
    }

    @Specialization(limit = "3", replaces = {"doCachedSignature", "doCachedSignatureString"})
    static Object doGeneric(Object symbol, Object signature,
                    @CachedLibrary("signature") InteropLibrary interop,
                    @CachedLibrary("symbol") NativeSymbolLibrary symbolLibrary) throws UnsupportedMessageException, UnsupportedTypeException {
        return NFISymbol.createBound(symbol, parseSignature(symbol, signature, interop, symbolLibrary));
    }

}
