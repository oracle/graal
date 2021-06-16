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
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
public final class EspressoLanguageCache {

    private boolean isParserKlassCacheEnabled = EspressoOptions.UseParserKlassCache.getDefaultValue();
    private boolean isLinkedKlassCacheEnabled = EspressoOptions.UseParserKlassCache.getDefaultValue();

    private final Map<String, ParserKlass> bootParserKlassCache = new ConcurrentHashMap<>();
    private final Map<ParserKlassCacheKey, ParserKlass> appParserKlassCache = new ConcurrentHashMap<>();
    private final Map<LinkedKlassCacheKey, LinkedKlass> linkedKlassCache = new ConcurrentHashMap<>();

    private boolean sealed = false;

    public void updateEnv(final TruffleLanguage.Env env) {
        isParserKlassCacheEnabled = env.getOptions().get(EspressoOptions.UseParserKlassCache);
        isLinkedKlassCacheEnabled = env.getOptions().get(EspressoOptions.UseLinkedKlassCache);
    }

    public ParserKlass getOrCreateParserKlass(StaticObject loader, String name, byte[] bytes, EspressoContext context, ClassRegistry.ClassDefinitionInfo info) {
        if (isParserKlassCacheEnabled) {
            ParserKlassCacheKey key = null;
            ParserKlass parserKlass = bootParserKlassCache.get(name);
            if (sealed && parserKlass == null) {
                key = new ParserKlassCacheKey(bytes);
                parserKlass = appParserKlassCache.get(key);
            }
            if (parserKlass == null) {
                parserKlass = createParserKlass(loader, name, bytes, context, info);
                if (sealed) {
                    if (key == null) {
                        key = new ParserKlassCacheKey(bytes);
                    }
                    appParserKlassCache.put(key, parserKlass);
                } else {
                    bootParserKlassCache.put(name, parserKlass);
                }
            }
            return parserKlass;
        } else {
            return createParserKlass(loader, name, bytes, context, info);
        }
    }

    public LinkedKlass getOrCreateLinkedKlass(EspressoLanguage language, ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
        if (isLinkedKlassCacheEnabled) {
            LinkedKlassCacheKey key = new LinkedKlassCacheKey(parserKlass, superKlass, interfaces);
            LinkedKlass linkedKlass = linkedKlassCache.get(key);
            if (linkedKlass == null) {
                linkedKlass = createLinkedKlass(language, parserKlass, superKlass, interfaces);
                linkedKlassCache.put(key, linkedKlass);
            }
            return linkedKlass;
        } else {
            return createLinkedKlass(language, parserKlass, superKlass, interfaces);
        }
    }

    public void seal() {
        this.sealed = true;
    }

    public void reset() {
        this.bootParserKlassCache.clear();
        this.appParserKlassCache.clear();
        this.linkedKlassCache.clear();
        this.sealed = false;
    }

    private ParserKlass createParserKlass(StaticObject loader, String name, byte[] bytes, EspressoContext context, ClassRegistry.ClassDefinitionInfo info) {
        // May throw guest ClassFormatError, NoClassDefFoundError.
        return ClassfileParser.parse(new ClassfileStream(bytes, null), loader, name, context, info);
    }

    private LinkedKlass createLinkedKlass(EspressoLanguage language, ParserKlass parserKlass, LinkedKlass superKlass, LinkedKlass[] interfaces) {
        return LinkedKlass.create(language, parserKlass, superKlass, interfaces);
    }

    private static final class ParserKlassCacheKey {
        private static final String HASH_ALGORITHM = "SHA-256";

        private final byte[] bytes;
        private final String hash;

        ParserKlassCacheKey(byte[] bytes) {
            this.bytes = bytes;
            try {
                MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
                this.hash = new String(digest.digest(bytes));
            } catch (NoSuchAlgorithmException e) {
                throw EspressoError.shouldNotReachHere("SHA-256 algorithm not available but is required for ParserKlass caching");
            }
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
            return this.hash.equals(other.hash) && Arrays.equals(this.bytes, other.bytes);
        }

        @Override
        public int hashCode() {
            return hash.hashCode();
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
