/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.After;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Base class for polyglot tests that require internal access to a language or instrument
 * environment.
 *
 * @see #setupEnv()
 * @see #setupEnv(Context)
 * @see #setupEnv(Context, ProxyLanguage, ProxyInstrument)
 */
public abstract class AbstractPolyglotTest {

    protected static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    protected Context context;
    protected TruffleLanguage.Env languageEnv;
    protected TruffleLanguage<?> language;
    protected TruffleInstrument.Env instrumentEnv;
    protected boolean cleanupOnSetup = true;
    protected boolean enterContext = true;

    protected final void setupEnv(Context newContext, ProxyInstrument instrument) {
        setupEnv(newContext, null, instrument);
    }

    protected final void setupEnv(Context newContext, ProxyLanguage language) {
        setupEnv(newContext, language, null);
    }

    protected final void setupEnv(Context newContext, ProxyLanguage language, ProxyInstrument instrument) {
        if (cleanupOnSetup) {
            cleanup();
        }
        final ProxyLanguage usedLanguage;
        if (language == null) {
            usedLanguage = new ProxyLanguage();
        } else {
            usedLanguage = language;
        }
        final ProxyInstrument usedInstrument;
        if (instrument == null) {
            usedInstrument = new ProxyInstrument();
        } else {
            usedInstrument = instrument;
        }
        usedLanguage.setOnCreate((c) -> {
            this.languageEnv = c.env;
            this.language = usedLanguage.languageInstance;
        });
        usedInstrument.setOnCreate((env) -> instrumentEnv = env);

        ProxyLanguage.setDelegate(usedLanguage);
        ProxyInstrument.setDelegate(usedInstrument);

        Class<?> currentInstrumentClass = usedInstrument.getClass();
        String instrumentId = null;
        while (currentInstrumentClass != null && instrumentId == null) {
            TruffleInstrument.Registration reg = currentInstrumentClass.getAnnotation(TruffleInstrument.Registration.class);
            instrumentId = reg != null ? reg.id() : null;
            currentInstrumentClass = currentInstrumentClass.getSuperclass();
        }

        // forces initialization of instrument
        newContext.getEngine().getInstruments().get(instrumentId).lookup(ProxyInstrument.Initialize.class);
        // force initialization of proxy language

        Class<?> currentLanguageClass = usedLanguage.getClass();
        String languageId = null;
        while (currentLanguageClass != null && languageId == null) {
            Registration reg = currentLanguageClass.getAnnotation(Registration.class);
            languageId = reg != null ? reg.id() : null;
            currentLanguageClass = currentLanguageClass.getSuperclass();
        }

        newContext.initialize(languageId);
        // enter current context
        if (enterContext) {
            newContext.enter();
        }

        assertNotNull(this.languageEnv);
        assertNotNull(this.language);
        assertNotNull(this.instrumentEnv);

        this.context = newContext;
    }

    protected final void setupEnv(Context context) {
        setupEnv(context, null, null);
    }

    protected final void setupEnv() {
        setupEnv(Context.newBuilder().allowAllAccess(true).option("engine.InstrumentExceptionsAreThrown", "true").build(), null, null);
    }

    /**
     * Wraps a node in a RootNode and makes sure load listeners are notified, wrappers are inserted
     * and attached execution listeners are applied.
     */
    @SuppressWarnings("unchecked")
    protected final <T extends Node> Supplier<T> adoptNode(T node) {
        return adoptNode(this.language, node);
    }

    @SuppressWarnings({"unchecked", "static-method"})
    protected final <T extends Node> Supplier<T> adoptNode(TruffleLanguage<?> lang, T node) {
        TestRootNode root = new TestRootNode(lang, node);
        CallTarget target = Truffle.getRuntime().createCallTarget(root);
        // execute it to trigger instrumentations
        target.call();
        return () -> (T) root.node;
    }

    @After
    public final void cleanup() {
        if (context != null) {
            if (enterContext) {
                context.leave();
            }

            context.close();
            context = null;
        }
    }

    public static void assertFails(Runnable callable, Class<? extends Throwable> exceptionType) {
        assertFails((Callable<?>) () -> {
            callable.run();
            return null;
        }, exceptionType);
    }

    public static void assertFails(Callable<?> callable, Class<? extends Throwable> exceptionType) {
        try {
            callable.call();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                throw new AssertionError("expected instanceof " + exceptionType.getName() + " was " + t.getClass().getName(), t);
            }
            return;
        }
        fail("expected " + exceptionType.getName() + " but no exception was thrown");
    }

    public static <T extends Throwable> void assertFails(Runnable run, Class<T> exceptionType, Consumer<T> verifier) {
        try {
            run.run();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                throw new AssertionError("expected instanceof " + exceptionType.getName() + " was " + t.getClass().getName(), t);
            }
            verifier.accept(exceptionType.cast(t));
            return;
        }
        fail("expected " + exceptionType.getName() + " but no exception was thrown");
    }

    public static <T extends Throwable> void assertFails(Callable<?> callable, Class<T> exceptionType, Consumer<T> verifier) {
        try {
            callable.call();
        } catch (Throwable t) {
            if (!exceptionType.isInstance(t)) {
                throw new AssertionError("expected instanceof " + exceptionType.getName() + " was " + t.getClass().getName(), t);
            }
            verifier.accept(exceptionType.cast(t));
            return;
        }
        fail("expected " + exceptionType.getName() + " but no exception was thrown");
    }

    private static class TestRootNode extends RootNode {

        @Child private Node node;

        protected TestRootNode(TruffleLanguage<?> language, Node node) {
            super(language);
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // does nothing node is supposed to be exectued
            // externally.
            return null;
        }

    }

}
