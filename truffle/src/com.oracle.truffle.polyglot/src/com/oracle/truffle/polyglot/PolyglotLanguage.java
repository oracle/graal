/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.polyglot.EngineAccessor.LANGUAGE;
import static com.oracle.truffle.polyglot.EngineAccessor.NODES;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageImpl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.utilities.NeverValidAssumption;
import java.lang.ref.WeakReference;

final class PolyglotLanguage extends AbstractLanguageImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    final PolyglotEngineImpl engine;
    final LanguageCache cache;
    final LanguageInfo info;

    Language api; // effectively final
    final int index;
    private final boolean host;
    final RuntimeException initError;

    private volatile OptionDescriptors options;
    private volatile OptionValuesImpl optionValues;
    private volatile boolean initialized;

    private volatile PolyglotLanguageInstance initLanguage;
    private final LinkedList<PolyglotLanguageInstance> instancePool;

    final ContextProfile profile;
    private final LanguageReference<TruffleLanguage<Object>> multiLanguageReference;
    private final LanguageReference<TruffleLanguage<Object>> singleOrMultiLanguageReference;
    private final ContextReference<Object> multiContextReference;
    private final ContextReference<Object> singleOrMultiContextReference;
    final Assumption singleInstance = Truffle.getRuntime().createAssumption("Single language instance per engine.");
    private boolean firstInstance = true;

    @CompilationFinal volatile Class<?> contextClass;

    PolyglotLanguage(PolyglotEngineImpl engine, LanguageCache cache, int index, boolean host, RuntimeException initError) {
        super(engine.impl);
        this.engine = engine;
        this.cache = cache;
        this.initError = initError;
        this.index = index;
        this.host = host;
        this.profile = new ContextProfile(this);
        this.instancePool = new LinkedList<>();
        this.info = NODES.createLanguage(this, cache.getId(), cache.getName(), cache.getVersion(), cache.getDefaultMimeType(), cache.getMimeTypes(), cache.isInternal(), cache.isInteractive());
        this.multiLanguageReference = PolyglotReferences.createAlwaysMultiLanguage(this);
        this.multiContextReference = PolyglotReferences.createAlwaysMultiContext(this);

        this.singleOrMultiContextReference = PolyglotReferences.createAssumeSingleContext(this, engine.singleContext, null, multiContextReference, false);
        this.singleOrMultiLanguageReference = PolyglotReferences.createAssumeSingleLanguage(this, null, singleInstance, multiLanguageReference);
    }

    ContextPolicy getEffectiveContextPolicy(PolyglotLanguage inLanguage) {
        ContextPolicy sourcePolicy;
        if (engine.boundEngine) {
            // with a bound engine context policy is effectively always exclusive
            sourcePolicy = ContextPolicy.EXCLUSIVE;
        } else {
            if (inLanguage != null) {
                sourcePolicy = inLanguage.cache.getPolicy();
            } else {
                // we don't know which language we are in so null language means shared policy
                sourcePolicy = ContextPolicy.SHARED;
            }
        }
        return sourcePolicy;
    }

    PolyglotLanguageContext getCurrentLanguageContext() {
        return PolyglotContextImpl.requireContext().contexts[index];
    }

    void initializeContextClass(Object contextImpl) {
        CompilerAsserts.neverPartOfCompilation();
        Class<?> newClass = contextImpl == null ? Void.class : contextImpl.getClass();
        Class<?> currentClass = contextClass;
        if (currentClass == null) {
            contextClass = newClass;
        } else if (currentClass != newClass) {
            throw new IllegalStateException(String.format("Unstable context class expected %s got %s.", newClass, currentClass));
        }
    }

    boolean dependsOn(PolyglotLanguage otherLanguage) {
        Set<String> dependentLanguages = cache.getDependentLanguages();
        if (dependentLanguages.contains(otherLanguage.getId())) {
            return true;
        }
        for (String dependentLanguage : dependentLanguages) {
            PolyglotLanguage dependentLanguageObj = engine.idToLanguage.get(dependentLanguage);
            if (dependentLanguageObj != null && dependentLanguageObj.dependsOn(otherLanguage)) {
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
        try {
            engine.checkState();
            return getOptionsInternal();
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(this.engine, e);
        }
    }

    OptionDescriptors getOptionsInternal() {
        if (!this.initialized) {
            synchronized (engine) {
                if (!this.initialized) {
                    this.initLanguage = ensureInitialized(new PolyglotLanguageInstance(this));
                    this.initialized = true;
                }
            }
        }
        return options;
    }

    private PolyglotLanguageInstance createInstance() {
        assert Thread.holdsLock(engine);
        if (firstInstance) {
            firstInstance = false;
        } else if (singleInstance.isValid()) {
            singleInstance.invalidate();
        }
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
                    throw shouldNotReachHere();
            }
            instance.ensureMultiContextInitialized();
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
        synchronized (engine) {
            switch (cache.getPolicy()) {
                case EXCLUSIVE:
                    // nothing to do
                    break;
                case REUSE:
                    instancePool.addFirst(instance);
                    break;
                case SHARED:
                    // nothing to do
                    break;
                default:
                    throw shouldNotReachHere("Unknown context cardinality.");
            }
        }
    }

    void close() {
        assert Thread.holdsLock(engine);
        instancePool.clear();
    }

    /**
     * Returns a context reference sharable within this engine.
     */
    ContextReference<Object> getContextReference() {
        if (singleInstance.isValid() && !engine.conservativeContextReferences) {
            return singleOrMultiContextReference;
        } else {
            return multiContextReference;
        }
    }

    /**
     * Returns a language reference sharable within this engine.
     */
    LanguageReference<TruffleLanguage<Object>> getLanguageReference() {
        if (singleInstance.isValid()) {
            return singleOrMultiLanguageReference;
        } else {
            return multiLanguageReference;
        }
    }

    /**
     * Returns a context reference that always looks up the current context.
     */
    ContextReference<Object> getConservativeContextReference() {
        return multiContextReference;
    }

    /**
     * Returns a language reference that always looks up the current language.
     */
    LanguageReference<TruffleLanguage<Object>> getConservativeLanguageReference() {
        return multiLanguageReference;
    }

    OptionValuesImpl getOptionValues() {
        if (optionValues == null) {
            synchronized (engine) {
                if (optionValues == null) {
                    optionValues = new OptionValuesImpl(engine, getOptionsInternal(), false);
                }
            }
        }
        return optionValues;
    }

    OptionValuesImpl getOptionValuesIfExists() {
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
            return engine.creatorApi.getVersion();
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

        private final Assumption singleContext;
        @CompilationFinal private volatile WeakReference<Object> cachedSingleContext;
        @CompilationFinal private volatile WeakReference<PolyglotLanguageContext> cachedSingleLanguageContext;

        ContextProfile(PolyglotLanguage language) {
            this.singleContext = language.engine.boundEngine ? Truffle.getRuntime().createAssumption("Language single context.") : NeverValidAssumption.INSTANCE;
        }

        public Assumption getSingleContext() {
            return singleContext;
        }

        PolyglotLanguageContext profile(Object context) {
            if (singleContext.isValid()) {
                WeakReference<PolyglotLanguageContext> ref = cachedSingleLanguageContext;
                PolyglotLanguageContext cachedSingle = ref == null ? null : ref.get();
                if (singleContext.isValid()) {
                    assert cachedSingle == context : assertionError(cachedSingle, context);
                    return cachedSingle;
                }
            }
            return (PolyglotLanguageContext) context;
        }

        static String assertionError(Object cachedContext, Object currentContext) {
            return (cachedContext + " != " + currentContext);
        }

        void notifyContextCreate(PolyglotLanguageContext context, Env env) {
            if (singleContext.isValid()) {
                WeakReference<Object> ref = this.cachedSingleContext;
                Object cachedSingle = ref == null ? null : ref.get();
                assert cachedSingle != LANGUAGE.getContext(env) || cachedSingle == null : "Non-null context objects should be distinct";
                if (ref == null) {
                    if (singleContext.isValid()) {
                        this.cachedSingleContext = new WeakReference<>(LANGUAGE.getContext(env));
                        this.cachedSingleLanguageContext = new WeakReference<>(context);
                    }
                } else {
                    singleContext.invalidate();
                    cachedSingleContext = null;
                    cachedSingleLanguageContext = null;
                }
            }
        }
    }

    boolean assertCorrectEngine() {
        PolyglotContextImpl context = PolyglotContextImpl.requireContext();
        PolyglotLanguageContext languageContext = context.getContext(this);
        if (languageContext.isInitialized() && languageContext.language.engine != this.engine) {
            throw shouldNotReachHere(String.format("Context reference was used from an Engine that is currently not entered. " +
                            "ContextReference of engine %s was used but engine %s is currently entered. " +
                            "ContextReference must not be shared between multiple Engine instances.",
                            languageContext.language.engine.creatorApi,
                            this.engine.creatorApi));
        }
        return true;
    }

}
