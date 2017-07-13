/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;

/**
 * Polyglot (multi-language) access to a Graal {@linkplain Engine engine} for evaluating code
 * written in Graal-supported {@linkplain Language guest languages}, with support for
 * <em>interoperability</em> among those languages and with Java.
 *
 * <h4>Sample Usage</h4>
 *
 * Evaluate a fragment of Javascript code with a new context:
 *
 * <pre>
 * Context.create().eval("js", "42")
 * </pre>
 *
 * <h4>Context Creation and Disposal</h4>
 *
 * Create a default context with the static method {@link #create(String...)}, which optionally
 * specifies the languages that may be used in the context. Create a context with custom
 * configuration by using a {@linkplain #newBuilder(String...) builder}. The builder can configure
 * input, error and output streams, both engine and context options, and application arguments.
 * <p>
 * A context that is no longer needed should be {@linkplain #close() closed} to guarantee that all
 * allocated resources are freed. Contexts are {@link AutoCloseable} for use with the Java
 * {@code try-with-resources} statement.
 *
 * <h4>Isolation</h4>
 *
 * Each context is by default isolated from all other instances with respect to both language
 * evaluation semantics and resource consumption. Contexts can be optionally
 * {@linkplain Builder#engine(Engine) configured} to share a single underlying engine; see
 * {@link Engine} for more details about sharing.
 *
 * <h4>Language Initialization</h4>
 *
 * Each Graal language performs some initialization in a context before it can be used to execute
 * code, after which it remains initialized for the lifetime of the context. Initialization is by
 * default lazy and automatic, but it can be {@link Context#initialize(String) forced}.
 * <p>
 *
 * <h4>Evaluation</h4>
 *
 * <h4>Polyglot Values</h4>
 *
 * See {@link Value}
 *
 * <h4>Symbol Lookup</h4>
 *
 * After evaluating code that creates top-level named values, a context treats those values as Graal
 * <em>symbols</em> that can be {@link #lookup(String, String) retrieved} by specifying the language
 * and name.
 * <p>
 * Each context provides access to a shared (global) collection of Graal <em>symbols</em> that can
 * be {@link #importSymbol(String) retrieved} by name.
 *
 * <h4>Interoperability</h4>
 *
 * <h4>Exception Handling</h4>
 *
 * Most context methods throw {@link PolyglotException} when errors occur in guest languages.
 *
 * <h4>Proxies</h4>
 *
 * <h4>Thread-Safety</h4>
 *
 * A context permits guest language code evaluation on only one thread at a time, but it need not
 * always be the same thread. An attempt to execute code in a context where an evaluation is
 * currently underway will fail with an {@link IllegalStateException}.
 * <p>
 * Meta-data from the context's underlying {@link #getEngine() engine} can be retrieved safely by
 * any thread at any time.
 * <p>
 * A context may be {@linkplain #close() closed} from any thread, but only if the context is not
 * currently executing code. If a context is currently executing code, a different thread can kill
 * the execution and close the context using {@link #close(boolean)} .
 *
 * @since 1.0
 */
// TODO document that the current context class loader is captured when the engine is created.
public final class Context implements AutoCloseable {

    final AbstractContextImpl impl;

    Context(AbstractContextImpl impl) {
        this.impl = impl;
    }

    /**
     * Provides access to meta-data about the underlying Graal {@linkplain Engine engine}.
     *
     * @return the Graal {@link Engine} being used by this context
     */
    public Engine getEngine() {
        return impl.getEngineImpl();
    }

    /**
     * Evaluates guest language code, using the Graal {@linkplain Language language} that matches
     * the code's {@linkplain Source#getMimeType() MIME type}. {@link Source} objects may wrap
     * references to guest language code (e.g. a filename or URL) or may represent code literally.
     * <p>
     * The result is wrapped in an instance of {@link Value}, for which Java-typed access (objects)
     * can be created using {@link Value#as(Class) Value.as(Class)}.
     *
     * @param source guest language code, presumed to have an associated MIME type
     * @return result of the evaluation wrapped in a non-null {@link Value}
     */
    public Value eval(Source source) {
        return impl.eval(source.getLanguage(), source.impl);
    }

    /**
     * Evaluates literal guest language code using a specified Graal {@linkplain Language language}.
     *
     * <p>
     * The result is wrapped in an instance of {@link Value}, for which Java-typed access (objects)
     * can be created using {@link Value#as(Class) Value.as(Class)}.
     *
     * @param languageId
     * @param source textual source code
     * @return result of the evaluation wrapped in a non-null {@link Value}
     */
    public Value eval(String languageId, CharSequence source) {
        return eval(Source.create(languageId, source));
    }

    /**
     * Evaluates a symbol in the top-most scope of a specified language.
     * <p>
     * The result is wrapped in an instance of {@link Value}, for which Java-typed access (objects)
     * can be created using {@link Value#as(Class) Value.as(Class)}.
     *
     * @param language
     * @param symbol name of a symbol
     * @return result of the evaluation wrapped in a non-null {@link Value}
     */
    public Value lookup(String language, String symbol) {
        return impl.lookup(getEngine().getLanguage(language).impl, symbol);
    }

