/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.nodes.Node;

/**
 * Note most test coverage for context and language suppliers is achieved with
 * {@link ContextPolicyTest}. This test class only tests the direct API contracts.
 */
public class ContextAndLanguageSupplierTest extends AbstractPolyglotTest {

    public static final String LANGUAGE1 = "ContextAndLanguageSupplierTestLanguage1";
    public static final String LANGUAGE2 = "ContextAndLanguageSupplierTestLanguage2";
    public static final String LANGUAGE3 = "ContextAndLanguageSupplierTestLanguage3";
    public static final String LANGUAGE4 = "ContextAndLanguageSupplierTestLanguage4";
    public static final String LANGUAGE5 = "ContextAndLanguageSupplierTestLanguage5";
    public static final String LANGUAGE6 = "ContextAndLanguageSupplierTestLanguage6";
    private static final List<String> LANGUAGES = Arrays.asList(LANGUAGE1, LANGUAGE2, LANGUAGE3, LANGUAGE4, LANGUAGE5, LANGUAGE6);
    private static final List<Class<? extends Language1>> LANGUAGE_CLASSES = Arrays.asList(Language1.class, Language2.class, Language3.class, Language4.class, Language5.class, Language6.class);

    @Test
    public void testContextSupplier() {
        setupEnv();
        for (String lang : LANGUAGES) {
            context.initialize(lang);
        }
        adoptNode(this.language, new ContextSupplierTestNode()).get().execute();
    }

    @Test
    public void testLanguageSupplier() {
        setupEnv();
        for (String lang : LANGUAGES) {
            context.initialize(lang);
        }
        adoptNode(this.language, new LanguageSupplierTestNode()).get().execute();
    }

    private static class ContextSupplierTestNode extends Node {

        ContextSupplierTestNode() {
            try {
                lookupContextReference(ProxyLanguage.class);
                fail();
            } catch (IllegalStateException e) {
            }
        }

        @SuppressWarnings("unchecked")
        public void execute() {
            ContextReference<?> currentSupplier = lookupContextReference(ProxyLanguage.class);
            try {
                lookupContextReference(TruffleLanguage.class);
                fail();
            } catch (IllegalArgumentException e) {
            }
            try {
                lookupContextReference(InvalidLanguage.class);
                fail();
            } catch (IllegalArgumentException e) {
            }
            try {
                lookupContextReference(null);
            } catch (NullPointerException e) {
            }

            for (Class<? extends Language1> language : LANGUAGE_CLASSES) {
                ContextReference<Env> supplier = lookupContextReference(language);
                Env value = supplier.get();
                assertSame(Language1.getContext(language), value);
                assertSame(value, supplier.get());
            }

            assertSame(currentSupplier, lookupContextReference(ProxyLanguage.class));
        }
    }

    private static class LanguageSupplierTestNode extends Node {

        LanguageSupplierTestNode() {
            try {
                lookupLanguageReference(ProxyLanguage.class);
                fail();
            } catch (IllegalStateException e) {
            }
        }

        @SuppressWarnings("unchecked")
        public void execute() {
            try {
                lookupLanguageReference(TruffleLanguage.class);
                fail();
            } catch (IllegalArgumentException e) {
            }
            try {
                lookupLanguageReference(InvalidLanguage.class);
                fail();
            } catch (IllegalArgumentException e) {
            }
            try {
                lookupLanguageReference(null);
            } catch (NullPointerException e) {
            }

            for (Class<? extends Language1> language : LANGUAGE_CLASSES) {
                LanguageReference<? extends Language1> supplier = lookupLanguageReference(language);
                Language1 value = supplier.get();
                assertSame(Language1.getLanguage(language), value);
                assertSame(value, supplier.get());
            }
        }
    }

    @Registration(id = LANGUAGE1, name = LANGUAGE1)
    public static class Language1 extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        public static <T extends TruffleLanguage<C>, C> C getContext(Class<T> language) {
            return getCurrentContext(language);
        }

        public static <T extends TruffleLanguage<?>> T getLanguage(Class<T> language) {
            return getCurrentLanguage(language);
        }

    }

    @Registration(id = LANGUAGE2, name = LANGUAGE2)
    public static class Language2 extends Language1 {
    }

    @Registration(id = LANGUAGE3, name = LANGUAGE3)
    public static class Language3 extends Language1 {
    }

    @Registration(id = LANGUAGE4, name = LANGUAGE4)
    public static class Language4 extends Language1 {
    }

    @Registration(id = LANGUAGE5, name = LANGUAGE5)
    public static class Language5 extends Language1 {
    }

    @Registration(id = LANGUAGE6, name = LANGUAGE6)
    public static class Language6 extends Language1 {
    }

    public static class InvalidLanguage extends Language1 {
    }

}
