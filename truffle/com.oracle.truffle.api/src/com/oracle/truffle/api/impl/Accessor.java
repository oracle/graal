/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrument.ASTProber;
import com.oracle.truffle.api.instrument.AdvancedInstrumentResultListener;
import com.oracle.truffle.api.instrument.AdvancedInstrumentRootFactory;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/**
 * Communication between PolyglotEngine, TruffleLanguage API/SPI, and other services.
 */
public abstract class Accessor {
    private static Accessor API;
    private static Accessor SPI;
    private static Accessor NODES;
    private static Accessor INSTRUMENT;
    private static Accessor DEBUG;
    private static final ThreadLocal<Object> CURRENT_VM = new ThreadLocal<>();

    static {
        TruffleLanguage<?> lng = new TruffleLanguage<Object>() {
            @Override
            protected Object findExportedSymbol(Object context, String globalName, boolean onlyExplicit) {
                return null;
            }

            @Override
            protected Object getLanguageGlobal(Object context) {
                return null;
            }

            @Override
            protected boolean isObjectOfLanguage(Object object) {
                return false;
            }

            @Override
            protected CallTarget parse(Source code, Node context, String... argumentNames) throws IOException {
                throw new IOException();
            }

            @Override
            protected Object createContext(TruffleLanguage.Env env) {
                return null;
            }

            @Override
            protected boolean isInstrumentable(Node node) {
                return false;
            }

            @Override
            protected WrapperNode createWrapperNode(Node node) {
                return null;
            }

            @Override
            protected Visualizer getVisualizer() {
                return null;
            }

            @Override
            protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
                return null;
            }

            @Override
            protected AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws IOException {
                return null;
            }
        };
        lng.hashCode();
        new Node(null) {
        }.getRootNode();

