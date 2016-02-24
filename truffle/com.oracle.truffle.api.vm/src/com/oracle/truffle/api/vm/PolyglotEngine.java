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
package com.oracle.truffle.api.vm;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/**
 * Gate way into the world of {@link TruffleLanguage Truffle languages}. {@link #buildNew()
 * Instantiate} your own portal into the isolated, multi language system with all the registered
 * languages ready for your use. A {@link PolyglotEngine} runs inside of a <em>JVM</em>, there can
 * however be multiple instances (some would say tenants) of {@link PolyglotEngine} running next to
 * each other in a single <em>JVM</em> with a complete mutual isolation. There is 1:N mapping
 * between <em>JVM</em> and {@link PolyglotEngine}.
 * <p>
 * It would not be correct to think of a {@link PolyglotEngine} as a runtime for a single
 * {@link TruffleLanguage Truffle language} (Ruby, Python, R, C, JavaScript, etc.) either.
 * {@link PolyglotEngine} can host as many of Truffle languages as {@link Registration registered on
 * a class path} of your <em>JVM</em> application. {@link PolyglotEngine} orchestrates these
 * languages, manages exchange of objects and calls among them. While it may happen that there is
 * just one activated language inside of a {@link PolyglotEngine}, the greatest strength of
 * {@link PolyglotEngine} is in inter-operability between all Truffle languages. There is 1:N
 * mapping between {@link PolyglotEngine} and {@link TruffleLanguage Truffle language
 * implementations}.
 * <p>
 * Use {@link #buildNew()} to create new isolated portal ready for execution of various languages.
 * All the languages in a single portal see each other exported global symbols and can cooperate.
 * Use {@link #buildNew()} multiple times to create different, isolated portal environment
 * completely separated from each other.
 * <p>
 * Once instantiated use {@link #eval(com.oracle.truffle.api.source.Source)} with a reference to a
 * file or URL or directly pass code snippet into the virtual machine via
 * {@link #eval(com.oracle.truffle.api.source.Source)}. Support for individual languages is
 * initialized on demand - e.g. once a file of certain MIME type is about to be processed, its
 * appropriate engine (if found), is initialized. Once an engine gets initialized, it remains so,
 * until the virtual machine isn't garbage collected.
 * <p>
 * The engine is single-threaded and tries to enforce that. It records the thread it has been
 * {@link Builder#build() created} by and checks that all subsequent calls are coming from the same
 * thread. There is 1:1 mapping between {@link PolyglotEngine} and a thread that can tell it what to
 * do.
 */
@SuppressWarnings("rawtypes")
public class PolyglotEngine {
    static final boolean JAVA_INTEROP_ENABLED = !TruffleOptions.AOT;
    static final Logger LOG = Logger.getLogger(PolyglotEngine.class.getName());
    private static final SPIAccessor SPI = new SPIAccessor();
    private final Thread initThread;
    private final Executor executor;
    private final Map<String, Language> langs;
    private final InputStream in;
    private final OutputStream err;
    private final OutputStream out;
    private final EventConsumer<?>[] handlers;
    private final Map<String, Object> globals;
    private final Instrumenter instrumenter; // old instrumentation
    private final Object instrumentationHandler; // new instrumentation
    private final Map<String, Instrument> instruments;
    private final List<Object[]> config;
    // private final Object debugger;
    private boolean disposed;

