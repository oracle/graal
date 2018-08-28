/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
