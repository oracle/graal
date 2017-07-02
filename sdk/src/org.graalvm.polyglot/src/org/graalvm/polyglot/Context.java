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

public final class Context implements AutoCloseable {

    final AbstractContextImpl impl;
    private final Language primaryLanguage;

    Context(AbstractContextImpl impl, Language language) {
        this.impl = impl;
        this.primaryLanguage = language;
    }

    /**
     * Evaluates a source in the primary language of the context.
     */
    public Value eval(Source source) {
        if (primaryLanguage == null) {
            throw new IllegalArgumentException("This context was not created with a primary language. " +
                            "Use Context.eval(language, source) or create the context using Language.createContext() instead.");
        }
        return eval(primaryLanguage, source);
    }

    public Value eval(CharSequence source) {
        if (primaryLanguage == null) {
            throw new IllegalArgumentException("This context was not created with a primary language. " +
                            "Use Context.eval(language, source) or create the context using Language.createContext() instead.");
        }
        return eval(primaryLanguage, Source.create(source));
    }

    public Value eval(String languageId, CharSequence source) {
        return eval(getEngine().getLanguage(languageId), Source.create(source));
    }

    public Value eval(String languageId, Source source) {
        return eval(getEngine().getLanguage(languageId), source);
    }

    public Value eval(Language language, CharSequence source) {
        return eval(language, Source.create(source));
    }

    public Value eval(Language language, Source source) {
        return impl.eval(language.impl, source);
    }

    /**
     * Perform a lookup for a symbol in the top-most scope of the language.
     */
    public Value lookup(String key) {
        if (primaryLanguage == null) {
            throw new IllegalArgumentException("This context was not created with a primary language. " +
                            "Use Context.eval(language, source) or create the context using Language.createContext() instead.");
        }
        return lookup(primaryLanguage, key);
    }

    /**
     * Perform a lookup for a symbol in the top-most scope of the language.
     */
    public Value lookup(String language, String key) {
        return lookup(getEngine().getLanguage(language), key);
    }

    /**
     * Perform a lookup for a symbol in the top-most scope of the language.
     */
    public Value lookup(Language language, String key) {
        return impl.lookup(language.impl, key);
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
     */
    public void initialize(String language) {
        initialize(getEngine().getLanguage(language));
    }

    public void initialize(Language language) {
        impl.initializeLanguage(language.impl);
    }

    public Engine getEngine() {
        return impl.getEngineImpl();
    }

    /**
     * Closes this context and frees up potentially allocated native resources. Languages might not
     * be able to free all native resources allocated by a context automatically, therefore it is
     * recommended to close contexts after use. If the source {@link #getEngine() engine} is closed
     * then this context is closed automatically. If a context got cancelled then the cancelled
     * thread will throw a {@link PolyglotException} where the
     * {@link PolyglotException#isCancelled() cancelled} flag is set. Please note that cancelling a
     * single context can negatively affect the performance of other executing contexts of the same
     * engine while the cancellation request is processed.
     * <p>
     * If internal errors occur during closing of the language then they are printed to the
     * configured {@link Builder#setErr(OutputStream) error output stream}. If a context was closed
     * then all its methods will throw an {@link IllegalStateException} when invoked. If an an
     * attempt to close this context was successful then consecutive calls to close have no effect.
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
     * recommended to close contexts after use. If the source {@link #getEngine() engine} is closed
     * then this context is closed automatically. If the context is currently beeing executed on
     * another thread then an {@link IllegalStateException} is thrown. To close currently executing
     * contexts see {@link #close(boolean)}.
     * <p>
     * If internal errors occur during closing of the language then they are printed to the
     * configured {@link Builder#setErr(OutputStream) error output stream}. If a context was closed
     * then all its methods will throw an {@link IllegalStateException} when invoked. If an an
     * attempt to close this context was successful then consecutive calls to close have no effect.
     *
     * @throws IllegalStateException if the context is currently executing on another thread.
     * @see Engine#close() To close an engine.
     * @since 1.0
     */
    public void close() {
        close(false);
    }

    public static final class Builder {

        private final Engine engine;
        private Language primaryLanguage;

        private OutputStream out;
        private OutputStream err;
        private InputStream in;
        private Map<String, String> options;
        private Map<String, String[]> arguments;
        private boolean polyglot;

        Builder(Engine engine, Language primaryLanguage) {
            this.engine = engine;
            this.primaryLanguage = primaryLanguage;
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
         * If set to <code>true</code> allows access to other guest languages other than the primary
         * language. If set to <code>false</code> all accesses to the language will result in an
         * {@link IllegalStateException}.
         *
         * @since 1.0
         */
        public Builder setPolyglot(boolean polyglot) {
            this.polyglot = polyglot;
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

        public Builder setOut(OutputStream out) {
            this.out = out;
            return this;
        }

        public Builder setErr(OutputStream err) {
            this.err = err;
            return this;
        }

        public Builder setIn(InputStream in) {
            this.in = in;
            return this;
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
        public Builder setOption(String key, String value) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            if (this.options == null) {
                this.options = new HashMap<>();
            }
            this.options.put(key, value);
            return this;
        }

        /**
         * Sets the guest language application arguments for a language {@link Context context}.
         * Application arguments are typcially made available to guest language implementations. It
         * depends on the language whether and how they are accessible within the
         * {@link Context#eval(Source) evaluated} guest language scripts. Passing no arguments to a
         * language then it is equivalent to providing an empty arguments array.
         *
         * @param languageId the languageId available in the engine.
         * @param args an array of arguments passed to the guest language program
         * @throws IllegalArgumentException if an invalid language id was specified.
         * @since 1.0
         */
        public Builder setArguments(String languageId, String[] args) {
            Objects.requireNonNull(args);
            return setArguments(engine.getLanguage(languageId), args);
        }

        /**
         * Sets the guest language application arguments for a language {@link Context context}.
         * Application arguments are typcially made available to guest language implementations. It
         * depends on the language whether and how they are accessible within the
         * {@link Context#eval(Source) evaluated} guest language scripts. Passing no arguments to a
         * language then it is equivalent to providing an empty arguments array.
         *
         * @param languageId the languageId available in the engine.
         * @param args an array of arguments passed to the guest language program
         * @since 1.0
         */
        public Builder setArguments(Language language, String[] args) {
            Objects.requireNonNull(language);
            Objects.requireNonNull(args);
            String[] newArgs = args;
            if (args.length > 0) {
                newArgs = new String[args.length];
                for (int i = 0; i < args.length; i++) { // defensive copy
                    newArgs[i] = Objects.requireNonNull(args[i]);
                }
            }
            if (language.getEngine() != engine) {
                throw new IllegalArgumentException("Invalid language from another engine provided.");
            }
            if (arguments == null) {
                arguments = new HashMap<>();
            }
            arguments.put(language.getId(), newArgs);
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
        public Builder setOptions(Map<String, String> options) {
            for (String key : options.keySet()) {
                setOption(key, options.get(key));
            }
            return this;
        }

        public Context build() {
            return primaryLanguage.impl.createContext(out, err, in,
                            options == null ? Collections.emptyMap() : options,
                            arguments == null ? Collections.emptyMap() : arguments, polyglot);
        }

    }

}
