/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

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
import com.oracle.truffle.regex.AbstractConstantKeysObjectFactory.IsReadableCacheNodeGen;
import com.oracle.truffle.regex.AbstractConstantKeysObjectFactory.ReadCacheNodeGen;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

@ExportLibrary(InteropLibrary.class)
public abstract class AbstractConstantKeysObject implements RegexLanguageObject {

    public abstract TruffleReadOnlyKeysArray getKeys();

    public abstract Object readMemberImpl(String symbol) throws UnknownIdentifierException;

    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @ExportMessage
    public Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return getKeys();
    }

    @ExportMessage
    public boolean isMemberReadable(String member,
                    @Cached IsReadableCacheNode cache,
                    @Shared("receiverProfile") @Cached("createIdentityProfile()") ValueProfile receiverProfile) {
        return cache.execute(receiverProfile.profile(this), member);
    }

    @ExportMessage
    public Object readMember(String member,
                    @Cached ReadCacheNode readCache,
                    @Shared("receiverProfile") @Cached("createIdentityProfile()") ValueProfile receiverProfile) throws UnknownIdentifierException {
        return readCache.execute(receiverProfile.profile(this), member);
    }

    @GenerateUncached
    public abstract static class IsReadableCacheNode extends Node {

        public abstract boolean execute(AbstractConstantKeysObject receiver, String symbol);

        @SuppressWarnings("unused")
        @Specialization(guards = "symbol == cachedSymbol", limit = "8")
        static boolean cacheIdentity(AbstractConstantKeysObject receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isReadable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "8", replaces = "cacheIdentity")
        static boolean cacheEquals(AbstractConstantKeysObject receiver, String symbol,
                        @Cached("symbol") String cachedSymbol,
                        @Cached("isReadable(receiver, cachedSymbol)") boolean result) {
            return result;
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "cacheEquals")
        static boolean isReadable(AbstractConstantKeysObject receiver, String symbol) {
            return receiver.getKeys().contains(symbol);
        }

        public static IsReadableCacheNode create() {
            return IsReadableCacheNodeGen.create();
        }

        public static IsReadableCacheNode getUncached() {
            return IsReadableCacheNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class ReadCacheNode extends Node {

        public abstract Object execute(AbstractConstantKeysObject receiver, String symbol) throws UnknownIdentifierException;

        @Specialization(guards = "symbol == cachedSymbol", limit = "8")
        Object readIdentity(AbstractConstantKeysObject receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") String cachedSymbol) throws UnknownIdentifierException {
            return read(receiver, cachedSymbol);
        }

        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "8", replaces = "readIdentity")
        Object readEquals(AbstractConstantKeysObject receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") String cachedSymbol) throws UnknownIdentifierException {
            return read(receiver, cachedSymbol);
        }

        @Specialization(replaces = "readEquals")
        static Object read(AbstractConstantKeysObject receiver, String symbol) throws UnknownIdentifierException {
            return receiver.readMemberImpl(symbol);
        }

        public static ReadCacheNode create() {
            return ReadCacheNodeGen.create();
        }

        public static ReadCacheNode getUncached() {
            return ReadCacheNodeGen.getUncached();
        }
    }
}
