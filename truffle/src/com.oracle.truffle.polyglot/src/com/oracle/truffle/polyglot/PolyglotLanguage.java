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

import java.util.Set;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Language;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.polyglot.PolyglotLocals.LocalLocation;

final class PolyglotLanguage implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    final PolyglotEngineImpl engine;
    final LanguageCache cache;
    final LanguageInfo info;

    Language api; // effectively final
    final int engineIndex;
    final RuntimeException initError;

    private volatile OptionDescriptors options;
    private volatile OptionValuesImpl optionValues;
    private volatile boolean initialized;

    private volatile PolyglotLanguageInstance initLanguage;
    private volatile boolean firstInstance = true;

    @CompilationFinal volatile Class<?> contextClass;
    volatile LocalLocation[] previousContextLocalLocations;
    volatile LocalLocation[] previousContextThreadLocalLocations;

    PolyglotLanguage(PolyglotEngineImpl engine, LanguageCache cache, int engineIndex, RuntimeException initError) {
        this.engine = engine;
        this.cache = cache;
        this.initError = initError;
        this.engineIndex = engineIndex;
        this.info = NODES.createLanguage(this, cache.getId(), cache.getName(), cache.getVersion(), cache.getDefaultMimeType(), cache.getMimeTypes(), cache.isInternal(), cache.isInteractive());
    }

    PolyglotLanguageContext getCurrentLanguageContext() {
        return PolyglotContextImpl.requireContext().contexts[engineIndex];
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
        return engineIndex == PolyglotEngineImpl.HOST_LANGUAGE_INDEX;
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
        ensureInitialized();
        return options;
    }

    private void ensureInitialized() {
        if (!this.initialized) {
            synchronized (engine.lock) {
                if (!this.initialized) {
                    ensureInitialized(getInitLanguage());
                    this.initialized = true;
                }
            }
        }
    }

    PolyglotLanguageInstance getInitLanguage() {
        assert Thread.holdsLock(engine.lock);
        if (initLanguage == null) {
            this.initLanguage = new PolyglotLanguageInstance(this, null);
        }
        return initLanguage;
    }

    @SuppressWarnings("unchecked")
    PolyglotLanguageInstance createInstance(PolyglotSharingLayer sharing) {
        assert Thread.holdsLock(engine.lock);
        if (firstInstance) {
            firstInstance = false;
        }
        PolyglotLanguageInstance instance = null;
        if (initLanguage != null) {
            // reuse init language
            instance = this.initLanguage;
            instance.sharing = sharing;
            initLanguage = null;
        }
        if (instance == null) {
            instance = new PolyglotLanguageInstance(this, sharing);
            ensureInitialized(instance);
        }
        return instance;
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return engine;
    }

    private void ensureInitialized(PolyglotLanguageInstance instance) {
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
    }

    OptionValuesImpl getOptionValues() {
        if (optionValues == null) {
            synchronized (engine.lock) {
                if (optionValues == null) {
                    optionValues = new OptionValuesImpl(getOptionsInternal(), false);
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

    String getWebsite() {
        return cache.getWebsite();
    }
}
