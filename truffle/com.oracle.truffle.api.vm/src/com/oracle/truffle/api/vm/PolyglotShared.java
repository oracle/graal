/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.vm.PolyglotEngine.Access;
import com.oracle.truffle.api.vm.PolyglotEngine.Instrument;
import com.oracle.truffle.api.vm.PolyglotEngine.LanguageShared;
import com.oracle.truffle.api.vm.PolyglotEngine.SPIAccessor;

/** State shared across multiple engines. */
class PolyglotShared {

    final ComputeInExecutor.Info executor;
    final List<Object[]> config;
    final InputStream in;
    private final List<LanguageShared> languages;
    final DispatchOutputStream err;
    final DispatchOutputStream out;
    final Map<String, Object> globals;
    final Object instrumentationHandler;
    final Map<String, Instrument> instruments;
    final Object[] debugger = {null};
    final PolyglotEngineProfile engineProfile;
    final AtomicInteger instanceCount = new AtomicInteger(0);

    PolyglotShared(Executor executor, Map<String, Object> globals, OutputStream out, OutputStream err, InputStream in, List<Object[]> config) {
        this.executor = ComputeInExecutor.wrap(executor);
        this.out = SPIAccessor.instrumentAccess().createDispatchOutput(out);
        this.err = SPIAccessor.instrumentAccess().createDispatchOutput(err);
        this.in = in;
        this.globals = new HashMap<>(globals);
        this.config = config;
        this.instrumentationHandler = Access.INSTRUMENT.createInstrumentationHandler(this, this.out, this.err, in);
        /*
         * TODO the engine profile needs to be shared between all engines that potentially share
         * code. Currently this is stored statically to be compatible with the legacy deprecated API
         * TruffleLanguage#createFindContextNode() and the deprecated RootNode constructor. As soon
         * as this deprecated API is removed and EngineImpl#findVM() can be removed as well, we can
         * allocate this context store profile for each shared vm.
         */
        this.engineProfile = PolyglotEngine.GLOBAL_PROFILE;
        List<LanguageShared> languageList = new ArrayList<>();
        /* We want to create a language instance but per LanguageCache and not per mime type. */
        List<LanguageCache> convertedLanguages = new ArrayList<>(new HashSet<>(LanguageCache.languages().values()));
        Collections.sort(convertedLanguages);

        int languageIndex = 0;
        for (LanguageCache languageCache : convertedLanguages) {
            languageList.add(new LanguageShared(this, languageCache, languageIndex++));
        }
        this.languages = languageList;
        this.instruments = createInstruments(InstrumentCache.load());
    }

    OutputStream out() {
        return out;
    }

    PolyglotEngine currentVM() {
        return engineProfile.get();
    }

    List<Object[]> getConfig() {
        return config;
    }

    List<LanguageShared> getLanguages() {
        return languages;
    }

    private Map<String, Instrument> createInstruments(List<InstrumentCache> instrumentCaches) {
        Map<String, Instrument> instr = new LinkedHashMap<>();
        for (InstrumentCache cache : instrumentCaches) {
            Instrument instrument = PolyglotEngine.UNUSABLE_ENGINE.new Instrument(this, cache);
            instr.put(cache.getId(), instrument);
        }
        return Collections.unmodifiableMap(instr);
    }

    Env findEnv(@SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> languageClazz, boolean failIfNotFound) {
        for (LanguageShared languageDescription : languages) {
            Env env = languageDescription.getEnv(false);
            if (env != null && languageClazz.isInstance(languageDescription.getImpl(false))) {
                return env;
            }
        }
        if (failIfNotFound) {
            Set<String> languageNames = new HashSet<>();
            for (LanguageShared lang : languages) {
                languageNames.add(lang.cache.getClassName());
            }
            throw new IllegalStateException("Cannot find language " + languageClazz + " among " + languages);
        } else {
            return null;
        }
    }

}