    static {
        try {
            // We need to ensure that the Instrumentation class is loaded so accessors are created
            // properly.
            Class.forName(TruffleInstrument.class.getName(), true, TruffleInstrument.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Private & temporary only constructor.
     */
    PolyglotEngine() {
        assertNoTruffle();
        this.initThread = null;
        this.in = null;
        this.err = null;
        this.out = null;
        this.langs = null;
        this.handlers = null;
        this.globals = null;
        this.executor = null;
        this.instrumenter = null;
        this.instrumentationHandler = null;
        this.instruments = null;
        this.config = null;
    }

    /**
     * Real constructor used from the builder.
     */
    PolyglotEngine(Executor executor, Map<String, Object> globals, OutputStream out, OutputStream err, InputStream in, EventConsumer<?>[] handlers, List<Object[]> config) {
        assertNoTruffle();
        this.executor = executor;
        this.out = out;
        this.err = err;
        this.in = in;
        this.handlers = handlers;
        this.initThread = Thread.currentThread();
        this.globals = new HashMap<>(globals);
        this.instrumenter = SPI.createInstrumenter(this);
        this.config = config;
        // this.debugger = SPI.createDebugger(this, this.instrumenter);
        // new instrumentation
        this.instrumentationHandler = SPI.createInstrumentationHandler(this, out, err, in);
        Map<String, Language> map = new HashMap<>();
        /* We want to create a language instance but per LanguageCache and not per mime type. */
        Set<LanguageCache> uniqueCaches = new HashSet<>(LanguageCache.languages().values());
        for (LanguageCache languageCache : uniqueCaches) {
            Language newLanguage = new Language(languageCache);
            for (String mimeType : newLanguage.getMimeTypes()) {
                map.put(mimeType, newLanguage);
            }
        }
        this.langs = map;
        this.instruments = createAndAutostartDescriptors(InstrumentCache.load(getClass().getClassLoader()));
    }

    private Map<String, Instrument> createAndAutostartDescriptors(List<InstrumentCache> instrumentCaches) {
        Map<String, Instrument> instr = new LinkedHashMap<>();
        for (InstrumentCache cache : instrumentCaches) {
            Instrument instrument = new Instrument(cache);
            instr.put(cache.getId(), instrument);
        }
        return Collections.unmodifiableMap(instr);
    }

    private boolean isDebuggerOn() {
        for (EventConsumer<?> handler : handlers) {
            if (handler.type.getSimpleName().endsWith("ExecutionEvent")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creation of new Truffle virtual machine. Use the {@link Builder} methods to configure your
     * virtual machine and then create one using {@link Builder#build()}:
     *
     * <pre>
     * {@link PolyglotEngine} vm = {@link PolyglotEngine}.{@link PolyglotEngine#buildNew() buildNew()}
     *     .{@link Builder#setOut(java.io.OutputStream) setOut}({@link OutputStream yourOutput})
     *     .{@link Builder#setErr(java.io.OutputStream) setErr}({@link OutputStream yourOutput})
     *     .{@link Builder#setIn(java.io.InputStream) setIn}({@link InputStream yourInput})
     *     .{@link Builder#build() build()};
     * </pre>
     *
     * It searches for {@link Registration languages registered} in the system class loader and
     * makes them available for later evaluation via
     * {@link #eval(com.oracle.truffle.api.source.Source)} method.
     *
     * @return new builder to create isolated polyglot engine with pre-registered languages
     */
    public static PolyglotEngine.Builder newBuilder() {
        // making Builder non-static inner class is a
        // nasty trick to avoid the Builder class to appear
        // in Javadoc next to PolyglotEngine class
        PolyglotEngine vm = new PolyglotEngine();
        return vm.new Builder();
    }

    /**
     * @return new builder
     * @deprecated use {@link #newBuilder()}
     */
    @Deprecated
    public static PolyglotEngine.Builder buildNew() {
        return newBuilder();
    }

    /**
     * Builder for a new {@link PolyglotEngine}. Call various configuration methods in a chain and
     * at the end create new {@link PolyglotEngine virtual machine}:
     *
     * <pre>
     * {@link PolyglotEngine} vm = {@link PolyglotEngine}.{@link PolyglotEngine#buildNew() buildNew()}
     *     .{@link Builder#setOut(java.io.OutputStream) setOut}({@link OutputStream yourOutput})
     *     .{@link Builder#setErr(java.io.OutputStream) setErr}({@link OutputStream yourOutput})
     *     .{@link Builder#setIn(java.io.InputStream) setIn}({@link InputStream yourInput})
     *     .{@link Builder#build() build()};
     * </pre>
     */
    public class Builder {
        private OutputStream out;
        private OutputStream err;
        private InputStream in;
        private final List<EventConsumer<?>> handlers = new ArrayList<>();
        private final Map<String, Object> globals = new HashMap<>();
        private Executor executor;
        private List<Object[]> arguments;

        Builder() {
        }

        /**
         * Changes the default output for languages running in <em>to be created</em>
         * {@link PolyglotEngine virtual machine}. The default is to use {@link System#out}.
         *
         * @param os the stream to use as output
         * @return instance of this builder
         */
        public Builder setOut(OutputStream os) {
            out = os;
            return this;
        }

        /**
         * Changes the error output for languages running in <em>to be created</em>
         * {@link PolyglotEngine virtual machine}. The default is to use {@link System#err}.
         *
         * @param os the stream to use as output
         * @return instance of this builder
         */
        public Builder setErr(OutputStream os) {
            err = os;
            return this;
        }

        /**
         * Changes the default input for languages running in <em>to be created</em>
         * {@link PolyglotEngine virtual machine}. The default is to use {@link System#in}.
         *
         * @param is the stream to use as input
         * @return instance of this builder
         */
        public Builder setIn(InputStream is) {
            in = is;
            return this;
        }

        /**
         * Registers another instance of {@link EventConsumer} into the to be created
         * {@link PolyglotEngine}.
         *
         * @param handler the handler to register
         * @return instance of this builder
         */
        public Builder onEvent(EventConsumer<?> handler) {
            Objects.requireNonNull(handler);
            handlers.add(handler);
            return this;
        }

        /**
         * Provide configuration data to initialize the {@link PolyglotEngine} for a specific
         * language. These arguments {@link com.oracle.truffle.api.TruffleLanguage.Env#getConfig()
         * can be used by the language} to initialize and configure their
         * {@link com.oracle.truffle.api.TruffleLanguage#createContext(com.oracle.truffle.api.TruffleLanguage.Env)
         * initial execution state} correctly.
         *
         * {@codesnippet config.specify}
         *
         * If the same key is specified multiple times for the same language, the previous values
         * are replaced and just the last one remains.
         *
         * @param mimeType identification of the language for which the arguments are - if the
         *            language declares multiple MIME types, any of them can be used
         *
         * @param key to identify a language-specific configuration element
         * @param value to parameterize initial state of a language
         * @return instance of this builder
         */
        public Builder config(String mimeType, String key, Object value) {
            if (this.arguments == null) {
                this.arguments = new ArrayList<>();
            }
            this.arguments.add(new Object[]{mimeType, key, value});
            return this;
        }

        /**
         * Adds global named symbol into the configuration of to-be-built {@link PolyglotEngine}.
         * This symbol will be accessible to all languages via
         * {@link Env#importSymbol(java.lang.String)} and will take precedence over
         * {@link TruffleLanguage#findExportedSymbol symbols exported by languages itself}. Repeated
         * use of <code>globalSymbol</code> is possible; later definition of the same name overrides
         * the previous one.
         *
         * @param name name of the symbol to register
         * @param obj value of the object - expected to be primitive wrapper, {@link String} or
         *            <code>TruffleObject</code> for mutual inter-operability. If the object isn't
         *            of the previous types, the system tries to wrap it using
         *            {@link JavaInterop#asTruffleObject(java.lang.Object)}, if available
         * @return instance of this builder
         * @see PolyglotEngine#findGlobalSymbol(java.lang.String)
         * @throws IllegalArgumentException if the object isn't of primitive type and cannot be
         *             converted to {@link TruffleObject}
         */
        public Builder globalSymbol(String name, Object obj) {
            final Object truffleReady;
            if (obj instanceof TruffleObject || obj instanceof Number || obj instanceof String || obj instanceof Character || obj instanceof Boolean) {
                truffleReady = obj;
            } else {
                if (JAVA_INTEROP_ENABLED) {
                    truffleReady = JavaInterop.asTruffleObject(obj);
                } else {
                    throw new IllegalArgumentException();
                }
            }
            globals.put(name, truffleReady);
            return this;
        }

        /**
         * Provides own executor for running {@link PolyglotEngine} scripts. By default
         * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source)} and
         * {@link Value#invoke(java.lang.Object, java.lang.Object[])} are executed synchronously in
         * the calling thread. Sometimes, however it is more beneficial to run them asynchronously -
         * the easiest way to do so is to provide own executor when configuring the {
         * {@link #executor(java.util.concurrent.Executor) the builder}. The executor is expected to
         * execute all {@link Runnable runnables} passed into its
         * {@link Executor#execute(java.lang.Runnable)} method in the order they arrive and in a
         * single (yet arbitrary) thread.
         *
         * @param executor the executor to use for internal execution inside the {@link #build() to
         *            be created} {@link PolyglotEngine}
         * @return instance of this builder
         */
        @SuppressWarnings("hiding")
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Creates the {@link PolyglotEngine Truffle virtual machine}. The configuration is taken
         * from values passed into configuration methods in this class.
         *
         * @return new, isolated virtual machine with pre-registered languages
         */
        public PolyglotEngine build() {
            assertNoTruffle();
            if (out == null) {
                out = System.out;
            }
            if (err == null) {
                err = System.err;
            }
            if (in == null) {
                in = System.in;
            }
            return new PolyglotEngine(executor, globals, out, err, in, handlers.toArray(new EventConsumer[0]), arguments);
        }
    }

    /**
     * Descriptions of languages supported in this Truffle virtual machine.
     *
     * @return an immutable map with keys being MIME types and values the {@link Language
     *         descriptions} of associated languages
     */
    public Map<String, ? extends Language> getLanguages() {
        return Collections.unmodifiableMap(langs);
    }

    /**
     * Returns all instruments loaded in this this polyglot engine. Please note that some
     * instruments are enabled automatically at startup.
     *
     * @return the set of instruments
     */
    public Map<String, Instrument> getInstruments() {
        return instruments;
    }

    /**
     * Evaluates provided source. Chooses language registered for a particular
     * {@link Source#getMimeType() MIME type} (throws {@link IOException} if there is none). The
     * language is then allowed to parse and execute the source.
     *
     * @param source code snippet to execute
     * @return a {@link Value} object that holds result of an execution, never <code>null</code>
     * @throws IOException thrown to signal errors while processing the code
     */
    public Value eval(Source source) throws IOException {
        assertNoTruffle();
        String mimeType = source.getMimeType();
        checkThread();
        Language l = langs.get(mimeType);
        if (l == null) {
            throw new IOException("No language for MIME type " + mimeType + " found. Supported types: " + langs.keySet());
        }
        return eval(l, source);
    }

    /**
     * Dispose instance of this engine. A user can explicitly
     * {@link TruffleLanguage#disposeContext(java.lang.Object) dispose all resources} allocated by
     * the languages active in this engine, when it is known the system is not going to be used in
     * the future.
     * <p>
     * Calling any other method of this class after the dispose has been done yields an
     * {@link IllegalStateException}.
     */
    public void dispose() {
        checkThread();
        assertNoTruffle();
        disposed = true;
        ComputeInExecutor<Void> compute = new ComputeInExecutor<Void>(executor) {
            @Override
            protected Void compute() throws IOException {
                for (Language language : getLanguages().values()) {
                    TruffleLanguage<?> impl = language.getImpl(false);
                    if (impl != null) {
                        try {
                            SPI.dispose(impl, language.getEnv(true));
                        } catch (Exception | Error ex) {
                            LOG.log(Level.SEVERE, "Error disposing " + impl, ex);
                        }
                    }
                }

                for (Instrument instrument : instruments.values()) {
                    try {
                        /*
                         * TODO (chumer): ideally no cleanup is required for disposing
                         * PolyglotEngine if no ASTs are shared between instances. the anything
                         * might be shared assumption invalidates this optimization we should have a
                         * way to find out if a CallTarget/RootNode is shared across PolyglotEngine
                         * instances.
                         */
                        instrument.setEnabledImpl(false, false);
                    } catch (Exception | Error ex) {
                        LOG.log(Level.SEVERE, "Error disposing " + instrument, ex);
                    }
                }
                return null;
            }
        };
        try {
            compute.perform();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Value eval(final Language l, final Source s) throws IOException {
        final TruffleLanguage[] lang = {null};
        ComputeInExecutor<Object> compute = new ComputeInExecutor<Object>(executor) {
            @Override
            protected Object compute() throws IOException {
                return evalImpl(lang, s, l);
            }
        };
        compute.perform();
        return new Value(lang, compute);
    }

    Language createLanguage(Map.Entry<String, LanguageCache> en) {
        return new Language(en.getValue());
    }

    @SuppressWarnings("try")
    private Object evalImpl(TruffleLanguage<?>[] fillLang, Source s, Language l) throws IOException {
        try (Closeable d = SPI.executionStart(this, -1, isDebuggerOn(), s)) {
            TruffleLanguage<?> langImpl = l.getImpl(true);
            fillLang[0] = langImpl;
            return SPI.eval(langImpl, s, l.cache);
        }
    }

    @SuppressWarnings({"try", "deprecation"})
    final Object invokeForeign(final Node foreignNode, VirtualFrame frame, final TruffleObject receiver) throws IOException {
        assertNoTruffle();
        Object res;
        CompilerAsserts.neverPartOfCompilation();
        if (executor == null) {
            try (final Closeable c = SPI.executionStart(PolyglotEngine.this, -1, false, null)) {
                final Object[] args = ForeignAccess.getArguments(frame).toArray();
                res = ForeignAccess.execute(foreignNode, frame, receiver, args);
            }
        } else {
            res = invokeForeignOnExecutor(foreignNode, frame, receiver);
        }
        if (res instanceof TruffleObject) {
            return new EngineTruffleObject(this, (TruffleObject) res);
        } else {
            return res;
        }
    }

    static void assertNoTruffle() {
        CompilerAsserts.neverPartOfCompilation("Methods of PolyglotEngine must not be compiled by Truffle. Use Truffle interoperability or a @TruffleBoundary instead.");
    }

    @TruffleBoundary
    private Object invokeForeignOnExecutor(final Node foreignNode, VirtualFrame frame, final TruffleObject receiver) throws IOException {
        final MaterializedFrame materialized = frame.materialize();
        ComputeInExecutor<Object> compute = new ComputeInExecutor<Object>(executor) {
            @SuppressWarnings("try")
            @Override
            protected Object compute() throws IOException {
                try (final Closeable c = SPI.executionStart(PolyglotEngine.this, -1, false, null)) {
                    final Object[] args = ForeignAccess.getArguments(materialized).toArray();
                    RootNode node = SymbolInvokerImpl.createTemporaryRoot(TruffleLanguage.class, foreignNode, receiver, args.length);
                    final CallTarget target = Truffle.getRuntime().createCallTarget(node);
                    return target.call(args);
                }
            }
        };
        return compute.get();
    }

    /**
     * Looks global symbol provided by one of initialized languages up. First of all execute your
     * program via one of your {@link #eval(com.oracle.truffle.api.source.Source)} and then look
     * expected symbol up using this method.
     * <p>
     * The names of the symbols are language dependent, but for example the Java language bindings
     * follow the specification for method references:
     * <ul>
     * <li>"java.lang.Exception::new" is a reference to constructor of {@link Exception}
     * <li>"java.lang.Integer::valueOf" is a reference to static method in {@link Integer} class
     * </ul>
     * Once an symbol is obtained, it remembers values for fast access and is ready for being
     * invoked.
     *
     * @param globalName the name of the symbol to find
     * @return found symbol or <code>null</code> if it has not been found
     */
    public Value findGlobalSymbol(final String globalName) {
        checkThread();
        assertNoTruffle();
        final TruffleLanguage<?>[] lang = {null};
        ComputeInExecutor<Object> compute = new ComputeInExecutor<Object>(executor) {
            @Override
            protected Object compute() throws IOException {
                Object obj = globals.get(globalName);
                if (obj == null) {
                    for (Language dl : langs.values()) {
                        TruffleLanguage.Env env = dl.getEnv(false);
                        if (env == null) {
                            continue;
                        }
                        obj = SPI.findExportedSymbol(env, globalName, true);
                        if (obj != null) {
                            lang[0] = dl.getImpl(true);
                            break;
                        }
                    }
                }
                if (obj == null) {
                    for (Language dl : langs.values()) {
                        TruffleLanguage.Env env = dl.getEnv(false);
                        if (env == null) {
                            continue;
                        }
                        obj = SPI.findExportedSymbol(env, globalName, true);
                        if (obj != null) {
                            lang[0] = dl.getImpl(true);
                            break;
                        }
                    }
                }
                return obj;
            }
        };
        try {
            compute.perform();
            if (compute.get() == null) {
                return null;
            }
        } catch (IOException ex) {
            // OK, go on
        }
        return new Value(lang, compute);
    }

    private void checkThread() {
        if (initThread != Thread.currentThread()) {
            throw new IllegalStateException("PolyglotEngine created on " + initThread.getName() + " but used on " + Thread.currentThread().getName());
        }
        if (disposed) {
            throw new IllegalStateException("Engine has already been disposed");
        }
    }

    @SuppressWarnings("unchecked")
    void dispatch(Object ev) {
        Class type = ev.getClass();
        if (type.getSimpleName().equals("SuspendedEvent")) {
            dispatchSuspendedEvent(ev);
        }
        if (type.getSimpleName().equals("ExecutionEvent")) {
            dispatchExecutionEvent(ev);
        }
        dispatch(type, ev);
    }

    @SuppressWarnings("unused")
    void dispatchSuspendedEvent(Object event) {
    }

    @SuppressWarnings("unused")
    void dispatchExecutionEvent(Object event) {
    }

    @SuppressWarnings("unchecked")
    <Event> void dispatch(Class<Event> type, Event event) {
        for (EventConsumer handler : handlers) {
            if (handler.type == type) {
                handler.on(event);
            }
        }
    }

    /**
     * A future value wrapper. A user level wrapper around values returned by evaluation of various
     * {@link PolyglotEngine} functions like
     * {@link PolyglotEngine#findGlobalSymbol(java.lang.String)} and
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source)} or a value returned by
     * {@link #invoke(java.lang.Object, java.lang.Object...) a subsequent execution}. In case the
     * {@link PolyglotEngine} has been initialized for
     * {@link Builder#executor(java.util.concurrent.Executor) asynchronous execution}, the
     * {@link Value} represents a future - i.e., it is returned immediately, leaving the execution
     * running on behind.
     */
    public class Value {
        private final TruffleLanguage<?>[] language;
        private final ComputeInExecutor<Object> compute;
        private CallTarget target;

        Value(TruffleLanguage<?>[] language, ComputeInExecutor<Object> compute) {
            this.language = language;
            this.compute = compute;
        }

        Value(TruffleLanguage<?>[] language, final Object value) {
            this.language = language;
            this.compute = new ComputeInExecutor<Object>(null) {
                @Override
                protected Object compute() throws IOException {
                    return value;
                }
            };
        }

        /**
         * Obtains the object represented by this symbol. The <em>raw</em> object can either be a
         * wrapper about primitive type (e.g. {@link Number}, {@link String}, {@link Character},
         * {@link Boolean}) or a <em>TruffleObject</em> representing more complex object from a
         * language. The method can return <code>null</code>.
         *
         * @return the object or <code>null</code>
         * @throws IOException in case it is not possible to obtain the value of the object
         */
        public Object get() throws IOException {
            assertNoTruffle();
            Object result = waitForSymbol();
            if (result instanceof TruffleObject) {
                return new EngineTruffleObject(PolyglotEngine.this, (TruffleObject) result);
            } else {
                return result;
            }
        }

        /**
         * Obtains Java view of the object represented by this symbol. The method basically
         * delegates to
         * {@link JavaInterop#asJavaObject(java.lang.Class, com.oracle.truffle.api.interop.TruffleObject)}
         * . The method handles primitive types (like {@link Number}, etc.) by casting and returning
         * them. When a {@link String}.<code>class</code> is requested, the method let's the
         * language that produced the value to do the
         * {@link TruffleLanguage#toString(java.lang.Object, java.lang.Object) necessary formating}.
         *
         * @param <T> the type of the view one wants to obtain
         * @param representation the class of the view interface (it has to be an interface)
         * @return instance of the view wrapping the object of this symbol
         * @throws IOException in case it is not possible to obtain the value of the object
         * @throws ClassCastException if the value cannot be converted to desired view
         */
        public <T> T as(final Class<T> representation) throws IOException {
            assertNoTruffle();
            final Object obj = get();
            if (obj instanceof EngineTruffleObject) {
                EngineTruffleObject eto = (EngineTruffleObject) obj;
                if (representation.isInstance(eto.getDelegate())) {
                    return representation.cast(eto.getDelegate());
                }
            }
            if (representation == String.class) {
                final Class<? extends TruffleLanguage> clazz = language[0].getClass();
                Object unwrapped = obj;
                while (unwrapped instanceof EngineTruffleObject) {
                    unwrapped = ((EngineTruffleObject) obj).getDelegate();
                }
                return representation.cast(SPI.toString(language[0], findEnv(clazz), unwrapped));
            }
            if (representation.isInstance(obj)) {
                return representation.cast(obj);
            }
            if (JAVA_INTEROP_ENABLED) {
                return JavaInterop.asJavaObject(representation, (TruffleObject) obj);
            }
            throw new ClassCastException("Value cannot be represented as " + representation.getName());
        }

        /**
         * Invokes the symbol. If the symbol represents a function, then it should be invoked with
         * provided arguments. If the symbol represents a field, then first argument (if provided)
         * should set the value to the field; the return value should be the actual value of the
         * field when the <code>invoke</code> method returns.
         *
         * @param thiz this/self in language that support such concept; use <code>null</code> to let
         *            the language use default this/self or ignore the value
         * @param args arguments to pass when invoking the symbol
         * @return symbol wrapper around the value returned by invoking the symbol, never
         *         <code>null</code>
         * @throws IOException signals problem during execution
         */
        @Deprecated
        public Value invoke(final Object thiz, final Object... args) throws IOException {
            return execute(args);
        }

        /**
         * Executes the symbol. If the symbol represents a function, then it should be invoked with
         * provided arguments. If the symbol represents a field, then first argument (if provided)
         * should set the value to the field; the return value should be the actual value of the
         * field when the <code>invoke</code> method returns.
         *
         * @param args arguments to pass when invoking the symbol; either wrappers of Java primitive
         *            types (e.g. {@link java.lang.Byte}, {@link java.lang.Short},
         *            {@link java.lang.Integer}, {@link java.lang.Long}, {@link java.lang.Float},
         *            {@link java.lang.Double}, {@link java.lang.Character},
         *            {@link java.lang.Boolean}, and {@link java.lang.String}) or a
         *            {@link TruffleObject object created} by one of the languages)
         *
         * @return symbol wrapper around the value returned by invoking the symbol, never
         *         <code>null</code>
         * @throws IOException signals problem during execution
         */
        public Value execute(final Object... args) throws IOException {
            assertNoTruffle();
            get();
            ComputeInExecutor<Object> invokeCompute = new ComputeInExecutor<Object>(executor) {
                @SuppressWarnings("try")
                @Override
                protected Object compute() throws IOException {
                    try (final Closeable c = SPI.executionStart(PolyglotEngine.this, -1, false, null)) {
                        List<Object> arr = new ArrayList<>();
                        arr.addAll(Arrays.asList(args));
                        for (;;) {
                            try {
                                if (target == null) {
                                    target = SymbolInvokerImpl.createCallTarget(language[0], compute.get(), arr.toArray());
                                }
                                return target.call(arr.toArray());
                            } catch (ArgumentsMishmashException ex) {
                                target = null;
                            }
                        }
                    }
                }
            };
            invokeCompute.perform();
            return new Value(language, invokeCompute);
        }

        private Object waitForSymbol() throws IOException {
            assertNoTruffle();
            checkThread();
            return compute.get();
        }

        @Override
        public String toString() {
            return "PolyglotEngine.Value[" + compute + "]";
        }
    }

    /**
     * Represents a handle to a given installed instrumentation. With the handle it is possible to
     * get metadata provided by the instrument. Also it is possible to
     * {@link Instrument#setEnabled(boolean)} enable/disable a given instrument.
     *
     * @see PolyglotEngine#getInstruments()
     */
    public final class Instrument {

        private final InstrumentCache info;

        private boolean enabled;

        Instrument(InstrumentCache cache) {
            this.info = cache;
        }

        /**
         * @return the id of the instrument
         */
        public String getId() {
            return info.getId();
        }

        /**
         * @return a human readable name of the installed instrument.
         */
        public String getName() {
            return info.getName();
        }

        /**
         * @return the version of the installed instrument.
         */
        public String getVersion() {
            return info.getVersion();
        }

        InstrumentCache getCache() {
            return info;
        }

        /**
         * @return <code>true</code> if the underlying instrument is enabled else <code>false</code>
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Lookup additional service provided by the instrument. Here is an example how to query for
         * a hypothetical <code>DebuggerController</code>: {@codesnippet DebuggerExampleTest}
         *
         * @param <T> the type of the service
         * @param type class of the service that is being requested
         * @return instance of requested type, or <code>null</code> if no such service is available
         *         for the instrument
         */
        public <T> T lookup(Class<T> type) {
            return SPI.getInstrumentationHandlerService(instrumentationHandler, this, type);
        }

        /**
         * Enables/disables the installed instrument in the engine.
         *
         * @param enabled <code>true</code> to enable <code>false</code> to disable
         */
        public void setEnabled(final boolean enabled) {
            checkThread();
            if (this.enabled != enabled) {
                ComputeInExecutor<Void> compute = new ComputeInExecutor<Void>(executor) {
                    @Override
                    protected Void compute() throws IOException {
                        setEnabledImpl(enabled, true);
                        return null;
                    }

                };
                try {
                    compute.perform();
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }

        void setEnabledImpl(final boolean enabled, boolean cleanup) {
            if (this.enabled != enabled) { // check again for thread safety
                if (enabled) {
                    SPI.addInstrumentation(instrumentationHandler, this, getCache().getInstrumentationClass());
                } else {
                    SPI.disposeInstrumentation(instrumentationHandler, this, cleanup);
                }
                this.enabled = enabled;
            }
        }

        @Override
        public String toString() {
            return "Instrument [id=" + getId() + ", name=" + getName() + ", version=" + getVersion() + ", enabled=" + enabled + "]";
        }
    }

    /**
     * Description of a language registered in {@link PolyglotEngine Truffle virtual machine}.
     * Languages are registered by {@link Registration} annotation which stores necessary
     * information into a descriptor inside of the language's JAR file. When a new
     * {@link PolyglotEngine} is created, it reads all available descriptors and creates
     * {@link Language} objects to represent them. One can obtain a {@link #getName() name} or list
     * of supported {@link #getMimeTypes() MIME types} for each language. The actual language
     * implementation is not initialized until
     * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source) a code is evaluated} in it.
     */
    public class Language {
        private final Map<Source, CallTarget> cache;
        private final LanguageCache info;
        private TruffleLanguage.Env env;

        Language(LanguageCache info) {
            this.cache = new WeakHashMap<>();
            this.info = info;
        }

        /**
         * MIME types recognized by the language.
         *
         * @return returns immutable set of recognized MIME types
         */
        public Set<String> getMimeTypes() {
            return info.getMimeTypes();
        }

        /**
         * Human readable name of the language. Think of C, Ruby, JS, etc.
         *
         * @return string giving the language a name
         */
        public String getName() {
            return info.getName();
        }

        /**
         * Name of the language version.
         *
         * @return string specifying the language version
         */
        public String getVersion() {
            return info.getVersion();
        }

        /**
         * Evaluates provided source. Ignores the particular {@link Source#getMimeType() MIME type}
         * and forces evaluation in the context of <code>this</code> language.
         *
         * @param source code snippet to execute
         * @return a {@link Value} object that holds result of an execution, never <code>null</code>
         * @throws IOException thrown to signal errors while processing the code
         */
        public Value eval(Source source) throws IOException {
            assertNoTruffle();
            checkThread();
            return PolyglotEngine.this.eval(this, source);
        }

        /**
         * Returns value representing global object of the language.
         * <p>
         * The object is expected to be <code>TruffleObject</code> (e.g. a native object from the
         * other language) but technically it can be one of Java primitive wrappers ({@link Integer}
         * , {@link Double}, {@link Short}, etc.).
         *
         * @return the global object or <code>null</code> if the language does not support such
         *         concept
         */
        @SuppressWarnings("try")
        public Value getGlobalObject() {
            checkThread();
            try (Closeable d = SPI.executionStart(PolyglotEngine.this, -1, false, null)) {
                Object res = SPI.languageGlobal(getEnv(true));
                return res == null ? null : new Value(new TruffleLanguage[]{info.getImpl(true)}, res);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        TruffleLanguage<?> getImpl(boolean create) {
            getEnv(create);
            TruffleLanguage<?> impl = info.getImpl(false);
            return impl;
        }

        private Map<String, Object> getArgumentsForLanguage() {
            if (config == null) {
                return Collections.emptyMap();
            }

            Map<String, Object> forLanguage = new HashMap<>();
            for (Object[] mimeKeyValue : config) {
                if (getMimeTypes().contains(mimeKeyValue[0])) {
                    forLanguage.put((String) mimeKeyValue[1], mimeKeyValue[2]);
                }
            }
            return Collections.unmodifiableMap(forLanguage);
        }

        TruffleLanguage.Env getEnv(boolean create) {
            if (env == null && create) {
                env = SPI.attachEnv(PolyglotEngine.this, info.getImpl(true), out, err, in, instrumenter, getArgumentsForLanguage());
            }
            return env;
        }

        @Override
        public String toString() {
            return "[" + getName() + "@ " + getVersion() + " for " + getMimeTypes() + "]";
        }
    } // end of Language

    //
    // Accessor helper methods
    //

    TruffleLanguage<?> findLanguage(Class<? extends TruffleLanguage> languageClazz) {
        for (Map.Entry<String, Language> entrySet : langs.entrySet()) {
            Language languageDescription = entrySet.getValue();
            final TruffleLanguage<?> impl = languageDescription.getImpl(false);
            if (languageClazz.isInstance(impl)) {
                return impl;
            }
        }
        return null;
    }

    TruffleLanguage<?> findLanguage(String mimeType) {
        Language languageDescription = this.langs.get(mimeType);
        if (languageDescription != null) {
            return languageDescription.getImpl(true);
        }
        return null;
    }

    TruffleLanguage<?> findLanguage(Probe probe) {
        return findLanguage(SPI.findLanguage(probe));
    }

    Env findEnv(Class<? extends TruffleLanguage> languageClazz) {
        for (Map.Entry<String, Language> entrySet : langs.entrySet()) {
            Language languageDescription = entrySet.getValue();
            Env env = languageDescription.getEnv(false);
            if (env != null && languageClazz.isInstance(languageDescription.getImpl(false))) {
                return env;
            }
        }
        throw new IllegalStateException("Cannot find language " + languageClazz + " among " + langs);
    }

    private static class SPIAccessor extends Accessor {
        @Override
        public Object importSymbol(Object vmObj, TruffleLanguage<?> ownLang, String globalName) {
            PolyglotEngine vm = (PolyglotEngine) vmObj;
            Object g = vm.globals.get(globalName);
            if (g != null) {
                return g;
            }
            Set<Language> uniqueLang = new LinkedHashSet<>(vm.langs.values());
            for (Language dl : uniqueLang) {
                TruffleLanguage<?> l = dl.getImpl(false);
                TruffleLanguage.Env env = dl.getEnv(false);
                if (l == ownLang || l == null || env == null) {
                    continue;
                }
                Object obj = SPI.findExportedSymbol(env, globalName, true);
                if (obj != null) {
                    return obj;
                }
            }
            for (Language dl : uniqueLang) {
                TruffleLanguage<?> l = dl.getImpl(false);
                TruffleLanguage.Env env = dl.getEnv(false);
                if (l == ownLang || l == null || env == null) {
                    continue;
                }
                Object obj = SPI.findExportedSymbol(env, globalName, false);
                if (obj != null) {
                    return obj;
                }
            }
            return null;
        }

        @Override
        protected Env attachEnv(Object obj, TruffleLanguage<?> language, OutputStream stdOut, OutputStream stdErr, InputStream stdIn, Instrumenter instrumenter, Map<String, Object> config) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            return super.attachEnv(vm, language, stdOut, stdErr, stdIn, instrumenter, config);
        }

        @Override
        protected Object eval(TruffleLanguage<?> l, Source s, Map<Source, CallTarget> cache) throws IOException {
            return super.eval(l, s, cache);
        }

        @Override
        public Object findExportedSymbol(TruffleLanguage.Env env, String globalName, boolean onlyExplicit) {
            return super.findExportedSymbol(env, globalName, onlyExplicit);
        }

        @Override
        protected Object languageGlobal(TruffleLanguage.Env env) {
            return super.languageGlobal(env);
        }

        @Override
        protected Instrumenter createInstrumenter(Object vm) {
            return super.createInstrumenter(vm);
        }

        @Override
        protected Object createInstrumentationHandler(Object vm, OutputStream out, OutputStream err, InputStream in) {
            return super.createInstrumentationHandler(vm, out, err, in);
        }

        @Override
        protected Instrumenter getInstrumenter(Object obj) {
            final PolyglotEngine vm = (PolyglotEngine) obj;
            return vm.instrumenter;
        }

        @Override
        protected Object getInstrumentationHandler(Object obj) {
            final PolyglotEngine vm = (PolyglotEngine) obj;
            return vm.instrumentationHandler;
        }

        @Override
        protected <T> T getInstrumentationHandlerService(Object vm, Object key, Class<T> type) {
            return super.getInstrumentationHandlerService(vm, key, type);
        }

        @Override
        protected void detachFromInstrumentation(Object vm, Env env) {
            super.detachFromInstrumentation(vm, env);
        }

        @Override
        protected void addInstrumentation(Object instrumentationHandler, Object key, Class<?> instrumentationClass) {
            super.addInstrumentation(instrumentationHandler, key, instrumentationClass);
        }

        @Override
        protected void disposeInstrumentation(Object instrumentationHandler, Object key, boolean cleanupRequired) {
            super.disposeInstrumentation(instrumentationHandler, key, cleanupRequired);
        }

        @Override
        protected Class<? extends TruffleLanguage> findLanguage(Probe probe) {
            return super.findLanguage(probe);
        }

        @Override
        protected boolean isMimeTypeSupported(Object obj, String mimeType) {
            final PolyglotEngine vm = (PolyglotEngine) obj;
            return vm.findLanguage(mimeType) != null;
        }

        @Override
        protected Env findLanguage(Object obj, Class<? extends TruffleLanguage> languageClass) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            return vm.findEnv(languageClass);
        }

        @Override
        protected TruffleLanguage<?> findLanguageImpl(Object obj, Class<? extends TruffleLanguage> languageClazz, String mimeType) {
            final PolyglotEngine vm = (PolyglotEngine) obj;
            TruffleLanguage<?> language = null;
            if (languageClazz != null) {
                language = vm.findLanguage(languageClazz);
            }
            if (language == null && mimeType != null) {
                language = vm.findLanguage(mimeType);
            }
            if (language == null) {
                throw new IllegalStateException("Cannot find language " + languageClazz + " with mimeType" + mimeType + " among " + vm.langs);
            }
            return language;
        }

        @Override
        protected Closeable executionStart(Object obj, int currentDepth, boolean initializeDebugger, Source s) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            return super.executionStart(vm, -1, initializeDebugger, s);
        }

        @Override
        protected void dispatchEvent(Object obj, Object event) {
            PolyglotEngine vm = (PolyglotEngine) obj;
            vm.dispatch(event);
        }

        @Override
        protected void dispose(TruffleLanguage<?> impl, TruffleLanguage.Env env) {
            super.dispose(impl, env);
        }

        @Override
        protected String toString(TruffleLanguage language, TruffleLanguage.Env env, Object obj) {
            return super.toString(language, env, obj);
        }
    } // end of SPIAccessor
}
