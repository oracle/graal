/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
