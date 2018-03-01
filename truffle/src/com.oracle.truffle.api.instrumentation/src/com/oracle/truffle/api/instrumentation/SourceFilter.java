/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter.EventFilterExpression;
import com.oracle.truffle.api.source.Source;
import java.util.Arrays;

/**
 * A source filter represents an expression for a subset of guest language sources that are used in
 * an Truffle interpreter.
 * <p>
 * Start building event filters by calling {@link SourceFilter#newBuilder()} and complete them by
 * calling {@link Builder#build()}.
 *
 * @see SourceFilter#newBuilder()
 * @see Instrumenter#attachLoadSourceListener(SourceFilter, LoadSourceListener, boolean)
 * @since 0.32
 */
public final class SourceFilter {

    /**
     * A filter that matches any source.
     *
     * @since 0.32
     */
    public static final SourceFilter ANY = newBuilder().build();

    final EventFilterExpression[] expressions;

    private SourceFilter(EventFilterExpression[] expressions) {
        this.expressions = expressions;
    }

    /**
     * Creates a new {@link SourceFilter} expression using a {@link Builder builder} pattern.
     * Individual builder statements are interpreted as conjunctions (AND) while multiple parameters
     * for individual filter expressions are treated as disjunctions (OR). To create the final
     * filter finalize the expression using {@link Builder#build()}.
     *
     * @see Builder#sourceIs(Source...)
     * @see Builder#sourceIs(Predicate)
     * @see Builder#languageIs(String...)
     * @see Builder#includeInternal(boolean)
     * @see Builder#build()
     *
     * @return a new builder to create new {@link SourceFilter} instances
     * @since 0.32
     */
    public static SourceFilter.Builder newBuilder() {
        return new SourceFilter(null).new Builder();
    }

    /**
     * Configure your own {@link SourceFilter} before creating its instance. Specify various
     * parameters by calling individual {@link Builder} methods. When done, call {@link #build()}.
     *
     * @since 0.32
     */
    public final class Builder {

        private List<EventFilterExpression> expressions = new ArrayList<>();
        private boolean includeInternal = true;

        private Builder() {
        }

        /**
         * Add a filter for one of the given sources.
         *
         * @since 0.32
         */
        public Builder sourceIs(Source... source) {
            SourceSectionFilter.verifyNotNull(source);
            expressions.add(new EventFilterExpression.SourceIs(source));
            return this;
        }

        /**
         * Adds custom predicate to filter inclusion of {@link Source sources}. The predicate must
         * always return the same result for a source instance otherwise the behavior is undefined.
         * The predicate should be able run on multiple threads at the same time.
         *
         * @param predicate a test for source inclusion
         * @since 0.32
         */
        public Builder sourceIs(Predicate<Source> predicate) {
            if (predicate == null) {
                throw new IllegalArgumentException("Source predicate must not be null.");
            }
            expressions.add(new EventFilterExpression.SourceFilterIs(predicate));
            return this;
        }

        /**
         * Add a filter for all sources that specify one of the given {@link Source#getLanguage()
         * language ID}.
         *
         * @param languageIds matches one of the given language ID
         * @return the builder to chain calls
         * @since 0.32
         */
        public Builder languageIs(String... languageIds) {
            SourceSectionFilter.verifyNotNull(languageIds);
            expressions.add(new EventFilterExpression.SourceFilterIs(new Predicate<Source>() {
                @Override
                public boolean test(Source source) {
                    String language = source.getLanguage();
                    if (language != null) {
                        for (String otherLanguage : languageIds) {
                            if (otherLanguage.equals(language)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }

                @Override
                public String toString() {
                    return String.format("language ID is one-of %s", Arrays.toString(languageIds));
                }
            }));
            return this;
        }

        /**
         * Add a filter that includes or excludes {@link Source#isInternal() internal sources}. By
         * default, internal sources are included, call with <code>false</code> to exclude internal
         * code from instrumentation.
         *
         * @return the builder to chain calls
         * @since 0.32
         */
        public Builder includeInternal(boolean internal) {
            this.includeInternal = internal;
            return this;
        }

        /**
         * Finalizes and constructs the {@link SourceFilter} instance.
         *
         * @return the built filter expression
         * @since 0.32
         */
        public SourceFilter build() {
            if (!includeInternal) {
                expressions.add(new EventFilterExpression.SourceFilterIs(new Predicate<Source>() {
                    @Override
                    public boolean test(Source source) {
                        return !source.isInternal();
                    }

                    @Override
                    public String toString() {
                        return "source is not internal";
                    }
                }));
            }
            Collections.sort(expressions);
            return new SourceFilter(expressions.toArray(new EventFilterExpression[0]));
        }

    }
}
