/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.polyglot.VMAccessor.LANGUAGE;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.polyglot.PolyglotImpl.VMObject;
import com.oracle.truffle.polyglot.PolyglotValue.InteropCodeCache;

final class PolyglotLanguageInstance implements VMObject {

    final PolyglotLanguage language;
    final TruffleLanguage<?> spi;

    private final PolyglotSourceCache sourceCache;
    final Map<Class<?>, InteropCodeCache> valueCodeCache;
    final Map<Object, Object> hostInteropCodeCache;

    private volatile OptionValuesImpl firstOptionValues;
    private volatile boolean needsInitializeMultiContext;

    private final LanguageReference<TruffleLanguage<Object>> directLanguageSupplier;
    private final LanguageReference<TruffleLanguage<Object>> singleOrMultiLanguageSupplier;
    private final ContextReference<Object> directContextSupplier;
    final Assumption singleContext;

    PolyglotLanguageInstance(PolyglotLanguage language) {
        this.language = language;
        this.sourceCache = new PolyglotSourceCache();
        this.valueCodeCache = new ConcurrentHashMap<>();
        this.hostInteropCodeCache = new ConcurrentHashMap<>();
        this.singleContext = Truffle.getRuntime().createAssumption("Single context per language instance.");
        try {
            this.spi = language.cache.loadLanguage();
            LANGUAGE.initializeLanguage(spi, language.info, language, this);
            if (!language.engine.singleContext.isValid()) {
                initializeMultiContext();
            } else {
                this.needsInitializeMultiContext = !language.engine.boundEngine;
            }
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Error initializing language '%s' using class '%s'.", language.cache.getId(), language.cache.getClassName()), e);
        }
        if (PolyglotLanguage.CONSERVATIVE_REFERENCES) {
            this.directContextSupplier = language.getContextReference();
            this.singleOrMultiLanguageSupplier = language.getMultiLanguageReference();
            this.directLanguageSupplier = singleOrMultiLanguageSupplier;
        } else {
            if (language.engine.boundEngine && language.cache.getPolicy() == ContextPolicy.EXCLUSIVE) {
                this.directContextSupplier = new DirectSingleContextSupplier(this);
            } else {
                if (this.singleContext.isValid()) {
                    this.directContextSupplier = new DirectSingleOrMultiContextSupplier(this);
                } else {
                    this.directContextSupplier = language.getContextReference();
                }
            }
            this.directLanguageSupplier = new DirectLanguageSupplier(this);
            this.singleOrMultiLanguageSupplier = new DirectOrMultiLanguageSupplier(this, directLanguageSupplier);
        }
    }

    public PolyglotEngineImpl getEngine() {
        return language.engine;
    }

    boolean areOptionsCompatible(OptionValuesImpl newOptionValues) {
        OptionValuesImpl firstOptions = this.firstOptionValues;
        if (firstOptionValues == null) {
            return true;
        } else {
            return VMAccessor.LANGUAGE.areOptionsCompatible(spi, firstOptions, newOptionValues);
        }
    }

    void claim(OptionValuesImpl optionValues) {
        assert Thread.holdsLock(language.engine);
        if (this.firstOptionValues == null) {
            this.firstOptionValues = optionValues;
        }
    }

    void ensureMultiContextInitialized() {
        assert Thread.holdsLock(language.engine);
        if (needsInitializeMultiContext) {
            needsInitializeMultiContext = false;
            language.engine.initializeMultiContext(null);
            initializeMultiContext();
        }
    }

    void initializeMultiContext() {
        assert !language.engine.singleContext.isValid();
        if (language.cache.getPolicy() != ContextPolicy.EXCLUSIVE) {
            this.singleContext.invalidate();
            LANGUAGE.initializeMultiContext(spi);
        }
    }

    PolyglotSourceCache getSourceCache() {
        return sourceCache;
    }

    ContextReference<Object> getDirectContextSupplier() {
        return directContextSupplier;
    }

    ContextReference<Object> lookupContextSupplier(PolyglotLanguageInstance sourceLanguage) {
        assert this != sourceLanguage;
        switch (getEffectiveContextPolicy(sourceLanguage)) {
            case EXCLUSIVE:
                return this.directContextSupplier;
            case REUSE:
            case SHARED:
                return this.language.getContextReference();
            default:
                throw new AssertionError();
        }
    }

    LanguageReference<TruffleLanguage<Object>> getDirectLanguageSupplier() {
        return directLanguageSupplier;
    }

