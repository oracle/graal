/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.impl.DispatchOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A runtime environment for one or more {@link PolyglotEngine} instances. By default multiple
 * {@linkplain PolyglotEngine engines} operate independently - e.g. they have their own isolated
 * {@linkplain PolyglotRuntime runtime}. However, sometimes it may be useful (for example to save
 * valuable resources needed for code and various other metadata) to group a set of
 * {@linkplain PolyglotEngine engines} into a single {@linkplain PolyglotRuntime runtime}. One can
 * do it like this:
 * <p>
 * {@codesnippet com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest}
 * <p>
 * The above example prepares a single instance of the {@link PolyglotRuntime runtime} and
 * {@link PolyglotEngine.Builder#runtime(com.oracle.truffle.api.vm.PolyglotRuntime) uses it} when
 * configuring {@linkplain PolyglotEngine.Builder engine builder} in the <code>createEngine</code>
 * method. The method may be called multiple times yielding engines sharing essential execution
 * resources behind the scene.
 *
 * @since 0.25
 */
public final class PolyglotRuntime {
    private final List<PolyglotEngine.LanguageShared> languages;
    final Object instrumentationHandler;
    final Map<String, PolyglotEngine.Instrument> instruments;
    final Object[] debugger = {null};
    final PolyglotEngineProfile engineProfile;
    final AtomicInteger instanceCount = new AtomicInteger(0);
    final DispatchOutputStream out;
    final DispatchOutputStream err;
    final InputStream in;

    private PolyglotRuntime() {
        this(null, null, null);
    }

    PolyglotRuntime(DispatchOutputStream out, DispatchOutputStream err, InputStream in) {
        this.instrumentationHandler = PolyglotEngine.Access.INSTRUMENT.createInstrumentationHandler(this, out, err, in);
        /*
         * TODO the engine profile needs to be shared between all engines that potentially share
         * code. Currently this is stored statically to be compatible with the legacy deprecated API
         * TruffleLanguage#createFindContextNode() and the deprecated RootNode constructor. As soon
         * as this deprecated API is removed and EngineImpl#findVM() can be removed as well, we can
         * allocate this context store profile for each shared vm.
         */
        this.engineProfile = PolyglotEngine.GLOBAL_PROFILE;
        List<PolyglotEngine.LanguageShared> languageList = new ArrayList<>();
        /* We want to create a language instance but per LanguageCache and not per mime type. */
        List<LanguageCache> convertedLanguages = new ArrayList<>(new HashSet<>(LanguageCache.languages().values()));
        Collections.sort(convertedLanguages);

        int languageIndex = 0;
        for (LanguageCache languageCache : convertedLanguages) {
            languageList.add(new PolyglotEngine.LanguageShared(this, languageCache, languageIndex++));
        }
        this.languages = languageList;
        this.instruments = createInstruments(InstrumentCache.load());

        this.out = out;
        this.err = err;
        this.in = in;
    }

    PolyglotEngine currentVM() {
        return engineProfile.get();
    }

    List<PolyglotEngine.LanguageShared> getLanguages() {
        return languages;
    }

    private Map<String, PolyglotEngine.Instrument> createInstruments(List<InstrumentCache> instrumentCaches) {
        Map<String, PolyglotEngine.Instrument> instr = new LinkedHashMap<>();
        for (InstrumentCache cache : instrumentCaches) {
            PolyglotEngine.Instrument instrument = PolyglotEngine.UNUSABLE_ENGINE.new Instrument(this, cache);
            instr.put(cache.getId(), instrument);
        }
        return Collections.unmodifiableMap(instr);
    }

    /**
     * Starts creation of a new runtime instance. Call any methods of the {@link Builder} and finish
     * the creation by calling {@link Builder#build()}.
     *
     * @return new instance of a builder
     * @since 0.25
     */
    public static Builder newBuilder() {
        return new PolyglotRuntime().new Builder();
    }

    /**
     * Builder for creating new instance of a {@link PolyglotRuntime}.
     * 
     * @since 0.25
     */
    public final class Builder {
        private OutputStream out;
        private OutputStream err;
        private InputStream in;

        private Builder() {
        }

        /**
         * Configures default output for languages running in the {@link PolyglotEngine engine}
         * being built, defaults to {@link System#out}.
         *
         * @param os the stream to use as output
         * @return this builder
         * @since 0.25
         */
        public PolyglotRuntime.Builder setOut(OutputStream os) {
            out = os;
            return this;
        }

        /**
         * Configures error output for languages running in the {@link PolyglotRuntime runtime}
         * being built, defaults to {@link System#err}.
         *
         * @param os the stream to use as output
         * @return this builder
         * @since 0.25
         */
        public PolyglotRuntime.Builder setErr(OutputStream os) {
            err = os;
            return this;
        }

        /**
         * Configures default input for languages running in the {@link PolyglotRuntime runtime}
         * being built, defaults to {@link System#in}.
         *
         * @param is the stream to use as input
         * @return this builder
         * @since 0.25
         */
        public PolyglotRuntime.Builder setIn(InputStream is) {
            in = is;
            return this;
        }

        /**
         * Creates new instance of a runtime. Uses data stored in this builder to configure it. Once
         * the instance is obtained, pass it to
         * {@link PolyglotEngine.Builder#runtime(com.oracle.truffle.api.vm.PolyglotRuntime)} method.
         *
         * @return new instances of the {@link PolyglotRuntime}
         * @since 0.25
         */
        public PolyglotRuntime build() {
            DispatchOutputStream realOut = PolyglotEngine.SPIAccessor.instrumentAccess().createDispatchOutput(out == null ? System.out : out);
            DispatchOutputStream realErr = PolyglotEngine.SPIAccessor.instrumentAccess().createDispatchOutput(err == null ? System.err : err);
            InputStream realIn = in == null ? System.in : in;
            return new PolyglotRuntime(realOut, realErr, realIn);
        }
    }
}
