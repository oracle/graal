/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.util;

import static com.oracle.truffle.regex.util.Boundaries.mapKeySet;
import static com.oracle.truffle.regex.util.Boundaries.setToArray;

import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.regex.AbstractRegexObject;

@ExportLibrary(InteropLibrary.class)
public class TruffleReadOnlyMap extends AbstractRegexObject {

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
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnknownIdentifierException.create(symbol);
        }

        static Object readDirect(TruffleReadOnlyMap receiver, String symbol) {
            return Boundaries.mapGet(receiver.map, symbol);
        }
    }
}
