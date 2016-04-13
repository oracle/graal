/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import java.util.Map;
import java.util.Set;

/**
 * Communication between PolyglotEngine, TruffleLanguage API/SPI, and other services.
 */
public abstract class Accessor {
    public abstract static class Nodes {
        @SuppressWarnings("rawtypes")
        public abstract Class<? extends TruffleLanguage> findLanguage(RootNode n);

        public abstract boolean isInstrumentable(RootNode rootNode);

        public abstract boolean isTaggedWith(Node node, Class<?> tag);
    }

    public abstract static class EngineSupport {
        @SuppressWarnings("rawtypes")
        public abstract Env findEnv(Object vm, Class<? extends TruffleLanguage> languageClass);

        @SuppressWarnings("rawtypes")
        public abstract TruffleLanguage<?> findLanguageImpl(Object known, Class<? extends TruffleLanguage> languageClass, String mimeType);

        @SuppressWarnings("static-method")
        protected final Object findVM() {
            return CURRENT_VM.get();
        }

        public abstract Object getInstrumentationHandler(Object vm);

        public abstract Object getInstrumenter(Object vm);

        public abstract Object importSymbol(Object vm, TruffleLanguage<?> queryingLang, String globalName);

        public abstract void dispatchEvent(Object vm, Object event);

        public abstract boolean isMimeTypeSupported(Object vm, String mimeType);
    }

    public abstract static class LanguageSupport {
        public abstract Env attachEnv(Object vm, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Object instrumenter, Map<String, Object> config);

        public abstract Object eval(TruffleLanguage<?> l, Source s, Map<Source, CallTarget> cache) throws IOException;

        public abstract Object evalInContext(Object vm, Object ev, String code, Node node, MaterializedFrame frame) throws IOException;

        public abstract Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit);

        public abstract Object languageGlobal(TruffleLanguage.Env env);

        public abstract boolean isInstrumentable(Node node, TruffleLanguage<?> language);

        public abstract Object createWrapperNode(Node node, TruffleLanguage<?> language);

        public abstract void dispose(TruffleLanguage<?> impl, Env env);

        public abstract TruffleLanguage<?> findLanguage(Env env);

        public abstract CallTarget parse(TruffleLanguage<?> truffleLanguage, Source code, Node context, String... argumentNames) throws IOException;

        public abstract String toString(TruffleLanguage<?> language, Env env, Object obj);

