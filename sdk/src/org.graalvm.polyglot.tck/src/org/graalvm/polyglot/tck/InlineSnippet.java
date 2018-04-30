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
package org.graalvm.polyglot.tck;

import java.util.Objects;
import java.util.function.Predicate;

import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

/**
 * The unit of execution with an inline source. The {@link InlineSnippet} represents an execution of
 * a script and an associated inline source code that is to be executed at script's locations.
 * <p>
 * It's recommended to provide two snippets at least, one with local variables and restricted
 * {@link Builder#locationPredicate(Predicate) location predicate} and one with globally accessible
 * {@link #getCode() code} and no location predicate.
 *
 * @since 0.32
 */
public final class InlineSnippet {

    private final Snippet script;
    private final Predicate<SourceSection> locationPredicate;
    private final CharSequence code;
    private final ResultVerifier verifier;

    private InlineSnippet(
                    final Snippet script,
                    final CharSequence source,
                    final Predicate<SourceSection> locationPredicate,
                    final ResultVerifier verifier) {
        this.script = script;
        this.code = source;
        this.locationPredicate = locationPredicate;
        this.verifier = verifier;
    }

    /**
     * Returns a script to be executed. The {@link #getCode() source code} is executed inlined in
     * this script.
     *
     * @since 0.32
     */
    public Snippet getScript() {
        return this.script;
    }

    /**
     * Returns an inline source code that is to be executed at specific
     * {@link #getLocationPredicate() locations}, or at all statement and call locations if the
     * predicate is <code>null</code>.
     *
     * @since 0.32
     */
    public CharSequence getCode() {
        return code;
    }

    /**
     * Returns a testing predicate for locations at which {@link #getCode() source code} is
     * executed.
     *
     * @return the predicate that returns <code>true</code> for {@link SourceSection}s where the
     *         {@link #getCode() source code} is to be executed, or <code>null</code> if the source
     *         code should be executed at all instrumentable statement and call locations.
     * @since 0.32
     */
    public Predicate<SourceSection> getLocationPredicate() {
        return locationPredicate;
    }

    /**
     * Returns the {@link ResultVerifier} to verify the result of {@link #getCode() source code}
     * execution.
     *
     * @return either a custom or the default {@link ResultVerifier}.
     * @see ResultVerifier#accept(org.graalvm.polyglot.tck.ResultVerifier.SnippetRun).
     * @since 0.32
     */
    public ResultVerifier getResultVerifier() {
        return verifier;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.32
     */
    @Override
    public String toString() {
        return new StringBuilder(script.getId()).append(" : inline source").toString();
    }

    /**
     * Creates a new {@link InlineSnippet} builder object.
     *
     * @param script the script to execute (use
     *            {@link Snippet#newBuilder(String, Value, TypeDescriptor)} to create one)
     * @param code the inline source code that is to be executed inside the script
     * @return the new {@link Builder}
     * @since 0.32
     */
    public static Builder newBuilder(
                    final Snippet script,
                    final CharSequence code) {
        return new Builder(script, code);
    }

    /**
     * The builder of an {@link InlineSnippet}.
     *
     * @since 0.32
     */
    public static final class Builder {

        private final Snippet script;
        private final CharSequence code;
        private Predicate<SourceSection> predicate;
        private ResultVerifier verifier;

        private Builder(
                        final Snippet script,
                        final CharSequence code) {
            Objects.requireNonNull(script);
            Objects.requireNonNull(code);
            this.script = script;
            this.code = code;
        }

        /**
         * Sets a testing predicate for locations at which the source is executed. By default, the
         * source is executed at all instrumentable statement and call locations in the script.
         *
         * @return the predicate that returns <code>true</code> for {@link SourceSection}s where
         *         source is to be executed
         * @since 0.32
         */
        public Builder locationPredicate(final Predicate<SourceSection> locationPredicate) {
            this.predicate = locationPredicate;
            return this;
        }

        /**
         * Sets a custom verifier of a result of the {@link InlineSnippet#getCode() inline source}
         * execution.
         *
         * @param resultVerifier the custom {@link ResultVerifier}
         * @return this {@link Builder}
         * @see ResultVerifier#accept(org.graalvm.polyglot.tck.ResultVerifier.SnippetRun).
         * @since 0.32
         */
        public Builder resultVerifier(final ResultVerifier resultVerifier) {
            this.verifier = resultVerifier;
            return this;
        }

        /**
         * Creates a new {@link InlineSnippet} configured by this {@link Builder}.
         *
         * @return the {@link InlineSnippet}
         * @since 0.32
         */
        public InlineSnippet build() {
            return new InlineSnippet(
                            script,
                            code,
                            predicate,
                            verifier != null ? verifier : ResultVerifier.getDefaultResultVerifier());
        }
    }
}
