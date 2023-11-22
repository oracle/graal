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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.impl.ClassLoadingEnv;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.ContextDescription;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class CachedLinkedKlassProvider extends AbstractCachedKlassProvider implements LinkedKlassProvider {
    private final LinkedKlassProvider fallbackProvider;
    private final Map<LinkedKlassCacheKey, LinkedKlass> linkedKlassCache = new ConcurrentHashMap<>();

    public CachedLinkedKlassProvider(TruffleLogger logger, LinkedKlassProvider fallbackProvider) {
        super(logger);
        this.fallbackProvider = fallbackProvider;
    }

    @Override
    public LinkedKlass getLinkedKlass(ClassLoadingEnv env, ContextDescription description, StaticObject loader, ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces,
                    ClassRegistry.ClassDefinitionInfo info) {
        if (env.shouldCacheClass(info, loader)) {
            LinkedKlassCacheKey key = new LinkedKlassCacheKey(parserKlass, superKlass, interfaces);
            LinkedKlass linkedKlass = linkedKlassCache.get(key);
            if (linkedKlass == null) {
                getLogger().finer(() -> "LinkedKlass cache miss: " + parserKlass.getName());
                linkedKlass = fallbackProvider.getLinkedKlass(env, description, loader, parserKlass, superKlass, interfaces, info);
                linkedKlassCache.put(key, linkedKlass);
            } else {
                getLogger().finer(() -> "LinkedKlass cache hit: " + parserKlass.getName());
            }
            return linkedKlass;
        } else {
            return fallbackProvider.getLinkedKlass(env, description, loader, parserKlass, superKlass, interfaces, info);
        }
    }

    @Override
    public int getCachedLinkedKlassCount() {
        return linkedKlassCache.size();
    }

    private static final class LinkedKlassCacheKey {
        private final ParserKlass parserKlass;
        private final LinkedKlass superKlass;
        private final LinkedKlass[] interfaces;
        private final int hash;

        LinkedKlassCacheKey(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
            this.parserKlass = parserKlass;
            this.superKlass = superKlass;
            this.interfaces = interfaces;
            this.hash = 31 * Objects.hash(parserKlass, superKlass) + Arrays.hashCode(interfaces);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LinkedKlassCacheKey linkedKey = (LinkedKlassCacheKey) o;
            return parserKlass.equals(linkedKey.parserKlass) && Objects.equals(superKlass, linkedKey.superKlass) && Arrays.equals(interfaces, linkedKey.interfaces);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
