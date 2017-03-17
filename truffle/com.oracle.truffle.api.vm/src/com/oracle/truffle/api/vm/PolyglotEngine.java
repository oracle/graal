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

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.ComputeInExecutor.Info;
import com.oracle.truffle.api.vm.LanguageCache.LoadedLanguage;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import com.oracle.truffle.api.vm.PolyglotRootNode.EvalRootNode;
import java.util.HashSet;

/**
 * A multi-language execution environment for Truffle-implemented {@linkplain Language languages}
 * that supports <em>interoperability</em> among the Truffle languages and with Java, for example
 * cross-language calls, foreign object exchange, and shared <em>global symbols</em>. The
 * environment also includes a framework for <em>instrumentation</em> that supports both built-in
 * services, for example debugging and profiling, as well as API access to dynamic execution state
 * for external tools.
 * <p>
 * For a simple example see {@link #eval(Source) eval(Source)}. The
 * <em><a href="{@docRoot}/com/oracle/truffle/tutorial/package-summary.html">Truffle
 * Tutorial</a></em> provides much more general information about Truffle. For information
 * specifically related to Java applications, the tutorial
 * <em><a href="{@docRoot}/com/oracle/truffle/tutorial/embedding/package-summary.html">Embedding
 * Truffle Languages in Java</a></em> explains, with examples, how Java code can directly access
 * guest language functions, objects, classes, and some complex data structures with Java-typed
 * accessors. In the reverse direction, guest language code can access Java objects, classes, and
 * constructors.
 *
 * <h4>Engine Creation</h4>
 *
 * The {@link PolyglotEngine.Builder Builder} creates new <em>engine</em> instances and allows both
 * application- and language-specific configuration. The following example creates a default
 * instance.
 * <p>
 * {@codesnippet PolyglotEngineSnippets#defaultPolyglotEngine}
 *
 * <h4>Truffle Languages</h4>
 *
 * An engine supports every {@linkplain Language Truffle language} available on the host JVM class
 * path.
 * <p>
 * Languages are initialized on demand, the first time an engine evaluates code of a matching
 * {@linkplain Source#getMimeType() MIME type}. The engine throws an {@link IllegalStateException}
 * if no matching language is available. Languages remain initialized for the lifetime of the
 * engine.
 * <p>
 * Specific language environments can be configured, for example in response to command line
 * options, by building the engine with combinations of language-specific MIME-key-value settings (
 * {@link Builder#config(String, String, Object) Builder.config(String, String, Object)}) and
 * pre-registered global symbols ({@link Builder#globalSymbol(String, Object)
 * Builder.globalSymbol(String, Object)} )
 *
 * <h4>Global Symbols</h4>
 *
 * An engine supports communication among {@linkplain Language languages} via shared named values
 * known as <em>global symbols</em>. These typically implement guest language export/import
 * statements used for <em>language interoperation</em>.
 * <p>
 * Each language dynamically manages a namespace of global symbols. The engine provides its own
 * (static) namespace, configured when the engine is built (
 * {@link Builder#globalSymbol(String, Object) Builder.globalSymbol(String, Object)}).
 * <p>
 * An engine retrieves global symbols by name, first searching the engine's namespace, and then
 * searching all language namespaces in unspecified order, returning the first one found (
 * {@link #findGlobalSymbol(String) findGlobalSymbol(String)}). Name collisions across namespaces
 * are possible and can only be discovered by explicitly retrieving all global symbols with a
 * particular name ({@link #findGlobalSymbols(String) findGlobalSymbols(String)}).
 *
 * <h4>Truffle Cross-language Interoperation</h4>
 *
 * <em>Interoperability</em> among executing guest language programs is supported in part by the
 * cross-language exchange of <em>global symbols</em> whose {@linkplain #findGlobalSymbol(String)
 * retrieval} produces results wrapped {@link Value Value} instances. The {@linkplain Value#get()
 * content} of a {@link Value Value} may be a boxed Java primitive type or a <em>foreign object</em>
 * (implemented internally as a {@link TruffleObject}). Foreign objects support a message-based
 * protocol for access to their contents, possibly including fields and methods.
 * <p>
 * Foreign objects may be functional, in which case cross-language method/procedure calls are
 * possible. Foreign calls return results wrapped in non-null {@link Value Value} instances.
 *
 * <h4>Truffle-Java Interoperation</h4>
 *
 * <em>Interoperability</em> is also supported between Java and guest language programs. For example
 * a Java client can <em>export</em> global symbols during engine creation (
 * {@link Builder#globalSymbol(String, Object) Builder.globalSymbol(String, Object)}) and
 * <em>import</em> them at any time ({@link #findGlobalSymbol(String) findGlobalSymbol(String)}).
 * The {@linkplain Value#get() content} of a {@link Value Value} (for example retrieved from an
 * imported symbol) can be accessed ({@link Value#as(Class) Value.as(Class)}) as an object of
 * requested type (a kind of cross-langauge "cast"). A {@link Value Value} determined to be
 * functional can be called ({@link Value#execute(Object...) Value.execute(Object...)}) and will
 * return the result wrapped in a non-null {@link Value Value} instance.
 * <p>
 * Java clients <em>export values</em> in two ways:
 * <ul>
 * <li>bound to globally accessible names ({@link Builder#globalSymbol(String, Object)
 * Builder.globalSymbol(String, Object)}), or</li>
 * <li>as arguments to foreign method/procedure calls ({@link Value#execute(Object...)
 * Value.execute(Object...)}).</li>
 * </ul>
 * In either case the engine <em>wraps</em> exported non-primitive Java values so that they appear
 * to guest languages the same as other foreign objects. In situations where a Java object is
 * exported and eventually returned (by import or method/procedure call), the identity of the result
 * (obtained by {@link Value#get() Value.get()}) is preserved.
 * <p>
 * Exporting a Java <em>object</em> grants guest language access to the object's public fields and
 * methods. Exporting a Java <em>class</em> grants guest language access to the class's public
 * static fields and public constructors.
 *
 * <h4>Engine Isolation</h4>
 *
 * Engines are isolated using the engine group they are associated with. An engine group consists of
 * one engine that is created using the {@link Builder#build() builder} and all its {@link #fork()
 * forked} engines. An engine group runs as an isolated
 * <a href="https://en.wikipedia.org/wiki/Multitenancy">tenant</a> on a host Virtual Machine. Other
 * than shared host resources such as memory, no aspects of program execution, language
 * environments, or global symbols are shared with other engine groups. Each engine within one
 * engine group maintains isolates its data, but does not isolate its code.
 *
 * <h4>Threading</h4>
 *
 * Guest language code execution is single-threaded, performed on a thread determined by the
 * engine's configuration.
 * <p>
 * <ul>
 * <li>Execution ({@link #eval(Source) eval(Source)}) is by default <em>synchronous</em> (performed
 * on the calling thread) and only permitted on the thread that created the engine.</li>
 * <li>An engine can be configured with a custom Executor ({@link Builder#executor(Executor)
 * Builder.executor(Executor)}) that performs executions on a different thread. In this case the
 * engine requires only that all executions are performed on the same thread.</li>
 * </ul>
 * <p>
 * In contrast, instrumentation-based access to engine state is <em>thread-safe</em>, both from
 * built-in services such as debugging and profiling as well as from external tools.
 *
 * @since 0.9
 */
