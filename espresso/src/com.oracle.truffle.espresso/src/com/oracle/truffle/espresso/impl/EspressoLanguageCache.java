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
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * This class serves as a {@link ParserKlass} and {@link LinkedKlass} cache.
 *
 * {@link ParserKlass}es are identified by their name in order to improve performance when boot
 * classloader is used.
 *
 * {@link LinkedKlass}es are identified not only by their {@link ParserKlass}, but also by
 * superclass and implemented interfaces. Therefore, when caching {@link LinkedKlass}es, a composite
 * key needs to be used (see {@link LinkedKlassCacheKey}).
 */
public final class EspressoLanguageCache {
    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, EspressoLanguageCache.class);
    private final Map<Symbol<Symbol.Type>, ParserKlass> bootParserKlassCache = new ConcurrentHashMap<>();
    private final Map<ParserKlassCacheKey, ParserKlass> appParserKlassCache = new ConcurrentHashMap<>();
    private final Map<LinkedKlassCacheKey, LinkedKlass> linkedKlassCache = new ConcurrentHashMap<>();

    public int getBootParserKlassCacheSize() {
        return bootParserKlassCache.size();
    }

    public int getAppParserKlassCacheSize() {
        return appParserKlassCache.size();
    }

    public int getLinkedClassCacheSize() {
        return linkedKlassCache.size();
    }

    public ParserKlass getOrCreateParserKlass(ClassLoadingEnv env, StaticObject loader, Symbol<Symbol.Type> typeOrNull, byte[] bytes, ClassRegistry.ClassDefinitionInfo info) {
        Options options = context.getLanguageCacheOptions();
        assert (info.isAnonymousClass() && typeOrNull == null) || (!info.isAnonymousClass() && typeOrNull != null);
        if (options.ParserKlassCacheEnabled && shouldCacheClass(info)) {
            ParserKlassCacheKey key = null;
            ParserKlass parserKlass = null;

            boolean loaderIsBootOrPlatform = env.loaderIsBootOrPlatform(loader);

            // For boot/platform CL, query the boot cache
            if (loaderIsBootOrPlatform && !info.isAnonymousClass()) {
                parserKlass = bootParserKlassCache.get(typeOrNull);
            }

            // For other class loaders, query the application cache
            if (parserKlass == null) {
                key = new ParserKlassCacheKey(bytes);
                parserKlass = appParserKlassCache.get(key);
            }

            // If queries failed, add a new entry to the appropriate cache
            if (parserKlass == null) {
                logger.fine(() -> "Cache miss: " + typeOrNull);
                parserKlass = createParserKlass(env, loader, typeOrNull, bytes, info);
                if (loaderIsBootOrPlatform) {
                    bootParserKlassCache.put(typeOrNull, parserKlass);
                } else {
                    appParserKlassCache.put(key, parserKlass);
                }
            } else {
                ParserKlass finalParserKlass = parserKlass;
                logger.fine(() -> "Cache hit: " + finalParserKlass.getName());
            }
            return parserKlass;
        } else {
            return createParserKlass(env, loader, typeOrNull, bytes, info);
        }
    }

    public LinkedKlass getOrCreateLinkedKlass(Options options, ContextDescription description, ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces,
                    ClassRegistry.ClassDefinitionInfo info) {
        if (options.LinkedKlassCacheEnabled && shouldCacheClass(info)) {
            LinkedKlassCacheKey key = new LinkedKlassCacheKey(parserKlass, superKlass, interfaces);
            LinkedKlass linkedKlass = linkedKlassCache.get(key);
            if (linkedKlass == null) {
                linkedKlass = createLinkedKlass(description, parserKlass, superKlass, interfaces);
                linkedKlassCache.put(key, linkedKlass);
            }
            return linkedKlass;
        } else {
            return createLinkedKlass(description, parserKlass, superKlass, interfaces);
        }
    }

    private static boolean shouldCacheClass(ClassRegistry.ClassDefinitionInfo info) {
        return !info.isAnonymousClass() && !info.isHidden();
    }

    public void logCacheStatus() {
        logger.fine(() -> {
            int bootCacheCount = getBootParserKlassCacheSize();
            int appCacheCount = getAppParserKlassCacheSize();
            int linkedCacheCount = getLinkedClassCacheSize();
            return String.format("Cache status: [PK:%d+%d] [LK:%d]", bootCacheCount, appCacheCount, linkedCacheCount);
        });
    }

    private static ParserKlass createParserKlass(ClassLoadingEnv env, StaticObject loader, Symbol<Symbol.Type> typeOrNull, byte[] bytes, ClassRegistry.ClassDefinitionInfo info) {
        // May throw guest ClassFormatError, NoClassDefFoundError.
        return ClassfileParser.parse(env, new ClassfileStream(bytes, null), loader, typeOrNull, info);
    }

    private static LinkedKlass createLinkedKlass(ContextDescription description, ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
        return LinkedKlass.create(description, parserKlass, superKlass, interfaces);
    }

    public static final class Options {
        // Checkstyle: stop field name check
        public final boolean ParserKlassCacheEnabled;
        public final boolean LinkedKlassCacheEnabled;
        // Checkstyle: resume field name check

        public Options(final TruffleLanguage.Env env) {
            ParserKlassCacheEnabled = env.getOptions().get(EspressoOptions.UseParserKlassCache);
            LinkedKlassCacheEnabled = env.getOptions().get(EspressoOptions.UseLinkedKlassCache);
        }
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
