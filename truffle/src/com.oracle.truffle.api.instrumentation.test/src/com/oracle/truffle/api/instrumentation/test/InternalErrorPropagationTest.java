/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class InternalErrorPropagationTest extends AbstractPolyglotTest {

    static final String OTHER_LANGUAGE = "InstrumentationExceptionTest_OtherLanguage";

    public static class TestProxyInstrument extends ProxyInstrument {

        private final TestContextListener listener;

        public TestProxyInstrument(TestContextListener listener) {
            this.listener = listener;
        }

        public TestProxyInstrument() {
            this.listener = null;
        }

        @Override
        protected void onCreate(Env env) {
            super.onCreate(env);
            env.getInstrumenter().attachContextsListener(listener, true);
        }
    }

    @Test
    public void testInstrumentCreateException() {
        TestContextListener listener = new TestContextListener();
        ProxyInstrument instrument = new TestProxyInstrument(listener);
        TestEventListener triggerFailure = new TestEventListener();

        setupEnv(Context.create(ProxyLanguage.ID), instrument);

        try {
            listener.onContextCreated = triggerFailure;
            setupEnv(Context.create(ProxyLanguage.ID), instrument);
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
        }

        try {
            listener.onContextCreated = null;
            listener.onLanguageContextCreated = triggerFailure;
            setupEnv(Context.create(ProxyLanguage.ID), instrument);
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
        }

        try {
            listener.onLanguageContextCreated = null;
            listener.onLanguageContextInitialized = triggerFailure;
            setupEnv(Context.create(ProxyLanguage.ID), instrument);
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
        }

        listener.onLanguageContextInitialized = null;
        setupEnv(Context.create(ProxyLanguage.ID), instrument);
        try {
            listener.onLanguageContextFinalized = triggerFailure;
            context.close();
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
            listener.onLanguageContextFinalized = null;
            context.close();
        }

        listener.onLanguageContextFinalized = null;
        setupEnv(Context.create(ProxyLanguage.ID), instrument);
        try {
            listener.onLanguageContextDisposed = triggerFailure;
            context.close();
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
            listener.onLanguageContextDisposed = null;
            context.close();
        }

        listener.onLanguageContextDisposed = null;
        setupEnv(Context.create(ProxyLanguage.ID), instrument);
        try {
            listener.onContextClosed = triggerFailure;
            context.close();
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
            listener.onContextClosed = null;
            context.close();
        }

    }

    private static void triggerParseFailure(TruffleLanguage.Env env, TruffleContext c) {
        Object prev = c.enter();
        try {
            env.parsePublic(Source.newBuilder(OTHER_LANGUAGE, "", "").build());
        } finally {
            c.leave(prev);
        }
    }

    @Registration(id = OTHER_LANGUAGE, name = OTHER_LANGUAGE)
    public static class OtherLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    final class TestEventListener {

        void accept(TruffleContext c) {
            triggerParseFailure(languageEnv, c);
        }

    }

    static class TestContextListener implements ContextsListener {

        TestEventListener onLanguageContextInitialized;
        TestEventListener onLanguageContextFinalized;
        TestEventListener onLanguageContextDisposed;
        TestEventListener onLanguageContextCreated;
        TestEventListener onContextCreated;
        TestEventListener onContextClosed;

        public void onLanguageContextInitialized(TruffleContext c, LanguageInfo l) {
            if (onLanguageContextInitialized != null) {
                onLanguageContextInitialized.accept(c);
            }
        }

        public void onLanguageContextFinalized(TruffleContext c, LanguageInfo l) {
            if (onLanguageContextFinalized != null) {
                onLanguageContextFinalized.accept(c);
            }
        }

        public void onLanguageContextDisposed(TruffleContext c, LanguageInfo l) {
            if (onLanguageContextDisposed != null) {
                onLanguageContextDisposed.accept(c);
            }
        }

        public void onLanguageContextCreated(TruffleContext c, LanguageInfo l) {
            if (onLanguageContextCreated != null) {
                onLanguageContextCreated.accept(c);
            }
        }

        public void onContextCreated(TruffleContext c) {
            if (onContextCreated != null) {
                onContextCreated.accept(c);
            }
        }

        public void onContextClosed(TruffleContext c) {
            if (onContextClosed != null) {
                onContextClosed.accept(c);
            }
        }

    }

}
