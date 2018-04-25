/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.*;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.ComputeInExecutor.Info;
import com.oracle.truffle.api.vm.PolyglotRootNode.EvalRootNode;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.graalvm.polyglot.io.FileSystem;

/**
 * @since 0.9
 * @see TruffleLanguage More information for language implementors.
 * @deprecated use the {@link Context} instead.
 */
@SuppressWarnings({"deprecation", "rawtypes", "unused", "unchecked", "hiding"})
@Deprecated
public class PolyglotEngine {
    static final PolyglotEngine UNUSABLE_ENGINE = new PolyglotEngine();

    static {
        ensureInitialized();
    }

    static void ensureInitialized() {
        if (VMAccessor.SPI == null || !(VMAccessor.SPI.engineSupport() instanceof LegacyEngineImpl)) {
            VMAccessor.initialize(new LegacyEngineImpl());
        }
    }

    static final PolyglotEngineProfile GLOBAL_PROFILE = new PolyglotEngineProfile(null);
    static final Object UNSET_CONTEXT = new Object();
    private final Thread initThread;
    private final PolyglotCache cachedTargets;
    private final Map<PolyglotRuntime.LanguageShared, Language> sharedToLanguage;
    private final Map<String, Language> mimeTypeToLanguage;
    /* Used for fast context lookup */
    @CompilationFinal(dimensions = 1) final Language[] languageArray;
    final PolyglotRuntime runtime;
    final InputStream in;
    final DispatchOutputStream err;
    final DispatchOutputStream out;
    final ComputeInExecutor.Info executor;

    private volatile boolean disposed;

