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
 * A polyglot execution context for Graal {@linkplain Language guest languages} that supports
 * <em>interoperability</em> between languages and with Java.
 *
 * <h4>Sample Usage</h4>
 *
 * <h4>Context Creation</h4>
 *
 * A context can be created using its default configuration by invoked the static
 * {@link #create(String...) create()} method. The create method allows to limit which languages are
 * accessible in the context. If no language is passed then all installed languages are accessible.
 * <p>
 * A context builder can be created to customize the configuration using
 * {@link #newBuilder(String...)}. In addition to the {@link #create(String...)} method, the context
 * builder allows to configure the input, error, and output streams; engine and context options; as
 * well as application arguments. A context can be created using a {@link Builder#engine(Engine)
 * shared engine} which allows to share configuration and instruments between multiple execution
 * contexts. See {@link Engine} for further details.
 * <p>
 * After use a context needs to be {@link #close() closed} to free all allocated resources. A
 * context can be closed from any thread if no code is currently executing in that context. It is
 * also possible to cancel a running execution of a different thread using the boolean
 * <code>cancelIfRunning</code> parameter of the {@link #close(boolean) close} method. Contexts are
 * {@link AutoCloseable} to allow them to be used with the 'try-with-resources' Java statement.
 *
 * <h4>Evaluation</h4>
 *
 * Before evaluation a language needs to be initialized. Languages are initialized automatically the
 * first time a {@linkplain Source#getLanguage() language} is used. It is possible to
 * {@link Context#initialize(String) force} the initialization of a language. Languages remain
 * initialized for the lifetime of the context.
 * <p>
 *
 * <h4>Polyglot Values</h4>
 *
 * <h4>Symbol Lookup</h4>
 *
 * <h4>Interoperability</h4>
 *
 * <h4>Exception Handling</h4>
 *
 * Most methods of a context throw {@link PolyglotException} if errors occur in guest languages.
 *
 * <h4>Proxys</h4>
 *
 * <h4>Thread-Safety</h4>
 *
 * Guest language code execution is single-threaded. Therefore only one Thread can execute guest
 * language code at a time. However the executing thread can change as long as there are no two
 * threads executing at the same time. If two threads access one context at the same time then an
 * {@link IllegalStateException} is thrown. Meta-data access using the {@link #getEngine() engine}
 * is always thread safe.
 *
 * @since 1.0
 */
// TODO document that the current context class loader is captured when the engine is created.
public final class Context implements AutoCloseable {

    final AbstractContextImpl impl;

    Context(AbstractContextImpl impl) {
        this.impl = impl;
    }

    public Engine getEngine() {
        return impl.getEngineImpl();
    }

    /**
     * Evaluates a source in the primary language of the context.
     */
    public Value eval(Source source) {
        return impl.eval(source.getLanguage(), source.impl);
    }

    public Value eval(String languageId, CharSequence source) {
        return eval(Source.create(languageId, source));
    }

    /**
     * Perform a lookup for a symbol in the top-most scope of the language.
     */
    public Value lookup(String language, String symbol) {
        return impl.lookup(getEngine().getLanguage(language).impl, symbol);
    }

    public Value importSymbol(String key) {
        return impl.importSymbol(key);
    }

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
     * be able to free all native resources allocated by a context automatically. For this reason it is
     * recommended to close contexts after use. If the context is currently being executed on
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

    public static Context create(String... onlyLanguages) {
        return newBuilder(onlyLanguages).build();
    }

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
        private Predicate<String> classFilter;

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
         * Sets a class filter that allows to limit the classes that guest languages are allowed to
         * load. If the filter returns <code>true</code> then the class is accessible, else it is
         * not accessible and throws a guest language error when accessed.
         *
         * @param classFilter a predicate that returns <code>true</code> or <code>false</code> for a
         *            Java qualified class name.
         *
         * @since 1.0
         */
        public Builder javaClassFilter(Predicate<String> classFilter) {
            Objects.requireNonNull(classFilter);
            this.classFilter = classFilter;
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
                return engine.impl.createContext(null, null, null, classFilter, Collections.emptyMap(),
                                arguments == null ? Collections.emptyMap() : arguments,
                                onlyLanguages);
            } else {
                return engine.impl.createContext(out, err, in, classFilter,
                                options == null ? Collections.emptyMap() : options,
                                arguments == null ? Collections.emptyMap() : arguments,
                                onlyLanguages);
            }
        }

    }

}