@SuppressWarnings({"rawtypes"})
public class PolyglotEngine {
    static final Logger LOG = Logger.getLogger(PolyglotEngine.class.getName());
    static final PolyglotEngine UNUSABLE_ENGINE = new PolyglotEngine();
    private static final SPIAccessor SPI = new SPIAccessor();
    /**
     * Currently we need to have one global profile to support
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.SPIAccessor.EngineImpl#getCurrentVM()}.
     */
    static final PolyglotEngineProfile GLOBAL_PROFILE = new PolyglotEngineProfile(null);
    static final Object UNSET_CONTEXT = new Object();
    private final Thread initThread;
    private final PolyglotCache cachedTargets;
    private final Map<LanguageShared, Language> sharedToLanguage;
    private final Map<String, Language> mimeTypeToLanguage;
    /* Used for fast context lookup */
    @CompilationFinal(dimensions = 1) final Language[] languageArray;
    final PolyglotRuntime shared;
    private final List<PolyglotEngine> languageEngineForks;
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
    private HashMap<String, Object> globals;

    /**
     * Private & temporary only constructor.
     */
    PolyglotEngine() {
        assertNoCompilation();
        this.initThread = null;
        this.shared = null;
        this.cachedTargets = null;
        this.languageArray = null;
        this.sharedToLanguage = null;
        this.mimeTypeToLanguage = null;
        this.languageEngineForks = null;

        this.in = null;
        this.out = null;
        this.err = null;

        this.executor = null;
    }

    /**
     * Constructor used by forking.
     */
    PolyglotEngine(PolyglotEngine forkedEngine) {
        this.shared = forkedEngine.shared;
        this.initThread = forkedEngine.initThread;
        this.languageArray = new Language[shared.getLanguages().size()];
        this.cachedTargets = new PolyglotCache(this);
        this.sharedToLanguage = new HashMap<>();
        this.mimeTypeToLanguage = new HashMap<>();
        this.languageEngineForks = new ArrayList<>();
        this.shared.instanceCount.incrementAndGet();

        this.in = forkedEngine.in;
        this.out = forkedEngine.out;
        this.err = forkedEngine.err;

        this.executor = forkedEngine.executor;

        initLanguages();
    }

    /**
     * Constructor used from the builder.
     */
    PolyglotEngine(PolyglotRuntime shared, Executor executor, InputStream in, DispatchOutputStream out, DispatchOutputStream err, Map<String, Object> globals, List<Object[]> config) {
        assertNoCompilation();
        this.initThread = Thread.currentThread();
        this.shared = shared;
        this.languageArray = new Language[shared.getLanguages().size()];

        this.cachedTargets = new PolyglotCache(this);
        this.sharedToLanguage = new HashMap<>();
        this.mimeTypeToLanguage = new HashMap<>();
        this.languageEngineForks = new ArrayList<>();
        this.shared.instanceCount.incrementAndGet();

        this.in = in;
        this.out = out;
        this.err = err;

        this.executor = ComputeInExecutor.wrap(executor);
        this.globals = new HashMap<>(globals);
        this.config = config;

        initLanguages();
    }

    private void initLanguages() {
        for (LanguageShared languageShared : shared.getLanguages()) {
            Language newLanguage = new Language(languageShared);
            sharedToLanguage.put(languageShared, newLanguage);
            assert languageArray[languageShared.languageId] == null : "attempting to overwrite language";
            languageArray[languageShared.languageId] = newLanguage;
            for (String mimeType : languageShared.cache.getMimeTypes()) {
                mimeTypeToLanguage.put(mimeType, newLanguage);
            }
        }
    }

    PolyglotEngine enter() {
        return shared.engineProfile.enter(this);
    }

    void leave(Object prev) {
        shared.engineProfile.leave((PolyglotEngine) prev);
    }

    Info executor() {
        return executor;
    }

    private boolean isCurrentVM() {
        return this == shared.engineProfile.get();
    }

