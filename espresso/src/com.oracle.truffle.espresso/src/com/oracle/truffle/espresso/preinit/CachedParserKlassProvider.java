/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.preinit;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ClassLoadingEnv;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.verifier.MethodVerifier;

public final class CachedParserKlassProvider extends AbstractCachedKlassProvider implements ParserKlassProvider {
    private final ParserKlassProvider fallbackProvider;
    private final Map<Symbol<Symbol.Type>, ParserKlass> bootParserKlassCache = new ConcurrentHashMap<>();
    private final Map<ParserKlassCacheKey, ParserKlass> appParserKlassCache = new ConcurrentHashMap<>();

    public CachedParserKlassProvider(TruffleLogger logger, ParserKlassProvider fallbackProvider) {
        super(logger);
        this.fallbackProvider = fallbackProvider;
    }

    @Override
    public ParserKlass getParserKlass(ClassLoadingEnv env, StaticObject loader, Symbol<Symbol.Type> typeOrNull, byte[] bytes, ClassRegistry.ClassDefinitionInfo info) {
        if (env.shouldCacheClass(info, loader) && typeOrNull != null) {
            ParserKlassCacheKey key = null;
            ParserKlass parserKlass = null;

            boolean loaderIsBootOrPlatform = env.loaderIsBootOrPlatform(loader);
            boolean loaderIsApp = env.loaderIsAppLoader(loader);

            if (loaderIsBootOrPlatform) {
                // For boot/platform CL, query the boot cache
                parserKlass = bootParserKlassCache.get(typeOrNull);
            } else if (loaderIsApp) {
                // For other class loaders, query the application cache
                boolean verifiable = MethodVerifier.needsVerify(env.getLanguage(), loader);
                assert !info.isAnonymousClass() && !info.isHidden() && info.patches == null;
                key = new ParserKlassCacheKey(bytes, typeOrNull, verifiable);
                parserKlass = appParserKlassCache.get(key);
            }

            // If cache query failed, add a new entry to the appropriate cache
            if (parserKlass == null) {
                getLogger().finer(() -> "ParserKlass cache miss: " + typeOrNull);
                parserKlass = fallbackProvider.getParserKlass(env, loader, typeOrNull, bytes, info);
                if (loaderIsBootOrPlatform) {
                    bootParserKlassCache.put(typeOrNull, parserKlass);
                } else if (loaderIsApp) {
                    appParserKlassCache.put(key, parserKlass);
                }
                // For now, don't cache if loader is not app or platform.
            } else {
                ParserKlass finalParserKlass = parserKlass;
                getLogger().finer(() -> "ParserKlass cache hit: " + finalParserKlass.getName());
            }
            return parserKlass;
        } else {
            return fallbackProvider.getParserKlass(env, loader, typeOrNull, bytes, info);
        }
    }

    @Override
    public int getCachedParserKlassCount() {
        return bootParserKlassCache.size() + appParserKlassCache.size();
    }

    private static final class ParserKlassCacheKey {
        private final byte[] bytes;
        private final int hash;
        private final Symbol<Symbol.Type> type;
        private final boolean verifiable;

        ParserKlassCacheKey(byte[] bytes, Symbol<Symbol.Type> type, boolean verifiable) {
            this.bytes = bytes;
            this.type = type;
            this.verifiable = verifiable;
            this.hash = computeHash(bytes, type, verifiable);
        }

        private static int computeHash(byte[] bytes, Symbol<Symbol.Type> typeOrNull, boolean verifiable) {
            int result = Arrays.hashCode(bytes);
            result = 31 * result + typeOrNull.hashCode();
            result = 31 * result + Boolean.hashCode(verifiable);
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ParserKlassCacheKey other = (ParserKlassCacheKey) o;
            if (this.hash != other.hash) {
                return false;
            }
            return Arrays.equals(this.bytes, other.bytes) && this.type.equals(other.type) && this.verifiable == other.verifiable;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
