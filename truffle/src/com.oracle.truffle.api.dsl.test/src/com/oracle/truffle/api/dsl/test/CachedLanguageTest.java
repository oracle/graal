/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.CachedLanguageTestFactory.CachedWithFallbackNodeGen;
import com.oracle.truffle.api.dsl.test.CachedLanguageTestFactory.Valid1NodeGen;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.AbstractLibraryTest;

@SuppressWarnings("unused")
public class CachedLanguageTest extends AbstractLibraryTest {

    @Test
    public void testCachedLanguage() {
        setupEnv();
        context.initialize(TEST_LANGUAGE);
        CachedLanguageTestLanguage testLanguage = CachedLanguageTestLanguage.getCurrentLanguage();
        Valid1Node node;
        node = adoptNode(testLanguage, Valid1NodeGen.create()).get();
        assertEquals("s0", node.execute(42));
        assertEquals("s1", node.execute(42d));

        node = adoptNode(this.language, Valid1NodeGen.create()).get();
        assertEquals("s0", node.execute(42));
        assertEquals("s1", node.execute(42d));

        node = adoptNode(null, Valid1NodeGen.create()).get();
        assertEquals("s0", node.execute(42));
        assertEquals("s1", node.execute(42d));

        node = Valid1NodeGen.getUncached();
        assertEquals("s0", node.execute(42));
        assertEquals("s1", node.execute(42d));
    }

    @GenerateUncached
    @Introspectable
    abstract static class Valid1Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(int value,
                        @CachedLanguage CachedLanguageTestLanguage language, //
                        @CachedLanguage LanguageReference<CachedLanguageTestLanguage> languageSupplier) {
            assertSame(CachedLanguageTestLanguage.getCurrentLanguage(), language);
            assertSame(CachedLanguageTestLanguage.getCurrentLanguage(), languageSupplier.get());
            return "s0";
        }

        @Specialization
        static String s1(double value,
                        @CachedLanguage CachedLanguageTestLanguage language, //
                        @CachedLanguage LanguageReference<CachedLanguageTestLanguage> languageSupplier) {
            assertSame(CachedLanguageTestLanguage.getCurrentLanguage(), language);
            assertSame(CachedLanguageTestLanguage.getCurrentLanguage(), languageSupplier.get());
            return "s1";
        }
    }

    private static final String TEST_LANGUAGE = "CachedLanguageTestLanguage";

    @Registration(id = TEST_LANGUAGE, name = TEST_LANGUAGE)
    public static class CachedLanguageTestLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        public static CachedLanguageTestLanguage getCurrentLanguage() {
            return CachedLanguageTestLanguage.getCurrentLanguage(CachedLanguageTestLanguage.class);
        }

    }

    @Test
    public void testCacheWithFallback() {
        CachedWithFallback node;
        setupEnv();
        context.initialize(TEST_LANGUAGE);

        node = adoptNode(CachedWithFallbackNodeGen.create()).get();
        assertEquals("s0", node.execute(""));
        assertEquals("s0", node.execute(""));
        node.guard = false;
        assertEquals("fallback", node.execute(""));

        node = adoptNode(CachedWithFallbackNodeGen.create()).get();
        node.guard = false;
        assertEquals("fallback", node.execute(""));
        node.guard = true;
        assertEquals("s0", node.execute(""));
        assertEquals("s0", node.execute(""));
    }

    @SuppressWarnings("static-method")
    abstract static class CachedWithFallback extends Node {

        boolean guard = true;

        public abstract String execute(Object o);

        boolean isGuard(Object o) {
            return guard;
        }

        @Specialization(guards = {"language == cachedLanguage", "isGuard(o)"}, limit = "1")
        String s0(String o,
                        @CachedLanguage CachedLanguageTestLanguage language,
                        @Cached("language") CachedLanguageTestLanguage cachedLanguage) {
            return "s0";
        }

        @Fallback
        String fallback(Object o) {
            return "fallback";
        }
    }

    /*
     * Language without registration is an invalid language.
     */
    public static class InvalidLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

    }

    abstract static class CachedLanguageError1Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedLanguage specification. The parameter type must be a subtype of TruffleLanguage or of type LanguageReference<TruffleLanguage>.") @CachedLanguage Object language) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError2Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedLanguage specification. The type 'TruffleLanguage' is not a valid language type. Valid language types must be annotated with @Registration.") //
                        @CachedLanguage TruffleLanguage<?> language) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError3Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedLanguage specification. The type 'InvalidLanguage' is not a valid language type. Valid language types must be annotated with @Registration.") //
                        @CachedLanguage InvalidLanguage language) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError4Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedLanguage specification. The first type argument of the LanguageReference must be a subtype of 'TruffleLanguage'.")//
                        @CachedLanguage LanguageReference<? extends Object> languageSupplier) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError5Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedLanguage specification. The type 'TruffleLanguage' is not a valid language type. Valid language types must be annotated with @Registration.")//
                        @CachedLanguage LanguageReference<TruffleLanguage<?>> languageSupplier) {
            throw new AssertionError();
        }
    }

    abstract static class CachedLanguageError6Node extends Node {

        abstract Object execute(Object argument);

        @Specialization
        static String s0(Object value,
                        @ExpectError("Invalid @CachedLanguage specification. The type 'InvalidLanguage' is not a valid language type. Valid language types must be annotated with @Registration.")//
                        @CachedLanguage LanguageReference<InvalidLanguage> languageSupplier) {
            throw new AssertionError();
        }
    }

    @GenerateLibrary
    public abstract static class CachedLanguageTestLibrary extends Library {

        public abstract CachedLanguageTestLanguage m0(Object receiver);

        public abstract CachedLanguageTestLanguage m1(Object receiver);
    }

    @ExportLibrary(CachedLanguageTestLibrary.class)
    @SuppressWarnings("static-method")
    static class CachedLanguageLibraryReceiver {

        @ExportMessage
        final CachedLanguageTestLanguage m0(@CachedLanguage CachedLanguageTestLanguage env) {
            return env;
        }

        @ExportMessage
        final CachedLanguageTestLanguage m1(@CachedLanguage LanguageReference<CachedLanguageTestLanguage> env) {
            return env.get();
        }
    }

    @Test
    public void testCachedLanguageReceiver() {
        setupEnv();
        context.initialize(TEST_LANGUAGE);
        CachedLanguageLibraryReceiver receiver = new CachedLanguageLibraryReceiver();

        CachedLanguageTestLibrary cached = createCached(CachedLanguageTestLibrary.class, receiver);

        assertSame(CachedLanguageTestLanguage.getCurrentLanguage(), cached.m0(receiver));
        assertSame(CachedLanguageTestLanguage.getCurrentLanguage(), cached.m1(receiver));

        CachedLanguageTestLibrary uncached = getUncached(CachedLanguageTestLibrary.class, receiver);

        assertSame(CachedLanguageTestLanguage.getCurrentLanguage(), uncached.m0(receiver));
        assertSame(CachedLanguageTestLanguage.getCurrentLanguage(), uncached.m1(receiver));
    }

}
