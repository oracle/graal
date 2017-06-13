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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;

public final class PolyglotContext {

    private final Map<String, Context> contexts = new ConcurrentHashMap<>();

    private final AbstractContextImpl impl;
    private final Engine engine;

    PolyglotContext(Engine engine, AbstractContextImpl impl) {
        this.engine = engine;
        this.impl = impl;
    }

    public Value eval(String languageId, String source) {
        return getContext(languageId).eval(source);
    }

    public Value eval(String languageId, Source source) {
        return getContext(languageId).eval(source);
    }

    public Value eval(Language language, Source source) {
        return getContext(language).eval(source);
    }

    public Value eval(Language language, String source) {
        return getContext(language).eval(source);
    }

    public Context getContext(String languageId) {
        Context context = contexts.get(languageId);
        if (context == null) {
            Language language = engine.getLanguage(languageId);
            if (language == null) {
                throw new IllegalArgumentException(String.format("A language with id %s is not installed. Installed languages are: %s.", languageId, engine.getLanguages().keySet()));
            }
            context = initializeContext(language);
        }
        return context;
    }

    public Context getContext(Language language) {
        if (language.getEngine() != engine) {
            throw new IllegalArgumentException(String.format("Provided language was not created with the same engine as the %s instance.", PolyglotContext.class.getSimpleName()));
        }
        Context context = contexts.get(language.getId());
        if (context == null) {
            context = initializeContext(language);
        }
        return context;
    }

    private synchronized Context initializeContext(Language language) {
        Context prevContext = contexts.get(language.getId());
        if (prevContext != null) {
            return prevContext;
        }
        Context context = new Context(impl, language);
        context.initializeLanguage();
        contexts.put(language.getId(), context);
        return context;
    }

    public Value importSymbol(String key) {
        return impl.importSymbol(key);
    }

    public void exportSymbol(String key, Object value) {
        impl.exportSymbol(key, value);
    }

    public Engine getEngine() {
        return this.engine;
    }

    public static class Builder {

        private final Engine engine;

        OutputStream out;
        OutputStream err;
        InputStream in;
        final Map<String, String> options = new HashMap<>();

        Builder(Engine engine) {
            this.engine = engine;
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
         * Set an option for this polyglot {@link Context context}. If one of the set option keys or
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
            options.put(key, value);
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
                Objects.requireNonNull(options.get(key), "All option values must be non-null.");
            }
            this.options.putAll(options);
            return this;
        }

        public PolyglotContext build() {
            return engine.impl.createPolyglotContext(out, err, in, options);
        }

    }

}