        public abstract Object findContext(Env env);
    }

    public abstract static class InstrumentSupport {
        public abstract void addInstrument(Object instrumentationHandler, Object key, Class<?> instrumentClass);

        public abstract void disposeInstrument(Object instrumentationHandler, Object key, boolean cleanupRequired);

        public abstract <T> T getInstrumentationHandlerService(Object handler, Object key, Class<T> type);

        public abstract Object createInstrumentationHandler(Object vm, OutputStream out, OutputStream err, InputStream in);

        public abstract void collectEnvServices(Set<Object> collectTo, Object vm, TruffleLanguage<?> impl, Env context);

        public abstract void detachLanguageFromInstrumentation(Object vm, Env context);

        public abstract void onFirstExecution(RootNode rootNode);
    }

    public abstract static class OldInstrumentSupport {
        public abstract void probeAST(RootNode rootNode);
    }

    private static Accessor.LanguageSupport API;
    private static Accessor.EngineSupport SPI;
    private static Accessor.Nodes NODES;
    private static Accessor.OldInstrumentSupport INSTRUMENT;
    private static Accessor.InstrumentSupport INSTRUMENTHANDLER;
    private static Accessor DEBUG;

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

            @SuppressWarnings("deprecation")
            @Override
            protected boolean isInstrumentable(Node node) {
                return false;
            }

            @SuppressWarnings("deprecation")
            @Override
            protected com.oracle.truffle.api.instrument.WrapperNode createWrapperNode(Node node) {
                return null;
            }

            @Override
            protected Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws IOException {
                return null;
            }
        };
        lng.hashCode();
        new Node() {
        }.getRootNode();

        try {
            Class.forName("com.oracle.truffle.api.instrument.Instrumenter", true, Accessor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
        try {
            Class.forName("com.oracle.truffle.api.debug.Debugger", true, Accessor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected Accessor() {
        if (!this.getClass().getName().startsWith("com.oracle.truffle.api")) {
            throw new IllegalStateException();
        }
        if (this.getClass().getSimpleName().endsWith("API")) {
            if (API != null) {
                throw new IllegalStateException();
            }
            API = this.languageSupport();
        } else if (this.getClass().getSimpleName().endsWith("Nodes")) {
            if (NODES != null) {
                throw new IllegalStateException();
            }
            NODES = this.nodes();
        } else if (this.getClass().getSimpleName().endsWith("Instrument")) {
            if (INSTRUMENT != null) {
                throw new IllegalStateException();
            }
            INSTRUMENT = this.oldInstrumentSupport();
        } else if (this.getClass().getSimpleName().endsWith("InstrumentHandler")) {
            if (INSTRUMENTHANDLER != null) {
                throw new IllegalStateException();
            }
            INSTRUMENTHANDLER = this.instrumentSupport();
        } else if (this.getClass().getSimpleName().endsWith("Debug")) {
            if (DEBUG != null) {
                throw new IllegalStateException();
            }
            DEBUG = this;
        } else {
            if (SPI != null) {
                throw new IllegalStateException();
            }
            SPI = this.engineSupport();
        }
    }

    protected Accessor.Nodes nodes() {
        return NODES;
    }

    protected OldInstrumentSupport oldInstrumentSupport() {
        return INSTRUMENT;
    }

    protected LanguageSupport languageSupport() {
        return API;
    }

    protected EngineSupport engineSupport() {
        return SPI;
    }

    protected InstrumentSupport instrumentSupport() {
        return INSTRUMENTHANDLER;
    }

    static InstrumentSupport instrumentAccess() {
        return INSTRUMENTHANDLER;
    }

    static Accessor.Nodes nodesAccess() {
        return NODES;
    }

    //
    // execution
    //

    private static final ThreadLocal<Object> CURRENT_VM = new ThreadLocal<>();
    private static Reference<Object> previousVM = new WeakReference<>(null);
    private static Assumption oneVM = Truffle.getRuntime().createAssumption();

    public static void initializeThreadForUseWithPolglotEngine(Object /* because types are evil */ polyglotEngine) {
        assert polyglotEngine != null;
        assert CURRENT_VM.get() == null;
        CURRENT_VM.set(polyglotEngine);
    }

    @TruffleBoundary
    protected Closeable executionStart(Object vm, @SuppressWarnings("unused") int currentDepth, boolean debugger, Source s) {
        CompilerAsserts.neverPartOfCompilation("do not call Accessor.executionStart from compiled code");
        Objects.requireNonNull(vm);
        final Object prev = CURRENT_VM.get();
        final Closeable debugClose = DEBUG == null ? null : DEBUG.executionStart(vm, prev == null ? 0 : -1, debugger, s);
        if (!(vm == previousVM.get())) {
            previousVM = new WeakReference<>(vm);
            oneVM.invalidate();
            oneVM = Truffle.getRuntime().createAssumption();

        }
        CURRENT_VM.set(vm);
        class ContextCloseable implements Closeable {
            @TruffleBoundary
            @Override
            public void close() throws IOException {
                CURRENT_VM.set(prev);
                if (debugClose != null) {
                    debugClose.close();
                }
            }
        }
        return new ContextCloseable();
    }

    static Assumption oneVMAssumption() {
        return oneVM;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static <C> C findContext(Class<? extends TruffleLanguage> type) {
        Env env = SPI.findEnv(CURRENT_VM.get(), type);
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

    private static final TVMCI SUPPORT = Truffle.getRuntime().getCapability(TVMCI.class);

    @SuppressWarnings("deprecation")
    protected void onLoopCount(Node source, int iterations) {
        if (SUPPORT != null) {
            SUPPORT.onLoopCount(source, iterations);
        } else {
            // needs an additional compatibility check so older graal runtimes
            // still run with newer truffle versions
            RootNode root = source.getRootNode();
            if (root != null) {
                RootCallTarget target = root.getCallTarget();
                if (target instanceof com.oracle.truffle.api.LoopCountReceiver) {
                    ((com.oracle.truffle.api.LoopCountReceiver) target).reportLoopCount(iterations);
                }
            }
        }
    }

    static <T extends TruffleLanguage<?>> T findLanguageByClass(Object vm, Class<T> languageClass) {
        Env env = SPI.findEnv(vm, languageClass);
        TruffleLanguage<?> language = API.findLanguage(env);
        return languageClass.cast(language);
    }
}
