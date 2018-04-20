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

import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;

import java.util.Set;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageImpl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.vm.LanguageCache.LoadedLanguage;

@SuppressWarnings("deprecation")
final class PolyglotLanguage extends AbstractLanguageImpl implements com.oracle.truffle.api.vm.PolyglotImpl.VMObject {

    final PolyglotEngineImpl engine;
    final LanguageCache cache;
    Language api;
    LanguageInfo info;
    final int index;
    private final boolean host;
    final RuntimeException initError;

    private OptionDescriptors options;
    private volatile OptionValuesImpl optionValues;

    @CompilationFinal private ContextProfile profile;

    private volatile boolean initialized;

    PolyglotLanguage(PolyglotEngineImpl engine, LanguageCache cache, int index, boolean host, RuntimeException initError) {
        super(engine.impl);
        this.engine = engine;
        this.cache = cache;
        this.initError = initError;
        this.index = index;
        this.host = host;
    }

    boolean isInitialized() {
        return initialized;
    }

    void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    boolean dependsOn(PolyglotLanguage otherLanguage) {
        Set<String> dependentLanguages = cache.getDependentLanguages();
        if (dependentLanguages.contains(otherLanguage.getId())) {
            return true;
        }
        for (String dependentLanguage : dependentLanguages) {
            PolyglotLanguage dependentLanguageObj = engine.idToLanguage.get(dependentLanguage);
            if (dependentLanguageObj != null && dependsOn(dependentLanguageObj)) {
                return true;
            }
        }
        return false;
    }

    Object getCurrentContext() {
        Env env = PolyglotContextImpl.requireContext().contexts[index].env;
        if (env == null) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(
                            "The language context is not yet initialized or already disposed. ");
        }
        return LANGUAGE.getContext(env);
    }

    boolean isHost() {
        return host;
    }

    @Override
    public OptionDescriptors getOptions() {
        engine.checkState();
        ensureInitialized();
        return options;
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return engine;
    }

    @Override
    public Engine getEngineAPI() {
        return getEngine().api;
    }

    void ensureInitialized() {
        if (!initialized) {
            synchronized (engine) {
                if (!initialized) {
                    try {
                        profile = new ContextProfile(this);
                        LoadedLanguage loadedLanguage = cache.loadLanguage();
                        LANGUAGE.initializeLanguage(info, loadedLanguage.getLanguage(), loadedLanguage.isSingleton());
                        this.options = LANGUAGE.describeOptions(loadedLanguage.getLanguage(), cache.getId());
                    } catch (Exception e) {
                        throw new IllegalStateException(String.format("Error initializing language '%s' using class '%s'.", cache.getId(), cache.getClassName()), e);
                    }
                    initialized = true;
                }
            }
        }
    }

    ContextProfile requireProfile() {
        if (profile == null) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("No language context is active on this thread.");
        }
        return profile;
    }

    OptionValuesImpl getOptionValues() {
        if (optionValues == null) {
            synchronized (engine) {
                if (optionValues == null) {
                    optionValues = new OptionValuesImpl(engine, getOptions());
                }
            }
        }
        return optionValues;
    }

    void clearOptionValues() {
        optionValues = null;
    }

    <S> S lookup(Class<S> serviceClass) {
        ensureInitialized();
        return LANGUAGE.lookup(info, serviceClass);
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public String getImplementationName() {
        return cache.getImplementationName();
    }

    @Override
    public boolean isInteractive() {
        return cache.isInteractive();
    }

    @Override
    public String getVersion() {
        return cache.getVersion();
    }

    @Override
    public String getId() {
        return cache.getId();
    }

    @Override
    public String toString() {
        return "PolyglotLanguage [id=" + getId() + ", name=" + getName() + ", host=" + isHost() + "]";
    }

    static final class ContextProfile {

        private static final Object UNSET_CONTEXT = new Object();

        private final PolyglotLanguage language;
        private final Assumption singleContext = Truffle.getRuntime().createAssumption("Language single context.");
        @CompilationFinal private volatile Object cachedSingleContext = UNSET_CONTEXT;

        ContextProfile(PolyglotLanguage language) {
            this.language = language;
        }

        Object get() {
            assert assertCorrectEngine();
            if (singleContext.isValid()) {
                Object cachedSingle = cachedSingleContext;
                if (cachedSingle != UNSET_CONTEXT) {
                    assert assertGet(cachedSingle);
                    return cachedSingle;
                }
            }
            return lookupLanguageContext(PolyglotContextImpl.requireContext());
        }

        private boolean assertCorrectEngine() {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            if (context.engine != language.engine) {
                throw new AssertionError(String.format("Context reference was used from an Engine that is currently not entered. " +
                                "ContextReference of engine %s was used but engine %s is currently entered. " +
                                "ContextReference must not be shared between multiple TruffleLanguage instances.", language.engine.api, context.engine.api));
            }
            return true;
        }

        private boolean assertGet(Object cachedSingle) {
            // avoid race between current context and single context assertion
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            if (!singleContext.isValid()) {
                return true;
            }
            return cachedSingle == lookupLanguageContext(context);
        }

        private Object lookupLanguageContext(PolyglotContextImpl context) {
            Env env = context.contexts[language.index].env;
            if (env == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("The language context is not yet initialized or already disposed.");
            }
            return LANGUAGE.getContext(env);
        }

        void notifyContextCreate(Env env) {
            if (singleContext.isValid()) {
                Object cachedSingle = this.cachedSingleContext;
                assert cachedSingle != LANGUAGE.getContext(env) || cachedSingle == null : "Non-null context objects should be distinct";
                if (cachedSingle == UNSET_CONTEXT) {
                    if (singleContext.isValid()) {
                        cachedSingleContext = LANGUAGE.getContext(env);
                    }
                } else {
                    singleContext.invalidate();
                    cachedSingleContext = UNSET_CONTEXT;
                }
            }
        }

        void notifyEngineDisposed() {
            if (singleContext.isValid()) {
                // do not invalidate assumptions if engine is disposed anyway
                cachedSingleContext = UNSET_CONTEXT;
            }
        }
    }
}
