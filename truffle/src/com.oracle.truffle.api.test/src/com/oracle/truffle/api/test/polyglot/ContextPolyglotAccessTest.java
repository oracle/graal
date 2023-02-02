/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.evalTestLanguage;
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;

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

    @Registration
    static class NotExistingDependentTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(NotExistingDependentTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env env2 = Language2.getContext(LANGUAGE2);
            assertTrue(env2.getInternalLanguages().containsKey(LANGUAGE2));
            assertFalse(env2.getInternalLanguages().containsKey(NOT_EXISTING_LANGUAGE));

            return null;
        }
    }

    @Test
    public void testNotExistingDependent() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE2, NotExistingDependentTestLanguage.ID).allowPolyglotAccess(PolyglotAccess.NONE).build());
        context.initialize(LANGUAGE2);
        evalTestLanguage(context, NotExistingDependentTestLanguage.class, "");
    }

    @Registration
    static class EmbedderAccessDependentTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(EmbedderAccessDependentTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            language1.initializeLanguage(language1.getInternalLanguages().get(DEPENDENT));

            Env dependent = Dependent.getContext(DEPENDENT);
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

            return null;
        }
    }

    @Test
    public void testEmbedderAccessDependent() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, DEPENDENT, LANGUAGE1, EmbedderAccessDependentTestLanguage.ID).allowPolyglotAccess(PolyglotAccess.ALL).build());
        context.initialize(LANGUAGE1);

        evalTestLanguage(context, EmbedderAccessDependentTestLanguage.class, "");
    }

    @Registration
    static class NotExistingEmbedderTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(NotExistingEmbedderTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env env1 = Language1.getContext(LANGUAGE1);
            assertTrue(env1.getInternalLanguages().containsKey(LANGUAGE1));
            assertFalse(env1.getInternalLanguages().containsKey(NOT_EXISTING_LANGUAGE));

            return null;
        }
    }

    @Test
    public void testNotExistingEmbedder() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, NOT_EXISTING_LANGUAGE, NotExistingEmbedderTestLanguage.ID).allowPolyglotAccess(PolyglotAccess.ALL).build());
        context.initialize(LANGUAGE1);
        try {
            context.initialize(NOT_EXISTING_LANGUAGE);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("A language with id '" + NOT_EXISTING_LANGUAGE + "' is not installed."));
        }

        evalTestLanguage(context, NotExistingEmbedderTestLanguage.class, "");
    }

    @Registration
    static class AllAccessTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(AllAccessTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

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

            return null;
        }
    }

    @Test
    public void testAllAccess() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, AllAccessTestLanguage.ID).allowPolyglotAccess(PolyglotAccess.ALL).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        try {
            // not an embedder language
            context.initialize(DEPENDENT);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Access to language '" + DEPENDENT + "' is not permitted. ", e.getMessage());
        }

        evalTestLanguage(context, AllAccessTestLanguage.class, "");
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

    @Registration
    static class NoAccessTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(NoAccessTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            testNoAccessImpl();

            return null;
        }
    }

    @Test
    public void testNoAccess() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, NoAccessTestLanguage.ID).allowPolyglotAccess(PolyglotAccess.NONE).build());
        try {
            // not an embedder language
            context.initialize(DEPENDENT);
            fail();
        } catch (IllegalArgumentException e) {
        }

        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        evalTestLanguage(context, NoAccessTestLanguage.class, "");
    }

    @Registration
    static class NoPolyglotAccessWithAllAccessTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(NoPolyglotAccessWithAllAccessTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            testNoAccessImpl();

            return null;
        }
    }

    @Test
    public void testNoPolyglotAccessWithAllAccess() {
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, NoPolyglotAccessWithAllAccessTestLanguage.ID).allowAllAccess(true).allowPolyglotAccess(PolyglotAccess.NONE).build());
        try {
            // not an embedder language
            context.initialize(DEPENDENT);
            fail();
        } catch (IllegalArgumentException e) {
        }

        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        evalTestLanguage(context, NoPolyglotAccessWithAllAccessTestLanguage.class, "");
    }

    private static void testNoAccessImpl() {
        Env language1 = Language1.getContext(LANGUAGE1);
        Env language2 = Language2.getContext(LANGUAGE2);
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

    @Registration
    static class AllLanguagesNoAccessTestLanguage extends AbstractExecutableTestLanguage {

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            testNoAccessImpl();

            return null;
        }
    }

    @Test
    public void testAllLanguagesNoAccess() {
        setupEnv(Context.newBuilder().allowPolyglotAccess(PolyglotAccess.NONE).build());
        context.initialize(DEPENDENT);
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, AllLanguagesNoAccessTestLanguage.class, "");
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

    @Registration
    static class PolyglotExportPromotionTestLanguage extends AbstractExecutableTestLanguage {

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env env1 = Language1.getContext(LANGUAGE1);

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

            return null;
        }
    }

    @Test
    public void testPolyglotExportPromotion() {
        setupEnv();
        context.initialize(LANGUAGE1);

        evalTestLanguage(context, PolyglotExportPromotionTestLanguage.class, "");
    }

    @Registration
    static class CustomPolyglotEvalDirectTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CustomPolyglotEvalDirectTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

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

            return null;
        }
    }

    @Test
    public void testCustomPolyglotEvalDirect() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEval(LANGUAGE1, LANGUAGE2).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, CustomPolyglotEvalDirectTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, CustomPolyglotEvalDirectTestLanguage.class, "");
    }

    @Registration
    static class CustomPolyglotEvalDirectSameTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CustomPolyglotEvalDirectSameTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

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

            return null;
        }
    }

    @Test
    public void testCustomPolyglotEvalDirectSame() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEval(LANGUAGE1, LANGUAGE1).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, CustomPolyglotEvalDirectSameTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, CustomPolyglotEvalDirectSameTestLanguage.class, "");
    }

    @Registration
    static class CustomPolyglotEvalBetweenTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CustomPolyglotEvalBetweenTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

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

            return null;
        }
    }

    @Test
    public void testCustomPolyglotEvalBetween() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEvalBetween(LANGUAGE1, LANGUAGE2).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, CustomPolyglotEvalBetweenTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, CustomPolyglotEvalBetweenTestLanguage.class, "");
    }

    @Registration
    static class CustomPolyglotEvalBetweenSameTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CustomPolyglotEvalBetweenSameTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

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

            return null;
        }
    }

    @Test
    public void testCustomPolyglotEvalBetweenSame() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEvalBetween(LANGUAGE1, LANGUAGE1).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, CustomPolyglotEvalBetweenSameTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, CustomPolyglotEvalBetweenSameTestLanguage.class, "");
    }

    @Registration
    static class CustomPolyglotEvalBetweenThreeTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CustomPolyglotEvalBetweenThreeTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);
            Env language3 = Language2.getContext(LANGUAGE3);

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

            return null;
        }
    }

    @Test
    public void testCustomPolyglotEvalBetweenThree() {
        PolyglotAccess access = PolyglotAccess.newBuilder().allowEvalBetween(LANGUAGE1, LANGUAGE2, LANGUAGE3).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, LANGUAGE3, CustomPolyglotEvalBetweenThreeTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        context.initialize(LANGUAGE3);

        evalTestLanguage(context, CustomPolyglotEvalBetweenThreeTestLanguage.class, "");
    }

    @Registration
    static class CustomPolyglotEvalBetweenThree2TestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CustomPolyglotEvalBetweenThree2TestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);
            Env language3 = Language2.getContext(LANGUAGE3);

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

            return null;
        }
    }

    @Test
    public void testCustomPolyglotEvalBetweenThree2() {
        PolyglotAccess access = PolyglotAccess.newBuilder().//
                        allowEvalBetween(LANGUAGE1, LANGUAGE2, LANGUAGE3).//
                        denyEvalBetween(LANGUAGE1, LANGUAGE2).//
                        build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, LANGUAGE3, CustomPolyglotEvalBetweenThree2TestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);
        context.initialize(LANGUAGE3);

        evalTestLanguage(context, CustomPolyglotEvalBetweenThree2TestLanguage.class, "");
    }

    @Registration
    static class CustomPolyglotEvalSingleDenyTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CustomPolyglotEvalSingleDenyTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

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

            return null;
        }
    }

    @Test
    public void testCustomPolyglotEvalSingleDeny() {
        PolyglotAccess access = PolyglotAccess.newBuilder().//
                        allowEvalBetween(LANGUAGE1, LANGUAGE2).//
                        denyEval(LANGUAGE1, LANGUAGE2).//
                        build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, CustomPolyglotEvalSingleDenyTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, CustomPolyglotEvalSingleDenyTestLanguage.class, "");
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

    @Registration
    static class CustomBindingsAccessTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(CustomBindingsAccessTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

            assertBindingsAllowed(language1);
            assertBindingsNotAccessible(language2);

            return null;
        }
    }

    @Test
    public void testCustomBindingsAccess() {
        PolyglotAccess access = PolyglotAccess.newBuilder().//
                        allowBindingsAccess(LANGUAGE1).//
                        build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, CustomBindingsAccessTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, CustomBindingsAccessTestLanguage.class, "");
    }

    @Registration
    static class AllBindingsAccessTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(AllBindingsAccessTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

            assertBindingsAllowed(language1);
            assertBindingsAllowed(language2);

            return null;
        }
    }

    @Test
    public void testAllBindingsAccess() {
        PolyglotAccess access = PolyglotAccess.ALL;
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, AllBindingsAccessTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, AllBindingsAccessTestLanguage.class, "");
    }

    @Registration
    static class NoBindingsAccessTestLanguage extends AbstractExecutableTestLanguage {
        static final String ID = TestUtils.getDefaultLanguageId(NoBindingsAccessTestLanguage.class);

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language2.getContext(LANGUAGE2);

            assertBindingsNotAccessible(language1);
            assertBindingsNotAccessible(language2);

            return null;
        }
    }

    @Test
    public void testNoBindingsAccess() {
        PolyglotAccess access = PolyglotAccess.NONE;
        setupEnv(Context.newBuilder(ProxyLanguage.ID, LANGUAGE1, LANGUAGE2, NoBindingsAccessTestLanguage.ID).allowPolyglotAccess(access).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, NoBindingsAccessTestLanguage.class, "");
    }

    @Registration
    static class ParsePublic1TestLanguage extends AbstractExecutableTestLanguage {

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);

            assertPublicEvalDenied(language1, INTERNAL);
            assertPublicEvalAllowed(language1, DEPENDENT);
            assertPublicEvalAllowed(language1, LANGUAGE1);
            assertPublicEvalAllowed(language1, LANGUAGE2);

            assertInternalEvalAllowed(language1, INTERNAL);
            assertInternalEvalAllowed(language1, DEPENDENT);
            assertInternalEvalAllowed(language1, LANGUAGE1);
            assertInternalEvalAllowed(language1, LANGUAGE2);

            return null;
        }
    }

    @Test
    public void testParsePublic1() {
        setupEnv();
        context.initialize(LANGUAGE1);

        evalTestLanguage(context, ParsePublic1TestLanguage.class, "");
    }

    @Registration
    static class ParsePublic2TestLanguage extends AbstractExecutableTestLanguage {

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);

            assertPublicEvalDenied(language1, INTERNAL);
            assertPublicEvalDenied(language1, DEPENDENT);
            assertPublicEvalAllowed(language1, LANGUAGE1);
            assertPublicEvalDenied(language1, LANGUAGE2);

            assertInternalEvalAllowed(language1, INTERNAL);
            assertInternalEvalAllowed(language1, DEPENDENT);
            assertInternalEvalAllowed(language1, LANGUAGE1);
            assertInternalEvalDenied(language1, LANGUAGE2);

            return null;
        }
    }

    @Test
    public void testParsePublic2() {
        setupEnv(Context.newBuilder().allowPolyglotAccess(PolyglotAccess.NONE).build());
        context.initialize(LANGUAGE1);

        evalTestLanguage(context, ParsePublic2TestLanguage.class, "");
    }

    @Registration
    static class ParsePublic3TestLanguage extends AbstractExecutableTestLanguage {

        @CompilerDirectives.TruffleBoundary
        @Override
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            Env language1 = Language1.getContext(LANGUAGE1);
            Env language2 = Language1.getContext(LANGUAGE2);

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

            return null;
        }
    }

    @Test
    public void testParsePublic3() {
        setupEnv(Context.newBuilder().allowPolyglotAccess(PolyglotAccess.newBuilder().allowEval(LANGUAGE1, LANGUAGE2).build()).build());
        context.initialize(LANGUAGE1);
        context.initialize(LANGUAGE2);

        evalTestLanguage(context, ParsePublic3TestLanguage.class, "");
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
            return RootNode.createConstantNode(true).getCallTarget();
        }

        public static Env getContext(String language) {
            ContextReference<Env> ref;
            switch (language) {
                case LANGUAGE1:
                    ref = REFERENCE;
                    break;
                case LANGUAGE2:
                    ref = Language2.REFERENCE;
                    break;
                case DEPENDENT:
                    ref = Dependent.REFERENCE;
                    break;
                case INTERNAL:
                    ref = Internal.REFERENCE;
                    break;
                case LANGUAGE3:
                    ref = Language3.REFERENCE;
                    break;
                default:
                    throw new AssertionError();

            }
            return ref.get(null);
        }

        static final ContextReference<Env> REFERENCE = ContextReference.create(Language1.class);

    }

    @Registration(id = LANGUAGE2, name = LANGUAGE2, dependentLanguages = NOT_EXISTING_LANGUAGE)
    public static class Language2 extends Language1 {

        static final ContextReference<Env> REFERENCE = ContextReference.create(Language2.class);
    }

    @Registration(id = DEPENDENT, name = DEPENDENT)
    public static class Dependent extends Language1 {
        static final ContextReference<Env> REFERENCE = ContextReference.create(Dependent.class);
    }

    @Registration(id = INTERNAL, name = INTERNAL, internal = true)
    public static class Internal extends Language1 {
        static final ContextReference<Env> REFERENCE = ContextReference.create(Internal.class);
    }

    @Registration(id = LANGUAGE3, name = LANGUAGE3)
    public static class Language3 extends Language1 {
        static final ContextReference<Env> REFERENCE = ContextReference.create(Language3.class);
    }

}
