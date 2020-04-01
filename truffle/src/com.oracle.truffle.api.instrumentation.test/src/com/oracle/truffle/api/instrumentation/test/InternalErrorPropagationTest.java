package com.oracle.truffle.api.instrumentation.test;

import java.util.function.Consumer;

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

    @Test
    public void testInstrumentCreateException() {
        TestListener listener = new TestListener();
        ProxyInstrument instrument = new ProxyInstrument() {
            @Override
            protected void onCreate(Env env) {
                super.onCreate(env);
                env.getInstrumenter().attachContextsListener(listener, true);
            }
        };

        setupEnv(Context.create(ProxyLanguage.ID), instrument);

        try {
            listener.onContextCreated = (c) -> triggerParseFailure(languageEnv, c);
            setupEnv(Context.create(ProxyLanguage.ID), instrument);
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
        }

        try {
            listener.onContextCreated = null;
            listener.onLanguageContextCreated = (c) -> triggerParseFailure(languageEnv, c);
            setupEnv(Context.create(ProxyLanguage.ID), instrument);
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
        }

        try {
            listener.onLanguageContextCreated = null;
            listener.onLanguageContextInitialized = (c) -> triggerParseFailure(languageEnv, c);
            setupEnv(Context.create(ProxyLanguage.ID), instrument);
            Assert.fail();
        } catch (PolyglotException e) {
            Assert.assertTrue(e.isInternalError());
        }

        listener.onLanguageContextInitialized = null;
        setupEnv(Context.create(ProxyLanguage.ID), instrument);
        try {
            listener.onLanguageContextFinalized = (c) -> triggerParseFailure(languageEnv, c);
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
            listener.onLanguageContextDisposed = (c) -> triggerParseFailure(languageEnv, c);
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
            listener.onContextClosed = (c) -> triggerParseFailure(languageEnv, c);
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

    static class TestListener implements ContextsListener {

        Consumer<TruffleContext> onLanguageContextInitialized;
        Consumer<TruffleContext> onLanguageContextFinalized;
        Consumer<TruffleContext> onLanguageContextDisposed;
        Consumer<TruffleContext> onLanguageContextCreated;
        Consumer<TruffleContext> onContextCreated;
        Consumer<TruffleContext> onContextClosed;

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