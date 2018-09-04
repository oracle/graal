/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.impl.TypeConversion.AsStringNode;
import com.oracle.truffle.nfi.impl.LibFFILibraryMessageResolutionFactory.CachedLookupSymbolNodeGen;
import com.oracle.truffle.nfi.impl.TypeConversionFactory.AsStringNodeGen;

@MessageResolution(receiverType = LibFFILibrary.class)
class LibFFILibraryMessageResolution {

    abstract static class CachedLookupSymbolNode extends Node {

        private final ContextReference<NFIContext> ctxRef = NFILanguageImpl.getCurrentContextReference();

        protected abstract TruffleObject executeLookup(LibFFILibrary receiver, String symbol);

        @Specialization(guards = {"receiver == cachedReceiver", "symbol.equals(cachedSymbol)"})
        @SuppressWarnings("unused")
        protected TruffleObject lookupCached(LibFFILibrary receiver, String symbol,
                        @Cached("receiver") LibFFILibrary cachedReceiver,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("lookup(cachedReceiver, cachedSymbol)") TruffleObject cachedRet) {
            return cachedRet;
        }

        @Specialization(replaces = "lookupCached")
        protected TruffleObject lookup(LibFFILibrary receiver, String symbol) {
            try {
                return ctxRef.get().lookupSymbol(receiver, symbol);
            } catch (UnsatisfiedLinkError ex) {
                throw UnknownIdentifierException.raise(symbol);
            }
        }
    }

    @Resolve(message = "READ")
    abstract static class LookupSymbolNode extends Node {

        @Child private CachedLookupSymbolNode cached = CachedLookupSymbolNodeGen.create();
        @Child private AsStringNode asString = AsStringNodeGen.create(true);

        public TruffleObject access(LibFFILibrary receiver, Object symbol) {
            return cached.executeLookup(receiver, asString.execute(symbol));
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {

        private final ContextReference<NFIContext> ctxRef = NFILanguageImpl.getCurrentContextReference();

        @Child private AsStringNode asString = AsStringNodeGen.create(true);

        public int access(LibFFILibrary receiver, Object arg) {
            String symbol = asString.execute(arg);
            try {
                ctxRef.get().lookupSymbol(receiver, symbol);
                return KeyInfo.READABLE;
            } catch (UnsatisfiedLinkError ex) {
                return KeyInfo.NONE;
            }
        }
    }

    @CanResolve
    abstract static class CanResolveLibFFILibraryNode extends Node {

        public boolean test(TruffleObject receiver) {
            return receiver instanceof LibFFILibrary;
        }
    }
}
