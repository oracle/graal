/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * An instrumentation filter of allocations of guest language values.
 * <p>
 * To create the filter, use {@link #newBuilder()} and complete that by calling
 * {@link Builder#build()}.
 *
 * @see #newBuilder()
 * @see Instrumenter#attachAllocationListener(com.oracle.truffle.api.instrumentation.AllocationEventFilter,
 *      com.oracle.truffle.api.instrumentation.AllocationListener)
 *
 * @since 0.27
 */
public final class AllocationEventFilter {

    /**
     * A filter that matches all allocations in all languages.
     *
     * @since 0.27
     */
    public static final AllocationEventFilter ANY = newBuilder().build();

    private final Set<LanguageInfo> languageSet;

    AllocationEventFilter(Set<LanguageInfo> languages) {
        this.languageSet = languages;
    }

    /**
     * Creates a new {@link AllocationEventFilter} instance through a {@link Builder}.
     *
     * @since 0.27
     */
    public static Builder newBuilder() {
        return new AllocationEventFilter(null).new Builder();
    }

    boolean contains(LanguageInfo li) {
        if (languageSet == null) {
            return true;
        } else {
            return languageSet.contains(li);
        }
    }

    /**
     * A builder of {@link AllocationEventFilter}. Use methods in this class to set the filter
     * parameters.
     *
     * @since 0.27
     */
    public class Builder {

        LanguageInfo[] langs;

        Builder() {
        }

        /**
         * Specify languages that are instrumented for allocations of guest language values.
         * Initially the filter accepts any language. Multiple calls to this method rewrite the set
         * of languages.
         *
         * @since 0.27
         */
        public Builder languages(LanguageInfo... languages) {
            if (languages.length == 0) {
                throw new IllegalArgumentException("At least one language must be provided.");
            }
            this.langs = languages;
            return this;
        }

        /**
         * Create an instance of {@link AllocationEventFilter} based on the current setup of this
         * builder.
         *
         * @since 0.27
         */
        public AllocationEventFilter build() {
            Set<LanguageInfo> langSet;
            if (langs == null) {
                langSet = null;
            } else if (langs.length == 1) {
                langSet = Collections.singleton(langs[0]);
            } else {
                langSet = new HashSet<>();
                for (LanguageInfo li : langs) {
                    langSet.add(li);
                }
            }
            return new AllocationEventFilter(langSet);
        }
    }
}
