/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Language;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.polyglot.PolyglotLocals.LocalLocation;

final class PolyglotLanguage implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    final PolyglotEngineImpl engine;
    final LanguageCache cache;
    final LanguageInfo info;

    Language api; // effectively final
    final int contextIndex;
    private final boolean host;
    final RuntimeException initError;

    private volatile OptionDescriptors options;
    private volatile OptionValuesImpl optionValues;
    private volatile boolean initialized;

    private volatile PolyglotLanguageInstance initLanguage;
    private final LinkedList<PolyglotLanguageInstance> instancePool;

    final Assumption singleInstance = Truffle.getRuntime().createAssumption("Single language instance per engine.");
    private boolean firstInstance = true;

    @CompilationFinal volatile Class<?> contextClass;
    volatile LocalLocation[] previousContextLocalLocations;
    volatile LocalLocation[] previousContextThreadLocalLocations;

    final WeakAssumedValue<PolyglotLanguageInstance> singleLanguageInstance = new WeakAssumedValue<>("single language instance");

    PolyglotLanguage(PolyglotEngineImpl engine, LanguageCache cache, int contextIndex, boolean host, RuntimeException initError) {
        this.engine = engine;
        this.cache = cache;
        this.initError = initError;
        this.contextIndex = contextIndex;
        this.host = host;
        this.instancePool = new LinkedList<>();
        this.info = NODES.createLanguage(this, cache.getId(), cache.getName(), cache.getVersion(), cache.getDefaultMimeType(), cache.getMimeTypes(), cache.isInternal(), cache.isInteractive());
    }

    List<PolyglotLanguageInstance> getInstancePool() {
        synchronized (engine.lock) {
            return new ArrayList<>(instancePool);
        }
    }

    ContextPolicy getEffectiveContextPolicy(PolyglotLanguage inLanguage) {
        ContextPolicy sourcePolicy;
        if (engine.singleContextValue.isValid()) {
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
        return PolyglotContextImpl.requireContext().contexts[contextIndex];
    }

    boolean isFirstInstance() {
        return firstInstance;
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
            synchronized (engine.lock) {
                if (!this.initialized) {
                    this.initLanguage = ensureInitialized(new PolyglotLanguageInstance(this));
                    this.initialized = true;
                }
            }
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    private PolyglotLanguageInstance createInstance() {
        assert Thread.holdsLock(engine.lock);
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
        singleLanguageInstance.update(instance);
        return instance;
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return engine;
    }

    private PolyglotLanguageInstance ensureInitialized(PolyglotLanguageInstance instance) {
        if (!initialized) {
            synchronized (engine.lock) {
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
        synchronized (engine.lock) {
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
        synchronized (engine.lock) {
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
        synchronized (engine.lock) {
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
        assert Thread.holdsLock(engine.lock);
        instancePool.clear();
    }

    OptionValuesImpl getOptionValues() {
        if (optionValues == null) {
            synchronized (engine.lock) {
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

    public String getDefaultMimeType() {
        return cache.getDefaultMimeType();
    }

    void clearOptionValues() {
        optionValues = null;
    }

    public String getName() {
        return cache.getName();
    }

    public String getImplementationName() {
        return cache.getImplementationName();
    }

    public boolean isInteractive() {
        return cache.isInteractive();
    }

    public Set<String> getMimeTypes() {
        return cache.getMimeTypes();
    }

    public String getVersion() {
        final String version = cache.getVersion();
        if (version.equals("inherit")) {
            return engine.getVersion();
        } else {
            return version;
        }
    }

    public String getId() {
        return cache.getId();
    }

    @Override
    public String toString() {
        return "PolyglotLanguage [id=" + getId() + ", name=" + getName() + ", host=" + isHost() + "]";
    }

    boolean assertCorrectEngine() {
        PolyglotContextImpl context = PolyglotContextImpl.requireContext();
        PolyglotLanguageContext languageContext = context.getContext(this);
        if (languageContext.isInitialized() && languageContext.language.engine != this.engine) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw shouldNotReachHere(String.format("Context reference was used from an Engine that is currently not entered. " +
                            "ContextReference of engine %s was used but engine %s is currently entered. " +
                            "ContextReference must not be shared between multiple Engine instances.",
                            languageContext.language.engine,
                            this.engine));
        }
        return true;
    }

}
