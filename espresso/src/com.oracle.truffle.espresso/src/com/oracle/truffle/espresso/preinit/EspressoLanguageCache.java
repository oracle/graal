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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ParserKlass;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.impl.ClassLoadingEnv;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * {@link EspressoLanguageCache} provides {@link ParserKlass} and {@link LinkedKlass} instances that
 * can potentially be shared across {@link EspressoContext}s in the same {@link EspressoLanguage}.
 * {@link EspressoLanguageCache} will use different {@link ParserKlassProvider}s and
 * {@link LinkedKlassProvider}s depending on the configuration. When using context
 * pre-initialization or sharing, {@link EspressoLanguageCache} will use
 * {@link CachedParserKlassProvider} and {@link CachedLinkedKlassProvider} to store
 * {@link ParserKlass}es and {@link LinkedKlass}es created in the first {@link EspressoContext}. In
 * all other cases, {@link EspressoLanguageCache} will use {@link DefaultParserKlassProvider} and
 * {@link DefaultLinkedKlassProvider} that do not cache {@link ParserKlass}es and
 * {@link LinkedKlass}es.
 */
public final class EspressoLanguageCache {
    public enum CacheCapability {
        DEFAULT(0b00),
        PRE_INITIALIZED(0b01),
        SHARED(0b10);

        private final byte kind;

        CacheCapability(int kind) {
            this.kind = (byte) kind;
        }

        private static final byte MASK = (byte) ((1 << (CacheCapability.values().length - 1)) - 1);

        static boolean hasCache(byte kind) {
            return mask(kind) != 0;
        }

        static boolean areCompatible(byte kind, byte other) {
            return mask(kind) == mask(other);
        }

        private static byte mask(byte kind) {
            return (byte) (kind & MASK);
        }
    }

    private static final byte FROZEN = (byte) (CacheCapability.MASK + 1);

    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, EspressoLanguageCache.class);

    @CompilationFinal //
    private ParserKlassProvider parserKlassProvider = new DefaultParserKlassProvider();
    @CompilationFinal //
    private LinkedKlassProvider linkedKlassProvider = new DefaultLinkedKlassProvider();

    // region capability handling

    @CompilationFinal //
    private volatile byte capabilities = 0;

    private static boolean isFrozen(byte kind) {
        return (kind & FROZEN) != 0;
    }

    public void addCapability(CacheCapability capability) {
        addCapability(capability.kind);
    }

    public void ensureFrozen() {
        if (!isFrozen()) {
            addCapability(FROZEN);
        }
    }

    public void importFrom(EspressoLanguageCache other) {
        assert CacheCapability.areCompatible(this.capabilities, other.capabilities);
        this.parserKlassProvider = other.parserKlassProvider;
        this.linkedKlassProvider = other.linkedKlassProvider;
    }

    private synchronized void addCapability(byte capability) {
        byte currentCapabilities = capabilities;
        if ((capability & currentCapabilities) == 0) {
            if (isFrozen(currentCapabilities)) {
                throw EspressoError.fatal("Adding new capability to the language cache when a context has been used.");
            }
            if (needsAddCache(currentCapabilities, capability)) {
                this.parserKlassProvider = new CachedParserKlassProvider(logger, this.parserKlassProvider);
                this.linkedKlassProvider = new CachedLinkedKlassProvider(logger, this.linkedKlassProvider);
            }
            capabilities = (byte) (currentCapabilities | capability);
        }
    }

    private static boolean needsAddCache(byte old, byte capability) {
        return !CacheCapability.hasCache(old) && CacheCapability.hasCache(capability);
    }

    private boolean isFrozen() {
        return isFrozen(capabilities);
    }

    // endregion capability handling

    public EspressoLanguageCache() {
    }

    public void logCacheStatus() {
        logger.fine(() -> {
            int parserKlassCacheSize = parserKlassProvider.getCachedParserKlassCount();
            int linkedKlassCacheSize = linkedKlassProvider.getCachedLinkedKlassCount();
            return String.format("Cache state: [ParserKlasses: %d] [LinkedKlasses: %d]", parserKlassCacheSize, linkedKlassCacheSize);
        });
    }

    public ParserKlass getOrCreateParserKlass(ClassLoadingEnv env, StaticObject classLoader, Symbol<Type> typeOrNull, byte[] bytes, ClassRegistry.ClassDefinitionInfo info) {
        ensureFrozen();
        return parserKlassProvider.getParserKlass(env, classLoader, typeOrNull, bytes, info);
    }

    public LinkedKlass getOrCreateLinkedKlass(ClassLoadingEnv env, EspressoLanguage language, StaticObject loader, ParserKlass parserKlass, LinkedKlass linkedSuperKlass,
                    LinkedKlass[] linkedInterfaces,
                    ClassRegistry.ClassDefinitionInfo info) {
        ensureFrozen();
        return linkedKlassProvider.getLinkedKlass(env, language, loader, parserKlass, linkedSuperKlass, linkedInterfaces, info);
    }
}
