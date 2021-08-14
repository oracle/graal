/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * This class serves as a {@link ParserKlass} and {@link LinkedKlass} cache.
 *
 * In a multi-context environment, {@link ParserKlass} caching improves performance by keeping the
 * pre-made parsed and linked klasses. {@link LinkedKlass} caching removes redundant re-computation
 * of {@link LinkedKlass} fields, such as {@link LinkedKlassFieldLayout}.
 *
 * {@link ParserKlass}es are identified by their name in order to improve performance when
 * boot classloader is used.
 *
 * {@link LinkedKlass}es are identified not only by their {@link ParserKlass}, but also
 * by superclass and implemented interfaces. Therefore, when caching {@link LinkedKlass}es,
 * a composite key needs to be used (see {@link LinkedKlassCacheKey}).
 */
public final class EspressoKlassCache {

    private boolean isParserKlassCacheEnabled = EspressoOptions.UseParserKlassCache.getDefaultValue();
    private boolean shouldReportParserKlassCacheMisses = EspressoOptions.ReportParserKlassCacheMisses.getDefaultValue();
    private boolean isLinkedKlassCacheEnabled = EspressoOptions.UseLinkedKlassCache.getDefaultValue();

    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, EspressoKlassCache.class);
    private final Map<Symbol<Symbol.Type>, ParserKlass> bootParserKlassCache = new ConcurrentHashMap<>();
    private final Map<ParserKlassCacheKey, ParserKlass> appParserKlassCache = new ConcurrentHashMap<>();
    private final Map<LinkedKlassCacheKey, LinkedKlass> linkedKlassCache = new ConcurrentHashMap<>();

    private boolean sealed = false;

    public void updateEnv(final TruffleLanguage.Env env) {
        shouldReportParserKlassCacheMisses = env.getOptions().get(EspressoOptions.ReportParserKlassCacheMisses);
        isParserKlassCacheEnabled = env.getOptions().get(EspressoOptions.UseParserKlassCache);
        isLinkedKlassCacheEnabled = env.getOptions().get(EspressoOptions.UseLinkedKlassCache);
    }

    public int getBootParserKlassCacheSize() {
        return bootParserKlassCache.size();
    }

    public int getAppParserKlassCacheSize() {
        return appParserKlassCache.size();
    }

    public int getLinkedClassCacheSize() {
        return linkedKlassCache.size();
    }

    public ParserKlass getOrCreateParserKlass(StaticObject loader, Symbol<Symbol.Type> typeOrNull, byte[] bytes, EspressoContext context, ClassRegistry.ClassDefinitionInfo info) {
        assert (info.isAnonymousClass() && typeOrNull == null) || (!info.isAnonymousClass() && typeOrNull != null);
        if (isParserKlassCacheEnabled && (!info.isAnonymousClass() || sealed)) {
            ParserKlassCacheKey key = null;
            ParserKlass parserKlass = null;

            // Query the boot cache
            if (!info.isAnonymousClass()) {
                parserKlass = bootParserKlassCache.get(typeOrNull);
            }

            // If the query failed, query the application cache if the boot cache is sealed
            if (parserKlass == null && sealed) {
                key = new ParserKlassCacheKey(bytes);
                parserKlass = appParserKlassCache.get(key);
            }

            // If both queries failed, add a new entry to the appropriate cache
            if (parserKlass == null) {
                if (shouldReportParserKlassCacheMisses && !info.isAnonymousClass()) {
                    logger.info("Cache miss: " + typeOrNull);
                }
                parserKlass = createParserKlass(loader, typeOrNull, bytes, context, info);
                if (sealed) {
                    if (key == null) {
                        key = new ParserKlassCacheKey(bytes);
                    }
                    appParserKlassCache.put(key, parserKlass);
                } else if (!info.isAnonymousClass()) {
                    bootParserKlassCache.put(typeOrNull, parserKlass);
                }
            }
            return parserKlass;
        } else {
            return createParserKlass(loader, typeOrNull, bytes, context, info);
        }
    }

    public LinkedKlass getOrCreateLinkedKlass(EspressoLanguage language, JavaVersion version, ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces, ClassRegistry.ClassDefinitionInfo info) {
        if (isLinkedKlassCacheEnabled && !info.isAnonymousClass()) {
            LinkedKlassCacheKey key = new LinkedKlassCacheKey(parserKlass, superKlass, interfaces);
            LinkedKlass linkedKlass = linkedKlassCache.get(key);
            if (linkedKlass == null) {
                linkedKlass = createLinkedKlass(language, version, parserKlass, superKlass, interfaces);
                linkedKlassCache.put(key, linkedKlass);
            }
            return linkedKlass;
        } else {
            return createLinkedKlass(language, version, parserKlass, superKlass, interfaces);
        }
    }

    public void logCacheStatus() {
        logger.log(Level.FINE, () -> {
            int bootCacheCount = getBootParserKlassCacheSize();
            int appCacheCount = getAppParserKlassCacheSize();
            int linkedCacheCount = getLinkedClassCacheSize();
            return String.format("Cache status: [PK:%d+%d] [LK:%d]", bootCacheCount, appCacheCount, linkedCacheCount);
        });
    }

    public void seal() {
        this.sealed = true;
        logger.log(Level.FINE, "Sealed");
    }

    public void reset() {
        this.bootParserKlassCache.clear();
        this.appParserKlassCache.clear();
        this.linkedKlassCache.clear();
        this.sealed = false;
    }

    private ParserKlass createParserKlass(StaticObject loader, Symbol<Symbol.Type> typeOrNull, byte[] bytes, EspressoContext context, ClassRegistry.ClassDefinitionInfo info) {
        // May throw guest ClassFormatError, NoClassDefFoundError.
        return ClassfileParser.parse(new ClassfileStream(bytes, null), loader, typeOrNull, context, info);
    }

    private LinkedKlass createLinkedKlass(EspressoLanguage language, JavaVersion version, ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
        return LinkedKlass.create(language, version, parserKlass, superKlass, interfaces);
    }

    private static final class ParserKlassCacheKey {

        private final byte[] bytes;
        private final int hash;

        ParserKlassCacheKey(byte[] bytes) {
            this.bytes = bytes;
            this.hash = Arrays.hashCode(bytes);
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
            return this.hash == other.hash && Arrays.equals(this.bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class LinkedKlassCacheKey {
        private final ParserKlass parserKlass;
        private final LinkedKlass superKlass;
        private final LinkedKlass[] interfaces;

        LinkedKlassCacheKey(ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
            this.parserKlass = parserKlass;
            this.superKlass = superKlass;
            this.interfaces = interfaces;
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
            int hashCode = Objects.hash(parserKlass, superKlass);
            hashCode = 31 * hashCode + Arrays.hashCode(interfaces);
            return hashCode;
        }
    }
}
