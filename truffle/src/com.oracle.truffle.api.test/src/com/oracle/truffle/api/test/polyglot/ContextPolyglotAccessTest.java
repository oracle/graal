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
        assertTrue(env2.getInternalLanguages().containsKey(LANGUAGE2));
        assertFalse(env2.getInternalLanguages().containsKey(NOT_EXISTING_LANGUAGE));
    }

    @Test
    public void testEmbedderAccessDependent() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, DEPENDENT, LANGUAGE1).allowPolyglotAccess(PolyglotAccess.ALL).build());
        context.initialize(LANGUAGE1);

        Env language1 = Language1.getContext(Language1.class);
        language1.initializeLanguage(language1.getInternalLanguages().get(DEPENDENT));

        Env dependent = Dependent.getContext(Dependent.class);
        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalAllowed(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalDenied(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalDenied(language1, LANGUAGE2);

        assertPublicEvalDenied(dependent, INTERNAL);
        assertPublicEvalAllowed(dependent, DEPENDENT);
        assertPublicEvalAllowed(dependent, LANGUAGE1);
        assertPublicEvalDenied(dependent, LANGUAGE2);

        assertInternalEvalAllowed(dependent, INTERNAL);
        assertInternalEvalAllowed(dependent, DEPENDENT);
        assertInternalEvalAllowed(dependent, LANGUAGE1);
        assertInternalEvalDenied(dependent, LANGUAGE2);
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
        assertTrue(env1.getInternalLanguages().containsKey(LANGUAGE1));
        assertFalse(env1.getInternalLanguages().containsKey(NOT_EXISTING_LANGUAGE));
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
        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalAllowed(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalAllowed(language1, LANGUAGE2);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalAllowed(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalAllowed(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);

        testPolyglotAccess(language1, language2);
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
        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);
        assertTrue(language1.getInternalLanguages().containsKey(INTERNAL));
        assertTrue(language2.getInternalLanguages().containsKey(INTERNAL));
        assertLanguages(language1.getInternalLanguages(), LANGUAGE1, DEPENDENT);
        assertLanguages(language2.getInternalLanguages(), LANGUAGE2);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalDenied(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalDenied(language1, LANGUAGE2);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalDenied(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalDenied(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);

        assertBindingsDenied(language1);
        assertBindingsDenied(language2);
        assertNoEvalAccess(language1);
        assertNoEvalAccess(language2);
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
    public void testCustomPolyglotEvalDirect() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEval(LANGUAGE1, LANGUAGE2).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalAllowed(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalAllowed(language1, LANGUAGE2);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalDenied(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalDenied(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);

        assertNoEvalAccess(language2);
    }

    @Test
    public void testCustomPolyglotEvalDirectSame() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEval(LANGUAGE1, LANGUAGE1).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalDenied(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalDenied(language1, LANGUAGE2);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalDenied(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalDenied(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);

        assertNoEvalAccess(language1);
        assertNoEvalAccess(language2);
    }

    @Test
    public void testCustomPolyglotEvalBetween() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEvalBetween(LANGUAGE1, LANGUAGE2).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalAllowed(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalAllowed(language1, LANGUAGE2);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalAllowed(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalAllowed(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);
    }

    @Test
    public void testCustomPolyglotEvalBetweenSame() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEvalBetween(LANGUAGE1, LANGUAGE1).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalDenied(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalDenied(language1, LANGUAGE2);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalDenied(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalDenied(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);

        assertNoEvalAccess(language1);
        assertNoEvalAccess(language2);
    }

    @Test
    public void testCustomPolyglotEvalBetweenThree() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEvalBetween(LANGUAGE1, LANGUAGE2, LANGUAGE3).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, LANGUAGE3).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        context.initialize(LANGUAGE3);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);
        Env language3 = Language2.getContext(Language3.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalAllowed(language1, LANGUAGE2);
        assertPublicEvalAllowed(language1, LANGUAGE3);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalAllowed(language1, LANGUAGE2);
        assertInternalEvalAllowed(language1, LANGUAGE3);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalAllowed(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);
        assertPublicEvalAllowed(language2, LANGUAGE3);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalAllowed(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);
        assertInternalEvalAllowed(language2, LANGUAGE3);

        assertPublicEvalDenied(language3, INTERNAL);
        assertPublicEvalDenied(language3, DEPENDENT);
        assertPublicEvalAllowed(language3, LANGUAGE1);
        assertPublicEvalAllowed(language3, LANGUAGE2);
        assertPublicEvalAllowed(language3, LANGUAGE3);

        assertInternalEvalAllowed(language3, INTERNAL);
        assertInternalEvalDenied(language3, DEPENDENT);
        assertInternalEvalAllowed(language3, LANGUAGE1);
        assertInternalEvalAllowed(language3, LANGUAGE2);
        assertInternalEvalAllowed(language3, LANGUAGE3);
    }

    @Test
    public void testCustomPolyglotEvalBetweenThree2() {
        PolyglotAccess access = PolyglotAccess.newBuilder().//
                        allowEvalBetween(LANGUAGE1, LANGUAGE2, LANGUAGE3).//
                        denyEvalBetween(LANGUAGE1, LANGUAGE2).//
                        build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, LANGUAGE3).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        context.initialize(LANGUAGE3);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);
        Env language3 = Language2.getContext(Language3.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalDenied(language1, LANGUAGE2);
        assertPublicEvalAllowed(language1, LANGUAGE3);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalDenied(language1, LANGUAGE2);
        assertInternalEvalAllowed(language1, LANGUAGE3);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalDenied(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);
        assertPublicEvalAllowed(language2, LANGUAGE3);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalDenied(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);
        assertInternalEvalAllowed(language2, LANGUAGE3);

        assertPublicEvalDenied(language3, INTERNAL);
        assertPublicEvalDenied(language3, DEPENDENT);
        assertPublicEvalAllowed(language3, LANGUAGE1);
        assertPublicEvalAllowed(language3, LANGUAGE2);
        assertPublicEvalAllowed(language3, LANGUAGE3);

        assertInternalEvalAllowed(language3, INTERNAL);
        assertInternalEvalDenied(language3, DEPENDENT);
        assertInternalEvalAllowed(language3, LANGUAGE1);
        assertInternalEvalAllowed(language3, LANGUAGE2);
        assertInternalEvalAllowed(language3, LANGUAGE3);
    }

    @Test
    public void testCustomPolyglotEvalSingleDeny() {
        PolyglotAccess access = PolyglotAccess.newBuilder().//
                        allowEvalBetween(LANGUAGE1, LANGUAGE2).//
                        denyEval(LANGUAGE1, LANGUAGE2).//
                        build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalDenied(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalDenied(language1, LANGUAGE2);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalAllowed(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalAllowed(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);

        assertNoEvalAccess(language1);
    }

    @Test
    public void testCustomPolyglotEvalErrors() {
        PolyglotAccess.Builder builder = PolyglotAccess.newBuilder();

        assertFails(() -> builder.allowEval(null, ""), NullPointerException.class);
        assertFails(() -> builder.allowEval("", null), NullPointerException.class);
        assertFails(() -> builder.denyEval(null, ""), NullPointerException.class);
        assertFails(() -> builder.denyEval("", null), NullPointerException.class);
        assertFails(() -> builder.allowEvalBetween((String[]) null), NullPointerException.class);
        assertFails(() -> builder.denyEvalBetween((String[]) null), NullPointerException.class);
        assertFails(() -> builder.allowEvalBetween((String) null), NullPointerException.class);
        assertFails(() -> builder.denyEvalBetween((String) null), NullPointerException.class);

        PolyglotAccess access = PolyglotAccess.newBuilder().allowEval(NOT_EXISTING_LANGUAGE, LANGUAGE1).build();
        Context.Builder cBuilder = Context.newBuilder().allowPolyglotAccess(access);
        assertFails(() -> cBuilder.build(), IllegalArgumentException.class);

        access = PolyglotAccess.newBuilder().allowEval(LANGUAGE1, LANGUAGE2).build();
        Context.Builder cBuilder2 = Context.newBuilder(LANGUAGE2).allowPolyglotAccess(access);
        AbstractPolyglotTest.assertFails(() -> cBuilder2.build(), IllegalArgumentException.class,
                        (e) -> assertEquals("Language '" + LANGUAGE1 + "' configured in polyglot evaluation rule " + LANGUAGE1 + " -> " + LANGUAGE2 + " is not installed or available.",
                                        e.getMessage()));
    }

    @Test
    public void testCustomPolyglotBindingsErrors() {
        PolyglotAccess.Builder builder = PolyglotAccess.newBuilder();

        assertFails(() -> builder.allowBindingsAccess(null), NullPointerException.class);

        PolyglotAccess access = PolyglotAccess.newBuilder().allowBindingsAccess(NOT_EXISTING_LANGUAGE).build();
        Context.Builder cBuilder = Context.newBuilder().allowPolyglotAccess(access);
        AbstractPolyglotTest.assertFails(() -> cBuilder.build(), IllegalArgumentException.class,
                        (e) -> assertEquals("Language '" + NOT_EXISTING_LANGUAGE + "' configured in polyglot bindings access rule is not installed or available.", e.getMessage()));
    }

    @Test
    public void testCustomBindingsAccess() {
        PolyglotAccess access = PolyglotAccess.newBuilder().//
                        allowBindingsAccess(LANGUAGE1).//
                        build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertBindingsAllowed(language1);
        assertBindingsNotAccessible(language2);
    }

    @Test
    public void testAllBindingsAccess() {
        PolyglotAccess access = PolyglotAccess.ALL;
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertBindingsAllowed(language1);
        assertBindingsAllowed(language2);
    }

    @Test
    public void testNoBindingsAccess() {
        PolyglotAccess access = PolyglotAccess.NONE;
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language2.getContext(Language2.class);

        assertBindingsNotAccessible(language1);
        assertBindingsNotAccessible(language2);
    }

    @Test
    public void testParsePublic1() {
        setupEnv();
        context.initialize(LANGUAGE1);

        Env language1 = Language1.getContext(Language1.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalAllowed(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalAllowed(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalAllowed(language1, LANGUAGE2);
    }

    @Test
    public void testParsePublic2() {
        setupEnv(Context.newBuilder().allowPolyglotAccess(PolyglotAccess.NONE).build());
        context.initialize(LANGUAGE1);

        Env language1 = Language1.getContext(Language1.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalDenied(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalDenied(language1, LANGUAGE2);
    }

    @Test
    public void testParsePublic3() {
        setupEnv(Context.newBuilder().allowPolyglotAccess(PolyglotAccess.newBuilder().allowEval(LANGUAGE1, LANGUAGE2).build()).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        Env language1 = Language1.getContext(Language1.class);
        Env language2 = Language1.getContext(Language2.class);

        assertPublicEvalDenied(language1, INTERNAL);
        assertPublicEvalDenied(language1, DEPENDENT);
        assertPublicEvalAllowed(language1, LANGUAGE1);
        assertPublicEvalAllowed(language1, LANGUAGE2);

        assertInternalEvalAllowed(language1, INTERNAL);
        assertInternalEvalAllowed(language1, DEPENDENT);
        assertInternalEvalAllowed(language1, LANGUAGE1);
        assertInternalEvalAllowed(language1, LANGUAGE2);

        assertPublicEvalDenied(language2, INTERNAL);
        assertPublicEvalDenied(language2, DEPENDENT);
        assertPublicEvalDenied(language2, LANGUAGE1);
        assertPublicEvalAllowed(language2, LANGUAGE2);

        assertInternalEvalAllowed(language2, INTERNAL);
        assertInternalEvalDenied(language2, DEPENDENT);
        assertInternalEvalDenied(language2, LANGUAGE1);
        assertInternalEvalAllowed(language2, LANGUAGE2);
    }

    private static void assertNoEvalAccess(Env env) {
        assertFalse(env.isPolyglotEvalAllowed());
    }

    private static void assertBindingsDenied(Env env) {
        assertFalse(env.isPolyglotBindingsAccessAllowed());
        assertExportNotAcccessible(env);
        assertImportNotAcccessible(env);
        assertBindingsNotAccessible(env);
    }

    private static void assertBindingsAllowed(Env env) {
        assertTrue(env.isPolyglotBindingsAccessAllowed());
        testPolyglotAccess(env, env);
    }

    private static void assertInternalEvalDenied(Env env, String targetId) {
        assertFalse(env.getInternalLanguages().containsKey(targetId));
        try {
            env.parseInternal(Source.newBuilder(targetId, "", "").internal(true).build());
            fail();
        } catch (IllegalStateException e) {
            String languages = env.getInternalLanguages().keySet().toString();
            assertEquals("No language for id " + targetId + " found. Supported languages are: " + languages, e.getMessage());
        }
    }

    private static void assertInternalEvalAllowed(Env env, String targetId) {
        assertTrue(env.getInternalLanguages().containsKey(targetId));
        assertNotNull(env.parseInternal(Source.newBuilder(targetId, "", "").build()));
    }

    private static void assertPublicEvalDenied(Env env, String targetId) {
        assertFalse(env.getPublicLanguages().containsKey(targetId));
        try {
            env.parsePublic(Source.newBuilder(targetId, "", "").build());
            fail();
        } catch (IllegalStateException e) {
            String languages = env.getPublicLanguages().keySet().toString();
            assertEquals("No language for id " + targetId + " found. Supported languages are: " + languages, e.getMessage());
        }
    }

    private static void assertPublicEvalAllowed(Env env, String targetId) {
        assertTrue(env.getPublicLanguages().containsKey(targetId));
        assertNotNull(env.parsePublic(Source.newBuilder(targetId, "", "").build()));
        if (env.getPublicLanguages().size() > 1) {
            assertTrue(env.isPolyglotEvalAllowed());
        }
    }

    @Registration(id = LANGUAGE1, name = LANGUAGE1, dependentLanguages = DEPENDENT)
    public static class Language1 extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
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
