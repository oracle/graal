/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;

public class PolyglotReferences {

    static ContextReference<Object> createAlwaysSingleContext(PolyglotLanguage language) {
        return new SingleContext(language);
    }

    static ContextReference<Object> createAssumeSingleContext(PolyglotLanguage language,
                    Assumption validIf,
                    ContextReference<Object> fallback) {
        return new AssumeSingleContext(language, validIf, fallback);
    }

    static ContextReference<Object> createAlwaysMultiContext(PolyglotLanguage language) {
        return new MultiContextSupplier(language);
    }

    static LanguageReference<TruffleLanguage<Object>> createAlwaysSingleLanguage(PolyglotLanguage language, PolyglotLanguageInstance initValue) {
        return new SingleLanguage(language, initValue);
    }

    static LanguageReference<TruffleLanguage<Object>> createAssumeSingleLanguage(PolyglotLanguage language,
                    PolyglotLanguageInstance initValue,
                    Assumption validIf,
                    LanguageReference<TruffleLanguage<Object>> fallback) {
        return new AssumeSingleLanguage(language, initValue, validIf, fallback);
    }

    static LanguageReference<TruffleLanguage<Object>> createAlwaysMultiLanguage(PolyglotLanguage language) {
        return new MultiLanguageSupplier(language);
    }

    private static final class SingleLanguage extends LanguageReference<TruffleLanguage<Object>> {

        private final PolyglotLanguage language;
        @CompilationFinal private TruffleLanguage<Object> spi;

        @SuppressWarnings("unchecked")
        SingleLanguage(PolyglotLanguage language, PolyglotLanguageInstance initValue) {
            this.language = language;
            this.spi = initValue != null ? initValue.spi : null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public TruffleLanguage<Object> get() {
            assert language.assertCorrectEngine();
            TruffleLanguage<Object> languageSpi = this.spi;
            if (languageSpi == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.spi = languageSpi = language.getCurrentLanguageContext().getLanguageInstance().spi;
            }
            assert language.getConservativeLanguageReference().get() == languageSpi;
            return languageSpi;
        }

    }

    private static final class SingleContext extends ContextReference<Object> {

        private static final Object UNSET = new Object();

        final PolyglotLanguage language;
        @CompilationFinal private Object contextInstance = UNSET;

        SingleContext(PolyglotLanguage language) {
            this.language = language;
        }

        @Override
        public Object get() {
            assert language.assertCorrectEngine();
            Object context = this.contextInstance;
            if (context == UNSET) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextInstance = context = language.getCurrentLanguageContext().getContextImpl();
            }
            assert language.getConservativeContextReference().get() == context;
            return context;
        }
    }

    private static final class AssumeSingleContext extends ContextReference<Object> {

        private final WeakReference<ContextReference<Object>> singleContextReference;
        private final ContextReference<Object> fallbackReference;
        private final Assumption singleContext;

        AssumeSingleContext(PolyglotLanguage language, Assumption singleContextAssumption, ContextReference<Object> fallback) {
            this.singleContext = singleContextAssumption;
            this.singleContextReference = new WeakReference<>(createAlwaysSingleContext(language));
            this.fallbackReference = fallback;
        }

        @Override
        public Object get() {
            if (singleContext.isValid()) {
                ContextReference<Object> supplier = singleContextReference.get();
                if (supplier != null) {
                    Object result = supplier.get();
                    assert fallbackReference.get() == result;
                    return result;
                }
            }
            return fallbackReference.get();
        }
    }

    private static final class AssumeSingleLanguage extends LanguageReference<TruffleLanguage<Object>> {

        private final WeakReference<LanguageReference<TruffleLanguage<Object>>> singleLanguageReference;
        private final LanguageReference<TruffleLanguage<Object>> fallbackReference;
        private final Assumption singleLanguage;

        AssumeSingleLanguage(PolyglotLanguage language, PolyglotLanguageInstance initValue, Assumption singleContextAssumption, LanguageReference<TruffleLanguage<Object>> fallbackReference) {
            this.singleLanguage = singleContextAssumption;
            this.singleLanguageReference = new WeakReference<>(createAlwaysSingleLanguage(language, initValue));
            this.fallbackReference = fallbackReference;
        }

        @Override
        public TruffleLanguage<Object> get() {
            if (singleLanguage.isValid()) {
                LanguageReference<TruffleLanguage<Object>> supplier = singleLanguageReference.get();
                if (supplier != null) {
                    TruffleLanguage<Object> result = supplier.get();
                    assert fallbackReference.get() == result;
                    return result;
                }
            }
            return fallbackReference.get();
        }
    }

    private static final class MultiLanguageSupplier extends LanguageReference<TruffleLanguage<Object>> {

        final PolyglotLanguage language;

        MultiLanguageSupplier(PolyglotLanguage language) {
            this.language = language;
        }

        @SuppressWarnings("unchecked")
        @Override
        public TruffleLanguage<Object> get() {
            assert language.assertCorrectEngine();
            return PolyglotContextImpl.requireContext().getContext(language).getLanguageInstance().spi;
        }
    }

    private static final class MultiContextSupplier extends ContextReference<Object> {

        final PolyglotLanguage language;

        MultiContextSupplier(PolyglotLanguage language) {
            this.language = language;
        }

        @Override
        public Object get() {
            assert language.assertCorrectEngine();
            return PolyglotContextImpl.requireContext().getContext(language).getContextImpl();
        }

    }
}
