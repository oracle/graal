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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class NFILibrary implements TruffleObject {

    private final TruffleObject library;
    private final Map<String, TruffleObject> symbols;

    NFILibrary(TruffleObject library) {
        this.library = library;
        this.symbols = new HashMap<>();
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return NFILibraryMessageResolutionForeign.ACCESS;
    }

    TruffleObject getLibrary() {
        return library;
    }

    @TruffleBoundary
    TruffleObject findSymbol(String name) {
        return symbols.get(name);
    }

    @TruffleBoundary
    void preBindSymbol(String name, TruffleObject symbol) {
        symbols.put(name, symbol);
    }

    @TruffleBoundary
    Keys getSymbols() {
        return new Keys(symbols.keySet());
    }

    @MessageResolution(receiverType = Keys.class)
    static final class Keys implements TruffleObject {

        private final Object[] keys;

        private Keys(Set<String> keySet) {
            this.keys = keySet.toArray();
        }

        static boolean isInstance(TruffleObject obj) {
            return obj instanceof Keys;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return KeysForeign.ACCESS;
        }

        @Resolve(message = "GET_SIZE")
        abstract static class GetSize extends Node {

            int access(Keys receiver) {
                return receiver.keys.length;
            }
        }

        @Resolve(message = "READ")
        abstract static class Read extends Node {

            Object access(Keys receiver, int index) {
                if (index < 0 || index >= receiver.keys.length) {
                    CompilerDirectives.transferToInterpreter();
                    throw UnknownIdentifierException.raise(Integer.toString(index));
                }
                return receiver.keys[index];
            }
        }
    }
}