    /**
     * Imports a symbol from the polyglot scope or <code>null</code> if the symbol is not defined.
     * The polyglot scope is used to exchange symbols between guest languages and also the host
     * language. Guest languages can put and get symbols through language specific APIs. For
     * example, in JavaScript symbols of the polyglot scope can be get using
     * <code>Interop.get("key")</code> and set using <code>Interop.put("key", value)</code>.
     *
     * @param the key of the symbol
     * @since 1.0
     */
    public Value importSymbol(String key) {
        return impl.importSymbol(key);
    }

    /**
     * Exports a symbol into the polyglot scope. The polyglot scope is used to exchange symbols
     * between guest languages and the host language. Guest languages can put and get symbols
     * through language specific APIs. For example, in JavaScript symbols of the polyglot scope can
     * be accessed using <code>Interop.get("key")</code> and set using
     * <code>Interop.put("key", value)</code>. Any Java value or {@link Value} instance is allowed
     * to be passed as value.
     *
     * @param the key of the symbol
     * @since 1.0
     */
    public void exportSymbol(String key, Object value) {
        impl.exportSymbol(key, value);
    }

    /**
     * Forces the initialization of a language.
     *
     * @param language
     * @returns <code>true</code> if the language needed to be initialized.
     */
    public boolean initialize(String language) {
        return impl.initializeLanguage(getEngine().getLanguage(language).impl);
    }

    /**
     * Closes this context and frees up potentially allocated native resources. A context cannot
     * free all native resources allocated automatically. For this reason it is necessary to close
     * contexts after use. If a context is cancelled then the currently executing thread will throw
     * a {@link PolyglotException}. The exception indicates that it was
     * {@link PolyglotException#isCancelled() cancelled}. Please note that canceling a single
     * context can negatively affect the performance of other executing contexts constructed with
     * the same engine.
     * <p>
     * If internal errors occur during closing of the language then they are printed to the
     * configured {@link Builder#err(OutputStream) error output stream}. If a context was closed
     * then all its methods will throw an {@link IllegalStateException} when invoked. If an attempt
     * to close a context was successful then consecutive calls to close have no effect.
     *
     * @param cancelIfExecuting if <code>true</code> then currently executing contexts will be
     *            cancelled, else an {@link IllegalStateException} is thrown.
     * @see Engine#close() To close an engine.
     * @since 1.0
     */
    public void close(boolean cancelIfRunning) {
        impl.close(cancelIfRunning);
    }

    /**
     * Closes this context and frees up potentially allocated native resources. Languages might not
     * be able to free all native resources allocated by a context automatically. For this reason it
     * is recommended to close contexts after use. If the context is currently being executed on
     * another thread then an {@link IllegalStateException} is thrown. To close concurrently
     * executing contexts see {@link #close(boolean)}.
     * <p>
     * If internal errors occur during closing of the language then they are printed to the
     * configured {@link Builder#err(OutputStream) error output stream}. If a context was closed
     * then all its methods will throw an {@link IllegalStateException} when invoked. If an attempt
     * to close a context was successful then consecutive calls to close have no effect.
     *
     * @throws IllegalStateException if the context is currently executing on another thread.
     * @see Engine#close() To close an engine.
     * @since 1.0
     */
    public void close() {
        close(false);
    }

    /**
     * Creates a context with default configuration.
     *
     * @param onlyLanguages names of languages permitted in this context, {@code null} if all
     *            languages are permitted
     * @return a new context
     */
    public static Context create(String... onlyLanguages) {
        return newBuilder(onlyLanguages).build();
    }

    /**
     * Creates a builder for constructing a context with custom configuration.
     *
     * @param onlyLanguages names of languages permitted in this context, {@code null} if all
     *            languages are permitted
     * @return a builder that can create a context
     */
    public static Builder newBuilder(String... onlyLanguages) {
        return new Builder(onlyLanguages);
    }

    @SuppressWarnings("hiding")
    public static final class Builder {

        private Engine sharedEngine;
        private String[] onlyLanguages;

        private OutputStream out;
        private OutputStream err;
        private InputStream in;
        private Map<String, String> options;
        private Map<String, String[]> arguments;
        private Predicate<String> hostClassFilter;
        private boolean allowHostAccess;

        Builder(String... onlyLanguages) {
            Objects.requireNonNull(onlyLanguages);
            for (String onlyLanguage : onlyLanguages) {
                Objects.requireNonNull(onlyLanguage);
            }
            this.onlyLanguages = onlyLanguages;
        }

        public Builder engine(Engine engine) {
            Objects.requireNonNull(engine);
            this.sharedEngine = engine;
            return this;
        }

