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

import com.oracle.truffle.api.source.SourceSection;

/**
 * A filter to skip certain suspension locations. An instance of this filter can be provided to
 * {@link DebuggerSession#setSteppingFilter(com.oracle.truffle.api.debug.SuspensionFilter) debugger
 * session} to skip matching code during stepping.
 *
 * @see DebuggerSession#setSteppingFilter(com.oracle.truffle.api.debug.SuspensionFilter)
 * @since 0.26
 */
public class SuspensionFilter {

    private final boolean ignoreLanguageContextInitialization;
    private final boolean ignoreInternal;
    private final Predicate<SourceSection> sourceFilter;

    SuspensionFilter() {
        this.ignoreLanguageContextInitialization = false;
        this.ignoreInternal = false;
        this.sourceFilter = null;
    }

    private SuspensionFilter(boolean ignoreLanguageContextInitialization, boolean ignoreInternal, Predicate<SourceSection> sourceFilter) {
        this.ignoreLanguageContextInitialization = ignoreLanguageContextInitialization;
        this.ignoreInternal = ignoreInternal;
        this.sourceFilter = sourceFilter;
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

    public boolean isIgnoreInternal() {
        return ignoreInternal;
    }

    public Predicate<SourceSection> getSourceFilter() {
        return sourceFilter;
    }

    /**
     * A builder for creating a suspension filter.
     *
     * @since 0.26
     */
    public final class Builder {

        private boolean ignoreLanguageContextInitialization;
        private boolean ignoreInternal;
        private Predicate<SourceSection> sourceFilter;

        private Builder() {
        }

        /**
         * Set to ignore language initialization code.
         *
         * @param ignore <code>true</code> to ignore execution of language context initialization
         *            code, <code>false</code> not to ignore it.
         * @since 0.26
         */
        public Builder ignoreLanguageContextInitialization(boolean ignore) {
            this.ignoreLanguageContextInitialization = ignore;
            return this;
        }

        public Builder ignoreInternal(boolean ignore) {
            this.ignoreInternal = ignore;
            return this;
        }

        public Builder sourceFilter(Predicate<SourceSection> filter) {
            this.sourceFilter = filter;
            return this;
        }

        /**
         * Create a new suspension filter configured by the builder methods.
         *
         * @since 0.26
         */
        public SuspensionFilter build() {
            return new SuspensionFilter(ignoreLanguageContextInitialization, ignoreInternal, sourceFilter);
        }
    }
}