    static final boolean JDK8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    static {
        try {
            // We need to ensure that the Instrumentation class is loaded so accessors are created
            // properly.
            Class.forName(TruffleInstrument.class.getName(), true, TruffleInstrument.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
    private List<Object[]> config;
    private HashMap<String, DirectValue> globals;

    private final Map<Object, Object> javaInteropCodeCache = new ConcurrentHashMap<>();

    /**
     * Private & temporary only constructor.
     */
    PolyglotEngine() {
        assertNoCompilation();
        ensureInitialized();
        this.initThread = null;
        this.runtime = null;
        this.cachedTargets = null;
        this.languageArray = null;
        this.sharedToLanguage = null;
        this.mimeTypeToLanguage = null;

        this.in = null;
        this.out = null;
        this.err = null;

        this.executor = null;
    }

    /**
     * Constructor used from the builder.
     */
    PolyglotEngine(PolyglotRuntime runtime, Executor executor, InputStream in, DispatchOutputStream out, DispatchOutputStream err, Map<String, Object> globals, List<Object[]> config) {
        assertNoCompilation();

        this.initThread = Thread.currentThread();
        this.runtime = runtime;
        this.languageArray = new Language[runtime.getLanguages().size()];

        this.cachedTargets = new PolyglotCache(this);
        this.sharedToLanguage = new HashMap<>();
        this.mimeTypeToLanguage = new HashMap<>();

        this.in = in;
        this.out = out;
        this.err = err;

        this.executor = ComputeInExecutor.wrap(executor);
        this.globals = new HashMap<>();
        for (Entry<String, Object> entry : globals.entrySet()) {
            this.globals.put(entry.getKey(), new DirectValue(null, entry.getValue()));
        }
        this.config = config;

        initLanguages();
        runtime.notifyEngineCreated();
    }

    private void initLanguages() {
        for (PolyglotRuntime.LanguageShared languageShared : runtime.getLanguages()) {
            Language newLanguage = new Language(languageShared);
            sharedToLanguage.put(languageShared, newLanguage);
            assert languageArray[languageShared.languageId] == null : "attempting to overwrite language";
            languageArray[languageShared.languageId] = newLanguage;
            for (String mimeType : languageShared.cache.getMimeTypes()) {
                mimeTypeToLanguage.put(mimeType, newLanguage);
            }
        }
    }

    DirectValue findLegacyExportedSymbol(String symbolName) {
        DirectValue value = globals.get(symbolName);
        if (value != null) {
            return value;
        }
        DirectValue legacySymbol = findLegacyExportedSymbol(symbolName, true);
        if (legacySymbol != null) {
            return legacySymbol;
        }
        return findLegacyExportedSymbol(symbolName, false);
    }

    private DirectValue findLegacyExportedSymbol(String name, boolean onlyExplicit) {
        final Collection<? extends Language> uniqueLang = getLanguages().values();
        for (Language language : uniqueLang) {
            Env env = language.env;
            if (env != null) {
                Object s = VMAccessor.LANGUAGE.findExportedSymbol(env, name, onlyExplicit);
                if (s != null) {
                    return new DirectValue(language, s);
                }
            }
        }
        return null;
    }

    PolyglotEngine enter() {
        return runtime.engineProfile.enter(this);
    }

    void leave(Object prev) {
        runtime.engineProfile.leave((PolyglotEngine) prev);
    }

    Info executor() {
        return executor;
    }

    private boolean isCurrentVM() {
        return this == runtime.engineProfile.get();
    }

    /**
     * @since 0.10
     */
    public static PolyglotEngine.Builder newBuilder() {
        // making Builder non-static inner class is a
        // nasty trick to avoid the Builder class to appear
        // in Javadoc next to PolyglotEngine class
        PolyglotEngine engine = new PolyglotEngine();
        return engine.new Builder();
    }

    /**
     * @return new builder
     * @deprecated use {@link #newBuilder()}
     * @since 0.9
     */
    @Deprecated
    public static PolyglotEngine.Builder buildNew() {
        return newBuilder();
    }

    /**
     *
     * @since 0.9
     * @deprecated use {@link Context#newBuilder(String...)} instead.
     */
    @Deprecated
    public class Builder {
        private OutputStream out;
        private OutputStream err;
        private InputStream in;
        private PolyglotRuntime runtime;
        private final Map<String, Object> globals = new HashMap<>();
        private Executor executor;
        private List<Object[]> arguments;

        Builder() {
        }

        /**
         *
         * @param os the stream to use as output
         * @return this builder
         * @since 0.9
         */
        public PolyglotEngine.Builder setOut(OutputStream os) {
            out = os;
            return this;
        }

        /**
         * @since 0.9
         */
        public PolyglotEngine.Builder setErr(OutputStream os) {
            err = os;
            return this;
        }

        /**
         * @since 0.9
         */
        public PolyglotEngine.Builder setIn(InputStream is) {
            in = is;
            return this;
        }

        /**
         *
         * @since 0.11
         */
        public PolyglotEngine.Builder config(String mimeType, String key, Object value) {
            if (this.arguments == null) {
                this.arguments = new ArrayList<>();
            }
            this.arguments.add(new Object[]{mimeType, key, value});
            return this;
        }

        /**
         * @since 0.9
         */
        public PolyglotEngine.Builder globalSymbol(String name, Object obj) {
            final Object truffleReady = JavaInterop.asTruffleValue(obj);
            globals.put(name, truffleReady);
            return this;
        }

        /**
         *
         * @since 0.9
         */
        public PolyglotEngine.Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         *
         * @since 0.25
         */
        public PolyglotEngine.Builder runtime(PolyglotRuntime polyglotRuntime) {
            checkRuntime(polyglotRuntime);
            this.runtime = polyglotRuntime;
            return this;
        }

        /**
         * @since 0.9
         */
        public PolyglotEngine build() {
            assertNoCompilation();

            InputStream realIn;
            DispatchOutputStream realOut;
            DispatchOutputStream realErr;

            PolyglotRuntime realRuntime = runtime;
            if (realRuntime == null) {
                realRuntime = PolyglotRuntime.newBuilder().setIn(in).setOut(out).setErr(err).build(true);

                realIn = realRuntime.in;
                realOut = realRuntime.out;
                realErr = realRuntime.err;
            } else {
                checkRuntime(realRuntime);
                if (out == null) {
                    realOut = realRuntime.out;
                } else {
                    realOut = VMAccessor.INSTRUMENT.createDispatchOutput(out);
                    VMAccessor.engine().attachOutputConsumer(realOut, realRuntime.out);
                }
                if (err == null) {
                    realErr = realRuntime.err;
                } else {
                    realErr = VMAccessor.INSTRUMENT.createDispatchOutput(err);
                    VMAccessor.engine().attachOutputConsumer(realErr, realRuntime.err);
                }
                realIn = in == null ? realRuntime.in : in;
            }

            return new PolyglotEngine(realRuntime, executor, realIn, realOut, realErr, globals, arguments);
        }

        private void checkRuntime(PolyglotRuntime realRuntime) {
            if (realRuntime.disposed) {
                throw new IllegalArgumentException("Given runtime already disposed.");
            }
            if (realRuntime.automaticDispose) {
                throw new IllegalArgumentException(
                                "Cannot reuse private/create runtime of another engine. " +
                                                "Please usine an explicitely created PolyglotRuntime instead.");
            }
        }
    }

    /**
     * @since 0.9
     */
    public Map<String, ? extends Language> getLanguages() {
        return Collections.unmodifiableMap(mimeTypeToLanguage);
    }

    /**
     * @since 0.9
     * @deprecated use {@link #getRuntime()}.{@link PolyglotRuntime#getInstruments()}.
     */
    @Deprecated
    public Map<String, Instrument> getInstruments() {
        return runtime.instruments;
    }

    /**
     * @since 0.25
     */
    public PolyglotRuntime getRuntime() {
        return runtime;
    }

    /**
     * @since 0.9
     */
    public Value eval(Source source) {
        assertNoCompilation();
        assert checkThread();
        return evalImpl(findLanguage(source.getMimeType(), true), source);
    }

    private Value evalImpl(final Language l, final Source source) {
        assert checkThread();
        ComputeInExecutor<Object> compute = new ComputeInExecutor<Object>(executor()) {
            @Override
            protected Object compute() {
                CallTarget evalTarget = l.parserCache.get(source);
                if (evalTarget == null) {
                    evalTarget = PolyglotRootNode.createEval(PolyglotEngine.this, l, source);
                    l.parserCache.put(source, evalTarget);
                }
                return evalTarget.call();
            }
        };
        compute.perform();
        return new ExecutorValue(l, compute);
    }

    /**
     * @since 0.9
     */
    public void dispose() {
        assert checkThread();
        assertNoCompilation();
        disposed = true;

        ComputeInExecutor<Void> compute = new ComputeInExecutor<Void>(executor()) {
            @Override
            protected Void compute() {
                Object prev = enter();
                try {
                    disposeImpl();
                    return null;
                } finally {
                    leave(prev);
                }
            }
        };
        compute.get();
    }

    private void disposeImpl() {
        for (Language language : getLanguages().values()) {
            language.disposeContext();
        }
        runtime.notifyEngineDisposed();
    }

    private Object[] debugger() {
        return runtime.debugger;
    }

    /**
     * @since 0.9
     */
    public Value findGlobalSymbol(final String globalName) {
        assert checkThread();
        assertNoCompilation();
        ComputeInExecutor<DirectValue> compute = new ComputeInExecutor<DirectValue>(executor()) {
            @Override
            protected DirectValue compute() {
                Object prev = enter();
                try {
                    return findLegacyExportedSymbol(globalName);
                } finally {
                    leave(prev);
                }
            }
        };
        return compute.get();
    }

    /**
     * @since 0.22
     */
    public Iterable<Value> findGlobalSymbols(String globalName) {
        assert checkThread();
        assertNoCompilation();
        DirectValue value = globals.get(globalName);
        if (value == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(value);
        }
    }

    private static void assertNoCompilation() {
        CompilerAsserts.neverPartOfCompilation("Methods of PolyglotEngine must not be compiled by Truffle. Use Truffle interoperability or a @TruffleBoundary instead.");
    }

    private boolean checkThread() {
        if (initThread != Thread.currentThread()) {
            throw new IllegalStateException("PolyglotEngine created on " + initThread.getName() + " but used on " + Thread.currentThread().getName());
        }
        if (disposed) {
            throw new IllegalStateException("Engine has already been disposed");
        }
        return true;
    }

    private Language findLanguage(String mimeType, boolean failOnError) {
        Language l = mimeTypeToLanguage.get(mimeType);
        if (failOnError && l == null) {
            throw new IllegalStateException("No language for MIME type " + mimeType + " found. Supported types: " + mimeTypeToLanguage.keySet());
        }
        return l;
    }

    //
    // Accessor helper methods
    //

    Language findLanguage(PolyglotRuntime.LanguageShared env) {
        return sharedToLanguage.get(env);
    }

    /*
     * Special version for getLanguage for the fast-path.
     */
    Language getLanguage(Class<? extends TruffleLanguage<?>> languageClass) {
        if (CompilerDirectives.isPartialEvaluationConstant(this)) {
            return getLanguageImpl(languageClass);
        } else {
            return getLanguageBoundary(languageClass);
        }
    }

    @TruffleBoundary
    private Language getLanguageBoundary(Class<? extends TruffleLanguage<?>> languageClass) {
        return getLanguageImpl(languageClass);
    }

    private final FinalIntMap languageIndexMap = new FinalIntMap();

    private Language getLanguageImpl(Class<? extends TruffleLanguage<?>> languageClass) {
        int indexValue = languageIndexMap.get(languageClass);
        if (indexValue == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            Language language = findLanguage(languageClass, false, true);
            indexValue = language.shared.languageId;
            languageIndexMap.put(languageClass, indexValue);
        }
        return languageArray[indexValue];
    }

    Language findLanguage(Class<? extends TruffleLanguage> languageClazz, boolean onlyInitialized, boolean failIfNotFound) {
        for (Language lang : languageArray) {
            assert lang.shared.language != null;
            if (onlyInitialized && lang.getEnv(false) == null) {
                continue;
            }
            TruffleLanguage<?> spi = lang.shared.spi;
            if (languageClazz.isInstance(spi)) {
                return lang;
            }
        }
        if (failIfNotFound) {
            Set<String> languageNames = new HashSet<>();
            for (Language lang : languageArray) {
                languageNames.add(lang.shared.cache.getClassName());
            }
            throw new IllegalStateException("Cannot find language " + languageClazz + " among " + languageNames);
        } else {
            return null;
        }
    }

    Env findEnv(Class<? extends TruffleLanguage> languageClazz, boolean failIfNotFound) {
        Language language = findLanguage(languageClazz, true, failIfNotFound);
        if (language != null) {
            return language.getEnv(false);
        }
        return null;
    }

    /**
     * @since 0.9
     * @deprecated use {@link org.graalvm.polyglot.Value} instead.
     */
    @Deprecated
    public abstract class Value {
        private final Language language;
        private CallTarget executeTarget;
        private CallTarget asJavaObjectTarget;

        Value(Language language) {
            this.language = language;
        }

        abstract boolean isDirect();

        abstract Object value();

        private <T> T unwrapJava(Object value) {
            CallTarget unwrapTarget = cachedTargets.lookupAsJava(value.getClass());
            return (T) unwrapTarget.call(value, Object.class);
        }

        private <T> T asJavaObject(Class<T> type, Object value) {
            if (asJavaObjectTarget == null) {
                asJavaObjectTarget = cachedTargets.lookupAsJava(value == null ? void.class : value.getClass());
            }
            return (T) asJavaObjectTarget.call(value, type);
        }

        private Object executeDirect(Object[] args) {
            Object value = value();
            if (executeTarget == null) {
                executeTarget = cachedTargets.lookupExecute(value.getClass());
            }
            return executeTarget.call(value, args);
        }

        /**
         * @since 0.9
         */
        public Object get() {
            Object result = waitForSymbol();
            if (result instanceof TruffleObject) {
                result = unwrapJava(ConvertedObject.value(result));
                if (result instanceof TruffleObject) {
                    result = EngineTruffleObject.wrap(PolyglotEngine.this, result);
                }
            }
            return ConvertedObject.isNull(result) ? null : result;
        }

        /**
         * @since 0.9
         */
        public <T> T as(final Class<T> representation) {
            Object original = waitForSymbol();
            Object unwrapped = original;

            if (original instanceof TruffleObject) {
                Object realOrig = ConvertedObject.original(original);
                unwrapped = new ConvertedObject((TruffleObject) realOrig, unwrapJava(realOrig));
            }
            if (representation == String.class) {
                Object unwrappedConverted = ConvertedObject.original(unwrapped);
                Object string;
                if (language != null) {
                    PolyglotEngine prev = enter();
                    try {
                        string = VMAccessor.LANGUAGE.toStringIfVisible(language.getEnv(false), unwrappedConverted, false);
                    } finally {
                        leave(prev);
                    }
                } else {
                    /* Language can be null for PolyglotEngine global config values. */
                    string = unwrappedConverted.toString();
                }
                return representation.cast(string);

            }
            if (ConvertedObject.isInstance(representation, unwrapped)) {
                return ConvertedObject.cast(representation, unwrapped);
            }

            if (original instanceof TruffleObject) {
                original = EngineTruffleObject.wrap(PolyglotEngine.this, original);
            }
            Object javaValue = asJavaObject(representation, original);
            if (representation.isPrimitive()) {
                return (T) javaValue;
            } else {
                return representation.cast(javaValue);
            }
        }

        /**
         * @since 0.9
         */
        @Deprecated
        public Value invoke(final Object thiz, final Object... args) {
            return execute(args);
        }

        /**
         * @since 0.9
         */
        public Value execute(final Object... args) {
            if (isDirect()) {
                Object ret = executeDirect(args);
                return new DirectValue(language, ret);
            }
            assertNoCompilation();

            get();
            ComputeInExecutor<Object> invokeCompute = new ComputeInExecutor<Object>(PolyglotEngine.this.executor()) {
                @SuppressWarnings("try")
                @Override
                protected Object compute() {
                    return executeDirect(args);
                }
            };
            invokeCompute.perform();
            return new ExecutorValue(language, invokeCompute);
        }

        private Object waitForSymbol() {
            assertNoCompilation();
            assert PolyglotEngine.this.checkThread();
            Object value = value();
            assert value != null;
            assert !(value instanceof EngineTruffleObject);
            return value;
        }

        /**
         * @since 0.24
         */
        public Value getMetaObject() {
            if (language == null) {
                return null;
            }
            ComputeInExecutor<Object> invokeCompute = new ComputeInExecutor<Object>(executor()) {
                @SuppressWarnings("try")
                @Override
                protected Object compute() {
                    Object prev = enter();
                    try {
                        return VMAccessor.LANGUAGE.findMetaObject(language.getEnv(true), ConvertedObject.original(value()));
                    } finally {
                        leave(prev);
                    }
                }
            };
            Object value = invokeCompute.get();
            if (value != null) {
                return new DirectValue(language, value);
            } else {
                return null;
            }
        }

        /**
         * @since 0.24
         */
        public SourceSection getSourceLocation() {
            if (language == null) {
                return null;
            }
            ComputeInExecutor<SourceSection> invokeCompute = new ComputeInExecutor<SourceSection>(executor()) {
                @SuppressWarnings("try")
                @Override
                protected SourceSection compute() {
                    Object prev = enter();
                    try {
                        return VMAccessor.LANGUAGE.findSourceLocation(language.getEnv(true), ConvertedObject.original(value()));
                    } finally {
                        leave(prev);
                    }
                }
            };
            return invokeCompute.get();
        }
    }

    private class DirectValue extends Value {
        private final Object value;

        DirectValue(Language language, Object value) {
            super(language);
            this.value = value;
            assert value != null;
        }

        @Override
        boolean isDirect() {
            return true;
        }

        @Override
        Object value() {
            return value;
        }

        @Override
        public String toString() {
            return "PolyglotEngine.Value[value=" + value + ",computed=true,exception=null]";
        }
    }

    private class ExecutorValue extends Value {
        private final ComputeInExecutor<Object> compute;

        ExecutorValue(Language language, ComputeInExecutor<Object> compute) {
            super(language);
            this.compute = compute;
        }

        @Override
        boolean isDirect() {
            return false;
        }

        @Override
        Object value() {
            return compute.get();
        }

        @Override
        public String toString() {
            return "PolyglotEngine.Value[" + compute + "]";
        }
    }

    /**
     * @since 0.9
     * @deprecated Use {@link PolyglotRuntime.Instrument}.
     */
    @Deprecated
    public final class Instrument extends PolyglotRuntime.Instrument {
        Instrument(PolyglotRuntime runtime, InstrumentCache cache) {
            runtime.super(cache);
        }
    }

    /**
     * @since 0.9
     * @deprecated use {@link Engine#getLanguages()} instead.
     */
    @Deprecated
    public class Language {
        private volatile TruffleLanguage.Env env;
        final PolyglotRuntime.LanguageShared shared;
        @CompilationFinal volatile Object context = UNSET_CONTEXT;
        private final Map<Source, CallTarget> parserCache;

        Language(PolyglotRuntime.LanguageShared shared) {
            this.shared = shared;
            this.parserCache = new WeakHashMap<>();
        }

        PolyglotEngine engine() {
            return PolyglotEngine.this;
        }

        Object context() {
            return context;
        }

        /**
         * @since 0.9
         */
        public Set<String> getMimeTypes() {
            return shared.cache.getMimeTypes();
        }

        /**
         * @since 0.9
         */
        public String getName() {
            return shared.cache.getName();
        }

        /**
         * @since 0.9
         */
        public String getVersion() {
            return shared.cache.getVersion();
        }

        /**
         * @since 0.22
         */
        public boolean isInteractive() {
            return shared.cache.isInteractive();
        }

        /**
         * @since 0.9
         */
        public Value eval(Source source) {
            assertNoCompilation();
            return evalImpl(this, source);
        }

        /**
         * @since 0.9
         */
        @SuppressWarnings("try")
        public Value getGlobalObject() {
            assert checkThread();
            ComputeInExecutor<Value> compute = new ComputeInExecutor<Value>(executor()) {
                @Override
                protected Value compute() {
                    Object prev = enter();
                    try {
                        Object res = VMAccessor.LANGUAGE.languageGlobal(getEnv(true));
                        if (res == null) {
                            return null;
                        }
                        return new DirectValue(Language.this, res);
                    } finally {
                        leave(prev);
                    }
                }
            };
            return compute.get();
        }

        void disposeContext() {
            if (env != null) {
                synchronized (this) {
                    Env localEnv = this.env;
                    assert localEnv != null;
                    if (localEnv != null) {
                        try {
                            VMAccessor.LANGUAGE.dispose(localEnv);
                        } catch (Exception | Error ex) {
                            ex.printStackTrace();
                        }
                        this.env = null;
                        context = UNSET_CONTEXT;
                    }
                }
            }
        }

        TruffleLanguage.Env getEnv(boolean create) {
            TruffleLanguage.Env localEnv = env;
            if ((localEnv == null && create)) {
                // getEnv is accessed from the instrumentation code so it needs to be
                // thread-safe.
                synchronized (this) {
                    localEnv = env;
                    if (localEnv == null && create) {
                        localEnv = VMAccessor.LANGUAGE.createEnv(this, shared.getLanguageEnsureInitialized(), engine().out, engine().err, engine().in,
                                        getArgumentsForLanguage(), new OptionValuesImpl(null, shared.options), new String[0], FileSystems.newNoIOFileSystem(null));
                        this.env = localEnv;
                        context = VMAccessor.LANGUAGE.createEnvContext(localEnv);
                        VMAccessor.LANGUAGE.postInitEnv(localEnv);
                    }
                }
            }
            return localEnv;
        }

        /** @since 0.9 */
        @Override
        public String toString() {
            return "[" + getName() + "@ " + getVersion() + " for " + getMimeTypes() + "]";
        }

        private Map<String, Object> getArgumentsForLanguage() {
            if (config == null) {
                return Collections.emptyMap();
            }

            Map<String, Object> forLanguage = new HashMap<>();
            for (Object[] mimeKeyValue : config) {
                if (shared.cache.getMimeTypes().contains(mimeKeyValue[0])) {
                    forLanguage.put((String) mimeKeyValue[1], mimeKeyValue[2]);
                }
            }
            return Collections.unmodifiableMap(forLanguage);
        }

    } // end of Language

    //
    // Accessor helper methods
    //
    static final class LegacyEngineImpl extends EngineSupport {

        @Override
        public boolean isDisposed(Object vmObject) {
            return ((Language) vmObject).engine().disposed;
        }

        @Override
        public Object getCurrentContext(Object vmObject) {
            return findVMObject(vmObject).getCurrentContext();
        }

        @Override
        public CallTarget parseForLanguage(Object vmObject, Source source, String[] argumentNames) {
            Env env = ((Language) vmObject).engine().findLanguage(source.getMimeType(), true).getEnv(true);
            CallTarget target = LANGUAGE.parse(env, source, null, argumentNames);
            if (target == null) {
                throw new NullPointerException("Parsing has not produced a CallTarget for " + source);
            }
            return target;
        }

        @Override
        public OptionValues getCompilerOptionValues(RootNode rootNode) {
            // not supported for PolyglotEngine
            return null;
        }

        @Override
        public boolean isHostAccessAllowed(Object vmObject, Env env) {
            return false;
        }

        @Override
        public boolean isNativeAccessAllowed(Object vmObject, Env env) {
            // now way to specify access rights with legacy PolyglotEngine
            return true;
        }

        @Override
        public Object asBoxedGuestValue(Object guestObject, Object vmObject) {
            return guestObject;
        }

        @Override
        public Object lookupHostSymbol(Object vmObject, Env env, String symbolName) {
            return null;
        }

        @Override
        public Object asHostSymbol(Object vmObject, Class<?> symbolClass) {
            return null;
        }

        @Override
        public Object findMetaObjectForLanguage(Object vmObject, Object value) {
            return null;
        }

        @Override
        public Object getVMFromLanguageObject(Object engineObject) {
            return ((PolyglotRuntime.LanguageShared) engineObject).runtime;
        }

        @Override
        public Env getEnvForInstrument(Object vmObject, String languageId, String mimeType) {
            PolyglotEngine currentVM = ((Instrument) vmObject).getRuntime().currentVM();
            if (currentVM == null) {
                throw new IllegalStateException("No current engine found.");
            }
            Language lang = currentVM.findLanguage(mimeType, true);
            Env env = lang.getEnv(true);
            assert env != null;
            return env;
        }

        @Override
        public Object getPolyglotBindingsForLanguage(Object vmObject) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T lookup(InstrumentInfo info, Class<T> serviceClass) {
            Object vmObject = VMAccessor.LANGUAGE.getVMObject(info);
            Instrument instrument = (Instrument) vmObject;
            return instrument.lookup(serviceClass);
        }

        @Override
        public void addToHostClassPath(Object vmObject, TruffleFile entries) {
            // not supported
        }

        @Override
        public <S> S lookup(LanguageInfo language, Class<S> type) {
            PolyglotRuntime.LanguageShared cache = (PolyglotRuntime.LanguageShared) VMAccessor.NODES.getEngineObject(language);
            return VMAccessor.LANGUAGE.lookup(cache.getLanguageEnsureInitialized(), type);
        }

        @Override
        public Env getLanguageEnv(Object languageContextVMObject, LanguageInfo otherLanguage) {
            return null;
        }

        @Override
        public <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
            PolyglotEngine engine = PolyglotEngine.GLOBAL_PROFILE.get();
            if (engine == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("No current context available.");
            }
            Language language = engine.getLanguage(languageClass);
            if (language.env == null || language.context == UNSET_CONTEXT) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("No current context available.");
            }
            return (C) language.context;
        }

        @Override
        public TruffleContext getPolyglotContext(Object vmObject) {
            throw new UnsupportedOperationException("Polyglot contexts are not supported within PolygotEngine.");
        }

        @Override
        public <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
            PolyglotEngine engine = PolyglotEngine.GLOBAL_PROFILE.get();
            if (engine == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("No current language available.");
            }
            Language language = engine.getLanguage(languageClass);
            return languageClass.cast(language.shared.spi);
        }

        @Override
        public Map<String, LanguageInfo> getLanguages(Object vmObject) {
            PolyglotRuntime vm;
            if (vmObject instanceof Language) {
                vm = ((Language) vmObject).shared.getRuntime();
            } else if (vmObject instanceof Instrument) {
                vm = ((Instrument) vmObject).getRuntime();
            } else {
                throw new AssertionError();
            }
            return vm.languageInfos;
        }

        @Override
        public Map<String, InstrumentInfo> getInstruments(Object vmObject) {
            PolyglotRuntime vm;
            if (vmObject instanceof Language) {
                vm = ((Language) vmObject).shared.getRuntime();
            } else if (vmObject instanceof Instrument) {
                vm = ((Instrument) vmObject).getRuntime();
            } else {
                throw new AssertionError();
            }
            return vm.instrumentInfos;
        }

        @Override
        public Env getEnvForInstrument(LanguageInfo language) {
            return ((PolyglotRuntime.LanguageShared) VMAccessor.NODES.getEngineObject(language)).currentLanguage().getEnv(true);
        }

        @Override
        public Env getExistingEnvForInstrument(LanguageInfo language) {
            return ((PolyglotRuntime.LanguageShared) VMAccessor.NODES.getEngineObject(language)).currentLanguage().getEnv(false);
        }

        @Override
        public LanguageInfo getObjectLanguage(Object obj, Object vmObject) {
            for (PolyglotRuntime.LanguageShared ls : ((Instrument) vmObject).getRuntime().getLanguages()) {
                if (!ls.initialized) {
                    continue;
                }
                Env env = ls.currentLanguage().getEnv(false);
                if (env != null && VMAccessor.LANGUAGE.isObjectOfLanguage(env, obj)) {
                    return ls.language;
                }
            }
            return null;
        }

        @Override
        public Object getCurrentVM() {
            return PolyglotEngine.GLOBAL_PROFILE.get();
        }

        @Override
        public boolean isEvalRoot(RootNode target) {
            if (target instanceof EvalRootNode) {
                PolyglotEngine engine = ((EvalRootNode) target).getEngine();
                return engine.isCurrentVM();
            }
            return false;
        }

        @Override
        public boolean isMimeTypeSupported(Object vmObject, String mimeType) {
            return ((Language) vmObject).engine().findLanguage(mimeType, false) != null;
        }

        @Override
        public Env findEnv(Object vmObject, Class<? extends TruffleLanguage> languageClass, boolean failIfNotFound) {
            return ((PolyglotEngine) vmObject).findEnv(languageClass, failIfNotFound);
        }

        @Override
        public Object getInstrumentationHandler(Object vmObject) {
            return ((PolyglotRuntime.LanguageShared) vmObject).getRuntime().instrumentationHandler;
        }

        @Override
        public Object importSymbol(Object vmObject, Env env, String symbolName) {
            Value symbol = ((Language) vmObject).engine().findLegacyExportedSymbol(symbolName);
            if (symbol != null) {
                return symbol.value();
            } else {
                return null;
            }
        }

        @Override
        public void exportSymbol(Object vmObject, String symbolName, Object value) {
            Language language = (Language) vmObject;
            HashMap<String, DirectValue> global = language.engine().globals;
            if (value == null) {
                global.remove(symbolName);
            } else {
                global.put(symbolName, language.engine().new DirectValue(language, value));
            }
        }

        @Override
        public Map<String, ?> getExportedSymbols(Object vmObject) {
            Instrument instrument = (Instrument) vmObject;
            HashMap<String, DirectValue> globals = instrument.getRuntime().currentVM().globals;
            return new AbstractMap<String, Object>() {
                @Override
                public Set<Map.Entry<String, Object>> entrySet() {
                    LinkedHashSet<Map.Entry<String, Object>> valueEntries = new LinkedHashSet<>(globals.size());
                    for (Map.Entry<String, DirectValue> entry : globals.entrySet()) {
                        String name = entry.getKey();
                        Object value = toGuestValue(entry.getValue().value, vmObject);
                        Map.Entry<String, Object> valueEntry = new AbstractMap.SimpleImmutableEntry<>(name, value);
                        valueEntries.add(valueEntry);
                    }
                    return Collections.unmodifiableSet(valueEntries);
                }

                @Override
                public org.graalvm.polyglot.Value remove(Object key) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BiFunction<Object, Object, Object> createToGuestValueNode() {
            return new BiFunction<Object, Object, Object>() {
                @TruffleBoundary
                public Object apply(Object t, Object u) {
                    return toGuestValue(u, t);
                }
            };
        }

        @Override
        public BiFunction<Object, Object[], Object[]> createToGuestValuesNode() {
            return new BiFunction<Object, Object[], Object[]>() {
                @TruffleBoundary
                public Object[] apply(Object t, Object[] u) {
                    for (int i = 0; i < u.length; i++) {
                        u[i] = toGuestValue(u[i], t);
                    }
                    return u;
                }
            };
        }

        @Override
        public RootNode wrapHostBoundary(ExecutableNode executableNode, Supplier<String> name) {
            final PolyglotEngine engine = PolyglotEngine.GLOBAL_PROFILE.get();
            return new RootNode(null) {

                @Override
                public Object execute(VirtualFrame frame) {
                    Object prev = null;
                    if (engine != null) {
                        prev = engine.enter();
                    }
                    try {
                        return executableNode.execute(frame);
                    } finally {
                        if (engine != null) {
                            engine.leave(prev);
                        }
                    }
                }

                @Override
                public String getName() {
                    return name.get();
                }
            };
        }

        @Override
        public void registerDebugger(Object vm, Object debugger) {
            PolyglotEngine engine = (PolyglotEngine) vm;
            assert engine.debugger()[0] == null || engine.debugger()[0] == debugger;
            engine.debugger()[0] = debugger;
        }

        @Override
        public Object findOriginalObject(Object truffleObject) {
            if (truffleObject instanceof EngineTruffleObject) {
                return ((EngineTruffleObject) truffleObject).getDelegate();
            }
            return truffleObject;
        }

        @Override
        public NullPointerException newNullPointerException(String message, Throwable cause) {
            NullPointerException npe = new NullPointerException(message);
            npe.initCause(cause);
            return npe;
        }

        @Override
        public <T> T installJavaInteropCodeCache(Object languageContext, Object key, T value, Class<T> expectedType) {
            PolyglotEngine engine = (PolyglotEngine) languageContext;
            if (engine == null) {
                engine = PolyglotEngine.GLOBAL_PROFILE.get();
            }
            if (engine == null) {
                return value;
            }
            T result = expectedType.cast(engine.javaInteropCodeCache.putIfAbsent(key, value));
            if (result != null) {
                return result;
            } else {
                return value;
            }
        }

        @Override
        public <T> T lookupJavaInteropCodeCache(Object languageContext, Object key, Class<T> expectedType) {
            PolyglotEngine engine = (PolyglotEngine) languageContext;
            if (engine == null) {
                engine = PolyglotEngine.GLOBAL_PROFILE.get();
            }
            if (engine == null) {
                return null;
            }
            return expectedType.cast(engine.javaInteropCodeCache.get(key));
        }

        private static PolyglotRuntime.LanguageShared findVMObject(Object obj) {
            return ((PolyglotRuntime.LanguageShared) obj);
        }

        @Override
        public Object toGuestValue(Object obj, Object languageContext) {
            return JavaInterop.asTruffleValue(obj);
        }

        @Override
        public org.graalvm.polyglot.Value toHostValue(Object obj, Object languageContext) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<Scope> createDefaultLexicalScope(Node node, Frame frame) {
            return DefaultScope.lexicalScope(node, frame);
        }

        @Override
        public Iterable<Scope> createDefaultTopScope(Object global) {
            return DefaultScope.topScope(global);
        }

        @Override
        public void reportAllLanguageContexts(Object vmObject, Object contextsListener) {
            throw new UnsupportedOperationException("Internal contexts are not supported within PolygotEngine.");
        }

        @Override
        public void reportAllContextThreads(Object vmObject, Object threadsListener) {
            throw new UnsupportedOperationException("Internal contexts are not supported within PolygotEngine.");
        }

        @Override
        public TruffleContext getParentContext(Object impl) {
            throw new UnsupportedOperationException("Internal contexts are not supported within PolygotEngine.");
        }

        @Override
        public void closeInternalContext(Object impl) {
            throw new UnsupportedOperationException("Internal contexts are not supported within PolygotEngine.");
        }

        @Override
        public Object createInternalContext(Object vmObject, Map<String, Object> config, TruffleContext context) {
            throw new UnsupportedOperationException("Internal contexts are not supported within PolygotEngine.");
        }

        @Override
        public void initializeInternalContext(Object vmObject, Object contextImpl) {
            throw new UnsupportedOperationException("Internal contexts are not supported within PolygotEngine.");
        }

        @Override
        public Object enterInternalContext(Object impl) {
            throw new UnsupportedOperationException("Internal contexts are not supported within PolygotEngine.");
        }

        @Override
        public void leaveInternalContext(Object impl, Object prev) {
            throw new UnsupportedOperationException("Internal contexts are not supported within PolygotEngine.");
        }

        @Override
        public boolean isCreateThreadAllowed(Object vmObject) {
            return false;
        }

        @Override
        public RuntimeException wrapHostException(Object languageContext, Throwable exception) {
            return PolyglotImpl.wrapHostException((PolyglotLanguageContext) languageContext, exception);
        }

        @Override
        public boolean isHostException(Throwable exception) {
            return exception instanceof HostException;
        }

        @Override
        public Throwable asHostException(Throwable exception) {
            return ((HostException) exception).getOriginal();
        }

        @Override
        public ClassCastException newClassCastException(String message, Throwable cause) {
            return cause == null ? new PolyglotClassCastException(message) : new PolyglotClassCastException(message, cause);
        }

        @Override
        public IllegalArgumentException newIllegalArgumentException(String message, Throwable cause) {
            return cause == null ? new PolyglotIllegalArgumentException(message) : new PolyglotIllegalArgumentException(message, cause);
        }

        @Override
        public UnsupportedOperationException newUnsupportedOperationException(String message, Throwable cause) {
            return cause == null ? new PolyglotUnsupportedException(message) : new PolyglotUnsupportedException(message, cause);
        }

        @Override
        public ArrayIndexOutOfBoundsException newArrayIndexOutOfBounds(String message, Throwable cause) {
            return cause == null ? new PolyglotArrayIndexOutOfBoundsException(message) : new PolyglotArrayIndexOutOfBoundsException(message, cause);
        }

        @Override
        public Object getCurrentHostContext() {
            return null;
        }

        @Override
        public PolyglotException wrapGuestException(String languageId, Throwable e) {
            throw new UnsupportedOperationException("Not supported in legacy engine.");
        }

        @Override
        public Object legacyTckEnter(Object vm) {
            return ((PolyglotEngine) vm).enter();
        }

        @Override
        public void legacyTckLeave(Object vm, Object prev) {
            ((PolyglotEngine) vm).leave(prev);
        }

        @Override
        public <T> T getOrCreateRuntimeData(Object sourceVM, Supplier<T> constructor) {
            return null;
        }

        @Override
        public boolean isCharacterBasedSource(String language, String mimeType) {
            return true;
        }

        @Override
        public Thread createThread(Object vmObject, Runnable runnable, Object context) {
            throw new IllegalStateException("createThread is not supported.");
        }

        @Override
        public org.graalvm.polyglot.SourceSection createSourceSection(Object vmObject, org.graalvm.polyglot.Source source, SourceSection sectionImpl) {
            throw new UnsupportedOperationException("Not supported in legacy engine.");
        }

        @Override
        public String getValueInfo(Object languageContext, Object value) {
            return Objects.toString(value);
        }

        @Override
        public Class<? extends TruffleLanguage<?>> getLanguageClass(LanguageInfo language) {
            return ((PolyglotRuntime.LanguageShared) VMAccessor.NODES.getEngineObject(language)).cache.getLanguageClass();
        }

        @Override
        public boolean isDefaultFileSystem(FileSystem fs) {
            return false;
        }

        @Override
        public String getLanguageHome(Object engineObject) {
            return ((PolyglotRuntime.LanguageShared) engineObject).cache.getLanguageHome();
        }

        @Override
        public boolean isInstrumentExceptionsAreThrown(Object vmObject) {
            return false;
        }

        @Override
        public Handler getLogHandler() {
            return PolyglotLogHandler.INSTANCE;
        }

        @Override
        public LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown) {
            return PolyglotLogHandler.createLogRecord(level, loggerName, message, className, methodName, parameters, thrown);
        }

        @Override
        public Object getCurrentOuterContext() {
            return null;
        }

        @Override
        public Map<String, Level> getLogLevels(Object context) {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> getValidMimeTypes(String language) {
            if (language == null) {
                return LanguageCache.languageMimes().keySet();
            } else {
                LanguageCache lang = LanguageCache.languages().get(language);
                if (lang != null) {
                    return lang.getMimeTypes();
                } else {
                    return Collections.emptySet();
                }
            }
        }
    }
}