        public Builder out(OutputStream out) {
            Objects.requireNonNull(out);
            this.out = out;
            return this;
        }

        public Builder err(OutputStream err) {
            Objects.requireNonNull(err);
            this.err = err;
            return this;
        }

        public Builder in(InputStream in) {
            Objects.requireNonNull(in);
            this.in = in;
            return this;
        }

        /**
         * Allows guest languages to access the host language by loading new classes. If host
         * language instances or classes are provided using {@link Context#put(String, Object)} or
         * by passing it into the language then the language has automatically access to it.
         *
         * @since 1.0
         */
        public Builder allowHostAccess(boolean enabled) {
            this.allowHostAccess = enabled;
            return this;
        }

        /**
         * @since 1.0
         * @deprecated use {@link #hostClassFilter(Predicate)} instead
         */
        @Deprecated
        public Builder javaClassFilter(Predicate<String> classFilter) {
            Objects.requireNonNull(classFilter);
            this.hostClassFilter = classFilter;
            return hostClassFilter(classFilter);
        }

        /**
         * Sets a class filter that allows to limit the classes that are allowed to be loaded by
         * guest languages. If the filter returns <code>true</code> then the class is accessible,
         * else it is not accessible and throws an guest language error when accessed. In order to
         * have an effect {@link #allowHostAccess(boolean)} needs to be set to <code>true</code>.
         *
         * @param classFilter a predicate that returns <code>true</code> or <code>false</code> for a
         *            java qualified class name.
         *
         * @since 1.0
         */
        public Builder hostClassFilter(Predicate<String> classFilter) {
            Objects.requireNonNull(classFilter);
            this.hostClassFilter = classFilter;
            return this;
        }

        /**
         * Set an option for this {@link Context context}. By default any options for the
         * {@link Engine#getOptions() engine}, {@link Language#getOptions() language} or
         * {@link Instrument#getOptions() instrument} can be set for a context. If an
         * {@link #engine(Engine) explicit engine} is set for this context then only language
         * options can be set. Instrument and engine options can be set exclusively on the explicit
         * engine instance. If a language option was set for the context and the engine then the
         * option of the context is going to take precedence.
         * <p>
         * If one of the set option keys or values is invalid then an
         * {@link IllegalArgumentException} is thrown when the context is {@link #build() built}.
         * The given key and value must not be <code>null</code>.
         *
         * @see Engine.Builder#option(String, String) To specify an option for the engine.
         * @since 1.0
         */
        public Builder option(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            if (this.options == null) {
                this.options = new HashMap<>();
            }
            this.options.put(key, value);
            return this;
        }

        /**
         * Shortcut for setting multiple {@link #setOption(String, String) options} using a map. All
         * values of the provided map must be non-null.
         *
         * @param options a map options.
         * @see #option(String, String) To set a single option.
         * @since 1.0
         */
        public Builder options(Map<String, String> options) {
            for (String key : options.keySet()) {
                option(key, options.get(key));
            }
            return this;
        }

        /**
         * Sets the guest language application arguments for a language {@link Context context}.
         * Application arguments are typically made available to guest language implementations. It
         * depends on the language if and how they are accessible within the
         * {@link Context#eval(Source) evaluated} guest language scripts. Passing no arguments to a
         * language is equivalent to providing an empty arguments array.
         *
         * @param language the language id of the primary language.
         * @param args an array of arguments passed to the guest language program.
         * @throws IllegalArgumentException if an invalid language id was specified.
         * @since 1.0
         */
        public Builder arguments(String language, String[] args) {
            Objects.requireNonNull(language);
            Objects.requireNonNull(args);
            String[] newArgs = args;
            if (args.length > 0) {
                newArgs = new String[args.length];
                for (int i = 0; i < args.length; i++) { // defensive copy
                    newArgs[i] = Objects.requireNonNull(args[i]);
                }
            }
            if (arguments == null) {
                arguments = new HashMap<>();
            }
            arguments.put(language, newArgs);
            return this;
        }

        public Context build() {
            Engine engine = this.sharedEngine;
            if (engine == null) {
                org.graalvm.polyglot.Engine.Builder engineBuilder = Engine.newBuilder().options(options == null ? Collections.emptyMap() : options);
                if (out != null) {
                    engineBuilder.out(out);
                }
                if (err != null) {
                    engineBuilder.err(err);
                }
                if (in != null) {
                    engineBuilder.in(in);
                }
                engineBuilder.setBoundEngine(true);
                engine = engineBuilder.build();
                return engine.impl.createContext(null, null, null, allowHostAccess, hostClassFilter,
                                Collections.emptyMap(),
                                arguments == null ? Collections.emptyMap() : arguments, onlyLanguages);
            } else {
                return engine.impl.createContext(out, err, in, allowHostAccess, hostClassFilter,
                                options == null ? Collections.emptyMap() : options,
                                arguments == null ? Collections.emptyMap() : arguments, onlyLanguages);
            }
        }

    }

}
