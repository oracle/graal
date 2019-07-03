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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
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
    public void testAllAccess() {
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

        assertNotNull(env1.parsePublic(Source.newBuilder(LANGUAGE1, "", "").build()));
        assertNotNull(env1.parsePublic(Source.newBuilder(LANGUAGE2, "", "").build()));
        assertNotNull(env1.parsePublic(Source.newBuilder(DEPENDENT, "", "").build()));
        assertNotNull(env1.parseInternal(Source.newBuilder(INTERNAL, "", "").internal(true).build()));
        assertNotAccessible(env1, LANGUAGE3);
        assertNotNull(env2.parsePublic(Source.newBuilder(LANGUAGE1, "", "").build()));
        assertNotNull(env2.parsePublic(Source.newBuilder(LANGUAGE2, "", "").build()));
        assertNotNull(env2.parseInternal(Source.newBuilder(INTERNAL, "", "").internal(true).build()));
        assertNotAccessible(env2, DEPENDENT);
        assertNotAccessible(env2, LANGUAGE3);

        testPolyglotAccess(env1, env2);
    }

    private static void testPolyglotAccess(Env env1, Env env2) {
        try {
            env1.exportSymbol("symbol1", "value");
            assertEquals("value", env1.importSymbol("symbol1"));

            env2.exportSymbol("symbol2", "value");
            assertEquals("value", env2.importSymbol("symbol2"));
            assertEquals("value", env1.importSymbol("symbol2"));

            assertEquals("value", InteropLibrary.getFactory().getUncached().readMember(env1.getPolyglotBindings(), "symbol1"));
            assertEquals("value", InteropLibrary.getFactory().getUncached().readMember(env2.getPolyglotBindings(), "symbol2"));
        } catch (InteropException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertBindingsNotAccessible(Env env1) {
        try {
            env1.getPolyglotBindings();
            fail();
        } catch (SecurityException e) {
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

        assertAccessible(env1, LANGUAGE1);
        assertNotAccessible(env1, LANGUAGE2);
        assertNotAccessible(env1, LANGUAGE3);
        assertAccessible(env1, INTERNAL);
        assertAccessible(env1, DEPENDENT);

        assertAccessible(env2, LANGUAGE2);
        assertAccessible(env2, INTERNAL);
        assertNotAccessible(env2, DEPENDENT);
        assertNotAccessible(env2, LANGUAGE1);
        assertNotAccessible(env2, LANGUAGE3);

        assertNoPolyglotAccess(env1);
        assertNoPolyglotAccess(env2);
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

    @Test
    public void testCustomPolyglotAccessDirect() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowAccess(LANGUAGE1, LANGUAGE2).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertAccessible(language1, LANGUAGE1);
        assertAccessible(language1, LANGUAGE2);
        assertAccessible(language2, LANGUAGE2);
        assertNotAccessible(language2, LANGUAGE1);

        assertPolyglotAccess(language1);
        assertNoPolyglotAccess(language2);
    }

    @Test
    public void testCustomPolyglotAccessDirectSame() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowAccess(LANGUAGE1, LANGUAGE1).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertAccessible(language1, LANGUAGE1);
        assertNotAccessible(language1, LANGUAGE2);
        assertAccessible(language2, LANGUAGE2);
        assertNotAccessible(language2, LANGUAGE1);

        assertNoPolyglotAccess(language1);
        assertNoPolyglotAccess(language2);
    }

    @Test
    public void testCustomPolyglotAccessBetween() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowAccessBetween(LANGUAGE1, LANGUAGE2).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertAccessible(language1, LANGUAGE1);
        assertAccessible(language1, LANGUAGE2);
        assertAccessible(language2, LANGUAGE2);
        assertAccessible(language2, LANGUAGE1);

        assertPolyglotAccess(language1);
        assertPolyglotAccess(language2);
    }

    @Test
    public void testCustomPolyglotAccessBetweenSame() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowAccessBetween(LANGUAGE1, LANGUAGE1).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertAccessible(language1, LANGUAGE1);
        assertNotAccessible(language1, LANGUAGE2);
        assertAccessible(language2, LANGUAGE2);
        assertNotAccessible(language2, LANGUAGE1);

        assertNoPolyglotAccess(language1);
        assertNoPolyglotAccess(language2);
    }

    @Test
    public void testCustomPolyglotAccessBetweenThree() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowAccessBetween(LANGUAGE1, LANGUAGE2, LANGUAGE3).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, LANGUAGE3).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        context.initialize(LANGUAGE3);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);
        Env language3 = Language2.getContext(Language3.class);

        assertAccessible(language1, LANGUAGE1);
        assertAccessible(language1, LANGUAGE2);
        assertAccessible(language1, LANGUAGE3);

        assertAccessible(language2, LANGUAGE1);
        assertAccessible(language2, LANGUAGE2);
        assertAccessible(language2, LANGUAGE3);

        assertAccessible(language3, LANGUAGE1);
        assertAccessible(language3, LANGUAGE2);
        assertAccessible(language3, LANGUAGE3);

        assertPolyglotAccess(language1);
        assertPolyglotAccess(language2);
        assertPolyglotAccess(language3);
    }

    @Test
    public void testCustomPolyglotAccessBetweenThree2() {
        PolyglotAccess access = PolyglotAccess.newBuilder().//
                        allowAccessBetween(LANGUAGE1, LANGUAGE2, LANGUAGE3).//
                        denyAccessBetween(LANGUAGE1, LANGUAGE2).//
                        build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, LANGUAGE3).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        context.initialize(LANGUAGE3);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);
        Env language3 = Language2.getContext(Language3.class);

        assertAccessible(language1, LANGUAGE1);
        assertNotAccessible(language1, LANGUAGE2);
        assertAccessible(language1, LANGUAGE3);

        assertNotAccessible(language2, LANGUAGE1);
        assertAccessible(language2, LANGUAGE2);
        assertAccessible(language2, LANGUAGE3);

        assertAccessible(language3, LANGUAGE1);
        assertAccessible(language3, LANGUAGE2);
        assertAccessible(language3, LANGUAGE3);

        assertPolyglotAccess(language1);
        assertPolyglotAccess(language2);
        assertPolyglotAccess(language3);
    }

    @Test
    public void testCustomPolyglotAccessSingleDeny() {
        PolyglotAccess access = PolyglotAccess.newBuilder().//
                        allowAccessBetween(LANGUAGE1, LANGUAGE2).//
                        denyAccess(LANGUAGE1, LANGUAGE2).//
                        build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertAccessible(language1, LANGUAGE1);
        assertNotAccessible(language1, LANGUAGE2);

        assertAccessible(language2, LANGUAGE1);
        assertAccessible(language2, LANGUAGE2);

        assertNoPolyglotAccess(language1);
        assertPolyglotAccess(language2);
    }

    @Test
    public void testCustomPolyglotAccessErrors() {
        PolyglotAccess.Builder builder = PolyglotAccess.newBuilder();

        ValueAssert.assertFails(() -> builder.allowAccess(null, ""), NullPointerException.class);
        ValueAssert.assertFails(() -> builder.allowAccess("", null), NullPointerException.class);
        ValueAssert.assertFails(() -> builder.denyAccess(null, ""), NullPointerException.class);
        ValueAssert.assertFails(() -> builder.denyAccess("", null), NullPointerException.class);
        ValueAssert.assertFails(() -> builder.allowAccessBetween((String[]) null), NullPointerException.class);
        ValueAssert.assertFails(() -> builder.denyAccessBetween((String[]) null), NullPointerException.class);
        ValueAssert.assertFails(() -> builder.allowAccessBetween((String) null), NullPointerException.class);
        ValueAssert.assertFails(() -> builder.denyAccessBetween((String) null), NullPointerException.class);

        PolyglotAccess access = PolyglotAccess.newBuilder().allowAccess(NOT_EXISTING_LANGUAGE, LANGUAGE1).build();
        Context.Builder cBuilder = Context.newBuilder().allowPolyglotAccess(access);
        ValueAssert.assertFails(() -> cBuilder.build(), IllegalArgumentException.class);

        access = PolyglotAccess.newBuilder().allowAccess(LANGUAGE1, LANGUAGE2).build();
        Context.Builder cBuilder2 = Context.newBuilder(LANGUAGE2).allowPolyglotAccess(access);
        ValueAssert.assertFails(() -> cBuilder2.build(), IllegalArgumentException.class,
                        (e) -> assertEquals("Language '" + LANGUAGE1 + "' configured in polyglot access rule " + LANGUAGE1 + " -> " + LANGUAGE2 + " is not installed or available.", e.getMessage()));
    }

    @Test
    public void testParsePublic() {
        setupEnv();
        context.initialize(LANGUAGE1);

        Env language1 = Language1.getContext(Language1.class);

        ValueAssert.assertFails(() -> language1.parsePublic(Source.newBuilder(INTERNAL, "", "").build()), IllegalStateException.class);
        ValueAssert.assertFails(() -> language1.parseInternal(Source.newBuilder(INTERNAL, "", "").build()), IllegalArgumentException.class);

        assertNotNull(language1.parseInternal(Source.newBuilder(INTERNAL, "", "").internal(true).build()));
        assertNotNull(language1.parsePublic(Source.newBuilder(LANGUAGE1, "", "").build()));
        assertNotNull(language1.parsePublic(Source.newBuilder(LANGUAGE2, "", "").build()));
    }

    private static void assertNoPolyglotAccess(Env env) {
        assertFalse(env.isPolyglotAccessAllowed());
        assertExportNotAcccessible(env);
        assertImportNotAcccessible(env);
        assertBindingsNotAccessible(env);
    }

    private static void assertPolyglotAccess(Env env) {
        assertTrue(env.isPolyglotAccessAllowed());
        testPolyglotAccess(env, env);
    }

    private static void assertNotAccessible(Env env, String targetId) {
        assertFalse(env.getLanguages().containsKey(targetId));
        try {
            env.parseInternal(Source.newBuilder(targetId, "", "").internal(true).build());
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("No language for id " + targetId + " found. "));
        }
    }

    private static void assertAccessible(Env env, String targetId) {
        assertTrue(env.getLanguages().containsKey(targetId));
        boolean internal = env.getLanguages().get(targetId).isInternal();
        assertNotNull(env.parseInternal(Source.newBuilder(targetId, "", "").internal(true).build()));
        if (!internal) {
            assertNotNull(env.parsePublic(Source.newBuilder(targetId, "", "").build()));
        }
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