    /**
     * Returns a builder for creating an engine instance. After any configuration methods have been
     * called, the final {@link Builder#build() build()} step creates the engine and installs all
     * available languages. For example:
     *
     * <pre>
     * {@link PolyglotEngine} engine = {@link PolyglotEngine}.{@link PolyglotEngine#newBuilder() newBuilder()}
     *     .{@link Builder#setOut(java.io.OutputStream) setOut}({@link OutputStream yourOutput})
     *     .{@link Builder#setErr(java.io.OutputStream) setErr}({@link OutputStream yourOutput})
     *     .{@link Builder#setIn(java.io.InputStream) setIn}({@link InputStream yourInput})
     *     .{@link Builder#build() build()};
     * </pre>
     *
     * @return a builder to create a new engine with all available languages installed
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
     * A builder for creating an engine instance. After any configuration methods have been called,
     * the final {@link Builder#build() build()} step creates the engine and installs all available
     * languages. For example:
     *
     * <pre>
     * {@link PolyglotEngine} engine = {@link PolyglotEngine}.{@link PolyglotEngine#newBuilder() newBuilder()}
     *     .{@link Builder#setOut(java.io.OutputStream) setOut}({@link OutputStream yourOutput})
     *     .{@link Builder#setErr(java.io.OutputStream) setErr}({@link OutputStream yourOutput})
     *     .{@link Builder#setIn(java.io.InputStream) setIn}({@link InputStream yourInput})
     *     .{@link Builder#build() build()};
     * </pre>
     *
     * @since 0.9
     */
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
         * Configures default output for languages running in the {@link PolyglotEngine engine}
         * being built, defaults to {@link System#out}.
         *
         * @param os the stream to use as output
         * @return this builder
         * @since 0.9
         */
        public Builder setOut(OutputStream os) {
            out = os;
            return this;
        }

        /**
         * Configures error output for languages running in the {@link PolyglotEngine engine} being
         * built, defaults to {@link System#err}.
         *
         * @param os the stream to use as output
         * @return this builder
         * @since 0.9
         */
        public Builder setErr(OutputStream os) {
            err = os;
            return this;
        }

        /**
         * Configures default input for languages running in the {@link PolyglotEngine engine} being
         * built, defaults to {@link System#in}.
         *
         * @param is the stream to use as input
         * @return this builder
         * @since 0.9
         */
        public Builder setIn(InputStream is) {
            in = is;
            return this;
        }

        /**
         * Adds {@link Language Language}-specific initialization data to the {@link PolyglotEngine
         * engine} being built. For example:
         *
         * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#initializeWithParameters}
         *
         * If the same key is specified multiple times for the same language, only the last one
         * specified applies.
         *
         * @param mimeType identification of the language to which the configuration data is
         *            provided; any of the language's declared MIME types may be used
         *
         * @param key to identify a language-specific configuration element
         * @param value to parameterize initial state of a language
         * @return this builder
         * @since 0.11
         */
        public Builder config(String mimeType, String key, Object value) {
            if (this.arguments == null) {
                this.arguments = new ArrayList<>();
            }
            this.arguments.add(new Object[]{mimeType, key, value});
            return this;
        }

        /**
         * Adds a global symbol (named value) to be exported by the {@link PolyglotEngine engine}
         * being built. Any guest {@link Language Language} can <em>import</em> this symbol, which
         * takes precedence over any symbols exported under the same name by languages. Any number
         * of symbols may be added; in case of name-collision only the last one added will be
         * exported. The namespace of exported global symbols is immutable once the engine is built.
         * <p>
         * See {@linkplain PolyglotEngine "Truffle-Java Interoperation"} for the implications of
         * exporting Java data to guest languages. The following example demonstrates the export of
         * both a Java class and a Java object:
         *
         * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#configureJavaInterop}
         *
         * The <code>mul</code> and <code>compose</code> objects are then available to any guest
         * language.
         *
         * @param name name of the global symbol to register
         * @param obj value of the symbol to export
         * @return this builder
         * @see PolyglotEngine#findGlobalSymbol(String)
         * @throws IllegalArgumentException if the object isn't of primitive type and cannot be
         *             converted to {@link TruffleObject}
         * @since 0.9
         */
        public Builder globalSymbol(String name, Object obj) {
            final Object truffleReady = JavaInterop.asTruffleValue(obj);
            globals.put(name, truffleReady);
            return this;
        }

        /**
         * Provides an {@link Executor} for running guest language code asynchronously, on a thread
         * other than the calling thread.
         * <p>
         * By default engines execute both {@link PolyglotEngine#eval(Source)} and
         * {@link Value#execute(java.lang.Object...)} synchronously in the calling thread.
         * <p>
         * A custom {@link Executor} is expected to perform every execution it is given (via
         * {@link Executor#execute(Runnable)}) in order of arrival. An arbitrary thread may be used,
         * but the engine requires that there be only one.
         *
         * @param executor the executor of code to be used by {@link PolyglotEngine engine} being
         *            built
         * @return this builder
         * @since 0.9
         */
        @SuppressWarnings("hiding")
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Associates the {@linkplain #build() to be created} {@link PolyglotEngine engine} with its
         * {@link PolyglotRuntime execution runtime}. By default each {@link PolyglotEngine engine}
         * gets its own private runtime and as such it operates completely isolated - uninfluenced
         * by other {@link PolyglotEngine engines} in the same virtual machine. By letting multiple
         * {@link PolyglotEngine engines} to share the same {@link PolyglotRuntime runtime} one
         * instructs the system to share resources - for example code and metadata needed for the
         * {@link PolyglotEngine#eval(com.oracle.truffle.api.source.Source) execution of scripts}.
         * Sample usage:
         * <p>
         * {@codesnippet com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest}
         * <p>
         *
         * @param runtime an instance of runtime to associate this engine with
         * @return this builder
         * @since 0.25
         * @see PolyglotRuntime
         */
        public Builder runtime(PolyglotRuntime runtime) {
            this.runtime = runtime;
            return this;
        }

        /**
         * Creates an {@link PolyglotEngine engine} configured by builder methods.
         *
         * @return a new engine with all available languages installed
         * @since 0.9
         */
        public PolyglotEngine build() {
            assertNoCompilation();

            InputStream realIn;
            DispatchOutputStream realOut;
            DispatchOutputStream realErr;

            PolyglotRuntime realRuntime = runtime;
            if (realRuntime == null) {
                realRuntime = PolyglotRuntime.newBuilder().setIn(in).setOut(out).setErr(err).build();

                realIn = realRuntime.in;
                realOut = realRuntime.out;
                realErr = realRuntime.err;
            } else {
                if (out == null) {
                    realOut = realRuntime.out;
                } else {
                    realOut = SPIAccessor.instrumentAccess().createDispatchOutput(out);
                    SPIAccessor.engine().attachOutputConsumer(realOut, realRuntime.out);
                }
                if (err == null) {
                    realErr = realRuntime.err;
                } else {
                    realErr = SPIAccessor.instrumentAccess().createDispatchOutput(err);
                    SPIAccessor.engine().attachOutputConsumer(realErr, realRuntime.err);
                }
                realIn = in == null ? realRuntime.in : in;
            }

            return new PolyglotEngine(realRuntime, executor, realIn, realOut, realErr, globals, arguments);
        }
    }

    /**
     * Gets the map: MIME type --> {@linkplain Language metadata} for the matching language
     * installed in this engine, whether or not the language has been initialized.
     *
     * @return an immutable map: MIME type --> metadata for the language that supports the source
     *         type
     * @since 0.9
     */
    public Map<String, ? extends Language> getLanguages() {
        return Collections.unmodifiableMap(mimeTypeToLanguage);
    }

    /**
     * Gets the map: {@linkplain Instrument#getId() Instrument ID} --> {@link Instrument} loaded in
     * this {@linkplain PolyglotEngine engine}, whether the instrument is
     * {@linkplain Instrument#isEnabled() enabled} or not. Instrument instances are shared between
     * all {@link PolyglotEngine#fork() forked} instances of an engine.
     *
     * @return map of currently loaded instruments
     * @since 0.9
     * @deprecated use {@link #getRuntime()}.{@link PolyglotRuntime#getInstruments()}.
     */
    @Deprecated
    public Map<String, Instrument> getInstruments() {
        return shared.instruments;
    }

    /**
     * Access to associated runtime.
     * 
     * @return the runtime associated with this engine
     * @since 0.25
     */
    public PolyglotRuntime getRuntime() {
        return shared;
    }

    /**
     * Evaluates guest language source code, using the installed {@link Language Language} that
     * matches the code's {@link Source#getMimeType() MIME type}. Source code is provided to the
     * engine as {@link Source} objects, which may wrap references to guest language code (e.g. a
     * filename or URL) or may represent code literally as in the example below. The engine returns
     * the result wrapped in an instance of {@link Value Value}, for which Java-typed access
     * (objects) can be created using {@link Value#as(Class) Value.as(Class)}.
     *
     * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#evalCode}
     *
     * For sources marked as {@link Source#isInteractive() interactive}, the engine will will do
     * more, depending the language. This may include printing the result, in a language-specific
     * format, to the engine's {@link PolyglotEngine.Builder#setOut standard output}. It might read
     * input values queried by the language.
     * <p>
     * This method is useful for Java applications that <em>interoperate</em> with guest languages.
     * The general strategy is to {@linkplain #eval(Source) evaluate} guest language code that
     * produces the desired language element and then {@linkplain Value#as(Class) create} a Java
     * object of the appropriate type for Java <em>foreign</em> access to the result. The tutorial
     * <a href= "{@docRoot}/com/oracle/truffle/tutorial/embedding/package-summary.html">"Embedding
     * Truffle Languages in Java"</a> contains examples.
     *
     * @param source guest language code
     * @return result of the evaluation wrapped in a non-null {@link Value}
     * @throws IllegalStateException if no installed language matches the code's MIME type
     * @throws Exception thrown to signal errors while processing the code
     * @see Value#as(Class)
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
                     * Creates a new instance of {@link PolyglotEngine} with the same configuration
                     * and state as the original engine. Both the original engine and the forked
                     * engine can be used independent of each other. Both engines maintain separate
                     * state, but languages may decide to share code between forked {@link Language
                     * language} instances. Therefore the performance characteristics between forked
                     * engines are not isolated. Forking is an optional operation and might fail
                     * with {@link UnsupportedOperationException} if any of the initialized
                     * languages does not support forking. Forking is guaranteed to be supported if
                     * no language was yet initialized. Every engine must be {@link #dispose()
                     * disposed} independently, i.e. engines are not disposed automatically with
                     * their original engine. Forked engines can only be executed on the same thread
                     * as the original engine.
                     * <p>
                     * {@link #getInstruments() Instruments} are shared between all forked engines.
                     * {@link Instrument#setEnabled(boolean) Enabling} instruments applies to the
                     * original and all forked engines equally. Only if all engines were disposed
                     * then the instruments are going to be disposed as well.
                     * <p>
                     * To support forking in language {@link TruffleLanguage#forkContext(Object)}
                     * must be implemented. Languages may create forks on their own by invoking
                     * {@link Env#createFork(CallTarget)}.
                     *
                     * @since 0.25
                     */
                    /* NOTNOW public */ PolyglotEngine fork() throws UnsupportedOperationException {
        assertNoCompilation();
        checkThread();
        ComputeInExecutor<PolyglotEngine> compute = new ComputeInExecutor<PolyglotEngine>(executor()) {
            @Override
            protected PolyglotEngine compute() {
                PolyglotEngine prev = enter();
                try {
                    return forkImpl();
                } finally {
                    leave(prev);
                }
            }
        };
        return compute.get();
    }

    PolyglotEngine forkImpl() {
        PolyglotEngine forkedEngine = new PolyglotEngine(PolyglotEngine.this);
        for (Language forkingLanguage : languageArray) {
            if (!forkingLanguage.isInitialized()) {
                continue;
            }
            Language forkedLanguage = forkedEngine.languageArray[forkingLanguage.shared.languageId];
            assert forkedLanguage.shared == forkingLanguage.shared;
            try {
                forkedLanguage.initializeForkedContext(forkingLanguage);
            } catch (UnsupportedOperationException e) {
                throw new UnsupportedOperationException("The language " + forkingLanguage + " is already initialized but does not support forking.", e);
            }
            forkingLanguage.shared.contextFinalAssumption.invalidate();
        }
        return forkedEngine;
    }

    /**
     * Disposes this engine instance and {@link TruffleLanguage#disposeContext(Object) releases all
     * resources} allocated by languages active in this engine. Does not automatically dispose
     * {@link #fork() forked} engines.
     * <p>
     * Calling any other method on this instance after disposal throws an
     * {@link IllegalStateException}.
     *
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
        int referenceCount = shared.instanceCount.decrementAndGet();
        for (Language language : getLanguages().values()) {
            language.disposeContext();
        }

        for (PolyglotEngine languageFork : languageEngineForks) {
            if (!languageFork.disposed) {
                languageFork.disposeImpl();
            }
        }

        if (referenceCount == 0) {
            // only dispose instruments if all engine group is disposed
            for (PolyglotRuntime.Instrument instrument : getRuntime().getInstruments().values()) {
                try {
                    instrument.setEnabledImpl(false, false);
                } catch (Exception | Error ex) {
                    LOG.log(Level.SEVERE, "Error disposing " + instrument, ex);
                }
            }
        }
    }

    private Object[] debugger() {
        return shared.debugger;
    }

    /**
     * Finds a <em>global symbol</em> by name. Returns the symbol in the engine's namespace of
     * {@linkplain Builder#globalSymbol(String, Object) preconfigured} global symbols, if present.
     * Otherwise returns the first symbol found, if any, in a language namespace, which are queried
     * in an unspecified order.
     * <p>
     * Symbol names are language dependent. Cross-language name collisions are possible, in which
     * case this method only returns one of them (use {@link #findGlobalSymbols(String)} to return
     * all of them).
     *
     * @param globalName a global symbol name
     * @return the value of a global symbol with the specified name, <code>null</code> if none
     * @since 0.9
     */
    public Value findGlobalSymbol(final String globalName) {
        assert checkThread();
        assertNoCompilation();

        for (Object v : findGlobalSymbols(globalName)) {
            return (Value) v;
        }
        return null;
    }

    /**
     * Finds all <em>global symbols</em> with a specified name by searching every language's
     * namespace of exported symbols, together with the the engine's namespace of
     * {@linkplain Builder#globalSymbol(String, Object) preconfigured} symbols.
     * <p>
     * The following example shows how this method can be used to retrieve a single global symbol,
     * while treating name collisions as an error.
     *
     * {@link com.oracle.truffle.api.vm.PolyglotEngineSnippets#findAndReportMultipleExportedSymbols}
     *
     * @param globalName a global symbol name
     * @return iterable access to the values of global symbols with the specified name
     * @since 0.22
     */
    public Iterable<Value> findGlobalSymbols(String globalName) {
        assert checkThread();
        assertNoCompilation();
        return new Iterable<Value>() {

            final Iterable<? extends Object> iterable = importSymbol(null, globalName, true);

            public Iterator<Value> iterator() {
                return new Iterator<PolyglotEngine.Value>() {
                    final Iterator<? extends Object> iterator = iterable.iterator();

                    public boolean hasNext() {
                        ComputeInExecutor<Boolean> invokeCompute = new ComputeInExecutor<Boolean>(executor()) {
                            @SuppressWarnings("try")
                            @Override
                            protected Boolean compute() {
                                Object prev = enter();
                                try {
                                    return iterator.hasNext();
                                } finally {
                                    leave(prev);
                                }
                            }
                        };
                        return invokeCompute.get().booleanValue();
                    }

                    public Value next() {
                        ComputeInExecutor<Value> invokeCompute = new ComputeInExecutor<Value>(executor()) {
                            @SuppressWarnings("try")
                            @Override
                            protected Value compute() {
                                Object prev = enter();
                                try {
                                    return (Value) iterator.next();
                                } finally {
                                    leave(prev);
                                }
                            }
                        };
                        return invokeCompute.get();
                    }

                };
            }
        };
    }

    private Iterable<? extends Object> importSymbol(Language filterLanguage, String globalName, boolean needsValue) {
        class SymbolIterator implements Iterator<Object> {
            private final Collection<? extends Language> uniqueLang;
            private Object next;
            private Iterator<? extends Language> explicit;
            private Iterator<? extends Language> implicit;

            SymbolIterator(Collection<? extends Language> uniqueLang, Object first) {
                this.uniqueLang = uniqueLang;
                this.next = (needsValue && first != null) ? new DirectValue(null, first) : first;
            }

            @Override
            public boolean hasNext() {
                return findNext() != this;
            }

            @Override
            public Object next() {
                Object res = findNext();
                if (res == this) {
                    throw new NoSuchElementException();
                }
                assert !needsValue || res instanceof Value;
                next = null;
                return res;
            }

            private Object findNext() {
                if (next != null) {
                    return next;
                }

                if (explicit == null) {
                    explicit = uniqueLang.iterator();
                }

                while (explicit.hasNext()) {
                    Language dl = explicit.next();
                    TruffleLanguage<?> l = dl.getImpl(false);
                    TruffleLanguage.Env env = dl.getEnv(false);
                    if (dl != filterLanguage && l != null && env != null) {
                        Object obj = findExportedSymbol(dl, env, globalName, true);
                        if (obj != null) {
                            next = obj;
                            explicit.remove();
                            return next;
                        }
                    }
                }

                if (implicit == null) {
                    implicit = uniqueLang.iterator();
                }

                while (implicit.hasNext()) {
                    Language dl = implicit.next();
                    TruffleLanguage<?> l = dl.getImpl(false);
                    TruffleLanguage.Env env = dl.getEnv(false);
                    if (dl != filterLanguage && l != null && env != null) {
                        Object obj = findExportedSymbol(dl, env, globalName, false);
                        if (obj != null) {
                            next = obj;
                            return next;
                        }
                    }
                }
                return next = this;
            }

            private Object findExportedSymbol(Language lang, TruffleLanguage.Env env, String name, boolean onlyExplicit) {
                Object value = Access.LANGS.findExportedSymbol(env, lang.context, name, onlyExplicit);
                if (needsValue && value != null) {
                    value = new DirectValue(lang, value);
                }
                return value;
            }
        }
        Object globalObj = globals.get(globalName);
        final Collection<? extends Language> uniqueLang = getLanguages().values();
        return new Iterable<Object>() {
            @Override
            public Iterator<Object> iterator() {
                return new SymbolIterator(new LinkedHashSet<>(uniqueLang), globalObj);
            }
        };
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

    private Language findLanguage(LanguageShared env) {
        return sharedToLanguage.get(env);
    }

    Env findEnv(@SuppressWarnings("rawtypes") Class<? extends TruffleLanguage> languageClazz, boolean failIfNotFound) {
        for (Language lang : languageArray) {
            Env env = lang.getEnv(false);
            if (env != null && languageClazz.isInstance(lang.getImpl(false))) {
                return env;
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

    /**
     * A future value wrapper. A user level wrapper around values returned by evaluation of various
     * {@link PolyglotEngine} functions like {@link PolyglotEngine#findGlobalSymbol(String)} and
     * {@link PolyglotEngine#eval(Source)} or a value returned by
     * {@link #execute(java.lang.Object...) a subsequent execution}. In case the
     * {@link PolyglotEngine} has been initialized for
     * {@link Builder#executor(java.util.concurrent.Executor) asynchronous execution}, the
     * {@link Value} represents a future - i.e., it is returned immediately, leaving the execution
     * running on behind.
     *
     * @since 0.9
     */
    public abstract class Value {
        private final Language language;
        private CallTarget executeTarget;
        private CallTarget asJavaObjectTarget;

        Value(Language language) {
            this.language = language;
        }

        abstract boolean isDirect();

        abstract Object value();

        @SuppressWarnings("unchecked")
        private <T> T unwrapJava(Object value) {
            CallTarget unwrapTarget = cachedTargets.lookupAsJava(value.getClass());
            return (T) unwrapTarget.call(value, Object.class);
        }

        @SuppressWarnings("unchecked")
        private <T> T asJavaObject(Class<T> type, Object value) {
            if (asJavaObjectTarget == null) {
                asJavaObjectTarget = cachedTargets.lookupAsJava(value == null ? void.class : value.getClass());
            }
            return (T) asJavaObjectTarget.call(value, type);
        }

        @SuppressWarnings("try")
        private Object executeDirect(Object[] args) {
            Object value = value();
            if (executeTarget == null) {
                executeTarget = cachedTargets.lookupExecute(value.getClass());
            }
            return executeTarget.call(value, args);
        }

        /**
         * Returns the object represented by this value, possibly null. The <em>raw</em> object can
         * either be a wrapped primitive type (e.g. {@link Number}, {@link String},
         * {@link Character}, {@link Boolean}) or a {@link TruffleObject} representing more complex
         * object created by a language.
         *
         * @return the object, possibly <code>null</code>
         * @throws Exception in case it is not possible to obtain the value of the object
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
         * Creates Java-typed foreign access to the object wrapped by this {@link Value Value}, a
         * kind of cross-language "cast". Results depend on the requested type:
         * <ul>
         * <li>For primitive types such as {@link Number}, the value is simply cast and returned.
         * </li>
         * <li>A {@link String} is produced by the language that returned the value.</li>
         * <li>A {@link FunctionalInterface} instance is returned if the value
         * {@link Message#IS_EXECUTABLE can be executed}.</li>
         * <li>Aggregate types such as {@link List} and {@link Map} are supported, including when
         * used in combination with nested generics.</li>
         * </ul>
         * <p>
         * This method is useful for Java applications that <em>interoperate</em> with guest
         * language code. The general strategy is to {@linkplain PolyglotEngine#eval(Source)
         * evaluate} guest language code that produces the desired language element and then use
         * this method to create a Java object of the appropriate type for Java access to the
         * result. The tutorial <a href=
         * "{@docRoot}/com/oracle/truffle/tutorial/embedding/package-summary.html" >
         * "Embedding Truffle Languages in Java"</a> contains examples.
         *
         * @param <T> the type of the requested view
         * @param representation an interface describing the requested access (must be an interface)
         * @return instance of the view wrapping the object of this value
         * @throws Exception in case it is not possible to obtain the value of the object
         * @throws ClassCastException if the value cannot be converted to desired view
         * @since 0.9
         */
        @SuppressWarnings("unchecked")
        public <T> T as(final Class<T> representation) {
            Object original = waitForSymbol();
            Object unwrapped = original;

            if (original instanceof TruffleObject) {
                unwrapped = unwrapJava(ConvertedObject.original(original));
            }
            if (representation == String.class) {
                Object unwrappedConverted = ConvertedObject.original(unwrapped);
                Object string;
                if (language != null) {
                    PolyglotEngine prev = enter();
                    try {
                        string = Access.LANGS.toStringIfVisible(language.getEnv(false), language.context, unwrappedConvered, false, false);
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
         * @throws Exception signals problem during execution
         * @since 0.9
         */
        @Deprecated
        public Value invoke(final Object thiz, final Object... args) {
            return execute(args);
        }

        /**
         * Executes this value, depending on its content.
         * <ul>
         *
         * <li>If the value represents a function, makes a <em>foreign function call</em> using
         * appropriate Java arguments.</li>
         *
         * <li>If the value represents a field, then sets the field to the value of the first
         * argument, if provided, and returns the (possibly new) value of the field.</li>
         * </ul>
         * <p>
         * This method is useful for Java applications that <em>interoperate</em> with guest
         * language code. The general strategy is to {@linkplain PolyglotEngine#eval(Source)
         * evaluate} guest language code that produces the desired language element. If that element
         * is a guest language function, this method allows direct execution without giving the
         * function a Java type. The tutorial <a href=
         * "{@docRoot}/com/oracle/truffle/tutorial/embedding/package-summary.html" >
         * "Embedding Truffle Languages in Java"</a> contains examples.
         *
         * @param args arguments to pass when executing the value
         * @return result of the execution wrapped in a non-null {@link Value}
         * @throws Exception signals problem during execution
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
         * Get a meta-object of this value, if any. The meta-object represents a description of the
         * value, reveals it's kind and it's features.
         *
         * @return a value representing the meta-object, or <code>null</code>
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
                        return Access.LANGS.findMetaObject(language.env, language.context, value(), false);
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
         * Get a source location where this value is declared, if any.
         *
         * @return a source location of the object, or <code>null</code>
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
                        return Access.LANGS.findSourceLocation(language.env, language.context, value(), false);
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
     * A handle for an <em>instrument</em> installed in an engine, usable from other threads, that
     * can observe and inject behavior into language execution. The handle provides access to the
     * instrument's metadata and allows the instrument to be dynamically
     * {@linkplain Instrument#setEnabled(boolean) enabled/disabled} in the engine. Instrument
     * instances are shared between all {@link PolyglotEngine#fork() forked} instances.
     * <p>
     * All methods here, as well as instrumentation services in general, can be used safely from
     * threads other than the engine's single execution thread.
     * <p>
     * Refer to {@link TruffleInstrument} for information about implementing and installing
     * instruments.
     *
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
     * A handle for a Truffle language installed in a {@link PolyglotEngine}. The handle provides
     * access to the language's metadata, including the language's {@linkplain #getName() name},
     * {@linkplain #getVersion() version}, and supported {@linkplain #getMimeTypes() MIME types}.
     * <p>
     * A Truffle language implementation is an extension of the abstract class
     * {@link TruffleLanguage}, where more details about interactions between languages and engines
     * can be found.
     *
     * @see PolyglotEngine#getLanguages()
     * @since 0.9
     */
    public class Language {
        private volatile TruffleLanguage.Env env;
        final LanguageShared shared;
        @CompilationFinal private volatile Object context = UNSET_CONTEXT;
        private final Map<Source, CallTarget> parserCache;

        Language(LanguageShared shared) {
            this.shared = shared;
            this.parserCache = new WeakHashMap<>();
        }

        void initializeForkedContext(Language parentLanguage) {
            assert context == UNSET_CONTEXT : "forks must only happen once";
            this.context = Access.LANGS.forkContext(env, parentLanguage.context);
        }

        boolean isInitialized() {
            return context != UNSET_CONTEXT;
        }

        PolyglotEngine engine() {
            return PolyglotEngine.this;
        }

        Object context() {
            return context;
        }

        /**
         * Gets the MIME types supported by this language.
         *
         * @return an immutable set of supported MIME types
         * @since 0.9
         */
        public Set<String> getMimeTypes() {
            return shared.cache.getMimeTypes();
        }

        /**
         * Gets the human-readable name of this language.
         *
         * @return a human-friendly name
         * @since 0.9
         */
        public String getName() {
            return shared.cache.getName();
        }

        /**
         * Gets the version of this language.
         *
         * @return a version string
         * @since 0.9
         */
        public String getVersion() {
            return shared.cache.getVersion();
        }

        /**
         * Returns whether this language supports interactive evaluation of {@link Source sources}.
         * Such languages should be displayed in interactive environments and presented to the user.
         *
         * @return <code>true</code> if and only if this language implements an interactive response
         *         to evaluation of interactive sources.
         * @since 0.22
         */
        public boolean isInteractive() {
            return shared.cache.isInteractive();
        }

        /**
         * Evaluates code using this language, ignoring the code's {@link Source#getMimeType() MIME
         * type}.
         * <p>
         * When evaluating an {@link Source#isInteractive() interactive source} the result of the
         * {@link com.oracle.truffle.api.vm.PolyglotEngine#eval evaluation} is
         * {@link TruffleLanguage#isVisible(Object, Object) tested to be visible} and if the value
         * is visible, it gets {@link TruffleLanguage#toString(Object, Object) converted to string}
         * and printed to {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#setOut standard
         * output}.
         *
         * @param source code to execute
         * @return a non-null {@link Value} that holds the result
         * @throws Exception thrown to signal errors while processing the code
         * @since 0.9
         */
        public Value eval(Source source) {
            assertNoCompilation();
            return evalImpl(this, source);
        }

        /**
         * Returns this language's <em>global object</em>, {@code null} if not supported.
         * <p>
         * The result is expected to be a {@link TruffleObject} (e.g. a native object from the other
         * language) but technically it can also be one of Java's primitive wrappers (
         * {@link Integer} , {@link Double}, {@link Short}, etc.).
         *
         * @return this language's global object, <code>null</code> if the language has none
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
                        Object c = getContext(true);
                        Object res = Access.LANGS.languageGlobal(env, c);
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
            if (context != UNSET_CONTEXT) {
                synchronized (this) {
                    Object localContext = context;
                    Env localEnv = this.env;
                    assert localEnv != null;
                    if (localContext != UNSET_CONTEXT) {
                        try {
                            Access.LANGS.dispose(localEnv, localContext);
                        } catch (Exception | Error ex) {
                            LOG.log(Level.SEVERE, "Error disposing " + this, ex);
                        }
                        context = UNSET_CONTEXT;
                    }
                }
            }
        }

        Object getContext(boolean create) {
            Object localContext = context;
            if ((localContext == UNSET_CONTEXT && create)) {
                // getEnv is accessed from the instrumentation code so it needs to be thread-safe.
                synchronized (this) {
                    localContext = context;
                    if (localContext == UNSET_CONTEXT && create) {
                        Env localEnv = getEnv(create);
                        localContext = Access.LANGS.createContext(localEnv);
                        this.context = localContext;
                        Access.LANGS.postInitEnv(localEnv, context);
                    }
                }
            }
            return localContext;
        }

        TruffleLanguage<?> getImpl(boolean create) {
            getEnv(create);
            return shared.language;
        }

        TruffleLanguage.Env getEnv(boolean create) {
            TruffleLanguage.Env localEnv = env;
            if ((localEnv == null && create)) {
                // getEnv is accessed from the instrumentation code so it needs to be
                // thread-safe.
                synchronized (shared) {
                    localEnv = env;
                    if (localEnv == null && create) {
                        LoadedLanguage loadedLanguage = shared.getLanguage();
                        shared.language = loadedLanguage.getLanguage();
                        localEnv = Access.LANGS.createEnv(this, shared.language, loadedLanguage.isSingleton(), engine().out, engine().err, engine().in,
                                        getArgumentsForLanguage(), shared.cache.getName(), shared.cache.getVersion(), shared.cache.getMimeTypes());
                        env = localEnv;
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

    static final class LanguageShared {

        final LanguageCache cache;
        private final PolyglotRuntime engineShared;
        private final PolyglotEngineProfile engineProfile;
        private volatile TruffleLanguage<?> language;
        private final int languageId;
        private final Assumption contextFinalAssumption;
        private LoadedLanguage loadedLanguage;

        LanguageShared(PolyglotRuntime engineShared, LanguageCache cache, int languageId) {
            this.engineShared = engineShared;
            this.engineProfile = engineShared.engineProfile;
            assert engineProfile != null;
            this.cache = cache;
            this.languageId = languageId;
            // TODO this can move into context store profile as soon its no longer global.
            // we can reuse contextProfile.getConstantStoreAssumption() instead then.
            this.contextFinalAssumption = Truffle.getRuntime().createAssumption("Contextfinal");
        }

        Language currentLanguage() {
            return engineShared.currentVM().findLanguage(this);
        }

        Object getCurrentContext() {
            // is on fast-path
            final PolyglotEngine engine = engineProfile.get();
            Object context = PolyglotEngine.UNSET_CONTEXT;
            if (engine != null) {
                context = engine.languageArray[languageId].context;
            }
            if (context == PolyglotEngine.UNSET_CONTEXT) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(
                                "The language context is not yet initialized or already disposed. ");
            }
            return context;
        }

        boolean isContextFinal() {
            // is on fast-path
            return contextFinalAssumption.isValid();
        }

        private LoadedLanguage getLanguage() {
            if (loadedLanguage == null) {
                loadedLanguage = cache.loadLanguage();
            }
            return loadedLanguage;
        }

    }

    //
    // Accessor helper methods
    //

    static class Access {
        static final Accessor.LanguageSupport LANGS = SPIAccessor.langs();
        static final Accessor.InstrumentSupport INSTRUMENT = SPIAccessor.instrumentAccess();
        static final Accessor.JavaInteropSupport JAVA_INTEROP = SPIAccessor.javaInteropAccess();

        static Collection<ClassLoader> loaders() {
            return SPI.allLoaders();
        }
    }

    static class SPIAccessor extends Accessor {
        static LanguageSupport langs() {
            return SPI.languageSupport();
        }

        static EngineSupport engine() {
            return EngineImpl.ENGINE;
        }

        static InstrumentSupport instrumentAccess() {
            return SPI.instrumentSupport();
        }

        static JavaInteropSupport javaInteropAccess() {
            return SPI.javaInteropSupport();
        }

        Collection<ClassLoader> allLoaders() {
            return loaders();
        }

        @Override
        protected EngineSupport engineSupport() {
            return EngineImpl.ENGINE;
        }

        static final class EngineImpl extends EngineSupport {
            static final EngineImpl ENGINE = new EngineImpl();

            @Override
            public boolean isDisposed(Object languageShared) {
                return findShared(languageShared).engineShared.instanceCount.get() <= 0;
            }

            @Override
            public boolean contextReferenceFinal(Object languageShared) {
                return findShared(languageShared).isContextFinal();
            }

            @Override
            public Object contextReferenceGet(Object languageShared) {
                return findShared(languageShared).getCurrentContext();
            }

            @Override
            public Env getEnvForLanguage(Object languageShared, String mimeType) {
                return getEnvForInstrument(findShared(languageShared).engineShared, mimeType);
            }

            @Override
            public Env getEnvForInstrument(Object vm, String mimeType) {
                Language lang = ((PolyglotRuntime) vm).currentVM().findLanguage(mimeType, true);
                lang.getContext(true);
                Env env = lang.getEnv(false);
                assert env != null;
                return env;
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
            public boolean isMimeTypeSupported(Object languageShared, String mimeType) {
                return findShared(languageShared).engineShared.currentVM().findLanguage(mimeType, false) != null;
            }

            @Override
            public Env findEnv(Object vm, Class<? extends TruffleLanguage> languageClass, boolean failIfNotFound) {
                return ((PolyglotEngine) vm).findEnv(languageClass, failIfNotFound);
            }

            @Override
            public Object getInstrumentationHandler(Object languageShared) {
                return findShared(languageShared).engineShared.instrumentationHandler;
            }

            @Override
            public Iterable<? extends Object> importSymbols(Object languageShared, Env env, String globalName) {
                Language language = (Language) languageShared;
                return language.engine().importSymbol(language, globalName, false);
            }

            @SuppressWarnings("deprecation")
            @Override
            public <C> com.oracle.truffle.api.impl.FindContextNode<C> createFindContextNode(TruffleLanguage<C> lang) {
                Object vm = getCurrentVM();
                if (vm == null) {
                    throw new IllegalStateException("Cannot access current vm.");
                }
                return new FindContextNodeImpl<>(findEnv(vm, lang.getClass(), true));
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
            public CallTarget lookupOrRegisterComputation(Object truffleObject, RootNode computation, Object... keys) {
                CompilerAsserts.neverPartOfCompilation();
                assert keys.length > 0;
                Object key;
                if (keys.length == 1) {
                    key = keys[0];
                    assert TruffleOptions.AOT || assertKeyType(key);
                } else {
                    Pair p = null;
                    for (Object k : keys) {
                        assert TruffleOptions.AOT || assertKeyType(k);
                        p = new Pair(k, p);
                    }
                    key = p;
                }
                if (truffleObject instanceof EngineTruffleObject) {
                    PolyglotEngine engine = ((EngineTruffleObject) truffleObject).engine();
                    return engine.cachedTargets.lookupComputation(key, computation);
                }

                if (computation == null) {
                    return null;
                }
                return Truffle.getRuntime().createCallTarget(computation);
            }

            private static boolean assertKeyType(Object key) {
                assert key instanceof Class || key instanceof Method || key instanceof Message : "Unexpected key: " + key;
                return true;
            }

            @Override
            public CallTarget fork(Object languageShared, CallTarget callTarget) {
                LanguageShared sharedLanguage = (LanguageShared) languageShared;
                PolyglotEngine vm = sharedLanguage.engineProfile.get();
                if (vm == null) {
                    throw new IllegalStateException("Cannot access current vm.");
                }
                PolyglotEngine forkedVM = vm.forkImpl();
                vm.languageEngineForks.add(forkedVM);
                return Truffle.getRuntime().createCallTarget(new ForkRootNode(forkedVM, callTarget));
            }

            @Override
            public void disposeFork(Object languageShared, CallTarget forkTarget) {
                LanguageShared sharedLanguage = (LanguageShared) languageShared;
                PolyglotEngine vm = sharedLanguage.engineProfile.get();
                if (vm == null) {
                    throw new IllegalStateException("Cannot access current vm.");
                }
                RootCallTarget target = null;
                if (forkTarget instanceof RootCallTarget) {
                    target = (RootCallTarget) forkTarget;
                }
                if (target == null || !(target.getRootNode() instanceof ForkRootNode)) {
                    throw new IllegalArgumentException("Provided call target is not a valid fork target.");
                }
                ForkRootNode rootNode = (ForkRootNode) target.getRootNode();
                if (rootNode.engine.disposed) {
                    throw new IllegalStateException("Provided fork target already disposed.");
                }
                if (vm == rootNode.engine) {
                    throw new IllegalStateException("A fork cannot dispose itself.");
                }
                vm.languageEngineForks.remove(rootNode.engine);
                rootNode.engine.disposeImpl();
            }

            private LanguageShared findShared(Object obj) {
                return ((Language) obj).shared;
            }
        }

        private static final class Pair {
            final Object key;
            final Pair next;

            Pair(Object key, Pair next) {
                this.key = key;
                this.next = next;
            }

            @Override
            public int hashCode() {
                return this.key.hashCode() + (next == null ? 3754 : next.hashCode());
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final Pair other = (Pair) obj;
                if (!Objects.equals(this.key, other.key)) {
                    return false;
                }
                if (!Objects.equals(this.next, other.next)) {
                    return false;
                }
                return true;
            }

        }

    } // end of SPIAccessor

    private static final class ForkRootNode extends PolyglotRootNode {

        @Child private DirectCallNode callNode;

        ForkRootNode(PolyglotEngine engine, CallTarget forkTarget) {
            super(engine);
            this.callNode = DirectCallNode.create(forkTarget);
        }

        @Override
        protected Object executeImpl(VirtualFrame frame) {
            return callNode.call(frame.getArguments());
        }

    }

}

class PolyglotEngineSnippets {
    abstract class YourLang extends TruffleLanguage<Object> {
        public static final String MIME_TYPE = "application/my-test-lang";
    }

    public static PolyglotEngine defaultPolyglotEngine() {
        // @formatter:off
        // BEGIN: PolyglotEngineSnippets#defaultPolyglotEngine
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        // END: PolyglotEngineSnippets#defaultPolyglotEngine
        // @formatter:on
        return engine;
    }

    public static PolyglotEngine createPolyglotEngine(OutputStream yourOutput, InputStream yourInput) {
        // @formatter:off
        // BEGIN: PolyglotEngineSnippets#createPolyglotEngine
        PolyglotEngine engine = PolyglotEngine.newBuilder().
            setOut(yourOutput).
            setErr(yourOutput).
            setIn(yourInput).
            build();
        // END: PolyglotEngineSnippets#createPolyglotEngine
        // @formatter:on
        return engine;
    }

    public static int evalCode() {
        // @formatter:off
        // BEGIN: com.oracle.truffle.api.vm.PolyglotEngineSnippets#evalCode
        Source src = Source.newBuilder("3 + 39").
                        mimeType("application/my-test-lang").
                        name("example.test-lang").
                        build();
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();
        Value result = engine.eval(src);
        int answer = result.as(Integer.class);
        // END: com.oracle.truffle.api.vm.PolyglotEngineSnippets#evalCode
        // @formatter:on
        return answer;
    }

    public static PolyglotEngine initializeWithParameters() {
        // @formatter:off
        // BEGIN: com.oracle.truffle.api.vm.PolyglotEngineSnippets#initializeWithParameters
        String[] args = {"--kernel", "Kernel.som", "--instrument", "dyn-metrics"};
        PolyglotEngine.Builder builder = PolyglotEngine.newBuilder();
        builder.config(YourLang.MIME_TYPE, "CMD_ARGS", args);
        PolyglotEngine engine = builder.build();
        // END: com.oracle.truffle.api.vm.PolyglotEngineSnippets#initializeWithParameters
        // @formatter:on
        return engine;
    }

    // @formatter:off
    // BEGIN: com.oracle.truffle.api.vm.PolyglotEngineSnippets#configureJavaInterop
    public static final class Multiplier {
        public static int mul(int x, int y) {
            return x * y;
        }
    }

    public interface Multiply {
        int mul(int x, int y);
    }

    public static PolyglotEngine configureJavaInterop(Multiply multiply) {
        TruffleObject staticAccess = JavaInterop.asTruffleObject(Multiplier.class);
        TruffleObject instanceAccess = JavaInterop.asTruffleObject(multiply);

        PolyglotEngine engine = PolyglotEngine.newBuilder().
            globalSymbol("mul", staticAccess).
            globalSymbol("compose", instanceAccess).
            build();

        return engine;
    }
    // END: com.oracle.truffle.api.vm.PolyglotEngineSnippets#configureJavaInterop
    // @formatter:on

    static PolyglotEngine configureJavaInteropWithMul() {
        PolyglotEngineSnippets.Multiply multi = new PolyglotEngineSnippets.Multiply() {
            @Override
            public int mul(int x, int y) {
                return x * y;
            }
        };
        return configureJavaInterop(multi);
    }

    // @formatter:off
    // BEGIN: com.oracle.truffle.api.vm.PolyglotEngineSnippets#findAndReportMultipleExportedSymbols
    static Value findAndReportMultipleExportedSymbols(
                      PolyglotEngine engine, String name) {
        Value found = null;
        for (Value value : engine.findGlobalSymbols(name)) {
            if (found != null) {
                throw new IllegalStateException(
                    "Multiple global symbols exported with " + name + " name"
                );
            }
            found = value;
        }
        return found;
    }
    // END: com.oracle.truffle.api.vm.PolyglotEngineSnippets#findAndReportMultipleExportedSymbols
    // @formatter:on
}
