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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.RegexLanguageObject;

import java.util.Map;

import static com.oracle.truffle.regex.util.Boundaries.*;

public class TruffleReadOnlyMap implements RegexLanguageObject {

    private final Map<String, ? extends Object> map;

    public TruffleReadOnlyMap(Map<String, ? extends Object> map) {
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

        @Resolve(message = "KEY_INFO")
        abstract static class TruffleReadOnlyMapKeyInfoNode extends Node {

            public Object access(TruffleReadOnlyMap o, String name) {
                if (mapContainsKey(o.map, name)) {
                    return KeyInfo.READABLE;
                } else {
                    return KeyInfo.NONE;
                }
            }
        }

        @Resolve(message = "READ")
        abstract static class TruffleReadOnlyMapReadNode extends Node {

            public Object access(TruffleReadOnlyMap o, String name) {
                if (mapContainsKey(o.map, name)) {
                    return mapGet(o.map, name);
                }
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.raise(name);
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
