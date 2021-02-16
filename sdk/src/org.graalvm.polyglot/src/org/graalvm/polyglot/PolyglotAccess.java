/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * The following access rights may be granted:
 * <ul>
 * <li>The ability to evaluate code {@link Builder#allowEvalBetween(String...) between} two or just
 * for {@link Builder#allowEval(String, String) one} language. If a language has access to at least
 * one other language then the polyglot eval builtin will be available, otherwise access to that
 * builtin might be restricted. The concrete name of the polyglot eval builtin is language specific.
 * In JavaScript it is called <code>Polyglot.eval</code>.
 * <li>The ability to access members in the {@link Builder#allowBindingsAccess(String) polyglot
 * bindings}. The names of the guest language builtins to access polyglot bindings are language
 * specific. In JavaScript they are called <code>Polyglot.import</code> and
 * <code>Polyglot.export</code>.
 * </ul>
 * Access to polyglot evaluation and bindings builtins are always enabled when access policy
 * {@link #ALL} is used. In this mode polyglot evaluation builtins are available even if there is
 * just one {@link Engine#getLanguages() installed} language available.
 *
 * @since 19.0
 */
public final class PolyglotAccess {

    private static final UnmodifiableEconomicSet<String> EMPTY = EconomicSet.create();

    private final EconomicMap<String, EconomicSet<String>> evalAccess;
    private final EconomicSet<String> bindingsAccess;
    private final boolean allAccess;

    PolyglotAccess(boolean allAccess, EconomicMap<String, EconomicSet<String>> access, EconomicSet<String> bindingsAccess) {
        this.allAccess = allAccess;
        this.evalAccess = copyMap(access);
        this.bindingsAccess = bindingsAccess;
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

    String validate(UnmodifiableEconomicSet<String> availableLanguages) {
        if (evalAccess != null) {
            MapCursor<String, EconomicSet<String>> entries = evalAccess.getEntries();
            while (entries.advance()) {
                String invalidKey = null;
                if (!availableLanguages.contains(entries.getKey())) {
                    invalidKey = entries.getKey();
                }
                if (invalidKey == null) {
                    for (String entry : entries.getValue()) {
                        if (!availableLanguages.contains(entry)) {
                            invalidKey = entry;
                            break;
                        }
                    }
                }
                if (invalidKey != null) {
                    return String.format("Language '%s' configured in polyglot evaluation rule %s -> %s is not installed or available.",
                                    invalidKey, entries.getKey(), toStringSet(entries.getValue()));
                }
            }
        }

        if (bindingsAccess != null) {
            for (String language : bindingsAccess) {
                if (!availableLanguages.contains(language)) {
                    return String.format("Language '%s' configured in polyglot bindings access rule is not installed or available.",
                                    language);
                }
            }
        }
        return null;

    }

    static String toStringSet(EconomicSet<String> set) {
        StringBuilder b = new StringBuilder();
        String sep = "";
        for (String entry : set) {
            b.append(sep);
            b.append(entry);
            sep = ", ";
        }
        return b.toString();
    }

    UnmodifiableEconomicSet<String> getEvalAccess(String language) {
        if (allAccess) {
            return null;
        } else {
            if (evalAccess == null) {
                return EMPTY;
            } else {
                EconomicSet<String> a = evalAccess.get(language);
                if (a == null) {
                    return EMPTY;
                }
                return a;
            }
        }
    }

    UnmodifiableEconomicSet<String> getBindingsAccess() {
        if (allAccess) {
            return null;
        } else {
            if (bindingsAccess == null) {
                return EMPTY;
            } else {
                return bindingsAccess;
            }
        }
    }

    /**
     * Provides guest languages no access to other languages using polyglot builtins evaluation and
     * binding builtins.
     *
     * @since 19.0
     */
    public static final PolyglotAccess NONE = new PolyglotAccess(false, null, null);

    /**
     * Provides guest languages full access to other languages using polyglot evaluation and binding
     * builtins.
     *
     * @since 19.0
     */
    public static final PolyglotAccess ALL = new PolyglotAccess(true, null, null);

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

        private EconomicMap<String, EconomicSet<String>> evalAccess;
        private EconomicSet<String> bindingsAccess;

        Builder() {
        }

        /**
         * Allows bidirectional evaluation of code between the given languages. When called with
         * language <code>"A"</code> and language <code>"B"</code>, this is equivalent to calling
         * <code>{@linkplain #allowEval(String, String) allowEval}("A", "B")</code> and
         * <code>{@linkplain #allowEval(String, String) allowEval}("B", "A")</code>. If called with
         * more than two then all language evaluation combinations will be allowed. This method
         * potentially overrides already configured access rights with
         * {@link #allowEval(String, String)} or {@link #denyEval(String, String)}. The given
         * language array must be <code>null</code> and individual languages must not be
         * <code>null</code>.
         *
         * @see #allowEval(String, String)
         * @since 19.2
         */
        public Builder allowEvalBetween(String... languages) {
            Objects.requireNonNull(languages);
            if (evalAccess == null) {
                evalAccess = EconomicMap.create();
            }
            for (String language : languages) {
                Objects.requireNonNull(language);
                EconomicSet<String> languageAccess = evalAccess.get(language);
                if (languageAccess == null) {
                    languageAccess = EconomicSet.create();
                    evalAccess.put(language, languageAccess);
                }
                languageAccess.addAll(Arrays.asList(languages));
            }
            return this;
        }

        /**
         * Denies bidirectional evaluation of code between the given languages. When called with
         * language <code>"A"</code> and language <code>"B"</code>, this is equivalent to calling
         * <code>{@linkplain #denyEval(String, String) denyEval}("A", "B")</code> and
         * <code>{@linkplain #denyEval(String, String) denyEval}("B", "A")</code>. If called with
         * more than two then all language access combinations will be denied. This method
         * potentially overrides already configured access rights with
         * {@link #allowEval(String, String)} or {@link #denyEval(String, String)}. The given
         * language array must be <code>null</code> and individual languages must not be
         * <code>null</code>.
         *
         * @see #denyEval(String, String)
         * @since 19.2
         */
        public Builder denyEvalBetween(String... languages) {
            Objects.requireNonNull(languages);
            if (evalAccess != null) {
                for (String language : languages) {
                    Objects.requireNonNull(language);
                    EconomicSet<String> languageAccess = evalAccess.get(language);
                    if (languageAccess != null) {
                        languageAccess.removeAll(Arrays.asList(languages));
                    }
                }
            }
            return this;
        }

        /**
         * Allows evaluation of code by one language of another. This method only allows one-way
         * evaluation access. Every language always has implicitly access to itself. If a language
         * has access to one ore more different languages then the guest application will have
         * access to polyglot evaluation builtins. If a language has no access granted to another
         * language then access to polyglot evaluation builtins is denied.
         *
         * @see #allowEvalBetween(String...)
         * @since 19.2
         */
        public Builder allowEval(String from, String to) {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            if (evalAccess == null) {
                evalAccess = EconomicMap.create();
            }
            EconomicSet<String> languageAccess = evalAccess.get(from);
            if (languageAccess == null) {
                languageAccess = EconomicSet.create();
                evalAccess.put(from, languageAccess);
            }
            languageAccess.add(to);
            return this;
        }

        /**
         * Denies evaluation of code by one language of another. This method only denies one-way
         * evaluation. Every language has always evaluation access to itself. This access cannot be
         * denied.
         *
         * @see #denyEvalBetween(String...)
         * @since 19.2
         */
        public Builder denyEval(String from, String to) {
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
            if (evalAccess != null) {
                EconomicSet<String> languageAccess = evalAccess.get(from);
                if (languageAccess != null) {
                    languageAccess.remove(to);
                }
            }
            return this;
        }

        /**
         * Allows access to polyglot bindings for a language. The names of the guest language
         * builtins to access polyglot bindings are language specific. In JavaScript they are called
         * <code>Polyglot.import</code> and <code>Polyglot.export</code>.
         *
         * @see #denyBindingsAccess(String)
         * @since 19.2
         */
        public Builder allowBindingsAccess(String language) {
            Objects.requireNonNull(language);
            if (bindingsAccess == null) {
                bindingsAccess = EconomicSet.create();
            }
            bindingsAccess.add(language);
            return this;
        }

        /**
         * Denies access to polyglot bindings for a language. The provided language must not be
         * <code>null</code>.
         *
         * @see #allowBindingsAccess(String)
         * @since 19.2
         */
        public Builder denyBindingsAccess(String language) {
            Objects.requireNonNull(language);
            if (bindingsAccess != null) {
                bindingsAccess.remove(language);
            }
            return this;
        }

        /**
         * Creates an instance of the custom polyglot access configuration. This method may be
         * called multiple times to create more than one independent instances.
         *
         * @since 19.2
         */
        public PolyglotAccess build() {
            return new PolyglotAccess(false, evalAccess, bindingsAccess);
        }
    }
}
