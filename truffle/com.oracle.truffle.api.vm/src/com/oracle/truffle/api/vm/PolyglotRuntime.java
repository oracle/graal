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

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.LanguageInfo;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.vm.LanguageCache.LoadedLanguage;
import com.oracle.truffle.api.vm.PolyglotEngine.Access;
import com.oracle.truffle.api.vm.PolyglotEngine.Language;

/**
 * A runtime environment for one or more {@link PolyglotEngine} instances. Engines associated with a
 * runtime are allowed to cache/share resources between them.
 *
 * Usage:
 * <p>
 * {@codesnippet com.oracle.truffle.api.vm.PolyglotEngineSnippets#createEngines}
 * <p>
 * The above example prepares three engines that share the {@link PolyglotRuntime runtime}.
 *
 * @since 0.25
 * @see PolyglotEngine
 */
public final class PolyglotRuntime {
    private final List<LanguageShared> languages;
    final Object instrumentationHandler;
    @SuppressWarnings("deprecation") final Map<String, PolyglotEngine.Instrument> instruments;
    final Object[] debugger = {null};
    final PolyglotEngineProfile engineProfile;
    private final AtomicInteger instanceCount = new AtomicInteger(0);
    final DispatchOutputStream out;
    final DispatchOutputStream err;
    final InputStream in;
    volatile boolean disposed;
    final boolean automaticDispose;

    private PolyglotRuntime() {
        this(null, null, null, false);
    }

    PolyglotRuntime(DispatchOutputStream out, DispatchOutputStream err, InputStream in, boolean automaticDispose) {
        this.instrumentationHandler = PolyglotEngine.Access.INSTRUMENT.createInstrumentationHandler(this, out, err, in);
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
        this.automaticDispose = automaticDispose;
        this.languages = languageList;
        this.instruments = createInstruments(InstrumentCache.load());

        this.out = out;
        this.err = err;
        this.in = in;
    }

    PolyglotEngine currentVM() {
        return engineProfile.get();
    }

    List<LanguageShared> getLanguages() {
        return languages;
    }

    void notifyEngineDisposed() {
        instanceCount.decrementAndGet();
        if (automaticDispose) {
            dispose();
        }
    }

    void notifyEngineCreated() {
        instanceCount.incrementAndGet();
    }

    @SuppressWarnings("deprecation")
    private Map<String, PolyglotEngine.Instrument> createInstruments(List<InstrumentCache> instrumentCaches) {
        Map<String, PolyglotEngine.Instrument> instr = new LinkedHashMap<>();
        for (InstrumentCache cache : instrumentCaches) {
            PolyglotEngine.Instrument instrument = PolyglotEngine.UNUSABLE_ENGINE.new Instrument(this, cache);
            instr.put(cache.getId(), instrument);
        }
        return Collections.unmodifiableMap(instr);
    }

    /**
     * Gets the map: {@linkplain Instrument#getId() Instrument ID} --> {@link Instrument} loaded in
     * this {@linkplain PolyglotRuntime runtime}, whether the instrument is
     * {@linkplain Instrument#isEnabled() enabled} or not.
     *
     * @return map of currently loaded instruments
     * @since 0.25
     */
    public Map<String, ? extends Instrument> getInstruments() {
        return instruments;
    }

    /**
     * Disposes the runtime and with it all created instruments. Throws
     * {@link IllegalStateException} if not all engines created using this runtime are not yet
     * {@link PolyglotEngine#dispose() disposed}.
     * {@link PolyglotEngine.Builder#runtime(PolyglotRuntime) Default/private} runtimes of an engine
     * are disposed automatically with the engine.
     *
     * @since 0.25
     */
    public synchronized void dispose() {
        if (instanceCount.get() > 0) {
            throw new IllegalStateException("Cannot dispose runtime if not all engine instances are disposed.");
        }
        if (!disposed) {
            disposed = true;
            // only dispose instruments if all engine group is disposed
            for (PolyglotRuntime.Instrument instrument : getInstruments().values()) {
                try {
                    instrument.setEnabledImpl(false, false);
                } catch (Exception | Error ex) {
                    PolyglotEngine.LOG.log(Level.SEVERE, "Error disposing " + instrument, ex);
                }
            }
        }
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

    static final class LanguageShared {

        final LanguageCache cache;
        final PolyglotRuntime runtime;
        final PolyglotEngineProfile engineProfile;
        final int languageId;
        volatile LanguageInfo language;

        LanguageShared(PolyglotRuntime engineShared, LanguageCache cache, int languageId) {
            this.runtime = engineShared;
            this.engineProfile = engineShared.engineProfile;
            assert engineProfile != null;
            this.cache = cache;
            this.languageId = languageId;
        }

        Language currentLanguage() {
            return runtime.currentVM().findLanguage(this);
        }

        Object getCurrentContext() {
            // is on fast-path
            final PolyglotEngine engine = engineProfile.get();
            Object context = PolyglotEngine.UNSET_CONTEXT;
            if (engine != null) {
                context = engine.languageArray[languageId].context;
            }
            if (context == PolyglotEngine.UNSET_CONTEXT) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(
                                "The language context is not yet initialized or already disposed. ");
            }
            return context;
        }

        PolyglotRuntime getRuntime() {
            return runtime;
        }

        LanguageInfo getLanguage() {
            if (language == null) {
                synchronized (this) {
                    if (language == null) {
                        LoadedLanguage loadedLanguage = cache.loadLanguage();
                        this.language = Access.LANGS.initializeLanguage(this, loadedLanguage.getLanguage(), loadedLanguage.isSingleton(), cache.getName(), cache.getVersion(), cache.getMimeTypes());
                    }
                }
            }
            return language;
        }

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
            return new PolyglotRuntime(realOut, realErr, realIn, false);
        }

