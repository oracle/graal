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
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;

/**
 * A polyglot execution context for Graal {@linkplain Language guest languages} that support
 * <em>interoperability</em> among the languages and with Java.
 *
 * <h4>Sample Usage</h4>
 *
 * <h4>Context Creation</h4>
 *
 * A context can be created using its default configuration by invoked the static
 * {@link #create(String...) create()} method. The create method allows to limit which languages are
 * accessible in the context. If no language is passed then all installed languages are accessible.
 * <p>
 * To customize the configuration a context builder can be created using
 * {@link #newBuilder(String...)}. In addition to the {@link #create(String...)} method, the context
 * builder allows to configure the input, error and output streams, engine and context options as
 * well as application arguments. A context can be created using a {@link Builder#engine(Engine)
 * shared engine} which allows to share configuration and instruments between multiple execution
 * contexts. See {@link Engine} for further details.
 * <p>
 * After use a context needs to be {@link #close() closed} in order to free all allocated resources.
 * A context can be closed from any thread if no code is currently executing in the same context.
 * There is also the ability to cancel a running execution of a different thread using the boolean
 * <code>cancelIfRunning</code> parameter of the {@link #close(boolean) close} method. Contexts are
 * {@link AutoCloseable} in order to allow them to be used with the try with resource Java
 * statement.
 *
 * <h4>Evaluation</h4>
 *
 * Before evaluation a language needs to be initialized. Languages are initialized automatically,
 * the first time a {@linkplain Source#getLanguage() language} is used. It is possible to
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
 * Most methods of a context throw {@link PolyglotException} in case an error occured in a guest
 * language.
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
public final class Context implements AutoCloseable {

    final AbstractContextImpl impl;

    // primary language is deprecated and will be removed.
    private final Language primaryLanguage;

    Context(AbstractContextImpl impl, Language language) {
        this.impl = impl;
        this.primaryLanguage = language;
    }

    public Engine getEngine() {
        return impl.getEngineImpl();
    }

    /**
     * Evaluates a source in the primary language of the context.
     */
    public Value eval(Source source) {
        return eval(primaryLanguage != null ? primaryLanguage : getEngine().getLanguage(source.getLanguage()), source);
    }

    public Value eval(String languageId, CharSequence source) {
        return eval(Source.create(languageId, source));
    }

    @Deprecated
    public Value eval(CharSequence source) {
        if (primaryLanguage == null) {
            throw new UnsupportedOperationException("This context was not created with a primary language. " +
                            "Use Context.eval(language, source) or create the context using Language.createContext() instead.");
        }
        return eval(primaryLanguage, Source.create(primaryLanguage.getId(), source));
    }

    @Deprecated
    public Value eval(String languageId, Source source) {
        // hack to support legacy code
        return eval(getEngine().getLanguage(languageId), source);
    }

    @Deprecated
    public Value eval(Language language, CharSequence source) {
        return eval(language, Source.create(language.getId(), source));
    }

    @Deprecated
    public Value eval(Language language, Source source) {
        source.language = language.getId();
        return impl.eval(language.impl, source.impl);
    }

    /**
     * Perform a lookup for a symbol in the top-most scope of the language.
     *
     * @deprecated
     */
    @Deprecated
    public Value lookup(String key) {
        if (primaryLanguage == null) {
            throw new UnsupportedOperationException("This context was not created with a primary language. " +
                            "Use Context.eval(language, source) or create the context using Language.createContext() instead.");
        }
        return lookup(primaryLanguage.getId(), key);
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
     * Closes this context and frees up potentially allocated native resources. A context is not
     * able to free all native resources allocated automatically, therefore it is necessary to close
     * contexts after use. If a context is cancelled then the currently executing thread will throw
     * a {@link PolyglotException}. The exception indicates that it was
     * {@link PolyglotException#isCancelled() cancelled}. Please note that cancelling a single
     * context can negatively affect the performance of other executing contexts constructed with
     * the same engine.
     * <p>
     * If internal errors occur during closing of the language then they are printed to the
     * configured {@link Builder#err(OutputStream) error output stream}. If a context was closed
     * then all its methods will throw an {@link IllegalStateException} when invoked. If an an
     * attempt to close a context was successful then consecutive calls to close have no effect.
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
     * be able to free all native resources allocated by a context automatically, therefore it is
     * recommended to close contexts after use. If the context is currently beeing executed on
     * another thread then an {@link IllegalStateException} is thrown. To close concurrently
     * executing contexts see {@link #close(boolean)}.
     * <p>
     * If internal errors occur during closing of the language then they are printed to the
     * configured {@link Builder#err(OutputStream) error output stream}. If a context was closed
     * then all its methods will throw an {@link IllegalStateException} when invoked. If an an
     * attempt to close a context was successful then consecutive calls to close have no effect.
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

        Builder(String... onlyLanguages) {
            Objects.requireNonNull(onlyLanguages);
            for (String onlyLanguage : onlyLanguages) {
                Objects.requireNonNull(onlyLanguage);
            }
            this.onlyLanguages = onlyLanguages;
        }

        /**
         * @deprecated use {@link #setOut(OutputStream)} instead
         */
        @Deprecated
        public Builder setOut(PrintStream out) {
            this.out = out;
            return this;
        }

        /**
         * @deprecated use {@link #setErr(OutputStream)} instead
         */
        @Deprecated
        public Builder setErr(PrintStream err) {
            this.err = err;
            return this;
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

        /**
         * @deprecated use {@link #out(OutputStream)} instead.
         */
        @Deprecated
        public Builder setOut(OutputStream out) {
            return out(out);
        }

        public Builder err(OutputStream err) {
            Objects.requireNonNull(err);
            this.err = err;
            return this;
        }

        /**
         * @deprecated use {@link #err(OutputStream)} instead.
         */
        @Deprecated
        public Builder setErr(OutputStream err) {
            return err(err);
        }

        public Builder in(InputStream in) {
            Objects.requireNonNull(in);
            this.in = in;
            return this;
        }

        /**
         * @deprecated use {@link #in(InputStream)} instead.
         */
        @Deprecated
        public Builder setIn(InputStream in) {
            return in(in);
        }

        /**
         * Set an option for this language {@link Context context}. If one of the set option keys or
         * values is invalid then an {@link IllegalArgumentException} is thrown when the context is
         * {@link #build() built}. The given key and value must not be <code>null</code>. Options
         * for the engine or instruments must be specified using the
         * {@link Engine.Builder#setOption(String, String) engine builder}.
         *
         * @see Language#getOptions() To list all available options for a {@link Language language}.
         * @see Engine.Builder#setOption(String, String) To specify an option for the engine.
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
         * @see #setOption(String, String) To set a single option.
         * @since 1.0
         */
        public Builder options(Map<String, String> options) {
            for (String key : options.keySet()) {
                setOption(key, options.get(key));
            }
            return this;
        }

        /**
         * @deprecated use {@link #option(String, String)} instead.
         */
        @Deprecated
        public Builder setOption(String key, String value) {
            return option(key, value);
        }

        /**
         * @deprecated use {@link #options(Map)} instead
         */
        @Deprecated
        public Builder setOptions(Map<String, String> options) {
            return options(options);
        }

        /**
         * Sets the guest language application arguments for a language {@link Context context}.
         * Application arguments are typcially made available to guest language implementations. It
         * depends on the language whether and how they are accessible within the
         * {@link Context#eval(Source) evaluated} guest language scripts. Passing no arguments to a
         * language then it is equivalent to providing an empty arguments array.
         *
         * @param language the language id of the primary language.
         * @param args an array of arguments passed to the guest language program
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

        /**
         * Sets the application arguments for the primary language context.
         *
         * @see #setArguments(String, String[])
         * @since 1.0
         * @deprecated use {@link #setArguments(String, String[])}
         */
        @Deprecated
        public Builder setArguments(String[] args) {
            if (onlyLanguages == null || onlyLanguages.length != 1) {
                throw new IllegalArgumentException("No primary language in use. Use setArguments(String, STring[]) instead.");
            }
            arguments(onlyLanguages[0], args);
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
                return engine.impl.createContext(null, null, null, Collections.emptyMap(),
                                arguments == null ? Collections.emptyMap() : arguments,
                                onlyLanguages);
            } else {
                return engine.impl.createContext(out, err, in,
                                options == null ? Collections.emptyMap() : options,
                                arguments == null ? Collections.emptyMap() : arguments,
                                onlyLanguages);
            }

        }

    }

}
