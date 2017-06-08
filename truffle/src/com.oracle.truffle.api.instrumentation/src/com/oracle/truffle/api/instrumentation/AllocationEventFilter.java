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
