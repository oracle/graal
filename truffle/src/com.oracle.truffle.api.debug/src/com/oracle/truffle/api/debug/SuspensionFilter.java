/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.function.Predicate;

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

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
    private final boolean includeAvailableSourceSectionsOnly;
    private final Predicate<Source> sourcePredicate;

    private SuspensionFilter() {
        this.ignoreLanguageContextInitialization = false;
        this.includeInternal = false;
        this.includeAvailableSourceSectionsOnly = false;
        this.sourcePredicate = null;
    }

    private SuspensionFilter(boolean ignoreLanguageContextInitialization, boolean includeInternal, boolean includeAvailableSourceSectionsOnly, Predicate<Source> sourcePredicate) {
        this.ignoreLanguageContextInitialization = ignoreLanguageContextInitialization;
        this.includeInternal = includeInternal;
        this.includeAvailableSourceSectionsOnly = includeAvailableSourceSectionsOnly;
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
     * Test if only available source sections are included.
     */
    boolean isIncludeAvailableSourceSectionsOnly() {
        return includeAvailableSourceSectionsOnly;
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
        private boolean includeAvailableSourceSectionsOnly = false;
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
         * Set to suspend on available source sections only. By default all locations with or
         * without available source sections are suspended. If this flag is set to {@code true} then
         * {@code null} and not {@link SourceSection#isAvailable() available} source sections are
         * not suspended.
         *
         * @param availableOnly <code>true</code> to include only non-null and
         *            {@link SourceSection#isAvailable() available}
         *            {@link SuspendedEvent#getSourceSection() SourceSection}, <code>false</code> to
         *            include all.
         * @since 24.1
         */
        public Builder sourceSectionAvailableOnly(boolean availableOnly) {
            this.includeAvailableSourceSectionsOnly = availableOnly;
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
            return new SuspensionFilter(ignoreLanguageContextInitialization, includeInternal, includeAvailableSourceSectionsOnly, sourcePredicate);
        }
    }
}
