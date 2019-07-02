/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.polyglot;

import java.util.Arrays;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicSet;

/**
 * Represents an access policy for polyglot builtins in the guest languages.
 * <p>
 * If the two predefined access policies {@link #NONE} and {@link #ALL} are not sufficient then a
 * custom access configuration may be created using {@link #newBuilder()}. This allows to grant
 * individual access rights between the language.
 * <p>
 * Guest languages expose language builtins that allow access to other languages. Polyglot builtins
 * allow to export and share symbols in the {@link Context#getPolyglotBindings() polyglot bindings}
 * as well as evaluate code of other languages. Access to polyglot builtins and bindings is always
 * enabled if policy {@link #ALL} is used. In this mode polyglot builtins are available even if
 * there is just one {@link Engine#getLanguages() installed} language available.
 * <p>
 * If a custom access policy is used and if a language has access to one or more different languages
 * then the guest application will have access to polyglot builtins and
 * {@link Context#getPolyglotBindings() polyglot bindings}, otherwise the language will not have
 * access.
 *
 * @since 19.0
 */
public final class PolyglotAccess {

    private static final UnmodifiableEconomicSet<String> EMPTY = EconomicSet.create();

    private final EconomicMap<String, EconomicSet<String>> access;
    private final boolean allAccess;

    PolyglotAccess(boolean allAccess, EconomicMap<String, EconomicSet<String>> access) {
        this.allAccess = allAccess;
        this.access = copyMap(access);
    }

    private static EconomicMap<String, EconomicSet<String>> copyMap(EconomicMap<String, EconomicSet<String>> values) {
        if (values == null) {
            return null;
        }
        EconomicMap<String, EconomicSet<String>> newMap = EconomicMap.create(Equivalence.DEFAULT, values);
        MapCursor<String, EconomicSet<String>> cursor = newMap.getEntries();
        while (cursor.advance()) {
            newMap.put(cursor.getKey(), EconomicSet.create(Equivalence.DEFAULT, cursor.getValue()));
        }
        return newMap;
    }

    UnmodifiableEconomicSet<String> getAccessibleLanguages(String language) {
        if (allAccess) {
            return null;
        } else {
            if (access == null) {
                return EMPTY;
            } else {
                EconomicSet<String> a = access.get(language);
                if (a == null) {
                    return EMPTY;
                }
                return a;
            }
        }
    }

    /**
     * Provides guest languages no access to other languages using polyglot builtins.
     *
     * @since 19.0
     */
    public static final PolyglotAccess NONE = new PolyglotAccess(false, null);

    /**
     * Provides guest languages full access to other languages using polyglot builtins.
     *
     * @since 19.0
     */
    public static final PolyglotAccess ALL = new PolyglotAccess(true, null);

    /**
     * Creates a new custom polyglot access configuration builder. A polyglot access builder starts
     * with no access rights.
     *
     * @since 19.2
     */
    public static Builder newBuilder() {
        return NONE.new Builder();
    }

    /**
     * A builder for a polyglot access configuration. Builder instances are not thread-safe.
     *
     * @since 19.2
     */
    public final class Builder {

        private EconomicMap<String, EconomicSet<String>> access;

        Builder() {
        }

        /**
         * Allows bidirectional access between the given languages. When called with language
         * <code>"A"</code> and language <code>"B"</code>, this is equivalent to calling
         * <code>{@linkplain #allowAccess(String, String) allowAccess}("A", "B")</code> and
         * <code>{@linkplain #allowAccess(String, String) allowAccess}("B", "A")</code>. If called
         * with more than two then all language access combinations will be allowed. This method
         * potentially overrides already configured access rights with
         * {@link #allowAccess(String, String)} or {@link #denyAccess(String, String)}. The given
         * language array must be <code>null</code> and individual languages must not be
         * <code>null</code>.
         *
         * @see #allowAccess(String, String)
         * @since 19.2
         */
        public Builder allowAccessBetween(String... languages) {
            Objects.requireNonNull(languages);
            if (access == null) {
                access = EconomicMap.create();
            }
            for (String language : languages) {
                Objects.requireNonNull(language);
                EconomicSet<String> languageAccess = access.get(language);
                if (languageAccess == null) {
                    languageAccess = EconomicSet.create();
                    access.put(language, languageAccess);
                }
                languageAccess.addAll(Arrays.asList(languages));
            }
            return this;
        }

        /**
         * Denies bidirectional access between the given languages. When called with language
         * <code>"A"</code> and language <code>"B"</code>, this is equivalent to calling
         * <code>{@linkplain #denyAccess(String, String) denyAccess}("A", "B")</code> and
         * <code>{@linkplain #denyAccess(String, String) denyAccess}("B", "A")</code>. If called
         * with more than two then all language access combinations will be denied. This method
         * potentially overrides already configured access rights with
         * {@link #allowAccess(String, String)} or {@link #denyAccess(String, String)}. The given
         * language array must be <code>null</code> and individual languages must not be
         * <code>null</code>.
         *
         * @see #denyAccess(String, String)
         * @since 19.2
         */
        public Builder denyAccessBetween(String... languages) {
            Objects.requireNonNull(languages);
            if (access != null) {
                for (String language : languages) {
                    Objects.requireNonNull(language);
                    EconomicSet<String> languageAccess = access.get(language);
                    if (languageAccess != null) {
                        languageAccess.removeAll(Arrays.asList(languages));
                    }
                }
            }
            return this;
        }

        /**
         * Allows access from one language to another. This method only allows one-way access. Every
         * language has implicitly access to itself. This access may but does not need to be
         * granted. If a language has access to one ore more different languages then the guest
         * application will have access to polyglot builtins and the
         * {@link Context#getPolyglotBindings() polyglot bindings}. If a language has no granted
         * access to another language then access to polyglot builtins and bindings are denied.
         *
         * @see #allowAccessBetween(String...)
         * @since 19.2
         */
        public Builder allowAccess(String from, String to) {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            if (access == null) {
                access = EconomicMap.create();
            }
            EconomicSet<String> languageAccess = access.get(from);
            if (languageAccess == null) {
                languageAccess = EconomicSet.create();
                access.put(from, languageAccess);
            }
            languageAccess.add(to);
            return this;
        }

        /**
         * Denies access from one language to another. This method only denies one-way access. Every
         * language has always access to itself. This access may but does cannot be denied.
         *
         * @see #denyAccessBetween(String...)
         * @since 19.2
         */
        public Builder denyAccess(String from, String to) {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            if (access != null) {
                EconomicSet<String> languageAccess = access.get(from);
                if (languageAccess != null) {
                    languageAccess.remove(to);
                }
            }
            return this;
        }

        /**
         * Creates an instance of the custom polyglot access configuration. This method may be
         * called multiple times to create more than once.
         *
         * @since 19.2
         */
        public PolyglotAccess build() {
            return new PolyglotAccess(false, access);
        }
    }
}
