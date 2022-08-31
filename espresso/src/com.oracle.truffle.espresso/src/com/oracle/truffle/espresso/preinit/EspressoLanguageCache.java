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

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ClassLoadingEnv;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.impl.ContextDescription;
import com.oracle.truffle.espresso.impl.LinkedKlass;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * {@link EspressoLanguageCache} provides {@link ParserKlass} and {@link LinkedKlass} instances that
 * can potentially be shared across {@link EspressoContext}s in the same {@link EspressoLanguage}.
 * {@link EspressoLanguageCache} will use different {@link ParserKlassProvider}s and
 * {@link LinkedKlassProvider}s depending on the configuration. When using context
 * pre-initialization, {@link EspressoLanguageCache} will use {@link CachedParserKlassProvider} and
 * {@link CachedLinkedKlassProvider} to store {@link ParserKlass}es and {@link LinkedKlass}es
 * created in the first {@link EspressoContext}. In all other cases, {@link EspressoLanguageCache}
 * will use {@link DefaultParserKlassProvider} and {@link DefaultLinkedKlassProvider} that do not
 * cache {@link ParserKlass}es and {@link LinkedKlass}es.
 */
public final class EspressoLanguageCache {
    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, EspressoLanguageCache.class);
    private final ParserKlassProvider parserKlassProvider;
    private final LinkedKlassProvider linkedKlassProvider;

    public EspressoLanguageCache(boolean enableCaching) {
        ParserKlassProvider defaultParserKlassProvider = new DefaultParserKlassProvider();
        LinkedKlassProvider defaultLinkedKlassProvider = new DefaultLinkedKlassProvider();
        if (enableCaching) {
            parserKlassProvider = new CachedParserKlassProvider(logger, defaultParserKlassProvider);
            linkedKlassProvider = new CachedLinkedKlassProvider(logger, defaultLinkedKlassProvider);
        } else {
            parserKlassProvider = defaultParserKlassProvider;
            linkedKlassProvider = defaultLinkedKlassProvider;
        }
    }

    public void logCacheStatus() {
        logger.fine(() -> {
            int parserKlassCacheSize = parserKlassProvider.getCachedParserKlassCount();
            int linkedKlassCacheSize = linkedKlassProvider.getCachedLinkedKlassCount();
            return String.format("Cache state: [ParserKlasses: %d] [LinkedKlasses: %d]", parserKlassCacheSize, linkedKlassCacheSize);
        });
    }

    public ParserKlass getOrCreateParserKlass(ClassLoadingEnv env, StaticObject classLoader, Symbol<Symbol.Type> typeOrNull, byte[] bytes, ClassRegistry.ClassDefinitionInfo info) {
        return parserKlassProvider.getParserKlass(env, classLoader, typeOrNull, bytes, info);
    }

    public LinkedKlass getOrCreateLinkedKlass(ContextDescription description, ParserKlass parserKlass, LinkedKlass linkedSuperKlass, LinkedKlass[] linkedInterfaces,
                    ClassRegistry.ClassDefinitionInfo info) {
        return linkedKlassProvider.getLinkedKlass(description, parserKlass, linkedSuperKlass, linkedInterfaces, info);
    }
}
