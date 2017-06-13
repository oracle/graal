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
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractLanguageImpl;

public final class Context {

    final AbstractContextImpl impl;
    private final Language language;

    Context(AbstractContextImpl impl, Language language) {
        this.impl = impl;
        this.language = language;
    }

    public Value eval(Source source) {
        return impl.eval(language.impl, source.impl);
    }

    public Value eval(String source) {
        return impl.eval(language.impl, Source.create(source).impl);
    }

    void initializeLanguage() {
        impl.initializeLanguage(language.impl);
    }

    /**
     * Perform a lookup for a symbol in the top-most scope of the language.
     */
    public Value lookup(String key) {
        return impl.lookup(language.impl, key);
    }

    public Language getLanguage() {
        return language;
    }

    public static final class Builder {

        private final AbstractLanguageImpl languageImpl;

        private PrintStream out;
        private PrintStream err;
        private InputStream in;
        private Map<String, String> options = new HashMap<>();

        Builder(AbstractLanguageImpl languageImpl) {
            this.languageImpl = languageImpl;
        }

        public Builder setOut(PrintStream out) {
            this.out = out;
            return this;
        }

        public Builder setErr(PrintStream err) {
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
        public Builder setOptions(Map<String, String> options) {
            for (String key : options.keySet()) {
                Objects.requireNonNull(options.get(key), "All option values must be non-null.");
            }
            this.options.putAll(options);
            return this;
        }

        public Context build() {
            Context context = languageImpl.createContext(out, err, in, options);
            context.initializeLanguage();
            return context;
        }

    }

}