        try {
            Class.forName(Instrumenter.class.getName(), true, Instrumenter.class.getClassLoader());
            Class.forName(Debugger.class.getName(), true, Debugger.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected Accessor() {
        if (this.getClass().getSimpleName().endsWith("API")) {
            if (API != null) {
                throw new IllegalStateException();
            }
            API = this;
        } else if (this.getClass().getSimpleName().endsWith("Nodes")) {
            if (NODES != null) {
                throw new IllegalStateException();
            }
            NODES = this;
        } else if (this.getClass().getSimpleName().endsWith("Instrument")) {
            if (INSTRUMENT != null) {
                throw new IllegalStateException();
            }
            INSTRUMENT = this;
        } else if (this.getClass().getSimpleName().endsWith("Debug")) {
            if (DEBUG != null) {
                throw new IllegalStateException();
            }
            DEBUG = this;
        } else {
            if (SPI != null) {
                throw new IllegalStateException();
            }
            SPI = this;
        }
    }

    protected Env attachEnv(Object vm, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Instrumenter instrumenter) {
        return API.attachEnv(vm, language, stdOut, stdErr, stdIn, instrumenter);
    }

    protected Object eval(TruffleLanguage<?> l, Source s) throws IOException {
        return API.eval(l, s);
    }

    protected Object evalInContext(Object vm, SuspendedEvent ev, String code, FrameInstance frame) throws IOException {
        return API.evalInContext(vm, ev, code, frame);
    }

    protected Object importSymbol(Object vm, TruffleLanguage<?> queryingLang, String globalName) {
        return SPI.importSymbol(vm, queryingLang, globalName);
    }

    protected Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit) {
        return API.findExportedSymbol(env, globalName, onlyExplicit);
    }

    protected Object languageGlobal(TruffleLanguage.Env env) {
        return API.languageGlobal(env);
    }

    /**
     * Provided by each {@linkplain TruffleLanguage language implementation}.
     */
    @SuppressWarnings("rawtypes")
    protected boolean isInstrumentable(Object vm, Node node) {
        final RootNode rootNode = node.getRootNode();
        Class<? extends TruffleLanguage> languageClazz = findLanguage(rootNode);
        TruffleLanguage language = findLanguageImpl(vm, languageClazz);
        return isInstrumentable(node, language);
    }

    protected boolean isInstrumentable(Node node, TruffleLanguage<?> language) {
        return API.isInstrumentable(node, language);
    }

    /**
     * Provided by each {@linkplain TruffleLanguage language implementation}.
     */
    @SuppressWarnings("rawtypes")
    protected WrapperNode createWrapperNode(Object vm, Node node) {
        final RootNode rootNode = node.getRootNode();
        Class<? extends TruffleLanguage> languageClazz = findLanguage(rootNode);
        TruffleLanguage language = findLanguageImpl(vm, languageClazz);
        return createWrapperNode(node, language);
    }

    protected WrapperNode createWrapperNode(Node node, TruffleLanguage<?> language) {
        return API.createWrapperNode(node, language);
    }

    @Deprecated
    @SuppressWarnings("rawtypes")
    protected AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(Object vm, Class<? extends TruffleLanguage> languageClass, String expr, AdvancedInstrumentResultListener resultListener)
                    throws IOException {
        return API.createAdvancedInstrumentRootFactory(vm, languageClass, expr, resultListener);
    }

    @SuppressWarnings("rawtypes")
    protected Class<? extends TruffleLanguage> findLanguage(RootNode n) {
        return NODES.findLanguage(n);
    }

    @SuppressWarnings("rawtypes")
    protected Class<? extends TruffleLanguage> findLanguage(Probe probe) {
        return INSTRUMENT.findLanguage(probe);
    }

    @SuppressWarnings("rawtypes")
    protected Env findLanguage(Object known, Class<? extends TruffleLanguage> languageClass) {
        Object vm;
        if (known == null) {
            vm = CURRENT_VM.get();
            if (vm == null) {
                throw new IllegalStateException("Accessor.findLanguage access to vm");
            }
            if (languageClass == null) {
                return null;
            }
        } else {
            vm = known;
        }
        return SPI.findLanguage(vm, languageClass);
    }

    @SuppressWarnings("rawtypes")
    protected TruffleLanguage<?> findLanguageImpl(Object known, Class<? extends TruffleLanguage> languageClass) {
        Object vm;
        if (known == null) {
            vm = CURRENT_VM.get();
            if (vm == null) {
                throw new IllegalStateException("Accessor.findLanguageImpl access to vm");
            }
        } else {
            vm = known;
        }
        return SPI.findLanguageImpl(vm, languageClass);
    }

    protected Instrumenter getInstrumenter(Object known) {
        Object vm;
        if (known == null) {
            vm = CURRENT_VM.get();
            if (vm == null) {
                throw new IllegalStateException("Accessor.getInstrumenter access to vm");
            }
        } else {
            vm = known;
        }
        return SPI.getInstrumenter(vm);
    }

    protected Instrumenter createInstrumenter(Object vm) {
        return INSTRUMENT.createInstrumenter(vm);
    }

    protected Debugger createDebugger(Object vm, Instrumenter instrumenter) {
        return DEBUG.createDebugger(vm, instrumenter);
    }

    private static Reference<Object> previousVM = new WeakReference<>(null);
    private static Assumption oneVM = Truffle.getRuntime().createAssumption();

    protected Closeable executionStart(Object vm, Debugger debugger, Source s) {
        vm.getClass();
        final Closeable debugClose = DEBUG.executionStart(vm, debugger, s);
        final Object prev = CURRENT_VM.get();
        if (!(vm == previousVM.get())) {
            previousVM = new WeakReference<>(vm);
            oneVM.invalidate();
            oneVM = Truffle.getRuntime().createAssumption();

        }
        CURRENT_VM.set(vm);
        class ContextCloseable implements Closeable {
            @Override
            public void close() throws IOException {
                CURRENT_VM.set(prev);
                debugClose.close();
            }
        }
        return new ContextCloseable();
    }

    protected void dispatchEvent(Object vm, Object event) {
        SPI.dispatchEvent(vm, event);
    }

    static Assumption oneVMAssumption() {
        return oneVM;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <C> C findContext(Class<? extends TruffleLanguage> type) {
        Env env = SPI.findLanguage(CURRENT_VM.get(), type);
        return (C) API.findContext(env);
    }

    /**
     * Don't call me. I am here only to let NetBeans debug any Truffle project.
     *
     * @param args
     */
    public static void main(String... args) {
        throw new IllegalStateException();
    }

    protected Object findContext(Env env) {
        return API.findContext(env);
    }

    protected TruffleLanguage<?> findLanguage(Env env) {
        return API.findLanguage(env);
    }

    /** Applies all registered {@linkplain ASTProber probers} to the AST. */
    protected void probeAST(RootNode rootNode) {
        INSTRUMENT.probeAST(rootNode);
    }

    protected void dispose(TruffleLanguage<?> impl, Env env) {
        API.dispose(impl, env);
    }
}
