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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.nfi.LibFFILibraryMessageResolutionFactory.CachedLookupSymbolNodeGen;

@MessageResolution(receiverType = LibFFILibrary.class)
class LibFFILibraryMessageResolution {

    abstract static class CachedLookupSymbolNode extends Node {

        protected abstract LibFFISymbol executeLookup(LibFFILibrary receiver, String symbol);

        @Specialization(guards = {"receiver == cachedReceiver", "symbol.equals(cachedSymbol)"})
        @SuppressWarnings("unused")
        protected LibFFISymbol lookupCached(LibFFILibrary receiver, String symbol,
                        @Cached("receiver") LibFFILibrary cachedReceiver,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("lookup(cachedReceiver, cachedSymbol)") LibFFISymbol cachedRet) {
            return cachedRet;
        }

        @Specialization(replaces = "lookupCached")
        protected LibFFISymbol lookup(LibFFILibrary receiver, String symbol) {
            try {
                return receiver.lookupSymbol(symbol);
            } catch (UnsatisfiedLinkError ex) {
                throw UnknownIdentifierException.raise(symbol);
            }
        }
    }

    @Resolve(message = "READ")
    abstract static class LookupSymbolNode extends Node {

        @Child private CachedLookupSymbolNode cached = CachedLookupSymbolNodeGen.create();

        public LibFFISymbol access(LibFFILibrary receiver, String symbol) {
            return cached.executeLookup(receiver, symbol);
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class ExecuteSymbolNode extends Node {
        @CompilerDirectives.CompilationFinal private String cachedSymbol;
        @CompilerDirectives.CompilationFinal private TruffleObject cachedObj;
        private Node execute;

        public Object access(LibFFILibrary receiver, String symbol, Object[] args) {
            TruffleObject obj;
            if (symbol.equals(cachedSymbol)) {
                obj = cachedObj;
            } else {
                CompilerDirectives.transferToInterpreter();
                obj = receiver.findSymbol(symbol);
                if (cachedObj == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedObj = obj;
                    execute = insert(Message.createExecute(args.length).createNode());
                }
            }
            try {
                return ForeignAccess.sendExecute(execute, obj, args);
            } catch (InteropException ex) {
                throw ex.raise();
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
