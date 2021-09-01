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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

/**
 * Implements the deprecated symbol.bind(signature).
 *
 * Users should migrate to signature.bind(symbol), to separate parsing the signature from the actual
 * bind call, and improve code sharing.
 */
@GenerateUncached
@ImportStatic(NFILanguage.class)
abstract class BindSignatureNode extends Node {

    abstract Object execute(NFISymbol symbol, Object signature) throws UnsupportedMessageException, UnsupportedTypeException;

    static String asString(InteropLibrary interop, Object signature) throws UnsupportedTypeException {
        try {
            return interop.asString(signature);
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedTypeException.create(new Object[]{signature});
        }
    }

    @TruffleBoundary
    static Source createSignatureSource(String backend, Object signature) throws UnsupportedTypeException {
        String sigString = asString(InteropLibrary.getUncached(), signature);
        return Source.newBuilder("nfi", String.format("with %s %s", backend, sigString), "bind").build();
    }

    @TruffleBoundary
    static NFISignature parseSignature(String backend, Object signature) throws UnsupportedTypeException {
        Source source = createSignatureSource(backend, signature);
        CallTarget ct = NFIContext.get(null).env.parseInternal(source);
        return (NFISignature) ct.call();
    }

    @Specialization(limit = "5", guards = {"symbol.backend == cachedBackend", "signature == cachedSignature"}, assumptions = "getSingleContextAssumption()")
    @SuppressWarnings("unused")
    static Object doCachedSignature(NFISymbol symbol, Object signature,
                    @Cached("symbol.backend") String cachedBackend,
                    @Cached("signature") Object cachedSignature,
                    @Cached("parseSignature(cachedBackend, cachedSignature)") NFISignature parsedSignature) {
        return NFISymbol.createBound(symbol.backend, symbol.nativeSymbol, parsedSignature);
    }

    @Specialization(limit = "5", guards = "cachedSignature.equals(asString(interop, signature))", replaces = "doCachedSignature")
    @SuppressWarnings("unused")
    Object doCachedSignatureString(NFISymbol symbol, Object signature,
                    @CachedLibrary("signature") InteropLibrary interop,
                    @Cached("asString(interop, signature)") String cachedSignature,
                    @Cached("createSignatureSource(symbol.backend, cachedSignature)") Source signatureSource,
                    @Cached IndirectCallNode call) {
        CallTarget parsedSignature = NFIContext.get(this).env.parseInternal(signatureSource);
        return NFISymbol.createBound(symbol.backend, symbol.nativeSymbol, (NFISignature) call.call(parsedSignature));
    }

    @Specialization(replaces = {"doCachedSignature", "doCachedSignatureString"})
    static Object doGeneric(NFISymbol symbol, Object signature) throws UnsupportedTypeException {
        return NFISymbol.createBound(symbol.backend, symbol.nativeSymbol, parseSignature(symbol.backend, signature));
    }

}
