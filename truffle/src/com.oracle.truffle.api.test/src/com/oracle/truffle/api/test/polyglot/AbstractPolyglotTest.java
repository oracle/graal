/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertNotNull;

import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.After;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
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

    protected Context context;
    protected TruffleLanguage.Env languageEnv;
    protected TruffleLanguage<?> language;
    protected TruffleInstrument.Env instrumentEnv;
    protected boolean cleanupOnSetup;

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
        newContext.enter();

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
        TestRootNode root = new TestRootNode(this.language, node);
        CallTarget target = Truffle.getRuntime().createCallTarget(root);
        // execute it to trigger instrumentations
        target.call();
        return () -> (T) root.node;
    }

    @After
    public final void cleanup() {
        if (context != null) {
            context.leave();
            context.close();
            context = null;
        }
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
