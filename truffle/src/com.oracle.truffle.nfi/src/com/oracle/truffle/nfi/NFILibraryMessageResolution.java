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
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.NFILibraryMessageResolutionFactory.CachedLookupSymbolNodeGen;
import com.oracle.truffle.nfi.NFILibraryMessageResolutionFactory.IdentToStringNodeGen;

@MessageResolution(receiverType = NFILibrary.class)
class NFILibraryMessageResolution {

    abstract static class CachedLookupSymbolNode extends Node {

        protected abstract TruffleObject executeLookup(NFILibrary receiver, String symbol);

        @Specialization(guards = {"receiver == cachedReceiver", "symbol.equals(cachedSymbol)"})
        @SuppressWarnings("unused")
        protected TruffleObject lookupCached(NFILibrary receiver, String symbol,
                        @Cached("receiver") NFILibrary cachedReceiver,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("lookup(cachedReceiver, cachedSymbol, createRead())") TruffleObject cachedRet) {
            return cachedRet;
        }

        @Specialization(replaces = "lookupCached")
        protected TruffleObject lookup(NFILibrary receiver, String symbol, @Cached("createRead()") Node read) {
            TruffleObject preBound = receiver.findSymbol(symbol);
            if (preBound != null) {
                return preBound;
            } else {
                try {
                    return (TruffleObject) ForeignAccess.sendRead(read, receiver.getLibrary(), symbol);
                } catch (InteropException ex) {
                    throw ex.raise();
                }
            }
        }

        protected Node createRead() {
            return Message.READ.createNode();
        }
    }

    @Resolve(message = "READ")
    abstract static class LookupSymbolNode extends Node {

        @Child private CachedLookupSymbolNode cached = CachedLookupSymbolNodeGen.create();
        @Child private IdentToStringNode toString = IdentToStringNode.create();

        public TruffleObject access(NFILibrary receiver, Object symbol) {
            return cached.executeLookup(receiver, toString.execute(symbol));
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class InvokeSymbolNode extends Node {

        @Child private CachedLookupSymbolNode cached = CachedLookupSymbolNodeGen.create();
        @Child private Node execute = Message.EXECUTE.createNode();

        public Object access(NFILibrary receiver, String symbol, Object... args) {
            TruffleObject obj = cached.executeLookup(receiver, symbol);
            try {
                return ForeignAccess.sendExecute(execute, obj, args);
            } catch (InteropException ex) {
                throw ex.raise();
            }
        }
    }

    @Resolve(message = "KEYS")
    abstract static class KeysNode extends Node {

        public Object access(NFILibrary receiver) {
            return receiver.getSymbols();
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {

        @Child private IdentToStringNode toString = IdentToStringNode.create();
        @Child private Node keyInfo = Message.KEY_INFO.createNode();

        public int access(NFILibrary receiver, Object arg) {
            String symbol = toString.execute(arg);
            if (receiver.findSymbol(symbol) != null) {
                return KeyInfo.READABLE | KeyInfo.INVOCABLE;
            } else {
                return ForeignAccess.sendKeyInfo(keyInfo, receiver.getLibrary(), symbol);
            }
        }
    }

    abstract static class IdentToStringNode extends Node {

        abstract String execute(Object arg);

        @Specialization
        String doString(String str) {
            return str;
        }

        @Specialization(guards = "checkIsBoxed(isBoxed, arg)")
        @SuppressWarnings("unused")
        String doBoxed(TruffleObject arg,
                        @Cached("createIsBoxed()") Node isBoxed,
                        @Cached("createUnbox()") Node unbox,
                        @Cached("create()") IdentToStringNode toString) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, arg);
                return toString.execute(unboxed);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(ex, new Object[]{arg});
            }
        }

        @Specialization(guards = {"isOther(arg)"})
        @TruffleBoundary
        String doOther(Object arg) {
            return arg.toString();
        }

        protected static Node createIsBoxed() {
            return Message.IS_BOXED.createNode();
        }

        protected static Node createUnbox() {
            return Message.UNBOX.createNode();
        }

        protected static boolean checkIsBoxed(Node isBoxed, TruffleObject arg) {
            return ForeignAccess.sendIsBoxed(isBoxed, arg);
        }

        protected static boolean isOther(Object obj) {
            return !(obj instanceof String) && !(obj instanceof TruffleObject);
        }

        protected static IdentToStringNode create() {
            return IdentToStringNodeGen.create();
        }
    }

    @CanResolve
    abstract static class CanResolveNFILibrary extends Node {

        public boolean test(TruffleObject receiver) {
            return receiver instanceof NFILibrary;
        }
    }
}