        PolyglotRuntime build(boolean autoDispose) {
            DispatchOutputStream realOut = PolyglotEngine.SPIAccessor.instrumentAccess().createDispatchOutput(out == null ? System.out : out);
            DispatchOutputStream realErr = PolyglotEngine.SPIAccessor.instrumentAccess().createDispatchOutput(err == null ? System.err : err);
            InputStream realIn = in == null ? System.in : in;
            return new PolyglotRuntime(realOut, realErr, realIn, autoDispose);
        }
    }

    /**
     * A handle for an <em>instrument</em> installed in the {@linkplain PolyglotRuntime runtime},
     * usable from other threads, that can observe and inject behavior into language execution. The
     * handle provides access to the instrument's metadata and allows the instrument to be
     * dynamically {@linkplain Instrument#setEnabled(boolean) enabled/disabled} in the runtime.
     * <p>
     * All methods here, as well as instrumentation services in general, can be used safely from
     * threads other than the engine's single execution thread.
     * <p>
     * Refer to {@link TruffleInstrument} for information about implementing and installing
     * instruments.
     *
     * @see PolyglotRuntime#getInstruments()
     * @since 0.25
     */
    public class Instrument {

        private final InstrumentCache info;
        private final Object instrumentLock = new Object();
        private volatile boolean enabled;

        Instrument(InstrumentCache cache) {
            this.info = cache;
        }

        /**
         * Gets the id clients can use to acquire this instrument.
         *
         * @return this instrument's unique id
         * @since 0.9
         */
        public String getId() {
            return info.getId();
        }

        /**
         * Gets a human readable name of this instrument.
         *
         * @return this instrument's user-friendly name
         * @since 0.9
         */
        public String getName() {
            return info.getName();
        }

        /**
         * Gets the version of this instrument.
         *
         * @return this instrument's version
         * @since 0.9
         */
        public String getVersion() {
            return info.getVersion();
        }

        InstrumentCache getCache() {
            return info;
        }

        /**
         * Returns whether this instrument is currently enabled in the engine.
         *
         * @return this instrument's status in the engine
         * @since 0.9
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Returns an additional service provided by this instrument, specified by type.
         * <p>
         * Here is an example for locating a hypothetical <code>DebuggerController</code>:
         *
         * {@codesnippet DebuggerExampleTest}
         *
         * @param <T> the type of the service
         * @param type class of the service that is being requested
         * @return instance of requested type, <code>null</code> if no such service is available
         * @since 0.9
         */
        public <T> T lookup(Class<T> type) {
            return PolyglotEngine.Access.INSTRUMENT.getInstrumentationHandlerService(PolyglotRuntime.this.instrumentationHandler, this, type);
        }

        /**
         * Enables/disables this instrument in the engine.
         *
         * @param enabled <code>true</code> to enable <code>false</code> to disable
         * @since 0.9
         */
        public void setEnabled(final boolean enabled) {
            if (PolyglotRuntime.this.instanceCount.get() == 0) {
                throw new IllegalStateException("All engines have already been disposed");
            }
            setEnabledImpl(enabled, true);
        }

        void setEnabledImpl(final boolean enabled, boolean cleanup) {
            synchronized (instrumentLock) {
                if (this.enabled != enabled) {
                    if (enabled) {
                        PolyglotEngine.Access.INSTRUMENT.addInstrument(PolyglotRuntime.this.instrumentationHandler, this, getCache().getInstrumentationClass());
                    } else {
                        PolyglotEngine.Access.INSTRUMENT.disposeInstrument(PolyglotRuntime.this.instrumentationHandler, this, cleanup);
                    }
                    this.enabled = enabled;
                }
            }
        }

        /**
         * @since 0.9
         */
        @Override
        public String toString() {
            return "Instrument [id=" + getId() + ", name=" + getName() + ", version=" + getVersion() + ", enabled=" + enabled + "]";
        }
    }

}
