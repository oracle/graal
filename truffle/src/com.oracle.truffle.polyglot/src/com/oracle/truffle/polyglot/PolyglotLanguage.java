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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.VMAccessor.LANGUAGE;
import static com.oracle.truffle.polyglot.VMAccessor.NODES;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageImpl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;

final class PolyglotLanguage extends AbstractLanguageImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    final PolyglotEngineImpl engine;
    final LanguageCache cache;
    final LanguageInfo info;

    Language api; // effectivley final
    final int index;
    private final boolean host;
    final RuntimeException initError;

    private volatile OptionDescriptors options;
    private volatile OptionValuesImpl optionValues;
    private volatile boolean initialized;

    private volatile PolyglotLanguageInstance initLanguage;
    private final LinkedList<PolyglotLanguageInstance> instancePool = new LinkedList<>();

    final ContextProfile profile;

    PolyglotLanguage(PolyglotEngineImpl engine, LanguageCache cache, int index, boolean host, RuntimeException initError) {
        super(engine.impl);
        this.engine = engine;
        this.cache = cache;
        this.initError = initError;
        this.index = index;
        this.host = host;
        this.profile = new ContextProfile(this);
        this.info = NODES.createLanguage(this, cache.getId(), cache.getName(), cache.getVersion(), cache.getDefaultMimeType(), cache.getMimeTypes(), cache.isInternal(), cache.isInteractive());
    }

    PolyglotLanguageContext getCurrentLanguageContext() {
        return PolyglotContextImpl.requireContext().contexts[index];
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

    boolean isHost() {
        return host;
    }

    @Override
    public OptionDescriptors getOptions() {
        engine.checkState();
        if (!initialized) {
            synchronized (engine) {
                if (!initialized) {
                    try {
                        this.initLanguage = ensureInitialized(new PolyglotLanguageInstance(this));
                    } catch (Throwable e) {
                        // failing to initialize the language for getting the option descriptors
                        // should not be a fatal error. this typically happens when an invalid
                        // language is on the classpath.
                        return OptionDescriptors.EMPTY;
                    }
                    initialized = true;
                }
            }
        }
        return options;
    }

    private PolyglotLanguageInstance createInstance() {
        assert Thread.holdsLock(engine);
        PolyglotLanguageInstance instance = null;
        if (initLanguage != null) {
            // reuse init language
            instance = this.initLanguage;
            initLanguage = null;
        }
        if (instance == null) {
            instance = ensureInitialized(new PolyglotLanguageInstance(this));
        }
        return instance;
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return engine;
    }

    private PolyglotLanguageInstance ensureInitialized(PolyglotLanguageInstance instance) {
        if (!initialized) {
            synchronized (engine) {
                if (!initialized) {
                    try {
                        this.options = LANGUAGE.describeOptions(instance.spi, cache.getId());
                    } catch (Exception e) {
                        throw new IllegalStateException(String.format("Error initializing language '%s' using class '%s'.", cache.getId(), cache.getClassName()), e);
                    }
                    initialized = true;
                }
            }
        }
        return instance;
    }

    PolyglotLanguageInstance allocateInstance(OptionValuesImpl newOptions) {
        PolyglotLanguageInstance instance;
        synchronized (engine) {
            switch (cache.getPolicy()) {
                case EXCLUSIVE:
                    instance = createInstance();
                    break;
                case REUSE:
                    instance = fetchFromPool(newOptions, false);
                    break;
                case SHARED:
                    instance = fetchFromPool(newOptions, true);
                    break;
                default:
                    throw new AssertionError("Unknown context cardinality.");
            }
        }
        return instance;
    }

    private PolyglotLanguageInstance fetchFromPool(OptionValuesImpl newOptions, boolean shared) {
        synchronized (engine) {
            PolyglotLanguageInstance foundInstance = null;
            for (Iterator<PolyglotLanguageInstance> iterator = instancePool.iterator(); iterator.hasNext();) {
                PolyglotLanguageInstance instance = iterator.next();
                if (instance.areOptionsCompatible(newOptions)) {
                    if (!shared) {
                        iterator.remove();
                    }
                    foundInstance = instance;
                    break;
                }
            }
            if (foundInstance == null) {
                foundInstance = createInstance();
                foundInstance.claim(newOptions);
                if (shared) {
                    instancePool.addFirst(foundInstance);
                }
            }
            return foundInstance;
        }
    }

    void freeInstance(PolyglotLanguageInstance instance) {
        switch (cache.getPolicy()) {
            case EXCLUSIVE:
                // nothing to do
                break;
            case REUSE:
                synchronized (engine) {
                    profile.notifyLanguageFreed();
                    instancePool.addFirst(instance);
                }
                break;
            case SHARED:
                // nothing to do
                break;
            default:
                throw new AssertionError("Unknown context cardinality.");
        }
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

    @Override
    public String getDefaultMimeType() {
        return cache.getDefaultMimeType();
    }

    void clearOptionValues() {
        optionValues = null;
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
    public Set<String> getMimeTypes() {
        return cache.getMimeTypes();
    }

    @Override
    public String getVersion() {
        final String version = cache.getVersion();
        if (version.equals("inherit")) {
            return engine.getVersion();
        } else {
            return version;
        }
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
            if (!language.engine.boundEngine) {
                singleContext.invalidate();
            }
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
            PolyglotLanguageContext languageContext = context.getContext(language);
            if (languageContext.isInitialized() && languageContext.language.engine != language.engine) {
                throw new AssertionError(String.format("Context reference was used from an Engine that is currently not entered. " +
                                "ContextReference of engine %s was used but engine %s is currently entered. " +
                                "ContextReference must not be shared between multiple Engine instances.",
                                languageContext.language.engine.creatorApi,
                                language.engine.creatorApi));
            }
            return true;
        }

        private boolean assertGet(Object cachedSingle) {
            // avoid race between current context and single context assertion
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            if (!singleContext.isValid()) {
                return true;
            }
            Object verifyContext = lookupLanguageContext(context);
            if (cachedSingle != verifyContext) {
                throw new AssertionError(String.format("Expected %s but got %s.", cachedSingle, verifyContext));
            }
            return true;
        }

        private Object lookupLanguageContext(PolyglotContextImpl context) {
            Env env = context.getContext(language).env;
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

        void notifyLanguageFreed() {
            if (singleContext.isValid()) {
                // do not invalidate assumptions if engine is disposed anyway
                cachedSingleContext = UNSET_CONTEXT;
            }
        }
    }

}
