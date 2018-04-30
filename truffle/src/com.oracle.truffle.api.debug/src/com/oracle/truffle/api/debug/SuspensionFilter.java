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
package com.oracle.truffle.api.debug;

import java.util.function.Predicate;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/**
 * A filter to limit the suspension locations. An instance of this filter can be provided to
 * {@link DebuggerSession#setSteppingFilter(com.oracle.truffle.api.debug.SuspensionFilter) debugger
 * session} to step on locations matching this filter only.
 *
 * @see DebuggerSession#setSteppingFilter(com.oracle.truffle.api.debug.SuspensionFilter)
 * @since 0.26
 */
public final class SuspensionFilter {

    private final boolean ignoreLanguageContextInitialization;
    private final boolean includeInternal;
    private final Predicate<Source> sourcePredicate;

    private SuspensionFilter() {
        this.ignoreLanguageContextInitialization = false;
        this.includeInternal = false;
        this.sourcePredicate = null;
    }

    private SuspensionFilter(boolean ignoreLanguageContextInitialization, boolean includeInternal, Predicate<Source> sourcePredicate) {
        this.ignoreLanguageContextInitialization = ignoreLanguageContextInitialization;
        this.includeInternal = includeInternal;
        this.sourcePredicate = sourcePredicate;
    }

    /**
     * Returns a builder for creating a new suspension filter.
     *
     * @since 0.26
     */
    public static Builder newBuilder() {
        return new SuspensionFilter().new Builder();
    }

    /**
     * Test if execution of language initialization code is to be ignored.
     *
     * @since 0.26
     */
    public boolean isIgnoreLanguageContextInitialization() {
        return ignoreLanguageContextInitialization;
    }

    /**
     * Test if execution of {@link RootNode#isInternal() internal code} is included.
     */
    boolean isInternalIncluded() {
        return includeInternal;
    }

    /**
     * Get a {@link Predicate} that filters based on a {@link Source}.
     */
    Predicate<Source> getSourcePredicate() {
        return sourcePredicate;
    }

    /**
     * A builder for creating a suspension filter.
     *
     * @since 0.26
     */
    public final class Builder {

        private boolean ignoreLanguageContextInitialization;
        private boolean includeInternal = false;
        private Predicate<Source> sourcePredicate;

        private Builder() {
        }

        /**
         * Set to ignore language initialization code. The language initialization code is not
         * ignored by default.
         *
         * @param ignore <code>true</code> to ignore execution of language context initialization
         *            code, <code>false</code> not to ignore it.
         * @since 0.26
         */
        public Builder ignoreLanguageContextInitialization(boolean ignore) {
            this.ignoreLanguageContextInitialization = ignore;
            return this;
        }

        /**
         * Set to include or exclude {@link RootNode#isInternal() internal code} in the filter.
         * Internal code is excluded by default.
         *
         * @param internal <code>true</code> to include execution of internal code,
         *            <code>false</code> to exclude it.
         * @since 0.29
         */
        public Builder includeInternal(boolean internal) {
            this.includeInternal = internal;
            return this;
        }

        /**
         * Set a {@link Predicate} that filters based on a {@link Source}. The predicate must always
         * return the same result for a source instance otherwise the behavior is undefined. The
         * predicate should be able run on multiple threads at the same time.
         *
         * @param filter a source section filter
         * @since 0.29
         */
        public Builder sourceIs(Predicate<Source> filter) {
            this.sourcePredicate = filter;
            return this;
        }

        /**
         * Create a new suspension filter configured by the builder methods.
         *
         * @since 0.26
         */
        public SuspensionFilter build() {
            return new SuspensionFilter(ignoreLanguageContextInitialization, includeInternal, sourcePredicate);
        }
    }
}
