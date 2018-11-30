/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.RegexLanguageObject;

import java.util.Map;

import static com.oracle.truffle.regex.util.Boundaries.mapKeySet;
import static com.oracle.truffle.regex.util.Boundaries.setToArray;
import static com.oracle.truffle.regex.util.TruffleReadOnlyMapFactory.TruffleReadOnlyMapMessageResolutionFactory.KeyInfoCacheNodeGen;
import static com.oracle.truffle.regex.util.TruffleReadOnlyMapFactory.TruffleReadOnlyMapMessageResolutionFactory.ReadCacheNodeGen;

public class TruffleReadOnlyMap implements RegexLanguageObject {

    private final Map<String, ?> map;

    public TruffleReadOnlyMap(Map<String, ?> map) {
        this.map = map;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return TruffleReadOnlyMapMessageResolutionForeign.ACCESS;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof TruffleReadOnlyMap;
    }

    @MessageResolution(receiverType = TruffleReadOnlyMap.class)
    static final class TruffleReadOnlyMapMessageResolution {

        @Resolve(message = "KEYS")
        abstract static class TruffleReadOnlyMapKeysNode extends Node {

            public Object access(TruffleReadOnlyMap o) {
                return new TruffleReadOnlyMapKeysObject(setToArray(mapKeySet(o.map), new String[0]));
            }
        }

        abstract static class KeyInfoCacheNode extends Node {

            abstract Object execute(TruffleReadOnlyMap receiver, String symbol);

            @Specialization(guards = {"receiver == cachedReceiver", "cachedKey.equals(key)", "cachedContainsKey"}, limit = "8")
            Object readCached(@SuppressWarnings("unused") TruffleReadOnlyMap receiver, @SuppressWarnings("unused") String key,
                            @Cached("receiver") @SuppressWarnings("unused") TruffleReadOnlyMap cachedReceiver,
                            @Cached("key") @SuppressWarnings("unused") String cachedKey,
                            @Cached("mapContainsKey(receiver, key)") @SuppressWarnings("unused") boolean cachedContainsKey) {
                return KeyInfo.READABLE;
            }

            @Specialization(replaces = "readCached")
            Object readDynamic(TruffleReadOnlyMap receiver, String key) {
                if (mapContainsKey(receiver, key)) {
                    return KeyInfo.READABLE;
                } else {
                    return KeyInfo.NONE;
                }
            }

            static boolean mapContainsKey(TruffleReadOnlyMap map, String key) {
                return Boundaries.mapContainsKey(map.map, key);
            }
        }

        @Resolve(message = "KEY_INFO")
        abstract static class TruffleReadOnlyMapKeyInfoNode extends Node {

            @Child KeyInfoCacheNode cache = KeyInfoCacheNodeGen.create();

            public Object access(TruffleReadOnlyMap o, String name) {
                return cache.execute(o, name);
            }
        }

        abstract static class ReadCacheNode extends Node {

            abstract Object execute(TruffleReadOnlyMap receiver, String symbol);

            @Specialization(guards = {"receiver == cachedReceiver", "cachedKey.equals(key)", "cachedContainsKey"}, limit = "8")
            Object readCached(@SuppressWarnings("unused") TruffleReadOnlyMap receiver, @SuppressWarnings("unused") String key,
                            @Cached("receiver") @SuppressWarnings("unused") TruffleReadOnlyMap cachedReceiver,
                            @Cached("key") @SuppressWarnings("unused") String cachedKey,
                            @Cached("mapContainsKey(receiver, key)") @SuppressWarnings("unused") boolean cachedContainsKey,
                            @Cached("mapGet(receiver, key)") Object cachedValue) {
                return cachedValue;
            }

            @Specialization(replaces = "readCached")
            Object readDynamic(TruffleReadOnlyMap receiver, String key) {
                Object value = mapGet(receiver, key);
                if (value != null) {
                    return value;
                }
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(key);
            }

            static Object mapGet(TruffleReadOnlyMap map, String key) {
                return Boundaries.mapGet(map.map, key);
            }

            static boolean mapContainsKey(TruffleReadOnlyMap map, String key) {
                return Boundaries.mapContainsKey(map.map, key);
            }

        }

        @Resolve(message = "READ")
        abstract static class TruffleReadOnlyMapReadNode extends Node {

            @Child ReadCacheNode cache = ReadCacheNodeGen.create();

            public Object access(TruffleReadOnlyMap o, String name) {
                return cache.execute(o, name);
            }
        }
    }

    static class TruffleReadOnlyMapKeysObject implements RegexLanguageObject {

        private final String[] keys;

        TruffleReadOnlyMapKeysObject(String[] keys) {
            this.keys = keys;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return TruffleReadOnlyMapKeysObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof TruffleReadOnlyMapKeysObject;
        }

        @MessageResolution(receiverType = TruffleReadOnlyMapKeysObject.class)
        static final class TruffleReadOnlyMapKeysObjectMessageResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class TruffleReadOnlyMapKeysObjectHasSizeNode extends Node {

                @SuppressWarnings("unused")
                public Object access(TruffleReadOnlyMapKeysObject keysObject) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class TruffleReadOnlyMapKeysObjectGetSizeNode extends Node {

                public Object access(TruffleReadOnlyMapKeysObject keysObject) {
                    return keysObject.keys.length;
                }
            }

            @Resolve(message = "READ")
            abstract static class TruffleReadOnlyMapKeysObjectReadNode extends Node {

                public Object access(TruffleReadOnlyMapKeysObject keysObject, int index) {
                    if (index >= keysObject.keys.length) {
                        CompilerDirectives.transferToInterpreter();
                        throw UnknownIdentifierException.raise(Integer.toString(index));
                    }
                    return keysObject.keys[index];
                }
            }
        }
    }
}
