/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.util;

import static com.oracle.truffle.regex.util.Boundaries.mapKeySet;
import static com.oracle.truffle.regex.util.Boundaries.setToArray;

import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.regex.RegexLanguageObject;

@ExportLibrary(InteropLibrary.class)
public class TruffleReadOnlyMap implements RegexLanguageObject {

    private final Map<String, ?> map;

    public TruffleReadOnlyMap(Map<String, ?> map) {
        this.map = map;
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new TruffleReadOnlyKeysArray(setToArray(mapKeySet(map), new String[map.size()]));
    }

    @ExportMessage
    boolean isMemberReadable(String member,
                    @Cached IsReadableCacheNode cache,
                    @Shared("receiverProfile") @Cached("createIdentityProfile()") ValueProfile receiverProfile) {
        return cache.execute(receiverProfile.profile(this), member);
    }

    @ExportMessage
    Object readMember(String member,
                    @Cached ReadCacheNode readCache,
                    @Shared("receiverProfile") @Cached("createIdentityProfile()") ValueProfile receiverProfile) throws UnknownIdentifierException {
        return readCache.execute(receiverProfile.profile(this), member);
    }

    @GenerateUncached
    abstract static class IsReadableCacheNode extends Node {

        abstract boolean execute(TruffleReadOnlyMap receiver, String symbol);

        @SuppressWarnings("unused")
        @Specialization(guards = {"receiver == cachedReceiver", "symbol == cachedSymbol", "result"}, limit = "6")
        static boolean cacheIdentity(TruffleReadOnlyMap receiver, String symbol,
                        @Cached("receiver") TruffleReadOnlyMap cachedReceiver,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isReadable(cachedReceiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"receiver == cachedReceiver", "symbol.equals(cachedSymbol)", "result"}, limit = "6", replaces = "cacheIdentity")
        static boolean cacheEquals(TruffleReadOnlyMap receiver, String symbol,
                        @Cached("receiver") TruffleReadOnlyMap cachedReceiver,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isReadable(cachedReceiver, cachedSymbol)") boolean result) {
            return result;
        }

        @Specialization(replaces = "cacheEquals")
        static boolean isReadable(TruffleReadOnlyMap receiver, String symbol) {
            return Boundaries.mapContainsKey(receiver.map, symbol);
        }
    }

    @GenerateUncached
    abstract static class ReadCacheNode extends Node {

        abstract Object execute(TruffleReadOnlyMap receiver, String symbol) throws UnknownIdentifierException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"receiver == cachedReceiver", "symbol == cachedSymbol", "result != null"}, limit = "6")
        static Object readIdentity(TruffleReadOnlyMap receiver, String symbol,
                        @Cached("receiver") TruffleReadOnlyMap cachedReceiver,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("readDirect(cachedReceiver, cachedSymbol)") Object result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"receiver == cachedReceiver", "symbol.equals(cachedSymbol)", "result != null"}, limit = "6", replaces = "readIdentity")
        static Object readEquals(TruffleReadOnlyMap receiver, String symbol,
                        @Cached("receiver") TruffleReadOnlyMap cachedReceiver,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("readDirect(cachedReceiver, cachedSymbol)") Object result) {
            return result;
        }

        @Specialization(replaces = "readEquals")
        static Object read(TruffleReadOnlyMap receiver, String symbol) throws UnknownIdentifierException {
            Object value = readDirect(receiver, symbol);
            if (value != null) {
                return value;
            }
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.create(symbol);
        }

        static Object readDirect(TruffleReadOnlyMap receiver, String symbol) {
            return Boundaries.mapGet(receiver.map, symbol);
        }
    }
}
