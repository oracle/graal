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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotAccess;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

public class ContextPolyglotAccessTest extends AbstractPolyglotTest {

    public static final String LANGUAGE1 = "ContextPolyglotAccessTestLanguage1";
    public static final String LANGUAGE2 = "ContextPolyglotAccessTestLanguage2";
    public static final String DEPENDENT = "ContextPolyglotAccessTestDependentLanguage";
    public static final String INTERNAL = "ContextPolyglotAccessTestInternalLanguage";
    public static final String LANGUAGE3 = "ContextPolyglotAccessTestLanguage3";

    public static final String NOT_EXISTING_LANGUAGE = "$$$LanguageThatDoesNotExist$$$";

    @Test
    public void testNull() {
        try {
            Context.newBuilder().allowPolyglotAccess(null);
            fail();
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testNotExistingDependent() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE2).allowPolyglotAccess(PolyglotAccess.NONE).build());
        context.initialize(LANGUAGE2);
        Env env2 = Language2.getContext(Language2.class);
        assertTrue(env2.getLanguages().containsKey(LANGUAGE2));
        assertFalse(env2.getLanguages().containsKey(NOT_EXISTING_LANGUAGE));
    }

    @Test
    public void testNotExistingEmbedder() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, NOT_EXISTING_LANGUAGE).allowPolyglotAccess(PolyglotAccess.ALL).build());
        context.initialize(LANGUAGE1);
        try {
            context.initialize(NOT_EXISTING_LANGUAGE);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("A language with id '" + NOT_EXISTING_LANGUAGE + "' is not installed."));
        }
        Env env1 = Language1.getContext(Language1.class);
        assertTrue(env1.getLanguages().containsKey(LANGUAGE1));
        assertFalse(env1.getLanguages().containsKey(NOT_EXISTING_LANGUAGE));
    }

    @Test
    public void testAllAccess() throws UnsupportedMessageException, UnknownIdentifierException {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(PolyglotAccess.ALL).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        try {
            // not an embedder language
            context.initialize(DEPENDENT);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Access to language '" + DEPENDENT + "' is not permitted. ", e.getMessage());
        }
        Env env1 = Language1.getContext(Language1.class);
        Env env2 = Language2.getContext(Language2.class);
        assertTrue(env1.getLanguages().containsKey(INTERNAL));
        assertTrue(env2.getLanguages().containsKey(INTERNAL));
        assertLanguages(env1.getLanguages(), ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, DEPENDENT);
        assertLanguages(env2.getLanguages(), ProxyLanguage.ID, LANGUAGE1, LANGUAGE2);

        assertNotNull(env1.parse(Source.newBuilder(LANGUAGE1, "", "").build()));
        assertNotNull(env1.parse(Source.newBuilder(LANGUAGE2, "", "").build()));
        assertNotNull(env1.parse(Source.newBuilder(DEPENDENT, "", "").build()));
        assertNotNull(env1.parse(Source.newBuilder(INTERNAL, "", "").build()));
        assertAccessNotPermitted(env1, LANGUAGE3);
        assertNotNull(env2.parse(Source.newBuilder(LANGUAGE1, "", "").build()));
        assertNotNull(env2.parse(Source.newBuilder(LANGUAGE2, "", "").build()));
        assertNotNull(env2.parse(Source.newBuilder(INTERNAL, "", "").build()));
        assertAccessNotPermitted(env2, DEPENDENT);
        assertAccessNotPermitted(env2, LANGUAGE3);

        env1.exportSymbol("symbol1", "value");
        assertEquals("value", env1.importSymbol("symbol1"));

        env2.exportSymbol("symbol2", "value");
        assertEquals("value", env2.importSymbol("symbol2"));
        assertEquals("value", env1.importSymbol("symbol2"));

        assertEquals("value", InteropLibrary.getFactory().getUncached().readMember(env1.getPolyglotBindings(), "symbol1"));
        assertEquals("value", InteropLibrary.getFactory().getUncached().readMember(env2.getPolyglotBindings(), "symbol2"));

    }

    private static void assertBindingsNotAccessible(Env env1) {
        try {
            env1.getPolyglotBindings();
            fail();
        } catch (SecurityException e) {
        }
    }

    private static void assertAccessNotPermitted(Env from, String to) {
        try {
            from.parse(Source.newBuilder(to, "", "").build());
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Access to language '" + to + "' is not permitted. ", e.getMessage());
        }
    }

    @Test
    public void testNoAccess() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(PolyglotAccess.NONE).build());
        try {
            // not an embedder language
            context.initialize(DEPENDENT);
            fail();
        } catch (IllegalArgumentException e) {
        }
        testNoAccessImpl();
    }

    @Test
    public void testNoPolyglotAccessWithAllAccess() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowAllAccess(true).allowPolyglotAccess(PolyglotAccess.NONE).build());
        try {
            // not an embedder language
            context.initialize(DEPENDENT);
            fail();
        } catch (IllegalArgumentException e) {
        }
        testNoAccessImpl();
    }

    private void testNoAccessImpl() {
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        Env env1 = Language1.getContext(Language1.class);
        Env env2 = Language2.getContext(Language2.class);
        assertTrue(env1.getLanguages().containsKey(INTERNAL));
        assertTrue(env2.getLanguages().containsKey(INTERNAL));
        assertLanguages(env1.getLanguages(), LANGUAGE1, DEPENDENT);
        assertLanguages(env2.getLanguages(), LANGUAGE2);

        assertNotNull(env1.parse(Source.newBuilder(LANGUAGE1, "", "").build()));
        assertNotNull(env1.parse(Source.newBuilder(INTERNAL, "", "").build()));
        assertNotNull(env1.parse(Source.newBuilder(DEPENDENT, "", "").build()));
        assertAccessNotPermitted(env1, LANGUAGE2);
        assertAccessNotPermitted(env1, LANGUAGE3);
        assertNotNull(env2.parse(Source.newBuilder(LANGUAGE2, "", "").build()));
        assertNotNull(env2.parse(Source.newBuilder(INTERNAL, "", "").build()));
        assertAccessNotPermitted(env2, DEPENDENT);
        assertAccessNotPermitted(env2, LANGUAGE1);
        assertAccessNotPermitted(env2, LANGUAGE3);

        assertExportNotAcccessible(env1);
        assertImportNotAcccessible(env1);

        assertExportNotAcccessible(env2);
        assertImportNotAcccessible(env2);

        assertBindingsNotAccessible(env1);
        assertBindingsNotAccessible(env2);
    }

    private static void assertImportNotAcccessible(Env env1) {
        try {
            env1.importSymbol("symbol1");
            fail();
        } catch (SecurityException e) {
            assertEquals("Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.", e.getMessage());
        }
    }

    private static void assertExportNotAcccessible(Env env1) {
        try {
            env1.exportSymbol("symbol1", "value");
            fail();
        } catch (SecurityException e) {
            assertEquals("Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.", e.getMessage());
        }
    }

    @Test
    public void testAllLanguagesNoAccess() {
        setupEnv(Context.newBuilder().allowPolyglotAccess(PolyglotAccess.NONE).build());
        context.initialize(DEPENDENT);
        testNoAccessImpl();
    }

    private static void assertLanguages(Map<String, LanguageInfo> languages, String... expectedLanguages) {
        List<String> nonInternal = new ArrayList<>();
        for (LanguageInfo language : languages.values()) {
            if (!language.isInternal()) {
                nonInternal.add(language.getId());
            }
        }
        assertEquals(Arrays.toString(expectedLanguages) + " was " + nonInternal.toString(), expectedLanguages.length, nonInternal.size());
        for (String language : expectedLanguages) {
            assertTrue(language, languages.containsKey(language));
        }
    }

    static class MyClass {

    }

    @Test
    public void testPolyglotExportPromotion() {
        setupEnv();
        context.initialize(LANGUAGE1);

        Env env1 = Language1.getContext(Language1.class);

        try {
            env1.exportSymbol("symbol", new Object());
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            env1.exportSymbol("symbol", new BigDecimal("42"));
            fail();
        } catch (IllegalArgumentException e) {
        }

        try {
            env1.exportSymbol("symbol", new MyClass());
            fail();
        } catch (IllegalArgumentException e) {
        }

        env1.exportSymbol("symbol", "");
        env1.exportSymbol("symbol", 'a');
        env1.exportSymbol("symbol", true);
        env1.exportSymbol("symbol", (byte) 42);
        env1.exportSymbol("symbol", (short) 42);
        env1.exportSymbol("symbol", 42);
        env1.exportSymbol("symbol", 42L);
        env1.exportSymbol("symbol", 42f);
        env1.exportSymbol("symbol", 42d);
        env1.exportSymbol("symbol", new TruffleObject() {
        });
    }

    @Registration(id = LANGUAGE1, name = LANGUAGE1, dependentLanguages = DEPENDENT)
    public static class Language1 extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(true));
        }

        public static <T extends TruffleLanguage<C>, C> C getContext(Class<T> language) {
            return getCurrentContext(language);
        }

        public static <T extends TruffleLanguage<?>> T getLanguage(Class<T> language) {
            return getCurrentLanguage(language);
        }

    }

    @Registration(id = LANGUAGE2, name = LANGUAGE2, dependentLanguages = NOT_EXISTING_LANGUAGE)
    public static class Language2 extends Language1 {
    }

    @Registration(id = DEPENDENT, name = DEPENDENT)
    public static class Dependent extends Language1 {
    }

    @Registration(id = INTERNAL, name = INTERNAL, internal = true)
    public static class Internal extends Language1 {
    }

    @Registration(id = LANGUAGE3, name = LANGUAGE3)
    public static class Language3 extends Language1 {
    }

}