    LanguageReference<TruffleLanguage<Object>> lookupLanguageSupplier(PolyglotLanguageInstance sourceLanguage) {
        assert this != sourceLanguage;
        switch (getEffectiveContextPolicy(sourceLanguage)) {
            case EXCLUSIVE:
                return this.directLanguageSupplier;
            case REUSE:
            case SHARED:
                if (this.language.singleLanguage.isValid()) {
                    return this.singleOrMultiLanguageSupplier;
                } else {
                    return this.language.getMultiLanguageReference();
                }
            default:
                throw new AssertionError();
        }
    }

    private ContextPolicy getEffectiveContextPolicy(PolyglotLanguageInstance sourceRootLanguage) {
        ContextPolicy sourcePolicy;
        if (language.engine.boundEngine) {
            // with a bound engine context policy is effectively always exclusive
            sourcePolicy = ContextPolicy.EXCLUSIVE;
        } else {
            if (sourceRootLanguage != null) {
                sourcePolicy = sourceRootLanguage.language.cache.getPolicy();
            } else {
                // null source language means shared policy
                sourcePolicy = ContextPolicy.SHARED;
            }
        }
        return sourcePolicy;
    }

    private static final class DirectLanguageSupplier extends LanguageReference<TruffleLanguage<Object>> {

        private final TruffleLanguage<Object> spi;
        private final PolyglotLanguageInstance instance;

        @SuppressWarnings("unchecked")
        DirectLanguageSupplier(PolyglotLanguageInstance instance) {
            this.spi = (TruffleLanguage<Object>) instance.spi;
            this.instance = instance;
        }

        @Override
        public TruffleLanguage<Object> get() {
            assert instance.language.getMultiLanguageReference().get() == spi;
            return this.spi;
        }

    }

    private static final class DirectOrMultiLanguageSupplier extends LanguageReference<TruffleLanguage<Object>> {

        private final WeakReference<LanguageReference<TruffleLanguage<Object>>> singleLanguageSupplier;
        private final LanguageReference<TruffleLanguage<Object>> multiLanguageReference;
        private final Assumption singleLanguage;

        DirectOrMultiLanguageSupplier(PolyglotLanguageInstance targetLanguage, LanguageReference<TruffleLanguage<Object>> directSupplier) {
            this.singleLanguageSupplier = new WeakReference<>(directSupplier);
            this.singleLanguage = targetLanguage.language.singleLanguage;
            this.multiLanguageReference = targetLanguage.language.getMultiLanguageReference();
        }

        @Override
        public TruffleLanguage<Object> get() {
            if (singleLanguage.isValid()) {
                LanguageReference<TruffleLanguage<Object>> supplier = singleLanguageSupplier.get();
                if (supplier != null) {
                    TruffleLanguage<Object> language = supplier.get();
                    assert multiLanguageReference.get() == language;
                    return language;
                }
            }
            return multiLanguageReference.get();
        }
    }

    private static final class DirectSingleContextSupplier extends ContextReference<Object> {

        private static final Object UNSET = new Object();

        final PolyglotLanguageInstance instance;
        @CompilationFinal private Object contextInstance = UNSET;

        DirectSingleContextSupplier(PolyglotLanguageInstance instance) {
            this.instance = instance;
        }

        @Override
        public Object get() {
            assert instance.language.assertCorrectEngine();
            Object context = this.contextInstance;
            if (context == UNSET) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextInstance = context = instance.language.getCurrentLanguageContext().getContextImpl();
            }
            assert instance.language.getMultiContextReference().get() == context;
            return context;
        }
    }

    private static final class DirectSingleOrMultiContextSupplier extends ContextReference<Object> {

        private final WeakReference<DirectSingleContextSupplier> singleContextSupplier;
        private final ContextReference<Object> multiContextSupplier;
        private final Assumption singleContext;

        DirectSingleOrMultiContextSupplier(PolyglotLanguageInstance instance) {
            this.singleContext = instance.singleContext;
            this.singleContextSupplier = new WeakReference<>(new DirectSingleContextSupplier(instance));
            this.multiContextSupplier = instance.language.getContextReference();
        }

        @Override
        public Object get() {
            if (singleContext.isValid()) {
                DirectSingleContextSupplier supplier = singleContextSupplier.get();
                if (supplier != null) {
                    return supplier.get();
                }
            }
            return multiContextSupplier.get();
        }
    }

}
